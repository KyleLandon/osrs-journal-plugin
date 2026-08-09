package com.osrsjournal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class CollectionLogCaptureTest
{
	@Test
	public void parseObtainedReadsCounts()
	{
		int[] counts = CollectionLogCapture.parseObtained("Obtained: <col=ff9040>3</col>/7");
		assertNotNull(counts);
		assertEquals(3, counts[0]);
		assertEquals(7, counts[1]);
	}

	@Test
	public void parseObtainedHandlesCommas()
	{
		int[] counts = CollectionLogCapture.parseObtained("Obtained: 1,024 / 1,500");
		assertNotNull(counts);
		assertEquals(1024, counts[0]);
		assertEquals(1500, counts[1]);
	}

	@Test
	public void parseObtainedRejectsOtherText()
	{
		assertNull(CollectionLogCapture.parseObtained("Kill Count: 12"));
		assertNull(CollectionLogCapture.parseObtained(null));
		assertNull(CollectionLogCapture.parseObtained(""));
	}

	@Test
	public void parseKillCountReadsAmount()
	{
		CollectionLogCapture.KillCount kc =
			CollectionLogCapture.parseKillCount("Kill Count: <col=ffffff>1,234</col>");
		assertNotNull(kc);
		assertEquals("Kill Count", kc.getName());
		assertEquals(1234, kc.getAmount());
	}

	@Test
	public void parseKillCountSkipsObtained()
	{
		assertNull(CollectionLogCapture.parseKillCount("Obtained: 3/7"));
	}

	@Test
	public void parseKillCountHandlesCompletions()
	{
		CollectionLogCapture.KillCount kc =
			CollectionLogCapture.parseKillCount("Completions: 42");
		assertNotNull(kc);
		assertEquals("Completions", kc.getName());
		assertEquals(42, kc.getAmount());
	}
}
