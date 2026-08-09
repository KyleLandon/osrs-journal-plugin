package com.osrsjournal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Syncs game data to the OSRS Journal cloud through Edge Functions,
 * authenticated by a per-character sync token.
 *
 * <p>Thin orchestration layer between the plugin's event handlers and
 * {@link HostedApiService}: it looks up the token for the RSN, shapes the
 * payload, and feeds pairing-state updates back to {@link PairingService}.
 * All methods perform network I/O and must be called from executor threads,
 * never the client thread.
 */
@Slf4j
@Singleton
class JournalSyncService
{
    @Inject
    private HostedApiService hostedApiService;

    @Inject
    private SyncTokenStore syncTokenStore;

    @Inject
    private PairingService pairingService;

    private final AtomicReference<SyncStatus> lastStatus = new AtomicReference<>(SyncStatus.idle());

    /** Ensures a sync token exists for {@code rsn}; may call pair-init. */
    PairingState ensurePairing(String rsn)
    {
        return pairingService.ensurePairing(rsn);
    }

    PairingState getPairingState(String rsn)
    {
        return pairingService.getCurrentState(rsn);
    }

    String getSyncToken(String rsn)
    {
        return syncTokenStore.getToken(rsn);
    }

    SyncStatus getLastStatus()
    {
        return lastStatus.get();
    }

    HostedApiService.SyncResult syncLogin(
        String rsn,
        List<Map<String, Object>> playerRecord,
        List<Map<String, Object>> skillRecords,
        List<Map<String, Object>> questRecords,
        List<Map<String, Object>> equipRecords,
        List<Map<String, Object>> diaryRecords,
        List<Map<String, Object>> combatAchievementRecords
    )
    {
        // Note: profile_public is intentionally NOT sent here — the website's
        // privacy toggle is the source of truth once an account is linked.
        // It is only pushed from syncPrivacy() when the user changes the
        // RuneLite config toggle.
        HostedApiService.SyncPayload payload = new HostedApiService.SyncPayload()
            .players(playerRecord)
            .skills(skillRecords)
            .quests(questRecords)
            .equipment(equipRecords, true)
            .diaries(diaryRecords)
            .combatAchievements(combatAchievementRecords)
            .touchLastSynced(true);

        return syncWithRepair(rsn, payload, "login");
    }

