package com.osrsjournal;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Plugin settings. Plugin Hub requires any third-party network feature to be
 * <b>opt-in</b> (disabled by default) with the standard {@code warning} text.
 * Bank sync is a second opt-in on top of that.
 * The Advanced section only matters for self-hosted backends and stays collapsed.
 */
@ConfigGroup("osrsjournal")
public interface OsrsJournalConfig extends Config
{
    @ConfigSection(
        name = "Sync Options",
        description = "Control what data is synced and when",
        position = 0
    )
    String syncSection = "sync";

    @ConfigItem(
        keyName = "syncEnabled",
        name = "Enable Sync",
        description = "Opt in to sync your character (skills, quests, worn gear, diaries, combat achievements) "
            + "to journal.osrsjournal.com. Off by default — enable this, then pair the character on the website.",
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
        description = "When enabled (and sync is on), sends your bank to journal.osrsjournal.com when you open it, "
            + "and keeps your inventory in sync so owned items count whether they are in the bank or your bag. "
            + "Only you can see this data when signed in on the website.",
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
        description = "Skills and quests visible to others on journal.osrsjournal.com (like Wise Old Man). "
            + "Bank and worn gear stay private.",
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
        description = "Seconds to wait after the last XP gain before syncing skills",
        section = syncSection,
        position = 4
    )
    default int skillDebounceSeconds()
    {
        return 3;
    }

    @ConfigSection(
        name = "Advanced",
        description = "Self-hosted deployments only — leave blank for journal.osrsjournal.com",
        position = 5,
        closedByDefault = true
    )
    String advancedSection = "advanced";

    @ConfigItem(
        keyName = "apiBaseUrl",
        name = "API override",
        description = "Leave blank to use the built-in OSRS Journal cloud",
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
