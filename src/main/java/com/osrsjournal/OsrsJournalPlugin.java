package com.osrsjournal;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * OSRS Journal Plugin
 *
 * <p>Listens for in-game events and syncs the following to the OSRS Journal cloud
 * (journal.osrsjournal.com):
 * <ul>
 *   <li>All 23 skill levels + XP ({@link StatChanged})</li>
 *   <li>All quest states ({@link GameTick} — polled every ~1 min)</li>
 *   <li>Worn equipment ({@link ItemContainerChanged} for {@code InventoryID.EQUIPMENT})</li>
 *   <li>Bank contents ({@link ItemContainerChanged} for {@code InventoryID.BANK})</li>
 * </ul>
 *
 * <p><strong>Threading model:</strong>
 * All {@code @Subscribe} handlers run on the <em>client thread</em>.
 * Data is collected there, then dispatched to the {@link ScheduledExecutorService}
 * for off-thread network I/O via {@link HostedApiService}. The client thread is
 * never blocked by network activity.
 */
@Slf4j
@PluginDescriptor(
    name = "OSRS Journal",
    configName = "osrsjournal",
    description = "Syncs your character to journal.osrsjournal.com — stats, quests, gear and bank with a sidebar summary",
    tags = {"sync", "cloud", "stats", "quests", "journal", "tracker"}
)
public class OsrsJournalPlugin extends Plugin
{
    // How often to poll for quest state changes (game ticks — 1 tick ≈ 0.6 s)
    private static final int QUEST_POLL_TICKS = 100; // ~1 minute

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OsrsJournalConfig config;

    @Inject
    private JournalSyncService journalSyncService;

    @Inject
    private PairingService pairingService;

    @Inject
    private HostedApiService hostedApiService;

    /**
     * RuneLite's shared thread pool — use this for all off-thread work.
     * Never create your own ExecutorService in a plugin.
     */
    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ClientToolbar clientToolbar;

    private OsrsJournalPanel panel;
    private NavigationButton navButton;
    private boolean openedSidebarOnce;

    // ── State ──────────────────────────────────────────────────────────────────

    /** Debounce handle for skill sync — cancelled/rescheduled on every {@link StatChanged}. */
    private ScheduledFuture<?> skillSyncFuture;

    /** Snapshot of quest states used to detect changes between polls. */
    private final Map<Quest, QuestState> lastQuestStates = new EnumMap<>(Quest.class);

    private int ticksSinceQuestPoll = 0;
    private String currentRsn = null;

    // ── Plugin lifecycle ───────────────────────────────────────────────────────

    @Override
    public void configure(Binder binder)
    {
        binder.bind(OsrsJournalPanel.class);
        binder.bind(JournalBrowser.class);
    }

    @Override
    protected void startUp()
    {
        panel = injector.getInstance(OsrsJournalPanel.class);
        panel.init();
        panel.setRefreshListener(this::manualRefresh);

        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "journal_icon.png");
        if (icon == null)
        {
            icon = ImageUtil.loadImageResource(getClass(), "/osrsjournal/journal_icon.png");
        }
        if (icon == null)
        {
            log.warn("OSRS Journal icon missing from plugin jar — sidebar tab may not appear");
        }

