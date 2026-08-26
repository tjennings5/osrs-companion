package com.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Cerberus awards 4.6 XP per damage: 4 for the style skills, times her 1.15
 * experience multiplier (xpbonus = 15 on her infobox, corroborated by her
 * 690 slayer XP being exactly 600 hitpoints x 1.15).
 */
public class XpDamageTest
{
	/** Cerberus' experience multiplier (xpbonus = 15), the case this logic was built against. */
	private static final double CERBERUS_XP_MULTIPLIER = 1.15;
	private static final double CERBERUS_XP_PER_DAMAGE = XpDamage.BASE_XP_PER_DAMAGE * CERBERUS_XP_MULTIPLIER;

	@Test
	public void usesCerberusExperienceMultiplierNotThePlainRate()
	{
		assertEquals(4.6, CERBERUS_XP_PER_DAMAGE, 0.0001);

		// A 40 damage hit awards 184 XP here, not 160. Dividing by 4 would have
		// read it as 46 - the ~15% overshoot that broke the health count.
		XpDamage xp = new XpDamage(CERBERUS_XP_MULTIPLIER);
		xp.addXp(184);
		assertEquals(40, xp.getInFlightDamage());
	}

	@Test
	public void inFlightDamageClearsAsHitsplatsConfirmIt()
	{
		XpDamage xp = new XpDamage(CERBERUS_XP_MULTIPLIER);
		xp.addXp((int) Math.round(30 * CERBERUS_XP_PER_DAMAGE));
		assertEquals(30, xp.getInFlightDamage());

		xp.addConfirmedDamage(30);
		assertEquals("landed damage must stop counting as in flight", 0, xp.getInFlightDamage());
	}

	@Test
	public void aWrongRateCannotAccumulate()
	{
		// Even with a badly wrong rate, once hitsplats confirm the damage the
		// in-flight figure returns to zero rather than drifting fight-long.
		XpDamage xp = new XpDamage(CERBERUS_XP_MULTIPLIER);
		for (int i = 0; i < 20; i++)
		{
			xp.addXp(1000);
			xp.addConfirmedDamage(1000);
		}
		assertEquals(0, xp.getInFlightDamage());
	}

	@Test
	public void ratePerDamageIsMeasuredFromRealHits()
	{
		XpDamage xp = new XpDamage(CERBERUS_XP_MULTIPLIER);
		assertFalse(xp.isCalibrated());

		// Feed exactly 4.6 XP per damage and it should recover that rate.
		for (int i = 0; i < 10; i++)
		{
			xp.addXp(92);           // 20 damage at 4.6
			xp.addConfirmedDamage(20);
		}
		assertTrue("should have measured the rate by now", xp.isCalibrated());
		assertEquals(4.6, xp.getXpPerDamage(), 0.05);
	}

	@Test
	public void implausibleMeasurementsAreRejected()
	{
		XpDamage xp = new XpDamage(CERBERUS_XP_MULTIPLIER);
		for (int i = 0; i < 10; i++)
		{
			xp.addXp(2000);          // absurd rate
			xp.addConfirmedDamage(20);
		}
		assertFalse(xp.isCalibrated());
		assertEquals(CERBERUS_XP_PER_DAMAGE, xp.getXpPerDamage(), 0.0001);
	}

	@Test
	public void resetKeepsTheLearnedRateForTheNextKill()
	{
		XpDamage xp = new XpDamage(CERBERUS_XP_MULTIPLIER);
		for (int i = 0; i < 10; i++)
		{
			xp.addXp(92);
			xp.addConfirmedDamage(20);
		}
		double learned = xp.getXpPerDamage();
		xp.reset();
		assertEquals(0, xp.getConfirmedDamage());
		assertEquals("the rate belongs to the monster, not the kill", learned, xp.getXpPerDamage(), 0.0001);
	}
}
