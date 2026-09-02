package com.vinnyvangogh.combatinfo;

import java.awt.Color;

/**
 * Turns a recovered health range into the text and colour drawn on screen.
 *
 * Pure: no client, no config object, no state. Everything it needs is passed
 * in, so the formatting rules are unit testable without a running game.
 */
final class HealthReadout
{
	private static final Color FULL = new Color(0, 146, 54, 230);
	private static final Color MID = new Color(255, 193, 7, 230);
	private static final Color LOW = new Color(199, 26, 26, 230);

	private HealthReadout()
	{
	}

	/**
	 * @param range      recovered health, or null when max health is unknown
	 * @param maxHealth  the actor's max health, or -1 when unknown
	 * @param ratio      the raw health ratio
	 * @param scale      the raw health scale
	 * @param mode       what the user asked to see
	 * @param decimals   show one decimal place on percentages
	 * @return the text to draw, or null if there is nothing meaningful to say
	 */
	static String text(HealthRecovery.Range range, int maxHealth, int ratio, int scale,
		DisplayMode mode, boolean decimals)
	{
		final String percent = percentText(ratio, scale, decimals);

		// Without a max health there is no hitpoints value to show, only the
		// fraction of the bar. Falling back is better than showing nothing.
		if (range == null || maxHealth <= 0)
		{
			return percent;
		}

		final String hp = range.midpoint() + " / " + maxHealth;

		switch (mode)
		{
			case PERCENTAGE:
				return percent;
			case BOTH:
				return hp + " (" + percent + ")";
			case HITPOINTS:
			default:
				return hp;
		}
	}

	static String percentText(int ratio, int scale, boolean decimals)
	{
		final double fraction = fraction(ratio, scale) * 100.0;
		return decimals
			? String.format("%.1f%%", fraction)
			: String.format("%d%%", Math.round(fraction));
	}

	/** Remaining health as 0..1, straight from the bar with no recovery involved. */
	static double fraction(int ratio, int scale)
	{
		if (scale <= 0 || ratio <= 0)
		{
			return 0;
		}
		return Math.min(1.0, (double) ratio / scale);
	}

	/** Green through amber to red. Interpolated so it moves smoothly as they drop. */
	static Color colour(double fraction)
	{
		final double f = Math.max(0, Math.min(1, fraction));
		return f >= 0.5
			? blend(MID, FULL, (f - 0.5) * 2)
			: blend(LOW, MID, f * 2);
	}

	private static Color blend(Color from, Color to, double t)
	{
		return new Color(
			(int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
			(int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
			(int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
			(int) Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
	}
}
