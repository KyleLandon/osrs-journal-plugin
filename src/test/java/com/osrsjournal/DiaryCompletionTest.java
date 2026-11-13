package com.osrsjournal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Varbits;
import org.junit.Test;

public class DiaryCompletionTest
{
    @Test
    public void legacyKaramjaTiersCompleteOnlyAtTwo()
    {
        int[] legacy = {Varbits.DIARY_KARAMJA_EASY, Varbits.DIARY_KARAMJA_MEDIUM, Varbits.DIARY_KARAMJA_HARD};
        for (int varbit : legacy)
        {
            assertFalse(OsrsJournalPlugin.isDiaryTierComplete(varbit, 0));
            assertFalse("1 means started for legacy Karamja", OsrsJournalPlugin.isDiaryTierComplete(varbit, 1));
            assertTrue(OsrsJournalPlugin.isDiaryTierComplete(varbit, 2));
        }
    }

    @Test
    public void standardTiersCompleteAtOne()
    {
        assertFalse(OsrsJournalPlugin.isDiaryTierComplete(Varbits.DIARY_KARAMJA_ELITE, 0));
        assertTrue(OsrsJournalPlugin.isDiaryTierComplete(Varbits.DIARY_KARAMJA_ELITE, 1));
        assertFalse(OsrsJournalPlugin.isDiaryTierComplete(Varbits.DIARY_VARROCK_EASY, 0));
        assertTrue(OsrsJournalPlugin.isDiaryTierComplete(Varbits.DIARY_VARROCK_EASY, 1));
    }
}
