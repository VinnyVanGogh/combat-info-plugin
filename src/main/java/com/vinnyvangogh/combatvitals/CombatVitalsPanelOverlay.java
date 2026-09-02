package com.vinnyvangogh.combatvitals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
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
class CombatVitalsPanelOverlay extends OverlayPanel
{
	/** The same seven the base client compares, in the same order. */
	private static final HiscoreSkill[] COMBAT_SKILLS = {
		HiscoreSkill.ATTACK,
		HiscoreSkill.STRENGTH,
		HiscoreSkill.DEFENCE,
		HiscoreSkill.HITPOINTS,
		HiscoreSkill.RANGED,
		HiscoreSkill.MAGIC,
		HiscoreSkill.PRAYER,
	};

	private static final Skill[] OWN_SKILLS = {
		Skill.ATTACK,
		Skill.STRENGTH,
		Skill.DEFENCE,
		Skill.HITPOINTS,
		Skill.RANGED,
		Skill.MAGIC,
		Skill.PRAYER,
	};

	private static final Color AHEAD = new Color(0, 200, 83);
	private static final Color BEHIND = new Color(220, 60, 60);

	private final CombatVitalsPlugin plugin;
	private final CombatVitalsConfig config;
	private final Client client;

	@Inject
	CombatVitalsPanelOverlay(CombatVitalsPlugin plugin, CombatVitalsConfig config, Client client)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.client = client;

		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_HIGH);
		panelComponent.setBorder(new Rectangle(2, 2, 2, 2));
		addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Combat Vitals overlay");
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

		final CombatVitalsPlugin.Readout readout = plugin.getReadout();
		if (readout == null)
		{
			return null;
		}

		final String text = HealthReadout.text(readout.getRange(), readout.getMaxHealth(),
			readout.getRatio(), readout.getScale(), config.displayMode(), config.percentageDecimals(),
			readout.getExactHealth());
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
				HealthReadout.fraction(readout.getRatio(), readout.getScale(),
					readout.getExactHealth(), readout.getMaxHealth()),
				config.healthFullColour(), config.healthMidColour(), config.healthLowColour()));
		}
		panelComponent.getChildren().add(line.build());

		addStatComparison(readout);

		return super.render(graphics);
	}

	/**
	 * Your combat stats against theirs, from the hiscores result already fetched
	 * for the hitpoints lookup. No second request: the same cache entry answers
	 * both, which is why this costs nothing beyond the panel space.
	 */
	private void addStatComparison(CombatVitalsPlugin.Readout readout)
	{
		if (!config.showStatComparison())
		{
			return;
		}

		final HiscoreResult hiscore = readout.getHiscore();
		if (hiscore == null)
		{
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Skill")
			.right("You / Them")
			.build());

		for (int i = 0; i < COMBAT_SKILLS.length; i++)
		{
			final net.runelite.client.hiscore.Skill theirs = hiscore.getSkill(COMBAT_SKILLS[i]);

			// Unranked in a skill comes back non-positive. Skipping the row is
			// honest; showing a zero would read as a level.
			if (theirs == null || theirs.getLevel() <= 0)
			{
				continue;
			}

			final int mine = client.getRealSkillLevel(OWN_SKILLS[i]);
			final int level = theirs.getLevel();

			panelComponent.getChildren().add(LineComponent.builder()
				.left(COMBAT_SKILLS[i].getName())
				.right(mine + " / " + level)
				.rightColor(mine == level ? Color.WHITE : (mine > level ? AHEAD : BEHIND))
				.build());
		}
	}
}
