package com.doom;

import lombok.Getter;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;

/**
 * Fight state for the Doom of Mokhaiotl, fed by {@link DoomHelperPlugin}'s
 * event handlers and read by the overlays. Plain Java with no client access,
 * so every timing rule lives here in one place.
 *
 * Timings below come from recorded fights (see {@link DoomRecorder}); the
 * comments say which recording behaviour each one is based on.
 */
class DoomTracker
{
	enum Phase
	{
		NORMAL,
		SHIELDED,
		BURROWED
	}

	/** What the boss is charging its beam for, which decides how it has to be interrupted. */
	enum Charge
	{
		NONE,
		/** Charge that starts with a Rock Throw; only a melee hit stops it. */
		MELEE,
		/** Shield phase; charges constantly and only demonbane stops it. */
		SHIELD,
		/** Car phase; charges constantly and any attack stops it. */
		BURROW
	}

	/**
	 * Volatile earth spawn to the first shockwave's damage. The slam animation
	 * starts at +19 and the shockwave graphic and damage land at +21 in every
	 * recorded delve (1-5); later waves follow every 2 ticks.
	 */
	private static final int SHOCKWAVE_HIT_TICKS = 21;

	/**
	 * The shield has to exist this many ticks before the first shockwave's
	 * damage. The countdown's zero is meant to be the true last click, not a
	 * safe one: a shield made early dissolves once it reaches its destination,
	 * which is what let stomps through in recordings (second orb broken at +10
	 * to +12). One tick - up on the tick before the damage - is the estimate;
	 * the latest clean click recorded so far is +14, two ticks earlier than
	 * this gives at normal range, so the next recordings confirm or move it.
	 */
	private static final int SHIELD_SLACK_TICKS = 1;

	/**
	 * From clicking an orb to the shield appearing, beyond the arrow's own
	 * hit delay: one tick for the click to be processed and about one more
	 * for the shield to spawn after the hit. Fitted to recordings, where it
	 * never came out later than this.
	 */
	private static final int CLICK_TO_SHIELD_EXTRA_TICKS = 2;

	/** How long the rock callout stays up: its last follow-up lands by +16 in every recording. */
	private static final int ROCK_CALLOUT_TICKS = 16;

	/**
	 * Car phase, from the delve 5 and 6 recordings: the boss starts moving 3
	 * ticks after its eye appears, arrives (and at delve 6+ slams) at +6, and
	 * the slam's damage lands at +12.
	 */
	private static final int EYE_TO_ARRIVAL_TICKS = 6;
	private static final int EYE_TO_SLAM_DAMAGE_TICKS = 12;

	/** Car slams start at delve 6; delve 5's car phase only zooms. */
	private static final int FIRST_SLAM_DELVE = 6;

	/**
	 * A melee charge starts 2 ticks after a Rock Throw animation. The shield
	 * phase also opens with the charge animation (2 ticks before the boss
	 * turns shielded) but with no Rock Throw before it, which is how the two
	 * are told apart.
	 */
	private static final int MELEE_CHARGE_AFTER_ROCK_TICKS = 3;

	/** Gap between successive shockwaves, from the wiki. */
	private static final int SHOCKWAVE_GAP_TICKS = 2;

	@Getter
	private int delve = 1;

	@Getter
	private Phase phase = Phase.NORMAL;

	/** True while the boss is in its beam charge animation outside the shield/burrow phases. */
	private boolean meleeCharging;

	private int lastRockThrowTick = -1;

	private AttackStyle rockStyle;
	private int rockLaunchTick = -1;

	/** Tick a melee charge was last interrupted, or -1. */
	private int lastPunishTick = -1;

	private int shockwaveSpawnTick = -1;

	@Getter
	private boolean earthenShieldMade;

	private int eyeTick = -1;

	/** Where the burrowed boss is headed (its centre ends up on the eye), and where it set off from. */
	@Getter
	private LocalPoint eyeLocation;
	@Getter
	private LocalPoint chargeStart;

	void reset()
	{
		delve = 1;
		resetDelve();
	}

	/** Clears everything tied to the current boss, keeping the delve level. */
	void resetDelve()
	{
		phase = Phase.NORMAL;
		meleeCharging = false;
		lastRockThrowTick = -1;
		rockStyle = null;
		rockLaunchTick = -1;
		lastPunishTick = -1;
		shockwaveSpawnTick = -1;
		earthenShieldMade = false;
		eyeTick = -1;
		eyeLocation = null;
		chargeStart = null;
	}

	void onDelveLevel(int level)
	{
		delve = level;
		resetDelve();
	}

	void onBossForm(int npcId)
	{
		if (npcId == DoomIds.BOSS_SHIELDED)
		{
			phase = Phase.SHIELDED;
		}
		else if (npcId == DoomIds.BOSS_BURROWED)
		{
			phase = Phase.BURROWED;
		}
		else
		{
			phase = Phase.NORMAL;
		}
		meleeCharging = false;
	}

