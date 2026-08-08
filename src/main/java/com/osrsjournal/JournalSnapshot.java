package com.osrsjournal;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Immutable view-model for the sidebar panel.
 *
 * <p>Built on the <em>client thread</em> via {@link #fromClient} (the only place
 * the RuneLite {@code Client} API may be read), then handed to Swing untouched —
 * so the panel never needs to reach back into game state from the EDT.
 */
@Getter
class JournalSnapshot
{
    private final String rsn;
    private final int combatLevel;
    private final int questPoints;
    private final int totalLevel;
    private final int questsFinished;
    private final List<SkillRow> skills;
    private final List<String> recentQuests;
    private final boolean syncEnabled;
    private final boolean bankSyncEnabled;
    private final boolean publicProfileEnabled;
    private final String pairCode;
    private final boolean accountLinked;
    private final boolean pairCodeExpired;
    private final String pairExpiryLabel;
    private final JournalSyncService.SyncStatus syncStatus;

    JournalSnapshot(
        String rsn,
        int combatLevel,
        int questPoints,
        int totalLevel,
        int questsFinished,
        List<SkillRow> skills,
        List<String> recentQuests,
        boolean syncEnabled,
        boolean bankSyncEnabled,
        boolean publicProfileEnabled,
        String pairCode,
        boolean accountLinked,
        boolean pairCodeExpired,
        String pairExpiryLabel,
        JournalSyncService.SyncStatus syncStatus
    )
    {
        this.rsn = rsn;
        this.combatLevel = combatLevel;
        this.questPoints = questPoints;
        this.totalLevel = totalLevel;
        this.questsFinished = questsFinished;
        this.skills = skills;
        this.recentQuests = recentQuests;
        this.syncEnabled = syncEnabled;
        this.bankSyncEnabled = bankSyncEnabled;
        this.publicProfileEnabled = publicProfileEnabled;
        this.pairCode = pairCode;
        this.accountLinked = accountLinked;
        this.pairCodeExpired = pairCodeExpired;
        this.pairExpiryLabel = pairExpiryLabel;
        this.syncStatus = syncStatus;
    }

    /** One-line status for the panel header, ordered by what the user should do next. */
    String getStatusText()
    {
        if (!syncEnabled)
        {
            return "Sync off — turn on Enable Sync in plugin settings (confirm the 3rd-party warning).";
        }
        if (syncStatus != null && syncStatus.getKind() == JournalSyncService.SyncStatus.Kind.ERROR
            && syncStatus.getMessage() != null)
        {
            return "Sync error: " + syncStatus.getMessage();
        }
        if (pairCodeExpired)
        {
            return "Pairing code expired — click New code, then enter it on the website.";
        }
        if (pairCode != null && !pairCode.isEmpty() && !accountLinked)
        {
            String expiry = pairExpiryLabel != null ? " (" + pairExpiryLabel + ")" : "";
            return "Link your account on journal.osrsjournal.com" + expiry;
        }
        if (syncStatus != null && syncStatus.getKind() == JournalSyncService.SyncStatus.Kind.WARNING
            && syncStatus.getMessage() != null)
        {
            return "Synced with warnings — " + syncStatus.getMessage();
        }
        if (accountLinked)
        {
            if (syncStatus != null && syncStatus.getKind() == JournalSyncService.SyncStatus.Kind.OK)
            {
                String age = syncStatus.getAgeLabel();
                return "Linked · " + syncStatus.getMessage() + (age != null ? " · " + age : "");
            }
            return "Linked · syncing to OSRS Journal cloud.";
        }
        return "Waiting for pairing — click New code if none appears.";
    }

    /** True when bank toggle is on but master sync is off (no-op trap). */
    boolean isBankSyncIneffective()
    {
        return bankSyncEnabled && !syncEnabled;
    }

    static class SkillRow
    {
        private final String label;
        private final int level;

        SkillRow(String label, int level)
        {
            this.label = label;
            this.level = level;
        }

        String getLabel()
        {
            return label;
        }

        int getLevel()
        {
            return level;
        }
    }

    static JournalSnapshot fromClient(
        net.runelite.api.Client client,
        boolean syncEnabled,
        boolean bankSyncEnabled,
        boolean publicProfileEnabled,
        PairingState pairing,
        JournalSyncService.SyncStatus syncStatus
    )
    {
        var player = client.getLocalPlayer();
        String rsn = player != null ? player.getName() : "Unknown";
        int cb = player != null ? player.getCombatLevel() : 0;
        int qp = client.getVarpValue(net.runelite.api.VarPlayer.QUEST_POINTS);
        int total = client.getTotalLevel();

        List<SkillRow> skills = new ArrayList<>();
        addSkill(skills, client, Skill.ATTACK);
        addSkill(skills, client, Skill.STRENGTH);
        addSkill(skills, client, Skill.DEFENCE);
        addSkill(skills, client, Skill.RANGED);
        addSkill(skills, client, Skill.MAGIC);
        addSkill(skills, client, Skill.PRAYER);
        addSkill(skills, client, Skill.HITPOINTS);
        addSkill(skills, client, Skill.SLAYER);

        int finished = 0;
        List<String> recent = new ArrayList<>();
        for (Quest quest : Quest.values())
        {
            QuestState state = quest.getState(client);
            if (state == QuestState.FINISHED)
            {
                finished++;
            }
            else if (state == QuestState.IN_PROGRESS && recent.size() < 6)
            {
                recent.add(quest.getName());
            }
        }

        String pairCode = pairing != null && pairing.needsPairingDisplay() ? pairing.getPairCode() : null;
        boolean linked = pairing != null && pairing.isLinked();
        boolean expired = pairing != null && pairing.isCodeExpired();
        String expiryLabel = pairing != null ? pairing.getExpiryLabel() : null;

        return new JournalSnapshot(
            rsn,
            cb,
            qp,
            total,
            finished,
            skills,
            recent,
            syncEnabled,
            bankSyncEnabled,
            publicProfileEnabled,
            pairCode,
            linked,
            expired,
            expiryLabel,
            syncStatus
        );
    }

    private static void addSkill(List<SkillRow> skills, net.runelite.api.Client client, Skill skill)
    {
        skills.add(new SkillRow(capitalize(skill.getName()), client.getRealSkillLevel(skill)));
    }

    private static String capitalize(String name)
    {
        if (name == null || name.isEmpty())
        {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }
}
