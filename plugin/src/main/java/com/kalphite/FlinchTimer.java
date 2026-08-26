package com.kalphite;

/**
 * A countdown measured in game ticks, from the moment you attack to the moment you may attack
 * again without the target getting a hit back.
 *
 * <p>Client-free on purpose so the counting rules can be tested without a running client, the
 * same way {@code com.combat.AttackClock} is.
 *
 * <p>The important rule is that a running countdown cannot be restarted. Attacking is detected
 * from two independent signals — your animation changing and your hitsplat landing — and for a
 * melee weapon both arrive for the same attack. Letting the second one restart the clock would
 * add a tick every swing. It also means an unrelated animation part-way through, eating or
 * drinking a potion, is harmlessly ignored.
 */
class FlinchTimer
{
	private int ticksRemaining;

	/** True from the attack until the countdown reaches zero. */
	boolean isCountingDown()
	{
		return ticksRemaining > 0;
	}

	/** True when the countdown has elapsed and you are clear to attack again. */
	boolean isReady()
	{
		return ticksRemaining == 0;
	}

	int getTicksRemaining()
	{
		return ticksRemaining;
	}

	/**
	 * Starts the countdown, unless one is already running.
	 *
	 * @return whether this call actually started it, so callers can log the real attacks
	 */
	boolean start(int ticks)
	{
		if (ticksRemaining > 0 || ticks <= 0)
		{
			return false;
		}
		ticksRemaining = ticks;
		return true;
	}

	/** Advances one game tick, stopping at zero rather than going negative. */
	void tick()
	{
		if (ticksRemaining > 0)
		{
			ticksRemaining--;
		}
	}

	/** Clears the countdown, for leaving the fight or logging out. */
	void reset()
	{
		ticksRemaining = 0;
	}
}
