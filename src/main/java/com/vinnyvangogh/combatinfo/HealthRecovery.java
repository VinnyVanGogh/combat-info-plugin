package com.vinnyvangogh.combatinfo;

/**
 * Inverts the server's health-bar quantisation.
 *
 * The server broadcasts a health bar as a ratio out of a scale rather than a
 * value, computing
 *
 *     healthRatio = 1 + (healthScale - 1) * health / maxHealth
 *
 * with integer division, and forcing healthRatio = 0 when health = 0. Inverting
 * that recovers a range of healths consistent with the observation, which
 * collapses to a single value only when {@code maxHealth <= healthScale}.
 *
 * Phase 0.5 measured healthScale at 30 for every actor, so NPCs at or under 30
 * hitpoints resolve exactly and players never do. The range is the honest
 * result; {@link Range#midpoint()} is the best single guess within it and is
 * what the base client displays.
 *
 * Pure arithmetic — no client, no state, fully unit testable.
 */
final class HealthRecovery
{
	private HealthRecovery()
	{
	}

	static final class Range
	{
		private final int min;
		private final int max;

		private Range(int min, int max)
		{
			this.min = min;
			this.max = max;
		}

		int min()
		{
			return min;
		}

		int max()
		{
			return max;
		}

		/** True when the observation pins the health to a single value. */
		boolean isExact()
		{
			return min == max;
		}

		/**
		 * The value that minimises worst-case error, not the most likely value.
		 * Every health in the range is equally consistent with the observation;
		 * this one is simply never wrong by more than half the range's width.
		 */
		int midpoint()
		{
			return (min + max + 1) / 2;
		}

		int width()
		{
			return max - min + 1;
		}

		@Override
		public String toString()
		{
			return isExact() ? Integer.toString(min) : min + "-" + max;
		}
	}

	/**
	 * @param healthRatio the actor's {@code getHealthRatio()}
	 * @param healthScale the actor's {@code getHealthScale()}
	 * @param maxHealth   the actor's true maximum health, known independently
	 * @return every health consistent with the observation
	 */
	static Range recover(int healthRatio, int healthScale, int maxHealth)
	{
		if (maxHealth <= 0 || healthScale <= 0)
		{
			throw new IllegalArgumentException(
				"maxHealth and healthScale must be positive, got " + maxHealth + " and " + healthScale);
		}

		// health = 0 forces healthRatio = 0, so a zero ratio is unambiguous.
		if (healthRatio <= 0)
		{
			return new Range(0, 0);
		}

		int min = 1;
		int max;

		if (healthScale > 1)
		{
			// healthRatio = 1 carries no lower bound beyond "alive", because the
			// health = 0 special case means ratio 1 is not produced by the
			// general formula the way ratios above 1 are.
			if (healthRatio > 1)
			{
				min = (maxHealth * (healthRatio - 1) + healthScale - 2) / (healthScale - 1);
			}

			max = Math.min((maxHealth * healthRatio - 1) / (healthScale - 1), maxHealth);
		}
		else
		{
			// With a scale of 1 the ratio is 1 for any living actor, so the
			// observation says only that they are alive.
			max = maxHealth;
		}

		// Guard the degenerate cases rather than trusting the arithmetic to stay
		// ordered at the boundaries.
		min = Math.max(1, Math.min(min, maxHealth));
		max = Math.max(min, Math.min(max, maxHealth));

		return new Range(min, max);
	}
}
