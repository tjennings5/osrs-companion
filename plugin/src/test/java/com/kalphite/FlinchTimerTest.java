package com.kalphite;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlinchTimerTest
{
	private FlinchTimer timer;

	@Before
	public void setUp()
	{
		timer = new FlinchTimer();
	}

	@Test
	public void startsReadyBeforeAnyAttack()
	{
		assertTrue(timer.isReady());
		assertFalse(timer.isCountingDown());
		assertEquals(0, timer.getTicksRemaining());
	}

	@Test
	public void countsDownOneTickAtATime()
	{
		timer.start(10);

		assertEquals(10, timer.getTicksRemaining());
		timer.tick();
		assertEquals(9, timer.getTicksRemaining());
		assertTrue(timer.isCountingDown());
		assertFalse(timer.isReady());
	}

	@Test
	public void becomesReadyExactlyAfterTheConfiguredTicks()
	{
		timer.start(10);
		for (int i = 0; i < 9; i++)
		{
			timer.tick();
		}

		assertFalse("should still be waiting on the 9th tick", timer.isReady());
		timer.tick();
		assertTrue("should be ready on the 10th", timer.isReady());
	}

	@Test
	public void doesNotGoNegativeWhenLeftRunning()
	{
		timer.start(2);
		for (int i = 0; i < 10; i++)
		{
			timer.tick();
		}

		assertEquals(0, timer.getTicksRemaining());
		assertTrue(timer.isReady());
	}

	/**
	 * The plugin detects an attack from both the animation and the hitsplat, and for a melee
	 * weapon both land on the same tick. If the second one restarted the clock, every swing would
	 * silently gain a tick.
	 */
	@Test
	public void aSecondSignalForTheSameAttackDoesNotRestartIt()
	{
		assertTrue(timer.start(10));
		timer.tick();

		assertFalse("a running countdown must not restart", timer.start(10));
		assertEquals(9, timer.getTicksRemaining());
	}

	@Test
	public void canBeStartedAgainOnceElapsed()
	{
		timer.start(3);
		timer.tick();
		timer.tick();
		timer.tick();

		assertTrue(timer.isReady());
		assertTrue(timer.start(3));
		assertEquals(3, timer.getTicksRemaining());
	}

	@Test
	public void resetClearsARunningCountdown()
	{
		timer.start(10);
		timer.reset();

		assertTrue(timer.isReady());
		assertEquals(0, timer.getTicksRemaining());
	}

	@Test
	public void aNonPositiveIntervalIsRefusedRatherThanStickingOnZero()
	{
		assertFalse(timer.start(0));
		assertFalse(timer.start(-5));
		assertTrue(timer.isReady());
	}
}
