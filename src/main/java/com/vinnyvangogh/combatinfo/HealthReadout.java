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
		DisplayMode mode, boolean decimals, int exactHealth)
	{
		final String percent = percentText(ratio, scale, decimals, exactHealth, maxHealth);

		// Without a max health there is no hitpoints value to show, only the
		// fraction of the bar. Falling back is better than showing nothing.
		if (maxHealth <= 0 || (range == null && exactHealth < 0))
		{
			return percent;
		}

		// An exact value is used as-is. Recovering a number the client already
		// knows would print a guess in place of a fact.
		final String hp = (exactHealth >= 0 ? exactHealth : range.midpoint()) + " / " + maxHealth;

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

	static String percentText(int ratio, int scale, boolean decimals, int exactHealth, int maxHealth)
	{
		final double fraction = fraction(ratio, scale, exactHealth, maxHealth) * 100.0;
		return decimals
			? String.format("%.1f%%", fraction)
			: String.format("%d%%", Math.round(fraction));
	}

	/**
	 * Remaining health as 0..1. Uses the exact values when they are known, and
	 * otherwise the bar's own ratio, which needs no recovery to be a fraction.
	 */
	static double fraction(int ratio, int scale, int exactHealth, int maxHealth)
	{
		if (exactHealth >= 0 && maxHealth > 0)
		{
			return Math.max(0, Math.min(1.0, (double) exactHealth / maxHealth));
		}
		if (scale <= 0 || ratio <= 0)
		{
			return 0;
		}
		return Math.min(1.0, (double) ratio / scale);
	}

	/**
	 * Interpolates through three user-chosen stops so the colour moves smoothly
	 * as health drops rather than snapping between bands.
	 */
	static Color colour(double fraction, Color full, Color mid, Color low)
	{
		final double f = Math.max(0, Math.min(1, fraction));
		return f >= 0.5
			? blend(mid, full, (f - 0.5) * 2)
			: blend(low, mid, f * 2);
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
