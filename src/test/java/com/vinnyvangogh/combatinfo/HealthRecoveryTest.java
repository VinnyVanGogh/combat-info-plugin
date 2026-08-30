package com.vinnyvangogh.combatinfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The recovery math is the one part of this plugin that is pure, so it is the
 * one part with no excuse for being untested.
 *
 * Several expectations below are pinned to measurements in BRIEF.md Phase 0.5
 * rather than to what the implementation happens to return.
 */
public class HealthRecoveryTest
{
	/** The server's own calculation, used to verify the inverse round-trips. */
	private static int serverRatio(int health, int healthScale, int maxHealth)
	{
		return health <= 0 ? 0 : 1 + (healthScale - 1) * health / maxHealth;
	}

	@Test
	public void recoversNpcHealthExactly()
	{
		// Measured: Guard, maxHealth 22, healthScale 30, ratio 20.
		HealthRecovery.Range r = HealthRecovery.recover(20, 30, 22);
		assertTrue("maxHealth <= healthScale must resolve exactly", r.isExact());
		assertEquals(15, r.min());
		assertEquals(15, r.midpoint());
	}

	@Test
	public void recoversPlayerHealthAsAnInterval()
	{
		// Measured: a live opponent, 88 max hitpoints, healthScale 30, ratio 10,
		// displayed by the stock plugin as "29/88".
		HealthRecovery.Range r = HealthRecovery.recover(10, 30, 88);
		assertFalse("maxHealth > healthScale cannot resolve exactly", r.isExact());
		assertEquals(28, r.min());
		assertEquals(30, r.max());
		assertEquals(3, r.width());
		assertEquals("the base client displays this midpoint", 29, r.midpoint());
	}

	@Test
	public void healthScaleOfOneKnowsOnlyThatTheActorLives()
	{
		HealthRecovery.Range r = HealthRecovery.recover(1, 1, 99);
		assertFalse(r.isExact());
		assertEquals(1, r.min());
		assertEquals(99, r.max());
	}

	@Test
	public void zeroRatioIsUnambiguouslyDead()
	{
		HealthRecovery.Range r = HealthRecovery.recover(0, 30, 88);
		assertTrue(r.isExact());
		assertEquals(0, r.min());
		assertEquals(0, r.midpoint());
	}

	@Test
	public void ratioOfOneHasNoLowerBoundBeyondAlive()
	{
		// health = 0 forces ratio 0, so ratio 1 is not produced by the general
		// formula and carries no usable lower bound.
		HealthRecovery.Range r = HealthRecovery.recover(1, 30, 88);
		assertEquals(1, r.min());
	}

	@Test
	public void everyRecoveredRangeContainsTheTrueHealth()
	{
		// The property the readout depends on: whatever the display shows, the
		// range must never exclude the actual value.
		for (int maxHealth = 1; maxHealth <= 120; maxHealth++)
		{
			for (int health = 1; health <= maxHealth; health++)
			{
				int ratio = serverRatio(health, 30, maxHealth);
				HealthRecovery.Range r = HealthRecovery.recover(ratio, 30, maxHealth);

				assertTrue(
					"range " + r + " excluded true health " + health + " of " + maxHealth,
					health >= r.min() && health <= r.max());
			}
		}
	}

	@Test
	public void midpointIsNeverWrongByMoreThanHalfTheRange()
	{
		for (int maxHealth = 1; maxHealth <= 120; maxHealth++)
		{
			for (int health = 1; health <= maxHealth; health++)
			{
				HealthRecovery.Range r =
					HealthRecovery.recover(serverRatio(health, 30, maxHealth), 30, maxHealth);

				assertTrue(
					"midpoint " + r.midpoint() + " too far from " + health + " for range " + r,
					Math.abs(r.midpoint() - health) <= r.width() / 2);
			}
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonPositiveMaxHealth()
	{
		HealthRecovery.recover(10, 30, 0);
	}
}