	void onBossAnimation(int animation, int tick)
	{
		switch (animation)
		{
			case AnimationID.DOM_ROCK_THROW_ATTACK:
				lastRockThrowTick = tick;
				meleeCharging = false;
				break;
			case AnimationID.DOM_BEAM_CHARGE:
				meleeCharging = phase == Phase.NORMAL && lastRockThrowTick >= 0
					&& tick - lastRockThrowTick <= MELEE_CHARGE_AFTER_ROCK_TICKS;
				break;
			case AnimationID.DOM_BEAM_CHARGE_LOOP:
				// Follows BEAM_CHARGE a tick later; keeps whatever it decided.
				break;
			case AnimationID.DOM_BEAM_CANCEL:
				if (meleeCharging)
				{
					lastPunishTick = tick;
				}
				meleeCharging = false;
				break;
			case AnimationID.DOM_DESPAWN:
				resetDelve();
				break;
			default:
				meleeCharging = false;
				break;
		}
	}

	void onRockLaunch(AttackStyle style, int tick)
	{
		rockStyle = style;
		rockLaunchTick = tick;
	}

	void onVolatileEarthSpawn(int tick)
	{
		// They all spawn on the same tick; only the first starts the timer.
		if (!isShockwavePending(tick))
		{
			shockwaveSpawnTick = tick;
			earthenShieldMade = false;
		}
	}

	/** The burrowed boss' eye appeared: it will charge from {@code from} to {@code eye}. */
	void onBurrowEye(int tick, LocalPoint eye, LocalPoint from)
	{
		eyeTick = tick;
		eyeLocation = eye;
		chargeStart = from;
	}

	/** True while the burrowed boss is about to charge or charging along its path. */
	boolean isChargePathActive(int tick)
	{
		return eyeTick >= 0 && tick <= eyeTick + EYE_TO_ARRIVAL_TICKS;
	}

	/** Ticks until the current car slam's damage, or -1 if none is coming. */
	int ticksUntilSlam(int tick)
	{
		if (eyeTick < 0 || delve < FIRST_SLAM_DELVE)
		{
			return -1;
		}
		int left = eyeTick + EYE_TO_SLAM_DAMAGE_TICKS - tick;
		return left >= 0 ? left : -1;
	}

	void onEarthenShieldSpawn()
	{
		earthenShieldMade = true;
	}

	void onTick(int tick)
	{
		if (shockwaveSpawnTick >= 0 && tick > lastShockwaveTick())
		{
			shockwaveSpawnTick = -1;
			earthenShieldMade = false;
		}
	}

	Charge getCharge()
	{
		switch (phase)
		{
			case SHIELDED:
				return Charge.SHIELD;
			case BURROWED:
				return Charge.BURROW;
			default:
				return meleeCharging ? Charge.MELEE : Charge.NONE;
		}
	}

	/** Style of the rock thrown most recently, while its follow-ups are still to land; otherwise null. */
	AttackStyle getRockCallout(int tick)
	{
		return rockLaunchTick >= 0 && tick <= rockLaunchTick + ROCK_CALLOUT_TICKS ? rockStyle : null;
	}

	/**
	 * Ticks until the boss attacks again after a melee punish, or -1 outside
	 * that window. Measured from the punish (BEAM_CANCEL) to the next attack
	 * animation: 7 ticks at delve 1 and 6 at delves 3-4 in recordings, which is
	 * the wiki's 8/7/6 table minus one. Delve 7+ follows that table unrecorded.
	 */
	int ticksUntilAttackAfterPunish(int tick)
	{
		if (lastPunishTick < 0)
		{
			return -1;
		}
		int delay = delve <= 2 ? 7 : delve <= 6 ? 6 : 5;
		int left = lastPunishTick + delay - tick;
		return left >= 0 ? left : -1;
	}

	boolean isShockwavePending(int tick)
	{
		return shockwaveSpawnTick >= 0 && tick <= lastShockwaveTick();
	}

	/** Ticks until the first shockwave hits; negative once the waves have started. */
	int ticksUntilShockwave(int tick)
	{
		return shockwaveSpawnTick + SHOCKWAVE_HIT_TICKS - tick;
	}

	/**
	 * Ticks left to click the second volatile earth so the shield is up just
	 * before the first shockwave, given the arrow's hit delay from where the
	 * player is standing. 0 is the last tick to click; negative is too late.
	 */
	int ticksToBreakOrb(int tick, int hitDelay, int offset)
	{
		int lastClick = shockwaveSpawnTick + SHOCKWAVE_HIT_TICKS - SHIELD_SLACK_TICKS
			- CLICK_TO_SHIELD_EXTRA_TICKS - hitDelay + offset;
		return lastClick - tick;
	}

	/** Number of shockwaves at the current delve, from the wiki's table. */
	int shockwaveCount()
	{
		if (delve <= 2)
		{
			return 1;
		}
		if (delve <= 4)
		{
			return 2;
		}
		if (delve <= 6)
		{
			return 3;
		}
		return delve == 7 ? 4 : 5;
	}

	private int lastShockwaveTick()
	{
		return shockwaveSpawnTick + SHOCKWAVE_HIT_TICKS + (shockwaveCount() - 1) * SHOCKWAVE_GAP_TICKS;
	}
}
