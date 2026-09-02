package com.vinnyvangogh.combatinfo;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Config group is "combat-info" and must never be renamed without a migration —
 * renaming silently resets everyone's saved settings.
 */
@ConfigGroup(CombatInfoConfig.GROUP)
public interface CombatInfoConfig extends Config
{
	String GROUP = "combat-info";

	@ConfigSection(
		name = "Display",
		description = "What the readout shows and where",
		position = 0
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Targets",
		description = "Which opponents the readout appears for",
		position = 1
	)
	String targetSection = "targets";

	@ConfigSection(
		name = "Advanced",
		description = "Timeouts and diagnostics",
		position = 2,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "showPanel",
		name = "Show panel",
		description = "Draw the opponent's health in a corner panel, like the stock plugin.",
		section = displaySection,
		position = 0
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverhead",
		name = "Show over target",
		description = "Draw the opponent's health above them in the world.",
		section = displaySection,
		position = 1
	)
	default boolean showOverhead()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayMode",
		name = "Display",
		description = "Show hitpoints, a percentage, or both.",
		section = displaySection,
		position = 2
	)
	default DisplayMode displayMode()
	{
		return DisplayMode.BOTH;
	}

	@ConfigItem(
		keyName = "percentageDecimals",
		name = "Decimal percentage",
		description = "Show one decimal place on percentages.",
		section = displaySection,
		position = 3
	)
	default boolean percentageDecimals()
	{
		return false;
	}

	@ConfigItem(
		keyName = "colourGradient",
		name = "Colour by health",
		description = "Fade the text from green to red as the target loses health.",
		section = displaySection,
		position = 8
	)
	default boolean colourGradient()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overheadPosition",
		name = "Overhead position",
		description = "Where above the target the text sits. Large NPCs often read better on Bottom.",
		section = displaySection,
		position = 5
	)
	default OverheadPosition overheadPosition()
	{
		return OverheadPosition.TOP;
	}

	@Alpha
	@ConfigItem(
		keyName = "healthFullColour",
		name = "Colour at full",
		description = "Colour of the readout at full health, used when Colour by health is on.",
		section = displaySection,
		position = 9
	)
	default Color healthFullColour()
	{
		return new Color(0, 146, 54, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "healthMidColour",
		name = "Colour at half",
		description = "Colour the readout passes through at half health.",
		section = displaySection,
		position = 10
	)
	default Color healthMidColour()
	{
		return new Color(255, 193, 7, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "healthLowColour",
		name = "Colour at empty",
		description = "Colour of the readout as health approaches zero.",
		section = displaySection,
		position = 11
	)
	default Color healthLowColour()
	{
		return new Color(199, 26, 26, 230);
	}

	@ConfigItem(
		keyName = "overheadStyle",
		name = "Style",
		description =
			"Bar behind text draws a health bar sized to fit the text, carrying the colour so the text "
				+ "can stay white. Text above health bar lifts the text clear of the game's own bar "
				+ "instead. Text only draws coloured text where it falls, which will overlap the "
				+ "game's bar.",
		section = displaySection,
		position = 4
	)
	default OverheadStyle overheadStyle()
	{
		return OverheadStyle.BAR;
	}

	@Alpha
	@ConfigItem(
		keyName = "barBackground",
		name = "Bar background",
		description =
			"Colour behind the health fill, used by the Bar behind text style. Opaque by default so "
				+ "the game's own health bar does not show through it. Lower the alpha to let it "
				+ "show again.",
		section = displaySection,
		position = 7
	)
	default Color barBackground()
	{
		return Color.BLACK;
	}

	@Range(min = -200, max = 200)
	@ConfigItem(
		keyName = "overheadOffset",
		name = "Overhead offset",
		description = "Extra height in pixels, applied after the position above.",
		section = displaySection,
		position = 6
	)
	default int overheadOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "hideGameHealthBar",
		name = "Hide game's health bar",
		description =
			"Hide the game's own health bar over your current target, so only this plugin's readout "
				+ "shows. The game offers no way to hide the bar alone: this hides the target's entire "
				+ "2D layer, which also removes their name, hitsplats and overhead prayer icons. That "
				+ "is a bad trade in PvP, which is why it is off by default.",
		section = displaySection,
		position = 12
	)
	default boolean hideGameHealthBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOwnHealth",
		name = "Show your own health",
		description =
			"Draw your own health over your character while you are engaged, in the same style as your "
				+ "opponent's. Taken from the hitpoints orb, so it is exact rather than recovered.",
		section = targetSection,
		position = 4
	)
	default boolean showOwnHealth()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showForNpcs",
		name = "NPC targets",
		description = "Show the readout when fighting NPCs.",
		section = targetSection,
		position = 0
	)
	default boolean showForNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showForPlayers",
		name = "Player targets",
		description = "Show the readout when fighting other players.",
		section = targetSection,
		position = 1
	)
	default boolean showForPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overheadForPlayers",
		name = "Draw over players",
		description = "Draw over player targets as well as NPCs. Turn off to keep players in the panel only.",
		section = targetSection,
		position = 2
	)
	default boolean overheadForPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPlayerHitpoints",
		name = "Look up player hitpoints",
		description =
			"Fetch the opponent's Hitpoints level from the hiscores so their health can be shown as a "
				+ "number rather than a percentage. One lookup per opponent, cached. Sends their display "
				+ "name to Jagex's hiscores service. The stock Opponent Information plugin does this with "
				+ "no way to turn it off.",
		section = targetSection,
		position = 3
	)
	default boolean showPlayerHitpoints()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "showStatComparison",
		name = "Stat comparison panel",
		description =
			"Compare your combat stats against a player opponent's, in the panel. Reads the same "
				+ "cached hiscores result as the hitpoints lookup, so it costs no extra request.",
		section = targetSection,
		position = 5
	)
	default boolean showStatComparison()
	{
		return false;
	}

	@ConfigItem(
		keyName = "opponentTimeout",
		name = "Opponent timeout (s)",
		description = "How long the readout stays up after combat ends. The stock plugin fixes this at 5.",
		section = advancedSection,
		position = 0
	)
	default int opponentTimeout()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "hideWhenHpHud",
		name = "Hide behind boss HP bar",
		description = "Stand down when the game's own boss health bar is already showing this target.",
		section = advancedSection,
		position = 1
	)
	default boolean hideWhenHpHud()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideWhenStockEnabled",
		name = "Hide panel if stock plugin is on",
		description =
			"Hide this plugin's panel while Opponent Information is enabled, so the two do not draw "
				+ "over each other. The overhead text is unaffected.",
		section = advancedSection,
		position = 2
	)
	default boolean hideWhenStockEnabled()
	{
		return true;
	}

}
