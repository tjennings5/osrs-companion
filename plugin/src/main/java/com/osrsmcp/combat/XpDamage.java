package com.osrsmcp.combat;

import lombok.Getter;

/**
 * Turns combat XP into an estimate of damage already dealt but not yet visible.
 *
 * The point is lead time. A ranged attack rolls its damage and awards XP when
 * the projectile launches, but the hitsplat only appears once it has travelled —
 * at boss attack range that is a tick or two later.
 *
 * <p><b>XP is an estimate, hitsplats are the truth.</b> This class deliberately
 * does not own the health count. Hitsplats do, and XP only supplies the
 * in-flight delta on top. An earlier version drove health directly from XP and a
 * wrong conversion rate compounded across a whole fight with nothing able to
 * correct it. Here a wrong rate only distorts the one or two hits currently in
 * the air, and is erased the moment they land.
 *
 * <p>Rate: melee and ranged award 4 XP per damage across the style skills
 * however they are split — rapid puts all 4 in Ranged, longrange splits 2/2 with
 * Defence, controlled splits 4 three ways. Monsters then apply their own
 * experience multiplier, passed in by the caller. The rate is also re-derived
 * from observed hitsplats, so it self-corrects if that figure is ever wrong or
 * changes.
 */
public final class XpDamage
{
	public static final double BASE_XP_PER_DAMAGE = 4.0;

	/** Confirmed damage required before the observed rate is trusted over the default. */
	public static final int CALIBRATION_MIN_DAMAGE = 100;

	/** Sanity bounds; anything outside these means the reading is wrong, not the constant. */
	public static final double MIN_PLAUSIBLE_RATE = 3.5;
	public static final double MAX_PLAUSIBLE_RATE = 6.5;

	private final double defaultXpPerDamage;

	private long totalXp;

	@Getter
	private int confirmedDamage;

	@Getter
	private double xpPerDamage;

	@Getter
	private boolean calibrated;

	/**
	 * @param monsterXpMultiplier the monster's experience bonus, e.g. 1.15 for
	 *                            Cerberus (xpbonus = 15 on her wiki infobox). Use
	 *                            1.0 for a monster with no bonus.
	 */
	public XpDamage(double monsterXpMultiplier)
	{
		this.defaultXpPerDamage = BASE_XP_PER_DAMAGE * monsterXpMultiplier;
		this.xpPerDamage = defaultXpPerDamage;
	}

	public void addXp(int xpDelta)
	{
		if (xpDelta > 0)
		{
			totalXp += xpDelta;
		}
	}

	/**
	 * Records damage a hitsplat has confirmed, and re-derives the XP rate once the
	 * visible damage has caught up with the XP — the only moment the two totals
	 * describe the same set of hits.
	 */
	public void addConfirmedDamage(int damage)
	{
		if (damage <= 0)
		{
			return;
		}
		confirmedDamage += damage;

		if (getInFlightDamage() > 0 || confirmedDamage < CALIBRATION_MIN_DAMAGE)
		{
			return;
		}

		double observed = (double) totalXp / confirmedDamage;
		if (observed >= MIN_PLAUSIBLE_RATE && observed <= MAX_PLAUSIBLE_RATE)
		{
			xpPerDamage = observed;
			calibrated = true;
		}
	}

	/** Damage the XP says has been dealt but no hitsplat has shown yet. */
	public int getInFlightDamage()
	{
		int fromXp = (int) Math.floor(totalXp / xpPerDamage);
		return Math.max(0, fromXp - confirmedDamage);
	}

	public boolean hasSeenAnyXp()
	{
		return totalXp > 0;
	}

	public void reset()
	{
		totalXp = 0;
		confirmedDamage = 0;
		// The learned rate is a property of the monster, not of one kill, so it
		// deliberately survives a reset and benefits the next fight immediately.
	}
}
