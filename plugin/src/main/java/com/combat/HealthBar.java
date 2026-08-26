package com.osrsmcp.combat;

/**
 * Converts an NPC's health bar ratio into the range of hitpoints it can mean.
 *
 * The bar is quantised: a boss reporting on a scale of roughly 120 covers about
 * five hitpoints per step, so a single reading never identifies an exact value —
 * it identifies a band. Treating a reading as one number (an earlier version
 * took the ceiling, biasing every sample to the top of its band) is what makes
 * bar-derived health slightly but persistently wrong.
 *
 * Knowing the band is what lets hitsplat-tracked health be corrected only when
 * it is genuinely impossible, instead of being dragged to a rounded value that
 * is less accurate than the count it is replacing.
 *
 * The arithmetic is the standard one the client itself implies, and is shared
 * by RuneLite's own health-bar readers.
 */
public final class HealthBar
{
	private HealthBar()
	{
	}

	/** Lowest hitpoints consistent with this reading, or 0 when the bar shows dead/absent. */
	public static int minHealth(int ratio, int scale, int maxHp)
	{
		if (ratio <= 0 || scale <= 0)
		{
			return 0;
		}
		if (scale <= 1 || ratio <= 1)
		{
			return 1;
		}
		return (maxHp * (ratio - 1) + scale - 2) / (scale - 1);
	}

	/** Highest hitpoints consistent with this reading. */
	public static int maxHealth(int ratio, int scale, int maxHp)
	{
		if (ratio <= 0 || scale <= 0)
		{
			return 0;
		}
		if (scale <= 1)
		{
			return maxHp;
		}
		return Math.min(maxHp, (maxHp * ratio - 1) / (scale - 1));
	}

	/** Midpoint of the band — the best single guess when nothing better is known. */
	public static int estimate(int ratio, int scale, int maxHp)
	{
		if (ratio <= 0 || scale <= 0)
		{
			return 0;
		}
		return (minHealth(ratio, scale, maxHp) + maxHealth(ratio, scale, maxHp) + 1) / 2;
	}

	/**
	 * Pulls a tracked value into the band the bar allows, leaving it untouched
	 * when it is already consistent.
	 *
	 * Correcting in both directions matters: a health count that only ever
	 * corrects one way accumulates error in the other, which is how a small
	 * per-hit overshoot became roughly ten hitpoints by the end of a kill.
	 */
	public static int clampToBand(int tracked, int ratio, int scale, int maxHp)
	{
		if (ratio <= 0 || scale <= 0)
		{
			return tracked;
		}
		int min = minHealth(ratio, scale, maxHp);
		int max = maxHealth(ratio, scale, maxHp);
		if (tracked < min)
		{
			return min;
		}
		if (tracked > max)
		{
			return max;
		}
		return tracked;
	}
}
