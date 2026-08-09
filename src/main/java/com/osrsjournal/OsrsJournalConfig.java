package com.osrsjournal;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Plugin settings. Plugin Hub requires any third-party network feature to be
 * <b>opt-in</b> (disabled by default) with the standard {@code warning} text.
 * Bank &amp; inventory sync is a second opt-in on top of Enable Sync.
 * The Advanced section only matters for self-hosted backends and stays collapsed.
 */
@ConfigGroup("osrsjournal")
public interface OsrsJournalConfig extends Config
{
    @ConfigSection(
        name = "Sync Options",
        description = "Control what data is synced and when. Enable Sync must be on before anything is sent.",
        position = 0
    )
    String syncSection = "sync";

    @ConfigItem(
        keyName = "syncEnabled",
        name = "Enable Sync",
        description = "Master switch. When on, syncs skills, quests, worn gear, diaries, combat "
            + "achievements, and Collection Log pages (when you open them in-game) to "
            + "journal.osrsjournal.com while you play. Off by default — enable this, "
            + "confirm the warning, then enter the pairing code from the sidebar on the website. "
            + "Bank & Inventory is a separate toggle below and does nothing while this is off.",
        // Exact wording required by RuneLite Plugin Hub / example-plugin AGENTS.md
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        section = syncSection,
        position = 1
    )
    default boolean syncEnabled()
    {
        // Plugin Hub: third-party server features must be disabled by default.
        return false;
    }

    @ConfigItem(
        keyName = "syncBank",
        name = "Sync Bank & Inventory",
        description = "Requires Enable Sync. When on, uploads your full bank whenever you open it, "
            + "and keeps your inventory snapshot updated so items count whether they are banked or "
            + "in your bag (gear planner, Graceful marks, wealth, etc.). Only you can see this when "
            + "signed in — public profiles never include bank or inventory.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        section = syncSection,
        position = 2
    )
    default boolean syncBank()
    {
        return false;
    }

    @ConfigItem(
        keyName = "publicProfile",
        name = "Public Profile",
        description = "When linked, skills and quests are visible to others on journal.osrsjournal.com "
            + "(like Wise Old Man). Bank, inventory, and worn gear stay private either way. "
            + "Requires Enable Sync for the plugin to push this setting.",
        section = syncSection,
        position = 3
    )
    default boolean publicProfile()
    {
        return true;
    }

    @ConfigItem(
        keyName = "skillDebounceSeconds",
        name = "Skill Debounce (s)",
        description = "Seconds to wait after the last XP gain before syncing skills (avoids spam while training).",
        section = syncSection,
        position = 4
    )
    @Range(min = 1, max = 60)
    default int skillDebounceSeconds()
    {
        return 3;
    }

    @ConfigSection(
        name = "Advanced",
        description = "Self-hosted deployments only — leave blank for journal.osrsjournal.com. "
            + "Only point API override at a server you trust.",
        position = 5,
        closedByDefault = true
    )
    String advancedSection = "advanced";

    @ConfigItem(
        keyName = "apiBaseUrl",
        name = "API override",
        description = "Leave blank to use the built-in OSRS Journal cloud. Self-hosters only — "
            + "must be a trusted HTTPS Edge Functions base URL.",
        section = advancedSection,
        position = 6
    )
    default String apiBaseUrl()
    {
        return "";
    }

    @ConfigItem(
        keyName = "pluginClientId",
        name = "Client ID",
        description = "X-Plugin-Client-Id override for self-hosted backends. Leave blank to use the built-in id.",
        section = advancedSection,
        position = 7
    )
    default String pluginClientId()
    {
        return "";
    }
}