        navButton = NavigationButton.builder()
            .tooltip("OSRS Journal")
            .icon(icon)
            .priority(2)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
        refreshPanel();
        log.info("OSRS Journal sidebar panel registered");
    }

    @Override
    protected void shutDown()
    {
        cancelSkillDebounce();
        lastQuestStates.clear();
        currentRsn = null;
        clientToolbar.removeNavigation(navButton);
        panel = null;
        navButton = null;
        log.debug("OSRS Journal stopped");
    }

    @Provides
    OsrsJournalConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OsrsJournalConfig.class);
    }

    // ── Event handlers (all run on the client thread) ─────────────────────────

    /**
     * Triggered when the game state changes.
     *
     * <p>On {@link GameState#LOGGED_IN} we schedule a full sync 2 s later (the player
     * name and stats aren't always readable in the same tick that the event fires).
     * On logout / world hop we clear state to avoid stale RSN references.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        switch (event.getGameState())
        {
            case LOGGED_IN:
                // Give the client a moment to fully load the player
                executor.schedule(this::performLoginSync, 2, TimeUnit.SECONDS);
                if (!openedSidebarOnce && navButton != null)
                {
                    openedSidebarOnce = true;
                    SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
                }
                break;
            case LOGIN_SCREEN:
            case HOPPING:
                currentRsn = null;
                lastQuestStates.clear();
                pairingService.clearState();
                cancelSkillDebounce();
                SwingUtilities.invokeLater(() ->
                {
                    if (panel != null)
                    {
                        panel.updateSummary(null);
                    }
                });
                break;
            default:
                break;
        }
    }

    /**
     * Pushes the privacy flag to the cloud when the user flips the Public Profile
     * toggle in plugin settings. The website toggle is otherwise the source of truth.
     */
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"osrsjournal".equals(event.getGroup()) || !"publicProfile".equals(event.getKey()))
        {
            return;
        }
        final String rsn = currentRsn;
        if (rsn == null)
        {
            return;
        }
        final boolean isPublic = config.publicProfile();
        executor.execute(() ->
        {
            if (!journalSyncService.syncPrivacy(rsn, isPublic))
            {
                log.warn("OSRS Journal: failed to update profile privacy for '{}'", rsn);
            }
        });
    }

    /**
     * Triggered whenever a skill's XP, level, or boosted level changes.
     *
     * <p>Many {@link StatChanged} events fire in rapid succession on login (one per
     * skill). We debounce them — waiting {@link OsrsJournalConfig#skillDebounceSeconds()}
     * after the <em>last</em> event before doing a single batch sync.
     */
    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (!config.syncEnabled() || currentRsn == null)
        {
            return;
        }

        cancelSkillDebounce();

        final String rsn = currentRsn;
        skillSyncFuture = executor.schedule(() ->
            // Re-enter the client thread to safely read the Client API
            clientThread.invokeLater(() ->
            {
                if (client.getGameState() != GameState.LOGGED_IN) return;
                List<Map<String, Object>> records = buildSkillRecords(rsn);
                executor.execute(() -> syncSkills(rsn, records));
            }),
            config.skillDebounceSeconds(), TimeUnit.SECONDS
        );
    }

    /**
     * Triggered when the contents of an {@link ItemContainer} change.
     *
     * <p>We handle two containers:
     * <ul>
     *   <li>{@link InventoryID#EQUIPMENT} — syncs worn gear immediately</li>
     *   <li>{@link InventoryID#BANK} — full bank re-sync (delete + insert) when opened</li>
     * </ul>
     *
     * <p>Data is read here on the client thread, then handed to the executor for network I/O.
     */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!config.syncEnabled() || currentRsn == null)
        {
            return;
        }

        final int containerId = event.getContainerId();
        final String rsn = currentRsn;

        if (containerId == InventoryID.EQUIPMENT.getId())
        {
            List<Map<String, Object>> records = buildEquipmentRecords(rsn);
            executor.execute(() -> syncEquipment(rsn, records));
        }
        else if (containerId == InventoryID.BANK.getId() && config.syncBank())
        {
            List<Map<String, Object>> records = buildBankRecords(rsn);
            executor.execute(() -> syncBank(rsn, records));
        }
    }

    /**
     * Runs every game tick (~0.6 s).
     *
     * <p>Quest state changes don't have a dedicated event, so we poll every
     * {@link #QUEST_POLL_TICKS} ticks, compare against the last known snapshot,
     * and sync only what changed.
     */
    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!config.syncEnabled() || currentRsn == null)
        {
            return;
        }

        if (++ticksSinceQuestPoll >= QUEST_POLL_TICKS)
        {
            ticksSinceQuestPoll = 0;
            checkAndSyncQuestChanges();
        }
    }

    // ── Data collection (client thread only) ──────────────────────────────────

    /**
     * Called off-thread by {@link #onGameStateChanged} after login settles.
     * Re-enters the client thread to collect all data, then fires off network calls.
     */
    private void performLoginSync()
    {
        if (!config.syncEnabled()) return;

        clientThread.invokeLater(() ->
        {
            if (client.getGameState() != GameState.LOGGED_IN) return;

            Player localPlayer = client.getLocalPlayer();
            if (localPlayer == null) return;

            String rsn = localPlayer.getName();
            if (rsn == null || rsn.isEmpty()) return;

            currentRsn = rsn;
            log.debug("OSRS Journal: full sync for '{}'", rsn);

            // Collect all data on the client thread before going async
            List<Map<String, Object>> playerRecord = buildPlayerRecord(rsn);
            List<Map<String, Object>> skillRecords  = buildSkillRecords(rsn);
            List<Map<String, Object>> questRecords  = buildAllQuestRecords(rsn);
            List<Map<String, Object>> equipRecords  = buildEquipmentRecords(rsn);

            executor.execute(() ->
            {
                journalSyncService.ensurePairing(rsn);

                boolean ok = journalSyncService.syncLogin(
                    rsn, playerRecord, skillRecords, questRecords, equipRecords);
                if (!ok)
                {
                    log.warn("OSRS Journal: login sync partially failed for '{}'", rsn);
                }
                refreshPanel();
            });
        });
    }

    /**
     * Reads current quest states, diffs against {@link #lastQuestStates}, and syncs
     * only the changed quests. Called on the client thread from {@link #onGameTick}.
     */
    private void checkAndSyncQuestChanges()
    {
        final String rsn = currentRsn;
        List<Map<String, Object>> changed = new ArrayList<>();

        for (Quest quest : Quest.values())
        {
            QuestState current = quest.getState(client);
            QuestState prev = lastQuestStates.put(quest, current);

            if (current != prev)
            {
                log.debug("Quest changed: {} {} -> {}", quest.getName(), prev, current);
                changed.add(ImmutableMap.of(
                    "rsn",        rsn,
                    "quest_name", quest.getName(),
                    "state",      current.name()
                ));
            }
        }

        if (!changed.isEmpty())
        {
            executor.execute(() -> syncQuests(rsn, changed));
            refreshPanel();
        }
    }

    // ── Off-thread sync helpers (executor thread only) ────────────────────────

    private void syncSkills(String rsn, List<Map<String, Object>> records)
    {
        if (!journalSyncService.syncSkills(rsn, records))
        {
            log.warn("OSRS Journal: skill sync failed for '{}'", rsn);
        }
    }

    private void syncQuests(String rsn, List<Map<String, Object>> records)
    {
        if (!journalSyncService.syncQuests(rsn, records))
        {
            log.warn("OSRS Journal: quest sync failed for '{}'", rsn);
        }
    }

    private void syncEquipment(String rsn, List<Map<String, Object>> records)
    {
        journalSyncService.syncEquipment(rsn, records);
    }

    private void syncBank(String rsn, List<Map<String, Object>> records)
    {
        journalSyncService.syncBank(rsn, records);
    }

    // ── Record builders (must be called on the client thread) ─────────────────

    private List<Map<String, Object>> buildPlayerRecord(String rsn)
    {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(ImmutableMap.of(
            "rsn",          rsn,
            "last_synced",  nowIso(),
            "quest_points", client.getVarpValue(VarPlayer.QUEST_POINTS)
        ));
        return records;
    }

    private List<Map<String, Object>> buildSkillRecords(String rsn)
    {
        List<Map<String, Object>> records = new ArrayList<>();

        for (Skill skill : Skill.values())
        {
            // Skill.OVERALL is a meta-value; handle it separately
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            records.add(ImmutableMap.of(
                "rsn",   rsn,
                "skill", skill.getName().toLowerCase(),
                "level", client.getRealSkillLevel(skill),
                "xp",    client.getSkillExperience(skill)
            ));
        }

        // Add overall totals
        records.add(ImmutableMap.of(
            "rsn",   rsn,
            "skill", "overall",
            "level", client.getTotalLevel(),
            "xp",    computeTotalXp()
        ));

        return records;
    }

    private List<Map<String, Object>> buildAllQuestRecords(String rsn)
    {
        List<Map<String, Object>> records = new ArrayList<>();
        lastQuestStates.clear();

        for (Quest quest : Quest.values())
        {
            QuestState state = quest.getState(client);
            lastQuestStates.put(quest, state);
            records.add(ImmutableMap.of(
                "rsn",        rsn,
                "quest_name", quest.getName(),
                "state",      state.name()
            ));
        }

        return records;
    }

    private List<Map<String, Object>> buildEquipmentRecords(String rsn)
    {
        ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
        if (container == null)
        {
            return new ArrayList<>();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        Item[] items = container.getItems();

        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
        {
            int idx = slot.getSlotIdx();
            if (idx < 0 || idx >= items.length)
            {
                continue;
            }

            Item item = items[idx];
            if (item == null || item.getId() == -1)
            {
                continue;
            }

            String itemName = resolveItemName(item.getId());
            records.add(ImmutableMap.<String, Object>builder()
                .put("rsn", rsn)
                .put("slot_id", idx)
                .put("slot_name", slot.name().toLowerCase())
                .put("item_id", item.getId())
                .put("item_name", itemName)
                .put("quantity", item.getQuantity())
                .build());
        }

        return records;
    }

    private List<Map<String, Object>> buildBankRecords(String rsn)
    {
        ItemContainer container = client.getItemContainer(InventoryID.BANK);
        if (container == null)
        {
            return new ArrayList<>();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (Item item : container.getItems())
        {
            if (item == null || item.getId() == -1)
            {
                continue;
            }
            records.add(ImmutableMap.of(
                "rsn",       rsn,
                "item_id",   item.getId(),
                "item_name", resolveItemName(item.getId()),
                "quantity",  item.getQuantity()
            ));
        }

        return records;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Resolves an item ID to its display name via the client's item composition cache.
     * Returns "Unknown" rather than null if the composition is unavailable.
     */
    private String resolveItemName(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        if (comp == null)
        {
            return "Unknown";
        }
        String name = comp.getName();
        return (name == null || name.equals("null")) ? "Unknown" : name;
    }

    /** Computes total XP across all skills (OVERALL not included in Skill.values() sum). */
    private long computeTotalXp()
    {
        long total = 0;
        for (Skill skill : Skill.values())
        {
            if (skill != Skill.OVERALL)
            {
                total += client.getSkillExperience(skill);
            }
        }
        return total;
    }

    private void cancelSkillDebounce()
    {
        if (skillSyncFuture != null && !skillSyncFuture.isDone())
        {
            skillSyncFuture.cancel(false);
            skillSyncFuture = null;
        }
    }

    /**
     * Sidebar Refresh button: retries pairing if there's no code yet (e.g. the
     * backend was unreachable at login), then redraws the panel.
     */
    private void manualRefresh()
    {
        final String rsn = currentRsn;
        if (rsn == null)
        {
            refreshPanel();
            return;
        }
        executor.execute(() ->
        {
            // pair-init is idempotent: reuses the sync token and reports the
            // server-side linked state, issuing a fresh code if unclaimed.
            journalSyncService.ensurePairing(rsn);
            refreshPanel();
        });
    }

    /** Reads live client data on the client thread, then updates the sidebar on the EDT. */
    private void refreshPanel()
    {
        if (panel == null)
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            JournalSnapshot snapshot = null;
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
                PairingState pairing = rsn != null ? journalSyncService.getPairingState(rsn) : null;
                snapshot = JournalSnapshot.fromClient(
                    client,
                    config.syncEnabled(),
                    config.syncBank(),
                    pairing
                );
            }
            final JournalSnapshot captured = snapshot;
            final String panelRsn = snapshot != null ? snapshot.getRsn() : null;
            SwingUtilities.invokeLater(() ->
            {
                panel.setCurrentRsn(panelRsn);
                panel.updateSummary(captured);
            });
        });
    }

    private static String nowIso()
    {
        return java.time.Instant.now().toString();
    }
}
