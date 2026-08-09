package com.osrsjournal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures obtained Collection Log items when the player opens a log page.
 *
 * <p>Listens for {@link ScriptID#COLLECTION_DRAW_LIST} (same hook as Chat
 * Commands' pets tracker), reads the page title, obtained counts, kill counts,
 * and item widgets, and accumulates pages until {@link #drainPages()} is called
 * by the sync path.
 */
@Slf4j
@Singleton
class CollectionLogCapture
{
    /** Title child on {@link InterfaceID.Collection#HEADER_TEXT} (ChatCommands uses 0). */
    private static final int HEADER_TITLE_CHILD = 0;
    /** "Obtained: X/Y" sits at index 1 when present (evansloan/collection-log). */
    private static final int HEADER_OBTAINED_CHILD = 1;
    /** Kill-count widgets start at index 2. */
    private static final int HEADER_KC_START_CHILD = 2;

    /**
     * Adventure-log varbit: non-zero when the collection log was opened from a
     * POH adventure log. We only sync when it is the local player's log.
     */
    private static final int ADVENTURE_LOG_COLLECTION_LOG_SELECTED = 12061;

    /** Adventure log interface group — used to learn whose exploits are open. */
    private static final int ADVENTURE_LOG_GROUP_ID = net.runelite.api.widgets.InterfaceID.ADVENTURE_LOG;
    private static final int ADVENTURE_LOG_TITLE_CHILD = 1;
    private static final Pattern ADVENTURE_LOG_TITLE_PATTERN =
        Pattern.compile("The Exploits of (.+)");

    private static final Pattern OBTAINED_PATTERN = Pattern.compile(
        "(?i)obtained:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)"
    );

    private final Client client;

    /** Pages changed since the last drain — keyed by page title. */
    private final Map<String, CapturedPage> pending = new LinkedHashMap<>();

    private Runnable onPagesChanged;

    /** True when the open adventure log belongs to the local player. */
    private boolean isPohOwner;

    @Inject
    CollectionLogCapture(Client client)
    {
        this.client = client;
    }

    void setOnPagesChanged(Runnable onPagesChanged)
    {
        this.onPagesChanged = onPagesChanged;
    }

    /** Clears pending pages and adventure-log ownership (e.g. on logout). */
    void resetSession()
    {
        drainPages();
        isPohOwner = false;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() != ADVENTURE_LOG_GROUP_ID)
        {
            return;
        }
        isPohOwner = false;
        Widget adventureLog = client.getWidget(ComponentID.ADVENTURE_LOG_CONTAINER);
        if (adventureLog == null || adventureLog.getChildren() == null)
        {
            return;
        }
        if (ADVENTURE_LOG_TITLE_CHILD >= adventureLog.getChildren().length)
        {
            return;
        }
        Widget title = adventureLog.getChild(ADVENTURE_LOG_TITLE_CHILD);
        if (title == null || title.getText() == null || client.getLocalPlayer() == null)
        {
            return;
        }
        Matcher m = ADVENTURE_LOG_TITLE_PATTERN.matcher(title.getText());
        if (m.find() && client.getLocalPlayer().getName() != null)
        {
            isPohOwner = m.group(1).equals(client.getLocalPlayer().getName());
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
        {
            return;
        }

        // Don't sync someone else's collection log opened via adventure log.
        boolean fromAdventureLog = client.getVarbitValue(ADVENTURE_LOG_COLLECTION_LOG_SELECTED) != 0;
        if (fromAdventureLog && !isPohOwner)
        {
            return;
        }

        Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
        if (header == null)
        {
            return;
        }

        Widget[] headerChildren = headerChildren(header);
        if (headerChildren == null || headerChildren.length == 0)
        {
            return;
        }

        Widget titleWidget = headerChildren.length > HEADER_TITLE_CHILD
            ? headerChildren[HEADER_TITLE_CHILD]
            : header.getChild(HEADER_TITLE_CHILD);
        if (titleWidget == null)
        {
            return;
        }
        String page = titleWidget.getText();
        if (page == null || page.isEmpty())
        {
            return;
        }
        page = Text.removeTags(page).replace('\u00A0', ' ').trim();
        if (page.isEmpty())
        {
            return;
        }

        Integer obtained = null;
        Integer obtainedTotal = null;
        if (headerChildren.length > HEADER_OBTAINED_CHILD && headerChildren[HEADER_OBTAINED_CHILD] != null)
        {
            int[] counts = parseObtained(headerChildren[HEADER_OBTAINED_CHILD].getText());
            if (counts != null)
            {
                obtained = counts[0];
                obtainedTotal = counts[1];
            }
        }

        List<KillCount> killCounts = new ArrayList<>();
        for (int i = HEADER_KC_START_CHILD; i < headerChildren.length; i++)
        {
            Widget kcWidget = headerChildren[i];
            if (kcWidget == null)
            {
                continue;
            }
            KillCount kc = parseKillCount(kcWidget.getText());
            if (kc != null)
            {
                killCounts.add(kc);
            }
        }

        Widget itemsRoot = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
        List<CapturedItem> obtainedItems = new ArrayList<>();
        if (itemsRoot != null && itemsRoot.getChildren() != null)
        {
            for (Widget child : itemsRoot.getChildren())
            {
                if (child == null || child.getOpacity() != 0)
                {
                    continue;
                }
                int itemId = child.getItemId();
                if (itemId <= 0)
                {
                    continue;
                }
                String name = cleanItemName(child.getName());
                int qty = child.getItemQuantity();
                if (qty <= 0)
                {
                    qty = 1;
                }
                obtainedItems.add(new CapturedItem(itemId, name, qty));
            }
        }

        CapturedPage captured = new CapturedPage(page, obtainedItems, killCounts, obtained, obtainedTotal);
        synchronized (pending)
        {
            pending.put(page, captured);
        }
        log.debug(
            "Collection log page '{}' — {} obtained item(s), {} kill count(s)",
            page,
            obtainedItems.size(),
            killCounts.size()
        );

        Runnable cb = onPagesChanged;
        if (cb != null)
        {
            cb.run();
        }
    }

    /**
     * Returns and clears pages captured since the last drain.
     * Safe to call from any thread; capture writes happen on the client thread.
     */
    Map<String, CapturedPage> drainPages()
    {
        synchronized (pending)
        {
            if (pending.isEmpty())
            {
                return Collections.emptyMap();
            }
            Map<String, CapturedPage> out = new LinkedHashMap<>(pending);
            pending.clear();
            return out;
        }
    }

    boolean hasPending()
    {
        synchronized (pending)
        {
            return !pending.isEmpty();
        }
    }

    /** Prefer dynamic children (kill-count widgets live there); fall back to getChildren. */
    private static Widget[] headerChildren(Widget header)
    {
        Widget[] dynamic = header.getDynamicChildren();
        if (dynamic != null && dynamic.length > 0)
        {
            return dynamic;
        }
        return header.getChildren();
    }

    /**
     * Parses {@code Obtained: 3/7} (tags/nbsp allowed). Returns {@code [obtained, total]}
     * or null if the text is not an obtained line.
     */
    static int[] parseObtained(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return null;
        }
        String text = Text.removeTags(raw).replace('\u00A0', ' ').trim();
        Matcher m = OBTAINED_PATTERN.matcher(text);
        if (!m.find())
        {
            return null;
        }
        try
        {
            int obtained = Integer.parseInt(m.group(1).replace(",", ""));
            int total = Integer.parseInt(m.group(2).replace(",", ""));
            return new int[]{obtained, total};
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Parses {@code Kill Count: 1,234} / {@code Completions: 12} style header lines.
     */
    static KillCount parseKillCount(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return null;
        }
        String text = Text.removeTags(raw).replace('\u00A0', ' ').trim();
        int sep = text.lastIndexOf(": ");
        if (sep <= 0 || sep + 2 >= text.length())
        {
            return null;
        }
        String name = text.substring(0, sep).trim();
        String amountStr = text.substring(sep + 2).trim().replace(",", "");
        if (name.isEmpty() || amountStr.isEmpty())
        {
            return null;
        }
        // Skip the Obtained line if it somehow lands in the KC range.
        if (name.equalsIgnoreCase("Obtained"))
        {
            return null;
        }
        try
        {
            int amount = Integer.parseInt(amountStr);
            return new KillCount(name, amount);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static String cleanItemName(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return "";
        }
        return Text.removeTags(raw).replace('\u00A0', ' ').trim();
    }

    /** One opened collection-log page with items and optional counters. */
    static final class CapturedPage
    {
        private final String page;
        private final List<CapturedItem> items;
        private final List<KillCount> killCounts;
        private final Integer obtained;
        private final Integer obtainedTotal;

        CapturedPage(
            String page,
            List<CapturedItem> items,
            List<KillCount> killCounts,
            Integer obtained,
            Integer obtainedTotal
        )
        {
            this.page = page;
            this.items = items != null ? items : Collections.emptyList();
            this.killCounts = killCounts != null ? killCounts : Collections.emptyList();
            this.obtained = obtained;
            this.obtainedTotal = obtainedTotal;
        }

        String getPage()
        {
            return page;
        }

        List<CapturedItem> getItems()
        {
            return items;
        }

        List<KillCount> getKillCounts()
        {
            return killCounts;
        }

        Integer getObtained()
        {
            return obtained;
        }

        Integer getObtainedTotal()
        {
            return obtainedTotal;
        }
    }

    /** Named counter shown on a collection-log page header (e.g. Kill Count). */
    static final class KillCount
    {
        private final String name;
        private final int amount;

        KillCount(String name, int amount)
        {
            this.name = name != null ? name : "";
            this.amount = amount;
        }

        String getName()
        {
            return name;
        }

        int getAmount()
        {
            return amount;
        }
    }

    /** Obtained item on a collection-log page. */
    static final class CapturedItem
    {
        private final int itemId;
        private final String itemName;
        private final int quantity;

        CapturedItem(int itemId, String itemName, int quantity)
        {
            this.itemId = itemId;
            this.itemName = itemName != null ? itemName : "";
            this.quantity = quantity;
        }

        int getItemId()
        {
            return itemId;
        }

        String getItemName()
        {
            return itemName;
        }

        int getQuantity()
        {
            return quantity;
        }
    }
}
