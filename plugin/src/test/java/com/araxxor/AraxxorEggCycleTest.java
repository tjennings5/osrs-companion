package com.araxxor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AraxxorEggCycleTest
{
	/**
	 * Eggs at the eight compass points around a centre of (0,0). The
	 * south-easternmost is unambiguous here, which is what the ordering hangs on.
	 */
	private static List<AraxxorEggCycle.Egg> compassRing()
	{
		return new ArrayList<>(Arrays.asList(
			new AraxxorEggCycle.Egg(0, 10, AraxxorMinion.RUPTURA),      // N
			new AraxxorEggCycle.Egg(7, 7, AraxxorMinion.ACIDIC),        // NE
			new AraxxorEggCycle.Egg(10, 0, AraxxorMinion.MIRRORBACK),   // E
			new AraxxorEggCycle.Egg(7, -7, AraxxorMinion.ACIDIC),       // SE
			new AraxxorEggCycle.Egg(0, -10, AraxxorMinion.MIRRORBACK),  // S
			new AraxxorEggCycle.Egg(-7, -7, AraxxorMinion.RUPTURA),     // SW
			new AraxxorEggCycle.Egg(-10, 0, AraxxorMinion.ACIDIC),      // W
			new AraxxorEggCycle.Egg(-7, 7, AraxxorMinion.MIRRORBACK))); // NW
	}

	@Test
	public void firstEggHatchesAfterThreeAttacksThenEverySix()
	{
		assertEquals(3, AraxxorEggCycle.attackForHatch(0));
		assertEquals(9, AraxxorEggCycle.attackForHatch(1));
		assertEquals(15, AraxxorEggCycle.attackForHatch(2));
		assertEquals(51, AraxxorEggCycle.attackForHatch(8));
	}

	@Test
	public void countsHatchesAgainstTheAttackNumber()
	{
		assertEquals(0, AraxxorEggCycle.hatchesSoFar(0));
		assertEquals(0, AraxxorEggCycle.hatchesSoFar(2));
		assertEquals(1, AraxxorEggCycle.hatchesSoFar(3));
		assertEquals(1, AraxxorEggCycle.hatchesSoFar(8));
		assertEquals(2, AraxxorEggCycle.hatchesSoFar(9));
		assertEquals(3, AraxxorEggCycle.hatchesSoFar(15));
	}

	@Test
	public void countsDownToTheNextHatch()
	{
		assertEquals(3, AraxxorEggCycle.attacksUntilNextHatch(0));
		assertEquals(1, AraxxorEggCycle.attacksUntilNextHatch(2));
		// Immediately after a hatch the next one is a full period away.
		assertEquals(6, AraxxorEggCycle.attacksUntilNextHatch(3));
		assertEquals(1, AraxxorEggCycle.attacksUntilNextHatch(8));
		assertEquals(6, AraxxorEggCycle.attacksUntilNextHatch(9));
	}

	@Test
	public void hatchOrderStartsAtTheSouthEasternEgg()
	{
		List<AraxxorMinion> order = AraxxorEggCycle.hatchOrder(compassRing());
		// The SE egg is ACIDIC, and it is what fixes the fight's special.
		assertEquals(AraxxorMinion.ACIDIC, order.get(0));
		assertEquals(AraxxorSpecial.ACID_BALL, order.get(0).getSpecial());
	}

	@Test
	public void hatchOrderRunsClockwiseFromThere()
	{
		// Clockwise from SE is SE, S, SW, W, NW, N, NE, E.
		assertEquals(Arrays.asList(
			AraxxorMinion.ACIDIC,      // SE
			AraxxorMinion.MIRRORBACK,  // S
			AraxxorMinion.RUPTURA,     // SW
			AraxxorMinion.ACIDIC,      // W
			AraxxorMinion.MIRRORBACK,  // NW
			AraxxorMinion.RUPTURA,     // N
			AraxxorMinion.ACIDIC,      // NE
			AraxxorMinion.MIRRORBACK   // E
		), AraxxorEggCycle.hatchOrder(compassRing()));
	}

	@Test
	public void orderingDoesNotDependOnTheOrderEggsSpawned()
	{
		List<AraxxorEggCycle.Egg> shuffled = compassRing();
		Collections.reverse(shuffled);
		assertEquals(AraxxorEggCycle.hatchOrder(compassRing()),
			AraxxorEggCycle.hatchOrder(shuffled));
	}

	@Test
	public void typeWrapsOnceTheRingIsExhausted()
	{
		List<AraxxorMinion> order = AraxxorEggCycle.hatchOrder(compassRing());
		assertEquals(order.get(0), AraxxorEggCycle.typeAt(order, order.size()));
		assertEquals(order.get(1), AraxxorEggCycle.typeAt(order, order.size() + 1));
	}

	@Test
	public void noEggsMeansNoPrediction()
	{
		assertEquals(Collections.emptyList(), AraxxorEggCycle.hatchOrder(Collections.emptyList()));
		assertNull(AraxxorEggCycle.typeAt(Collections.emptyList(), 0));
		assertNull(AraxxorEggCycle.typeAt(null, 0));
	}
}
