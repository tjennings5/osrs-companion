package com.osrsmcp.cerberus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Checks the max hit arithmetic against known reference values. */
public class CerberusMaxHitTest
{
	@Test
	public void matchesTheStandardFormulaAtBaseline()
	{
		// 99 strength, no bonuses at all: the well-known unarmed-ish baseline.
		// effective = 99 + 0 + 8 = 107; base = floor(0.5 + 107*64/640) = 11
		assertEquals(11, CerberusMaxHit.maxHit(99, 0, 1.0, 0, 1.0));
	}

	@Test
	public void prayerAndStyleRaiseIt()
	{
		int plain = CerberusMaxHit.maxHit(99, 100, 1.0, 0, 1.0);
		int prayed = CerberusMaxHit.maxHit(99, 100, 1.23, 3, 1.0);
		assertTrue("piety/rigour plus a style bonus must raise the max hit", prayed > plain);
	}

	@Test
	public void slayerHelmetMultiplierApplies()
	{
		int without = CerberusMaxHit.maxHit(99, 100, 1.23, 3, 1.0);
		int with = CerberusMaxHit.maxHit(99, 100, 1.23, 3, 1.15);
		assertEquals((int) Math.floor(without * 1.15), with);
	}

	@Test
	public void zeroLevelIsNotNegativeOrCrashing()
	{
		assertEquals(0, CerberusMaxHit.maxHit(0, 100, 1.23, 3, 1.15));
	}

	@Test
	public void bestOfTakesTheConservativeStyle()
	{
		// The weapon is never classified; whichever style could hit harder is the
		// one the hold warning has to plan around.
		assertEquals(42, CerberusMaxHit.bestOf(30, 42));
		assertEquals(42, CerberusMaxHit.bestOf(42, 30));
	}
}
