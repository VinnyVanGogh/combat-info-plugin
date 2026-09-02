package com.vinnyvangogh.combatinfo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
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
	/** Space between the text and the edge of the bar behind it. */
	private static final int PAD_X = 4;
	private static final int PAD_Y = 2;

	private static final Color BAR_BACKGROUND = new Color(0, 0, 0, 150);
	private static final Color BAR_BORDER = new Color(0, 0, 0, 200);

	/**
	 * Height cleared when lifting the text above the game's own health bar.
	 * The bar's position is not exposed by the API, so this is a fixed clearance
	 * that puts the text above it; the offset setting fine-tunes from there.
	 */
	private static final int BAR_CLEARANCE = 18;

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

		final double fraction = HealthReadout.fraction(readout.getRatio(), readout.getScale());

		if (config.overheadStyle() == OverheadStyle.BAR)
		{
			drawBar(graphics, location, text, fraction);

			// White on a filled bar. Colouring the text as well as the bar makes
			// the two fight each other — red text on a green bar is the worst
			// case and the reason this style exists.
			OverlayUtil.renderTextLocation(graphics, location, text, Color.WHITE);
			return null;
		}

		final Color colour = config.colourGradient() ? HealthReadout.colour(fraction) : Color.WHITE;
		OverlayUtil.renderTextLocation(graphics, location, text, colour);
		return null;
	}

	/**
	 * A bar exactly as wide as the text it sits behind. The game's own bar is a
	 * fixed handful of pixels and the readout is always wider, so sizing to the
	 * text is what makes the two legible together instead of overlapping.
	 */
	private void drawBar(Graphics2D graphics, Point location, String text, double fraction)
	{
		final FontMetrics metrics = graphics.getFontMetrics();
		final int textWidth = metrics.stringWidth(text);

		final int x = location.getX() - PAD_X;
		final int y = location.getY() - metrics.getAscent() - PAD_Y;
		final int width = textWidth + PAD_X * 2;
		final int height = metrics.getAscent() + metrics.getDescent() + PAD_Y * 2;

		graphics.setColor(BAR_BACKGROUND);
		graphics.fillRect(x, y, width, height);

		final int filled = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
		if (filled > 0)
		{
			graphics.setColor(config.colourGradient()
				? HealthReadout.colour(fraction)
				: new Color(0, 146, 54, 230));
			graphics.fillRect(x, y, filled, height);
		}

		graphics.setColor(BAR_BORDER);
		graphics.drawRect(x, y, width, height);
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

		// ABOVE_BAR has to clear the game's bar; the other styles either cover it
		// or deliberately sit where it falls.
		final int clearance = config.overheadStyle() == OverheadStyle.ABOVE_BAR ? BAR_CLEARANCE : 0;

		return base + clearance + config.overheadOffset();
	}
}
