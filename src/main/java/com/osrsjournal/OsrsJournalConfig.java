package com.osrsjournal;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Plugin settings. Privacy-sensitive defaults are deliberate:
 * bank sync is <b>off</b> (opt-in, per Plugin Hub expectations) while skills and
 * quests sync automatically — matching what public hiscores already expose.
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
        description = "Master switch — turn off to pause all syncing to journal.osrsjournal.com",
        section = syncSection,
        position = 1
    )
    default boolean syncEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "syncBank",
        name = "Sync Bank",
        description = "When enabled, sends your full bank to journal.osrsjournal.com each time you open the bank. "
            + "Off by default — only you can see bank data when signed in on the website.",
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
        description = "Only if your backend requires X-Plugin-Client-Id",
        section = advancedSection,
        position = 7
    )
    default String pluginClientId()
    {
        return "";
    }
}
