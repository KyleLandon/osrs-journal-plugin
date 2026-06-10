package com.osrsjournal;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("osrsjournal")
public interface OsrsJournalConfig extends Config
{
    // ── Hosted API (community / multi-user) ───────────────────────────────────

    @ConfigSection(
        name = "Cloud Sync",
        description = "Syncs to journal.osrsjournal.com — no setup required",
        position = 0
    )
    String hostedSection = "hosted";

    @ConfigItem(
        keyName = "hostedMode",
        name = "Cloud Sync",
        description = "Sync your character to OSRS Journal (recommended). Turn off only for self-hosted setups.",
        section = hostedSection,
        position = 1
    )
    default boolean hostedMode()
    {
        return true;
    }

    @ConfigItem(
        keyName = "apiBaseUrl",
        name = "API override (advanced)",
        description = "Leave blank to use the built-in OSRS Journal cloud",
        section = hostedSection,
        position = 2
    )
    default String apiBaseUrl()
    {
        return "";
    }

    @ConfigItem(
        keyName = "hostedAnonKey",
        name = "Web key override (advanced)",
        description = "Leave blank — only needed for custom deployments",
        section = hostedSection,
        position = 3
    )
    default String hostedAnonKey()
    {
        return "";
    }

    @ConfigItem(
        keyName = "pluginClientId",
        name = "Client ID (advanced)",
        description = "Leave blank for normal use",
        section = hostedSection,
        position = 4
    )
    default String pluginClientId()
    {
        return "";
    }

    // ── Self-hosted (advanced) ────────────────────────────────────────────────

    @ConfigSection(
        name = "Self-Hosted (advanced)",
        description = "Only when Cloud Sync is disabled",
        position = 5
    )
    String connectionSection = "connection";

    @ConfigItem(
        keyName = "supabaseUrl",
        name = "Supabase URL",
        description = "Your Supabase project URL, e.g. https://xxxx.supabase.co",
        section = connectionSection,
        position = 6
    )
    default String supabaseUrl()
    {
        return "";
    }

    @ConfigItem(
        keyName = "supabaseAnonKey",
        name = "Anon Key",
        description = "Your Supabase project's anon/public key",
        section = connectionSection,
        position = 7,
        secret = true
    )
    default String supabaseAnonKey()
    {
        return "";
    }

    // ── Sync Options ──────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Sync Options",
        description = "Control what data is synced and when",
        position = 7
    )
    String syncSection = "sync";

    @ConfigItem(
        keyName = "syncEnabled",
        name = "Enable Sync",
        description = "Master switch — turn off to pause all syncing without removing credentials",
        section = syncSection,
        position = 8
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
        position = 9
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
        position = 10
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
        position = 11
    )
    default int skillDebounceSeconds()
    {
        return 3;
    }
}
