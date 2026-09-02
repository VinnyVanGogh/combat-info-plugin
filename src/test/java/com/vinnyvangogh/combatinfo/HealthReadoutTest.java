package com.vinnyvangogh.combatinfo;

import java.awt.Color;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HealthReadoutTest
{
	/** Measured: Guard, 22 max hitpoints, healthScale 30, ratio 20 -> exactly 15. */
	private static HealthRecovery.Range guard()
	{
		return HealthRecovery.recover(20, 30, 22);
	}

	@Test
	public void showsHitpoints()
	{
		assertEquals("15 / 22",
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.HITPOINTS, false, -1));
	}

	@Test
	public void showsPercentage()
	{
		assertEquals("67%",
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.PERCENTAGE, false, -1));
	}

	@Test
	public void showsBoth()
	{
		assertEquals("15 / 22 (67%)",
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.BOTH, false, -1));
	}

	@Test
	public void showsDecimalPercentage()
	{
		assertEquals("66.7%",
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.PERCENTAGE, true, -1));
	}

	@Test
	public void fallsBackToPercentageWhenMaxHealthIsUnknown()
	{
		// An unranked player, or the lookup turned off. Showing the fraction is
		// honest; inventing a hitpoints value would not be.
		assertEquals("67%",
			HealthReadout.text(null, -1, 20, 30, DisplayMode.HITPOINTS, false, -1));
	}

	@Test
	public void anExactValueIsShownAsItselfNotRecovered()
	{
		// Your own health is known, so it must be printed rather than guessed.
		// The recovered range for this ratio is 28-30 with midpoint 29; if the
		// exact value ever loses to the midpoint, the readout has started
		// inventing numbers it did not need to.
		assertEquals("30 / 88",
			HealthReadout.text(HealthRecovery.recover(10, 30, 88), 88, 10, 30,
				DisplayMode.HITPOINTS, false, 30));
	}

	@Test
	public void anExactValueDrivesThePercentageToo()
	{
		assertEquals("34%",
			HealthReadout.text(null, 88, -1, -1, DisplayMode.PERCENTAGE, false, 30));
	}

	@Test
	public void exactFractionIgnoresTheBar()
	{
		// scale and ratio are absent for a self readout; the orb supplies both
		// numbers and the bar is not consulted at all.
		assertEquals(0.5, HealthReadout.fraction(-1, -1, 44, 88), 0.0001);
		assertEquals(1.0, HealthReadout.fraction(-1, -1, 99, 99), 0.0001);
		assertEquals(0.0, HealthReadout.fraction(-1, -1, 0, 99), 0.0001);
	}

	@Test
	public void fractionIsClampedAndSafe()
	{
		assertEquals(0.0, HealthReadout.fraction(0, 30, -1, -1), 0.0001);
		assertEquals(1.0, HealthReadout.fraction(30, 30, -1, -1), 0.0001);
		assertEquals(0.0, HealthReadout.fraction(10, 0, -1, -1), 0.0001);
		assertEquals(0.5, HealthReadout.fraction(15, 30, -1, -1), 0.0001);
	}

	private static final Color FULL = new Color(0, 146, 54, 230);
	private static final Color MID = new Color(255, 193, 7, 230);
	private static final Color LOW = new Color(199, 26, 26, 230);

	private static Color colour(double fraction)
	{
		return HealthReadout.colour(fraction, FULL, MID, LOW);
	}

	@Test
	public void colourReadsAsHealthyAtFullAndDangerousAtEmpty()
	{
		// Deliberately not asserting that any one channel moves monotonically.
		// The ramp passes through amber, whose green channel is higher than the
		// green endpoint's, so "greener as health rises" is not true and is not
		// what the gradient is for. What must hold is which channel dominates.
		assertTrue("full health should read green, not red",
			colour(1.0).getGreen() > colour(1.0).getRed());
		assertTrue("empty health should read red, not green",
			colour(0.0).getRed() > colour(0.0).getGreen());
		assertTrue("the ends must be visibly different",
			!colour(0.0).equals(colour(1.0)));
	}

	@Test
	public void colourHandlesOutOfRangeInput()
	{
		// Never throw over a display detail.
		colour(-5);
		colour(5);
	}

	@Test
	public void colourUsesTheStopsItIsGiven()
	{
		// The stops are user-configurable, so the ramp must follow them rather
		// than any palette baked into the class.
		final Color blue = new Color(0, 0, 255);
		final Color white = new Color(255, 255, 255);
		final Color black = new Color(0, 0, 0);

		assertEquals(blue, HealthReadout.colour(1.0, blue, white, black));
		assertEquals(black, HealthReadout.colour(0.0, blue, white, black));
		assertEquals(white, HealthReadout.colour(0.5, blue, white, black));
	}
}