    boolean syncSkills(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().skills(records), "skills");
    }

    boolean syncQuests(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().quests(records), "quests");
    }

    boolean syncDiariesAndCombatAchievements(
        String rsn,
        List<Map<String, Object>> diaryRecords,
        List<Map<String, Object>> combatAchievementRecords
    )
    {
        return syncPartial(
            rsn,
            new HostedApiService.SyncPayload()
                .diaries(diaryRecords)
                .combatAchievements(combatAchievementRecords),
            "diaries+CA"
        );
    }

    boolean syncEquipment(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().equipment(records, true), "equipment");
    }

    boolean syncBank(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().bank(records, true), "bank");
    }

    /** Inventory snapshot stored in player_inventory (counts with bank on the site). */
    boolean syncInventory(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().inventory(records), "inventory");
    }

    /** Bank + inventory in one request so ownership data stays consistent. */
    boolean syncBankAndInventory(
        String rsn,
        List<Map<String, Object>> bankRecords,
        List<Map<String, Object>> inventoryRecords
    )
    {
        return syncPartial(
            rsn,
            new HostedApiService.SyncPayload().bankAndInventory(bankRecords, inventoryRecords),
            "bank+inventory"
        );
    }

    /**
     * Collection-log pages opened in-game. Each map is
     * {@code page} + {@code items} (+ optional obtained / kill_counts).
     * When {@code playerRecord} is non-null it refreshes collection_count totals.
     */
    boolean syncCollectionLog(
        String rsn,
        List<Map<String, Object>> pages,
        List<Map<String, Object>> playerRecord
    )
    {
        if (pages == null || pages.isEmpty())
        {
            return true;
        }
        HostedApiService.SyncPayload payload = new HostedApiService.SyncPayload()
            .collectionLogPages(pages);
        if (playerRecord != null && !playerRecord.isEmpty())
        {
            payload.players(playerRecord).touchLastSynced(true);
        }
        return syncPartial(rsn, payload, "collection log");
    }

    /** Pushes the profile privacy flag; only called when the user flips the config toggle. */
    boolean syncPrivacy(String rsn, boolean isPublic)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload()
            .profilePublic(isPublic)
            .touchLastSynced(false), "privacy");
    }

    private boolean syncPartial(String rsn, HostedApiService.SyncPayload payload, String label)
    {
        return syncWithRepair(rsn, payload, label).isSuccess();
    }

    private HostedApiService.SyncResult syncWithRepair(
        String rsn,
        HostedApiService.SyncPayload payload,
        String label
    )
    {
        String token = syncTokenStore.getToken(rsn);
        if (token == null)
        {
            PairingState paired = pairingService.ensurePairing(rsn);
            token = paired != null ? paired.getSyncToken() : syncTokenStore.getToken(rsn);
            if (token == null)
            {
                lastStatus.set(SyncStatus.error("No sync token — open the sidebar to pair."));
                log.warn("No sync token for '{}' — {} sync skipped", rsn, label);
                return HostedApiService.SyncResult.failed(true, "No sync token");
            }
        }

        HostedApiService.SyncResult result = hostedApiService.sync(rsn, token, payload);
        if (!result.isSuccess() && result.isAuthFailed())
        {
            log.info("OSRS Journal: sync token stale for '{}' during {}, re-pairing", rsn, label);
            pairingService.ensurePairing(rsn);
            token = syncTokenStore.getToken(rsn);
            if (token != null)
            {
                result = hostedApiService.sync(rsn, token, payload);
            }
        }

        if (result.isSuccess())
        {
            pairingService.updateLinkedState(rsn, syncTokenStore.getToken(rsn), result.isClaimed());
            if (result.hasWarnings())
            {
                String warn = String.join("; ", result.getWarnings());
                lastStatus.set(SyncStatus.warning(label + ": " + warn));
            }
            else
            {
                lastStatus.set(SyncStatus.ok(label));
            }
        }
        else
        {
            String err = result.getError() != null ? result.getError() : (label + " sync failed");
            lastStatus.set(SyncStatus.error(err));
            log.warn("OSRS Journal: {} sync failed for '{}': {}", label, rsn, err);
        }
        return result;
    }

    /** Compact status shown in the sidebar. */
    static final class SyncStatus
    {
        enum Kind { IDLE, OK, WARNING, ERROR }

        private final Kind kind;
        private final String message;
        private final long atMs;

        private SyncStatus(Kind kind, String message)
        {
            this.kind = kind;
            this.message = message;
            this.atMs = System.currentTimeMillis();
        }

        static SyncStatus idle()
        {
            return new SyncStatus(Kind.IDLE, null);
        }

        static SyncStatus ok(String label)
        {
            return new SyncStatus(Kind.OK, "Last sync OK (" + label + ")");
        }

        static SyncStatus warning(String message)
        {
            return new SyncStatus(Kind.WARNING, message);
        }

        static SyncStatus error(String message)
        {
            return new SyncStatus(Kind.ERROR, message);
        }

        Kind getKind()
        {
            return kind;
        }

        String getMessage()
        {
            return message;
        }

        long getAtMs()
        {
            return atMs;
        }

        /** Relative age for sidebar, e.g. "just now", "2m ago". */
        String getAgeLabel()
        {
            if (kind == Kind.IDLE)
            {
                return null;
            }
            long secs = Math.max(0, (System.currentTimeMillis() - atMs) / 1000L);
            if (secs < 15)
            {
                return "just now";
            }
            if (secs < 60)
            {
                return secs + "s ago";
            }
            long mins = secs / 60;
            if (mins < 60)
            {
                return mins + "m ago";
            }
            return (mins / 60) + "h ago";
        }
    }
}
