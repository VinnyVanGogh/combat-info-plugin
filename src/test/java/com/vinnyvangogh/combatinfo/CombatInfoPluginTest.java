package com.vinnyvangogh.combatinfo;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a developer-mode RuneLite client with this plugin side-loaded.
 * Run it with {@code ./gradlew run}.
 */
public class CombatInfoPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CombatInfoPlugin.class);
		RuneLite.main(args);
	}
}
