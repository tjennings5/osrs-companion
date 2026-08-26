package com.osrsmcp.cerberus;

import com.osrsmcp.combat.AttackClock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Replays the attack animations from a real recorded fight (client.log,
 * 2026-08-04 12:17) through the clock.
 *
 * That fight is the reason the clock exists: the plugin counted 9 attacks where
 * roughly 14 happened, because Cerberus' DEFEND animation replaced five
 * consecutive attack animations while she was being damaged, and the triple's
 * two visible components were each counted as a separate attack.
 */
public class CerberusClockReplayTest
{
	/** Cerberus' attack speed in ticks. */
	private static final int CERBERUS_SPEED = 6;

	/** Attack animation ticks as logged. Everything else was DEFEND or an animation-end. */
	private static final int[] OBSERVED_ATTACK_ANIMATIONS = {29, 32, 44, 74, 80, 86, 96, 99, 111};

	private static final int LAST_TICK = 115;

	/**
	 * Feeds the recorded animations plus a game tick for every tick in between,
	 * exactly as the plugin would see them.
	 */
	private static List<String> replay(AttackClock clock)
	{
		List<String> events = new ArrayList<>();
		int nextAnim = 0;
		for (int tick = OBSERVED_ATTACK_ANIMATIONS[0]; tick <= LAST_TICK; tick++)
		{
			while (nextAnim < OBSERVED_ATTACK_ANIMATIONS.length
				&& OBSERVED_ATTACK_ANIMATIONS[nextAnim] == tick)
			{
				AttackClock.Event e = clock.onAttackAnimation(tick);
				events.add(e + "@" + tick + "=#" + clock.getAttackCount());
				nextAnim++;
			}
			if (clock.onGameTick(tick) == AttackClock.Event.ATTACK)
			{
				events.add("CLOCK@" + tick + "=#" + clock.getAttackCount());
			}
		}
		return events;
	}

	@Test
	public void recoversTheAttacksThatDefendHid()
	{
		AttackClock clock = new AttackClock(CERBERUS_SPEED, AttackClock.DEFAULT_TOLERANCE);
		replay(clock);

		// The old animation-only counter reached 9. The grid says roughly 14
		// attacks occurred in this span; the clock must land in that region and
		// well clear of the undercount.
		assertTrue("expected the clock to recover the masked attacks, got " + clock.getAttackCount(),
			clock.getAttackCount() >= 13);
	}

	@Test
	public void tripleComponentsDoNotEachCountAsAnAttack()
	{
		AttackClock clock = new AttackClock(CERBERUS_SPEED, AttackClock.DEFAULT_TOLERANCE);
		List<String> events = replay(clock);

		// Ticks 32 and 99 are the melee halves of the two triples in this fight;
		// both must be folded into the attack already counted, not counted anew.
		assertTrue(events.toString(), events.stream().anyMatch(e -> e.startsWith("SUB_ATTACK@32")));
		assertTrue(events.toString(), events.stream().anyMatch(e -> e.startsWith("SUB_ATTACK@99")));
	}

	@Test
	public void combosLandOnTheSlotsTheWikiSays()
	{
		AttackClock clock = new AttackClock(CERBERUS_SPEED, AttackClock.DEFAULT_TOLERANCE);
		List<String> events = replay(clock);

		// Every sub-attack marks a triple, and triples are attacks 1, 11, 21...
		// If the phase is right, each one lands on such a slot.
		for (String e : events)
		{
			if (e.startsWith("SUB_ATTACK"))
			{
				int attack = Integer.parseInt(e.substring(e.indexOf('#') + 1));
				assertTrue("sub-attack reported on attack " + attack + ", which is no combo slot: " + events,
					CerberusHelperPlugin.isTripleSlot(attack));
			}
		}
	}

	@Test
	public void rephasesFromTheLastComponentOfATriple()
	{
		// Phasing from the first component leaves the clock 3 ticks early for the
		// rest of the fight; the next attack follows the last component.
		AttackClock clock = new AttackClock(CERBERUS_SPEED, AttackClock.DEFAULT_TOLERANCE);
		clock.onAttackAnimation(29);
		clock.onAttackAnimation(32);
		assertEquals(32 + 6, clock.getNextAttackTick());
	}

	@Test
	public void animationOnScheduleBeatsTheClockToTheSlot()
	{
		AttackClock clock = new AttackClock(CERBERUS_SPEED, AttackClock.DEFAULT_TOLERANCE);
		clock.onAttackAnimation(100);
		assertEquals(1, clock.getAttackCount());

		// The clock must not fire for a slot an animation already claimed.
		assertEquals(AttackClock.Event.NONE, clock.onGameTick(105));
		assertEquals(AttackClock.Event.ATTACK, clock.onAttackAnimation(106));
		assertEquals(2, clock.getAttackCount());
		assertEquals(AttackClock.Event.NONE, clock.onGameTick(107));
	}

	@Test
	public void clockGivesUpWhenNothingConfirmsItForTooLong()
	{
		AttackClock clock = new AttackClock(CERBERUS_SPEED, AttackClock.DEFAULT_TOLERANCE);
		clock.onAttackAnimation(0);
		assertTrue(clock.isAnchored());

		// She stopped fighting; the clock must not invent attacks indefinitely.
		for (int tick = 1; tick <= 500; tick++)
		{
			clock.onGameTick(tick);
		}
		assertFalse("clock should have unanchored once nothing confirmed it", clock.isAnchored());
	}

	@Test
	public void survivesFiveConsecutiveMaskedAttacks()
	{
		// The recorded fight had five attacks in a row hidden by DEFEND. The
		// give-up threshold has to sit above that or it unanchors mid-fight.
		assertTrue(AttackClock.MAX_UNCONFIRMED_SLOTS > 5);
	}
}
