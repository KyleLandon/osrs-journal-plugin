package com.osrsjournal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PairingStateTest
{
	@Test
	public void linkedStateNeedsNoPairingDisplay()
	{
		PairingState state = new PairingState("Zezima", null, "token", true, 0);
		assertFalse(state.needsPairingDisplay());
		assertFalse(state.isCodeExpired());
		assertNull(state.getExpiryLabel());
		assertEquals(0, state.getSecondsRemaining());
	}

	@Test
	public void freshCodeCountsDown()
	{
		long now = System.currentTimeMillis();
		PairingState state = new PairingState("Zezima", "ABC123", null, false, 600, now);
		assertTrue(state.needsPairingDisplay());
		assertFalse(state.isCodeExpired());
		int remaining = state.getSecondsRemaining();
		assertTrue("expected close to 600s, got " + remaining, remaining >= 595 && remaining <= 600);
		assertTrue(state.getExpiryLabel().endsWith("left"));
	}

	@Test
	public void elapsedTimeReducesRemaining()
	{
		long issuedThirtySecondsAgo = System.currentTimeMillis() - 30_000L;
		PairingState state = new PairingState("Zezima", "ABC123", null, false, 600, issuedThirtySecondsAgo);
		int remaining = state.getSecondsRemaining();
		assertTrue("expected ~570s, got " + remaining, remaining >= 565 && remaining <= 571);
	}

	@Test
	public void expiredCodeReportsExpired()
	{
		long issuedLongAgo = System.currentTimeMillis() - 700_000L;
		PairingState state = new PairingState("Zezima", "ABC123", null, false, 600, issuedLongAgo);
		assertEquals(0, state.getSecondsRemaining());
		assertTrue(state.isCodeExpired());
		assertEquals("expired — click New code", state.getExpiryLabel());
	}

	@Test
	public void missingCodeNeverExpires()
	{
		PairingState state = new PairingState("Zezima", null, null, false, 600);
		assertFalse(state.needsPairingDisplay());
		assertFalse(state.isCodeExpired());
	}
}
