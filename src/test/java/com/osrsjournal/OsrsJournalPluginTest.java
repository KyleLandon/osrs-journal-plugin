package com.osrsjournal;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development client entrypoint used by {@code ./gradlew run}.
 * This is the Plugin Hub–documented way to load the plugin locally
 * (not Jagex Launcher sideloading).
 */
public class OsrsJournalPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OsrsJournalPlugin.class);
		RuneLite.main(args);
	}
}
