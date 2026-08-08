package com.osrsjournal;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class JournalConstantsTest
{
	@Test
	public void nullOrBlankFallsBackToProduction()
	{
		assertEquals(JournalConstants.API_BASE_URL, JournalConstants.resolveApiBase(null));
		assertEquals(JournalConstants.API_BASE_URL, JournalConstants.resolveApiBase(""));
		assertEquals(JournalConstants.API_BASE_URL, JournalConstants.resolveApiBase("   "));
	}

	@Test
	public void customUrlIsTrimmedAndKept()
	{
		assertEquals(
			"https://example.supabase.co/functions/v1",
			JournalConstants.resolveApiBase("  https://example.supabase.co/functions/v1  "));
	}

	@Test
	public void trailingSlashesAreStripped()
	{
		assertEquals(
			"https://example.supabase.co/functions/v1",
			JournalConstants.resolveApiBase("https://example.supabase.co/functions/v1///"));
	}

	@Test
	public void restUrlIsRewrittenToFunctionsEndpoint()
	{
		assertEquals(
			"https://example.supabase.co/functions/v1",
			JournalConstants.resolveApiBase("https://example.supabase.co/rest/v1"));
		assertEquals(
			"https://example.supabase.co/functions/v1",
			JournalConstants.resolveApiBase("https://example.supabase.co/rest/v1/"));
	}
}
