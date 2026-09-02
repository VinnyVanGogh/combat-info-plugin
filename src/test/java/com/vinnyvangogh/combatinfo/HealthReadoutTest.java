package com.vinnyvangogh.combatinfo;

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
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.HITPOINTS, false));
	}

	@Test
	public void showsPercentage()
	{
		assertEquals("67%",
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.PERCENTAGE, false));
	}

	@Test
	public void showsBoth()
	{
		assertEquals("15 / 22 (67%)",
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.BOTH, false));
	}

	@Test
	public void showsDecimalPercentage()
	{
		assertEquals("66.7%",
			HealthReadout.text(guard(), 22, 20, 30, DisplayMode.PERCENTAGE, true));
	}

	@Test
	public void fallsBackToPercentageWhenMaxHealthIsUnknown()
	{
		// An unranked player, or the lookup turned off. Showing the fraction is
		// honest; inventing a hitpoints value would not be.
		assertEquals("67%",
			HealthReadout.text(null, -1, 20, 30, DisplayMode.HITPOINTS, false));
	}

	@Test
	public void fractionIsClampedAndSafe()
	{
		assertEquals(0.0, HealthReadout.fraction(0, 30), 0.0001);
		assertEquals(1.0, HealthReadout.fraction(30, 30), 0.0001);
		assertEquals(0.0, HealthReadout.fraction(10, 0), 0.0001);
		assertEquals(0.5, HealthReadout.fraction(15, 30), 0.0001);
	}

	@Test
	public void colourReadsAsHealthyAtFullAndDangerousAtEmpty()
	{
		// Deliberately not asserting that any one channel moves monotonically.
		// The ramp passes through amber, whose green channel is higher than the
		// green endpoint's, so "greener as health rises" is not true and is not
		// what the gradient is for. What must hold is which channel dominates.
		assertTrue("full health should read green, not red",
			HealthReadout.colour(1.0).getGreen() > HealthReadout.colour(1.0).getRed());
		assertTrue("empty health should read red, not green",
			HealthReadout.colour(0.0).getRed() > HealthReadout.colour(0.0).getGreen());
		assertTrue("the ends must be visibly different",
			!HealthReadout.colour(0.0).equals(HealthReadout.colour(1.0)));
	}

	@Test
	public void colourHandlesOutOfRangeInput()
	{
		// Never throw over a display detail.
		HealthReadout.colour(-5);
		HealthReadout.colour(5);
	}
}
