package com.combat;

import lombok.Getter;

/**
 * A boss' attack sequence modelled as a fixed-period tick clock that animations
 * correct, rather than as a pure animation counter.
 *
 * Animation counting on its own does not work. This was learned the hard way at
 * Cerberus: while she is taking damage she plays her DEFEND animation, and it
 * replaces whatever attack animation would otherwise be visible. In a recorded
 * fight five consecutive attacks disappeared that way — every one of them
 * landing exactly on the tick grid, with a DEFEND in its place. Any counter that
 * only advances on a visible attack animation silently loses those attacks and
 * never recovers.
 *
 * So the clock advances on schedule whether or not an animation shows up, and
 * animations are used for two things instead: confirming the phase, and
 * identifying attacks that are not new attacks at all. An attack animation
 * arriving off schedule is another component of the attack already counted —
 * which is precisely what Cerberus' triple looks like.
 *
 * Pure and client-free so a recorded fight can be replayed against it in tests.
 */
public final class AttackClock
{
	public enum Event
	{
		/** Nothing happened this call. */
		NONE,
		/** A new attack was registered. */
		ATTACK,
		/**
		 * Another animation belonging to the attack already counted. Cerberus'
		 * triple is the motivating case, which makes this a reliable "this attack
		 * is a combo" signal even in phases where nothing else reveals it.
		 */
		SUB_ATTACK
	}

	/**
	 * How far off schedule an attack animation may land and still count as the
	 * scheduled attack. Observed offsets were 0 or ±1; Cerberus' triple components
	 * sit 3-4 ticks out, so 1 separates the two cleanly.
	 */
	public static final int DEFAULT_TOLERANCE = 1;

	/**
	 * Consecutive attacks the clock may invent without a single animation
	 * confirming it before it gives up and stops. Five masked attacks in a row is
	 * normal while a boss is being damaged, so this has to sit above that or it
	 * would unanchor mid-fight.
	 */
	public static final int MAX_UNCONFIRMED_SLOTS = 6;

	private final int tolerance;

	/**
	 * Not final: Araxxor speeds up from 6 ticks to 4 when he enrages, so the
	 * period has to be changeable without losing the count or the phase.
	 */
	@Getter
	private int attackSpeedTicks;

	@Getter
	private boolean anchored;

	@Getter
	private int attackCount;

	@Getter
	private int nextAttackTick;

	@Getter
	private int unconfirmedSlots;

	public AttackClock(int attackSpeedTicks, int tolerance)
	{
		this.attackSpeedTicks = attackSpeedTicks;
		this.tolerance = tolerance;
	}

	/** True when an animation landing on {@code animTick} is the attack due at {@code scheduledTick}. */
	public static boolean isOnSchedule(int animTick, int scheduledTick, int tolerance)
	{
		return Math.abs(animTick - scheduledTick) <= tolerance;
	}

	/**
	 * Changes the attack period mid-fight, re-phasing the next attack from
	 * {@code currentTick} so a boss that speeds up doesn't leave the clock waiting
	 * on the old, slower schedule. The attack count is deliberately preserved —
	 * Araxxor's egg cycle keeps counting across the enrage transition.
	 */
	public void setAttackSpeed(int ticks, int currentTick)
	{
		if (ticks <= 0 || ticks == attackSpeedTicks)
		{
			return;
		}
		attackSpeedTicks = ticks;
		if (anchored)
		{
			nextAttackTick = currentTick + ticks;
		}
	}

	public Event onAttackAnimation(int tick)
	{
		if (!anchored)
		{
			anchored = true;
			registerAttack(tick);
			unconfirmedSlots = 0;
			return Event.ATTACK;
		}

		if (isOnSchedule(tick, nextAttackTick, tolerance))
		{
			registerAttack(tick);
			unconfirmedSlots = 0;
			return Event.ATTACK;
		}

		// Off schedule: a further component of the attack already counted. Re-phase
		// from it, because the next attack follows the combo's *last* component
		// rather than its first — phasing from the first puts the clock 3 ticks
		// early for the rest of the fight.
		nextAttackTick = tick + attackSpeedTicks;
		unconfirmedSlots = 0;
		return Event.SUB_ATTACK;
	}

	/**
	 * Advances the clock when the scheduled attack produced no visible animation.
	 * Fires a tick after the tolerance window closes so a real animation always
	 * gets first claim on the slot.
	 */
	public Event onGameTick(int tick)
	{
		if (!anchored)
		{
			return Event.NONE;
		}

		if (tick < nextAttackTick + tolerance + 1)
		{
			return Event.NONE;
		}

		registerAttack(nextAttackTick);
		unconfirmedSlots++;
		if (unconfirmedSlots > MAX_UNCONFIRMED_SLOTS)
		{
			// The boss is not fighting any more - reset rather than counting phantom attacks.
			anchored = false;
		}
		return Event.ATTACK;
	}

	/** Corrects the attack number without disturbing the clock's phase. */
	public void resyncAttackCount(int correctedAttackNumber)
	{
		attackCount = correctedAttackNumber;
	}

	public void reset()
	{
		anchored = false;
		attackCount = 0;
		nextAttackTick = 0;
		unconfirmedSlots = 0;
	}

	private void registerAttack(int tick)
	{
		attackCount++;
		nextAttackTick = tick + attackSpeedTicks;
	}
}
