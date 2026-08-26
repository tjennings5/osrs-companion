package com.osrsmcp.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The bar identifies a band of possible hitpoints, never an exact value. */
public class HealthBarTest
{
	private static final int MAX = 600;
	private static final int SCALE = 120;

	@Test
	public void fullBarMeansFullHealth()
	{
		assertEquals(MAX, HealthBar.maxHealth(SCALE, SCALE, MAX));
		assertEquals(MAX, HealthBar.estimate(SCALE, SCALE, MAX));
	}

	@Test
	public void everyReadingCoversARangeRatherThanAPoint()
	{
		for (int ratio = 2; ratio < SCALE; ratio++)
		{
			int min = HealthBar.minHealth(ratio, SCALE, MAX);
			int max = HealthBar.maxHealth(ratio, SCALE, MAX);
			assertTrue("ratio " + ratio + " gave an inverted band", max >= min);
			int estimate = HealthBar.estimate(ratio, SCALE, MAX);
			assertTrue("estimate outside its own band at ratio " + ratio,
				estimate >= min && estimate <= max);
		}
	}

	@Test
	public void trackedValueInsideTheBandIsLeftAlone()
	{
		// Hitsplat counting is more precise than the bar, so a consistent value
		// must not be dragged to the band's midpoint.
		int ratio = 80;
		int min = HealthBar.minHealth(ratio, SCALE, MAX);
		int max = HealthBar.maxHealth(ratio, SCALE, MAX);
		for (int hp = min; hp <= max; hp++)
		{
			assertEquals(hp, HealthBar.clampToBand(hp, ratio, SCALE, MAX));
		}
	}

	@Test
	public void correctsUpwardsWhenDamageWasOvercounted()
	{
		// This is the drift that put us ~10 below her real health by the end of a
		// kill: previously only downward corrections were allowed.
		int ratio = 80;
		int min = HealthBar.minHealth(ratio, SCALE, MAX);
		assertEquals(min, HealthBar.clampToBand(min - 25, ratio, SCALE, MAX));
	}

	@Test
	public void correctsDownwardsWhenDamageWasMissed()
	{
		int ratio = 80;
		int max = HealthBar.maxHealth(ratio, SCALE, MAX);
		assertEquals(max, HealthBar.clampToBand(max + 25, ratio, SCALE, MAX));
	}

	@Test
	public void absentBarLeavesTheCountUntouched()
	{
		assertEquals(432, HealthBar.clampToBand(432, -1, SCALE, MAX));
		assertEquals(432, HealthBar.clampToBand(432, 0, SCALE, MAX));
	}
}
