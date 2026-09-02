package com.vinnyvangogh.combatinfo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws the readout over the target itself, which is the thing the stock
 * plugin cannot do.
 */
class CombatInfoOverheadOverlay extends Overlay
{
	private final CombatInfoPlugin plugin;
	private final CombatInfoConfig config;

	@Inject
	CombatInfoOverheadOverlay(CombatInfoPlugin plugin, CombatInfoConfig config)
	{
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverhead())
		{
			return null;
		}

		final CombatInfoPlugin.Readout readout = plugin.getReadout();
		if (readout == null)
		{
			return null;
		}

		if (!readout.isNpc() && !config.overheadForPlayers())
		{
			return null;
		}

		final String text = HealthReadout.text(readout.getRange(), readout.getMaxHealth(),
			readout.getRatio(), readout.getScale(), config.displayMode(), config.percentageDecimals());
		if (text == null)
		{
			return null;
		}

		final Actor actor = readout.getActor();
		final Point location = actor.getCanvasTextLocation(graphics, text, zOffset(actor));
		if (location == null)
		{
			return null;
		}

		final Color colour = config.colourGradient()
			? HealthReadout.colour(HealthReadout.fraction(readout.getRatio(), readout.getScale()))
			: Color.WHITE;

		OverlayUtil.renderTextLocation(graphics, location, text, colour);
		return null;
	}

	/**
	 * Height above the actor's base. A large NPC's own name plate and the
	 * top-screen boss bar both crowd the top, which is why this is adjustable
	 * rather than fixed.
	 */
	private int zOffset(Actor actor)
	{
		final int logicalHeight = Math.max(0, actor.getLogicalHeight());

		final int base;
		switch (config.overheadPosition())
		{
			case MIDDLE:
				base = logicalHeight / 2;
				break;
			case BOTTOM:
				base = 0;
				break;
			case TOP:
			default:
				base = logicalHeight;
				break;
		}

		return base + config.overheadOffset();
	}
}
