package com.vinnyvangogh.combatinfo;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Combat Info — a standalone replacement for the stock Opponent Information
 * plugin.
 *
 * This class carries no overlay yet. BRIEF.md gates every line of readout
 * behaviour behind Phase 0 (compliance research, now complete) and Phase 0.5
 * (measuring the real precision of the health-ratio recovery before any readout
 * is designed around it). Writing the overlay first and checking the rules
 * afterwards is how a plugin gets rejected once the work is already done.
 *
 * All this currently does is run {@link HealthScaleProbe}, the Phase 0.5
 * instrumentation. Both the probe and its wiring here come out before Phase 2.
 *
 * The template's config class was deleted rather than left empty: every option
 * this plugin will carry is a Phase 2 decision, and a stub config only invites
 * guessing at them early.
 */
@Slf4j
@PluginDescriptor(
	name = "Combat Info",
	description = "Opponent health for NPC and player targets, with overhead rendering and honest uncertainty",
	tags = {"combat", "overlay", "opponent", "health", "hitpoints", "pvp", "pvm"}
)
public class CombatInfoPlugin extends Plugin
{
	@Inject
	private EventBus eventBus;

	@Inject
	private HealthScaleProbe healthScaleProbe;

	@Override
	protected void startUp()
	{
		// The probe is not a Plugin, so it does not get subscribed for free.
		eventBus.register(healthScaleProbe);
		healthScaleProbe.startUp();
		log.debug("Combat Info started");
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(healthScaleProbe);
		healthScaleProbe.shutDown();
		log.debug("Combat Info stopped");
	}
}
