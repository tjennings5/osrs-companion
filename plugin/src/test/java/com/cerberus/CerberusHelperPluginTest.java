package com.osrsmcp.cerberus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Checks the attack sequence against the wiki's mechanics table:
 * triple on attack 1 then every ten, souls every seventh below 400 hp, lava
 * every fifth below 200 hp, resolved triple > souls > lava when they collide.
 */
public class CerberusHelperPluginTest
{
	private static final int FULL = 600;
	private static final int BELOW_SOULS = 399;
	private static final int BELOW_LAVA = 199;

	@Test
	public void tripleOpensTheFightAndRepeatsEveryTen()
	{
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(1, FULL));
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(11, FULL));
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(21, FULL));
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(31, FULL));
	}

	@Test
	public void soulsRequireBelow400Hp()
	{
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(7, 400));
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(7, FULL));
		assertEquals(CerberusSpecial.SOULS, CerberusHelperPlugin.predictSpecial(7, BELOW_SOULS));
		assertEquals(CerberusSpecial.SOULS, CerberusHelperPlugin.predictSpecial(14, BELOW_SOULS));
	}

	@Test
	public void lavaRequiresBelow200Hp()
	{
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(5, 200));
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(5, BELOW_SOULS));
		assertEquals(CerberusSpecial.LAVA, CerberusHelperPlugin.predictSpecial(5, BELOW_LAVA));
		assertEquals(CerberusSpecial.LAVA, CerberusHelperPlugin.predictSpecial(10, BELOW_LAVA));
	}

	@Test
	public void tripleBeatsSoulsOnAttack21()
	{
		// The wiki calls out attack #21 specifically as the overlap case.
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(21, BELOW_LAVA));
	}

	@Test
	public void soulsBeatLavaWhenBothAreDue()
	{
		// 35 is divisible by both 7 and 5, and is not a triple slot.
		assertEquals(CerberusSpecial.SOULS, CerberusHelperPlugin.predictSpecial(35, BELOW_LAVA));
	}

	@Test
	public void attack21IsNotARealSoulSlot()
	{
		// The whole No Ghosts strategy rests on this: 21 is divisible by 7, but
		// combo outranks souls, so the slot is consumed and no souls appear.
		assertTrue(CerberusHelperPlugin.isSoulSlot(7));
		assertTrue(CerberusHelperPlugin.isSoulSlot(14));
		assertFalse(CerberusHelperPlugin.isSoulSlot(21));
		assertTrue(CerberusHelperPlugin.isSoulSlot(28));
	}

	@Test
	public void holdingThrough14BuysFourteenAttacks()
	{
		// The wiki's claim: survive attack 14 above 400 and no ghosts appear
		// until attack 28.
		assertEquals(28, CerberusHelperPlugin.nextSoulsAttack(14));
		assertEquals(14, CerberusHelperPlugin.nextSoulsAttack(14) - 14);
	}

	@Test
	public void earlierSoulSlotsOnlyBuySeven()
	{
		// Why the strategy waits for 14 rather than going after 7: the gap after
		// 7 is half as long and not enough to finish her from 400.
		assertEquals(7, CerberusHelperPlugin.nextSoulsAttack(0));
		assertEquals(14, CerberusHelperPlugin.nextSoulsAttack(7));
		assertEquals(7, CerberusHelperPlugin.nextSoulsAttack(7) - 7);
		assertEquals(7, CerberusHelperPlugin.nextSoulsAttack(28) - 28);
	}

	@Test
	public void matchesWikiNoGhostsTable()
	{
		// Straight transcription of the wiki's attack table for a No Ghosts run,
		// evaluated below both thresholds so every conditional is live.
		int hp = BELOW_LAVA;
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(1, hp));
		assertEquals(CerberusSpecial.LAVA, CerberusHelperPlugin.predictSpecial(5, hp));
		assertEquals(CerberusSpecial.SOULS, CerberusHelperPlugin.predictSpecial(7, hp));
		assertEquals(CerberusSpecial.LAVA, CerberusHelperPlugin.predictSpecial(10, hp));
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(11, hp));
		assertEquals(CerberusSpecial.SOULS, CerberusHelperPlugin.predictSpecial(14, hp));
		assertEquals(CerberusSpecial.LAVA, CerberusHelperPlugin.predictSpecial(15, hp));
		assertEquals(CerberusSpecial.LAVA, CerberusHelperPlugin.predictSpecial(20, hp));
		assertEquals(CerberusSpecial.TRIPLE, CerberusHelperPlugin.predictSpecial(21, hp));
		assertEquals(CerberusSpecial.LAVA, CerberusHelperPlugin.predictSpecial(25, hp));
		assertEquals(CerberusSpecial.SOULS, CerberusHelperPlugin.predictSpecial(28, hp));
	}

	@Test
	public void holdPhaseStaysSilentAboveThreshold()
	{
		// Above 400 the soul slots produce nothing, which is exactly the gap the
		// No Ghosts tracking exists to fill: the cycle still has to be counted.
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(14, 401));
		assertTrue(CerberusHelperPlugin.isSoulSlot(14));
	}



	@Test
	public void howlSnapsTheCounterToTheRealSoulSlot()
	{
		int drift = CerberusHelperPlugin.MAX_RESYNC_DRIFT;
		assertEquals(14, CerberusHelperPlugin.nearestSoulSlot(14, drift));
		assertEquals(14, CerberusHelperPlugin.nearestSoulSlot(13, drift));
		assertEquals(14, CerberusHelperPlugin.nearestSoulSlot(15, drift));
		assertEquals(28, CerberusHelperPlugin.nearestSoulSlot(27, drift));
		assertEquals(7, CerberusHelperPlugin.nearestSoulSlot(8, drift));
	}

	@Test
	public void howlFarFromAnySoulSlotIsNotGuessedAt()
	{
		// Soul slots are 7 apart, so a howl this far from one means the model is
		// wrong rather than the count. Snapping anyway would invent an answer.
		assertEquals(-1, CerberusHelperPlugin.nearestSoulSlot(22, CerberusHelperPlugin.MAX_RESYNC_DRIFT));
	}

	@Test
	public void howlOnAComboSlotIsNeverExplainedAway()
	{
		// 21 is divisible by 7 but combo takes it, so souls cannot fire there.
		// Neither can any slot within the drift limit, so this stays unexplained.
		assertFalse(CerberusHelperPlugin.isSoulSlot(21));
		assertEquals(-1, CerberusHelperPlugin.nearestSoulSlot(21, CerberusHelperPlugin.MAX_RESYNC_DRIFT));
	}

	@Test
	public void resyncPreferstheEarlierSlotWhenEquidistant()
	{
		// Deterministic tie-breaking matters: the same fight must always
		// reconstruct the same way.
		assertEquals(CerberusHelperPlugin.nearestSoulSlot(21, 7), CerberusHelperPlugin.nearestSoulSlot(21, 7));
		assertEquals(14, CerberusHelperPlugin.nearestSoulSlot(21, 7));
	}

	@Test
	public void predictiveHoldWarnsBeforeTheFixedThreshold()
	{
		// 430 hp with 35s coming in: the next hit crosses 400, so warn now even
		// though the fixed 410 threshold has not been reached.
		assertTrue(CerberusHelperPlugin.shouldWarnHold(430, 35, 410, true));
		assertFalse(CerberusHelperPlugin.shouldWarnHold(430, 35, 410, false));
	}

	@Test
	public void fixedThresholdStillWarnsWithNoDamageHistory()
	{
		// First hit of the fight, nothing learned yet: the fixed threshold has to
		// carry it, or the warning never fires at all.
		assertTrue(CerberusHelperPlugin.shouldWarnHold(450, 0, 450, true));
		assertFalse(CerberusHelperPlugin.shouldWarnHold(500, 0, 450, true));
	}

	@Test
	public void noHoldWarningOnceAlreadyBelow400()
	{
		// Past the line the warning is pointless - that is the reset case, and a
		// late "hold damage" would just be noise.
		assertFalse(CerberusHelperPlugin.shouldWarnHold(399, 40, 450, true));
		assertFalse(CerberusHelperPlugin.shouldWarnHold(399, 40, 450, false));
	}

	@Test
	public void smallHitsDoNotTriggerThePredictionEarly()
	{
		// Chipping at her with 5s should not fire the warning 50 hp out.
		assertFalse(CerberusHelperPlugin.shouldWarnHold(450, 5, 410, true));
		assertTrue(CerberusHelperPlugin.shouldWarnHold(404, 5, 410, true));
	}

	@Test
	public void ordinaryAttacksAreNotSpecial()
	{
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(2, BELOW_LAVA));
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(3, BELOW_LAVA));
		assertEquals(CerberusSpecial.NORMAL, CerberusHelperPlugin.predictSpecial(0, BELOW_LAVA));
	}
}
