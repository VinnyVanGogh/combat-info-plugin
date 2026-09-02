package com.vinnyvangogh.combatinfo;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

/**
 * The corner panel, equivalent to the stock plugin's. It reads the tick
 * snapshot and does no work of its own beyond laying out text.
 */
class CombatInfoPanelOverlay extends OverlayPanel
{
	private final CombatInfoPlugin plugin;
	private final CombatInfoConfig config;

	@Inject
	CombatInfoPanelOverlay(CombatInfoPlugin plugin, CombatInfoConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_HIGH);
		panelComponent.setBorder(new Rectangle(2, 2, 2, 2));
		addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Combat Info overlay");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPanel())
		{
			return null;
		}

		// Two panels stacked in the same corner reads as a broken plugin, so
		// stand down rather than draw over the stock one.
		if (config.hideWhenStockEnabled() && plugin.stockPluginEnabled())
		{
			return null;
		}

		final CombatInfoPlugin.Readout readout = plugin.getReadout();
		if (readout == null)
		{
			return null;
		}

		final String text = HealthReadout.text(readout.getRange(), readout.getMaxHealth(),
			readout.getRatio(), readout.getScale(), config.displayMode(), config.percentageDecimals());
		if (text == null)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(readout.getName())
			.build());

		final LineComponent.LineComponentBuilder line = LineComponent.builder().left(text);
		if (config.colourGradient())
		{
			line.leftColor(HealthReadout.colour(
				HealthReadout.fraction(readout.getRatio(), readout.getScale())));
		}
		panelComponent.getChildren().add(line.build());

		return super.render(graphics);
	}
}
