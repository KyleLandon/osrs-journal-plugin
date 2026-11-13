package com.osrsjournal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private static final int TOP_SESSION_SKILLS = 3;
    private static final int MAX_CONTINUE_QUESTS = 5;

    private final String rsn;
    private final int combatLevel;
    private final int questPoints;
    private final int totalLevel;
    private final int questsFinished;
    private final int questsTotal;
    private final List<String> continueQuests;
    private final long sessionXpGained;
    private final int sessionLevelsGained;
    private final List<SkillDelta> sessionSkills;
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
        int questsTotal,
        List<String> continueQuests,
        long sessionXpGained,
        int sessionLevelsGained,
        List<SkillDelta> sessionSkills,
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
        this.questsTotal = questsTotal;
        this.continueQuests = continueQuests;
        this.sessionXpGained = sessionXpGained;
        this.sessionLevelsGained = sessionLevelsGained;
        this.sessionSkills = sessionSkills;
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

    @Getter
    static class SkillDelta
    {
        private final String label;
        private final int xpGained;
        private final int levelsGained;

        SkillDelta(String label, int xpGained, int levelsGained)
        {
            this.label = label;
            this.xpGained = xpGained;
            this.levelsGained = levelsGained;
        }
    }

    static JournalSnapshot fromClient(
        net.runelite.api.Client client,
        boolean syncEnabled,
        boolean bankSyncEnabled,
        boolean publicProfileEnabled,
        PairingState pairing,
        JournalSyncService.SyncStatus syncStatus,
        Map<Skill, Integer> sessionStartXp,
        Map<Skill, Integer> sessionStartLevel
    )
    {
        var player = client.getLocalPlayer();
        String rsn = player != null ? player.getName() : "Unknown";
        int cb = player != null ? player.getCombatLevel() : 0;
        int qp = client.getVarpValue(net.runelite.api.VarPlayer.QUEST_POINTS);
        int total = client.getTotalLevel();

        EnumMap<Skill, Integer> nowXp = new EnumMap<>(Skill.class);
        EnumMap<Skill, Integer> nowLevel = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            nowXp.put(skill, client.getSkillExperience(skill));
            nowLevel.put(skill, client.getRealSkillLevel(skill));
        }
        SessionProgress session = sessionProgress(sessionStartXp, sessionStartLevel, nowXp, nowLevel);

        int finished = 0;
        int questTotal = 0;
        List<String> continueQuests = new ArrayList<>();
        for (Quest quest : Quest.values())
        {
            questTotal++;
            QuestState state = quest.getState(client);
            if (state == QuestState.FINISHED)
            {
                finished++;
            }
            else if (state == QuestState.IN_PROGRESS && continueQuests.size() < MAX_CONTINUE_QUESTS)
            {
                continueQuests.add(quest.getName());
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
            questTotal,
            continueQuests,
            session.xpGained,
            session.levelsGained,
            session.topSkills,
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

    static SessionProgress sessionProgress(
        Map<Skill, Integer> startXp,
        Map<Skill, Integer> startLevel,
        Map<Skill, Integer> nowXp,
        Map<Skill, Integer> nowLevel
    )
    {
        if (startXp == null || startXp.isEmpty() || nowXp == null || nowXp.isEmpty())
        {
            return new SessionProgress(0, 0, Collections.emptyList());
        }
        long xpGained = 0;
        int levelsGained = 0;
        List<SkillDelta> deltas = new ArrayList<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            int start = startXp.getOrDefault(skill, nowXp.getOrDefault(skill, 0));
            int now = nowXp.getOrDefault(skill, start);
            int gain = Math.max(0, now - start);
            int startLv = startLevel != null
                ? startLevel.getOrDefault(skill, nowLevel.getOrDefault(skill, 1))
                : nowLevel.getOrDefault(skill, 1);
            int nowLv = nowLevel != null ? nowLevel.getOrDefault(skill, startLv) : startLv;
            int levels = Math.max(0, nowLv - startLv);
            xpGained += gain;
            levelsGained += levels;
            if (gain > 0 || levels > 0)
            {
                deltas.add(new SkillDelta(capitalize(skill.getName()), gain, levels));
            }
        }
        deltas.sort(Comparator.comparingInt(SkillDelta::getXpGained).reversed());
        if (deltas.size() > TOP_SESSION_SKILLS)
        {
            deltas = new ArrayList<>(deltas.subList(0, TOP_SESSION_SKILLS));
        }
        return new SessionProgress(xpGained, levelsGained, deltas);
    }

    static String formatXp(long xp)
    {
        if (xp >= 1_000_000)
        {
            return String.format("%.2fm", xp / 1_000_000.0);
        }
        if (xp >= 1_000)
        {
            return String.format("%.1fk", xp / 1000.0);
        }
        return String.valueOf(xp);
    }

    static String capitalize(String name)
    {
        if (name == null || name.isEmpty())
        {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }

    static final class SessionProgress
    {
        final long xpGained;
        final int levelsGained;
        final List<SkillDelta> topSkills;

        SessionProgress(long xpGained, int levelsGained, List<SkillDelta> topSkills)
        {
            this.xpGained = xpGained;
            this.levelsGained = levelsGained;
            this.topSkills = topSkills;
        }
    }
}
