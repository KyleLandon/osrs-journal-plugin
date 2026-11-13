package com.osrsjournal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.EnumMap;
import net.runelite.api.Skill;
import org.junit.Test;

public class JournalSnapshotTest
{
	@Test
	public void formatXpUsesCompactUnits()
	{
		assertEquals("900", JournalSnapshot.formatXp(900));
		assertEquals("1.5k", JournalSnapshot.formatXp(1500));
		assertEquals("124.5k", JournalSnapshot.formatXp(124500));
		assertEquals("1.25m", JournalSnapshot.formatXp(1_250_000));
	}

	@Test
	public void sessionProgressRanksByXpAndIgnoresUnchangedSkills()
	{
		EnumMap<Skill, Integer> startXp = new EnumMap<>(Skill.class);
		EnumMap<Skill, Integer> startLv = new EnumMap<>(Skill.class);
		EnumMap<Skill, Integer> nowXp = new EnumMap<>(Skill.class);
		EnumMap<Skill, Integer> nowLv = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			startXp.put(skill, 1000);
			startLv.put(skill, 10);
			nowXp.put(skill, 1000);
			nowLv.put(skill, 10);
		}
		nowXp.put(Skill.AGILITY, 83_000);
		nowLv.put(Skill.AGILITY, 12);
		nowXp.put(Skill.HITPOINTS, 13_000);
		nowXp.put(Skill.ATTACK, 1_100);

		JournalSnapshot.SessionProgress progress = JournalSnapshot.sessionProgress(
			startXp, startLv, nowXp, nowLv);

		assertEquals(94_100, progress.xpGained);
		assertEquals(2, progress.levelsGained);
		assertEquals(3, progress.topSkills.size());
		assertEquals("Agility", progress.topSkills.get(0).getLabel());
		assertEquals(82_000, progress.topSkills.get(0).getXpGained());
		assertEquals(2, progress.topSkills.get(0).getLevelsGained());
		assertEquals("Hitpoints", progress.topSkills.get(1).getLabel());
		assertEquals("Attack", progress.topSkills.get(2).getLabel());
	}

	@Test
	public void emptyBaselineIsZeroProgress()
	{
		JournalSnapshot.SessionProgress progress = JournalSnapshot.sessionProgress(
			new EnumMap<>(Skill.class),
			new EnumMap<>(Skill.class),
			new EnumMap<>(Skill.class),
			new EnumMap<>(Skill.class));
		assertEquals(0, progress.xpGained);
		assertEquals(0, progress.levelsGained);
		assertTrue(progress.topSkills.isEmpty());
	}
}
