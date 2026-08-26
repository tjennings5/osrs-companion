package com.osrsmcp.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Boss-agnostic behaviour of the clock. The Cerberus replay against a real
 * recorded fight lives in CerberusClockReplayTest; this covers the mechanics
 * that any boss depends on, including the speed change Araxxor needs at enrage.
 */
public class AttackClockTest
{
	private static AttackClock anchoredAt(int tick, int speed)
	{
		AttackClock clock = new AttackClock(speed, AttackClock.DEFAULT_TOLERANCE);
		clock.onAttackAnimation(tick);
		return clock;
	}

	@Test
	public void firstAnimationAnchorsAndSchedulesTheNext()
	{
		AttackClock clock = anchoredAt(10, 6);
		assertTrue(clock.isAnchored());
		assertEquals(1, clock.getAttackCount());
		assertEquals(16, clock.getNextAttackTick());
	}

	@Test
	public void speedChangeRephasesFromNowAndKeepsTheCount()
	{
		// Araxxor's enrage: 6 ticks becomes 4. The egg cycle keeps counting across
		// the transition, so the count must survive while the phase resets.
		AttackClock clock = anchoredAt(10, 6);
		clock.setAttackSpeed(4, 12);

		assertEquals("attack count must not reset when he speeds up", 1, clock.getAttackCount());
		assertEquals(4, clock.getAttackSpeedTicks());
		assertEquals("next attack should be re-phased from the transition, not the old schedule",
			16, clock.getNextAttackTick());
	}

	@Test
	public void speedChangeToTheSameValueIsANoOp()
	{
		AttackClock clock = anchoredAt(10, 6);
		clock.setAttackSpeed(6, 12);
		assertEquals(16, clock.getNextAttackTick());
	}

	@Test
	public void clockAdvancesWhenTheAnimationIsMissing()
	{
		AttackClock clock = anchoredAt(10, 6);
		// Tolerance window closes at 17, so the invented attack lands at 18.
		for (int tick = 11; tick <= 17; tick++)
		{
			assertEquals(AttackClock.Event.NONE, clock.onGameTick(tick));
		}
		assertEquals(AttackClock.Event.ATTACK, clock.onGameTick(18));
		assertEquals(2, clock.getAttackCount());
	}

	@Test
	public void givesUpAfterTooManyUnconfirmedSlots()
	{
		AttackClock clock = anchoredAt(10, 6);
		// Run far past the point where a boss that stopped fighting would otherwise
		// have phantom attacks counted forever.
		for (int tick = 11; tick <= 200; tick++)
		{
			clock.onGameTick(tick);
		}
		assertTrue("clock should unanchor rather than invent attacks indefinitely", !clock.isAnchored());
	}

	@Test
	public void offScheduleAnimationIsASubAttackNotANewOne()
	{
		AttackClock clock = anchoredAt(10, 6);
		// 3 ticks out is well beyond tolerance: another component of the same attack.
		assertEquals(AttackClock.Event.SUB_ATTACK, clock.onAttackAnimation(13));
		assertEquals(1, clock.getAttackCount());
		assertEquals("must re-phase from the last component", 19, clock.getNextAttackTick());
	}
}
