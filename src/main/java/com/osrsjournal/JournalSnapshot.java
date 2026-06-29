package com.osrsjournal;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

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
    private final String pairCode;
    private final boolean accountLinked;

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
        String pairCode,
        boolean accountLinked
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
        this.pairCode = pairCode;
        this.accountLinked = accountLinked;
    }

    String getStatusText()
    {
        if (!syncEnabled)
        {
            return "Sync paused — enable in plugin settings.";
        }
        if (pairCode != null && !pairCode.isEmpty() && !accountLinked)
        {
            return "Link your account: sign in at journal.osrsjournal.com and enter the code below.";
        }
        if (accountLinked)
        {
            return "Linked · syncing to OSRS Journal cloud.";
        }
        return "Waiting for pairing — click Refresh if no code appears.";
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
        PairingState pairing
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
            pairCode,
            linked
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
