package com.osrsjournal;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("osrsjournal")
public interface OsrsJournalConfig extends Config
{
    // ── Sync Options ──────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Sync Options",
        description = "Control what data is synced and when",
        position = 0
    )
    String syncSection = "sync";

    @ConfigItem(
        keyName = "syncEnabled",
        name = "Enable Sync",
        description = "Master switch — turn off to pause all syncing",
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
        description = "Sync bank contents each time the bank is opened",
        section = syncSection,
        position = 2
    )
    default boolean syncBank()
    {
        return true;
    }

    @ConfigItem(
        keyName = "publicProfile",
        name = "Public Profile",
        description = "Skills and quests visible to others on journal.osrsjournal.com (like Wise Old Man). Bank and gear stay private.",
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
        description = "Seconds to wait after the last StatChanged event before syncing skills. "
            + "Prevents dozens of requests firing at once on login.",
        section = syncSection,
        position = 4
    )
    default int skillDebounceSeconds()
    {
        return 3;
    }

    // ── Advanced (custom deployments only) ────────────────────────────────────

    @ConfigSection(
        name = "Advanced",
        description = "Overrides for custom deployments — leave blank for journal.osrsjournal.com",
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
        keyName = "hostedAnonKey",
        name = "Web key override",
        description = "Leave blank — only needed for custom deployments",
        section = advancedSection,
        position = 7,
        secret = true
    )
    default String hostedAnonKey()
    {
        return "";
    }

    @ConfigItem(
        keyName = "pluginClientId",
        name = "Client ID",
        description = "Leave blank for normal use",
        section = advancedSection,
        position = 8
    )
    default String pluginClientId()
    {
        return "";
    }
}
