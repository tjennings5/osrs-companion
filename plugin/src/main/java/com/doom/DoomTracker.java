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
	 * safe one, since a shield made early dissolves once it reaches its
	 * destination. Recorded boundary: a shield up at +19 blocked the +21 wave,
	 * one up at +20 did not (the player took 32).
	 */
	private static final int SHIELD_SLACK_TICKS = 2;

	/**
	 * From clicking an orb to the shield appearing, beyond the arrow's own
	 * hit delay. Late clicks in recordings took hit delay + 3 (click +15,
	 * shield +20; click +14, shield +19), so this is the worst case seen.
	 */
	private static final int CLICK_TO_SHIELD_EXTRA_TICKS = 3;

	/** Delve 5+ gets the boss' shield bonus reliably; at delves 3-4 it was missing about half the time. */
	private static final int SHIELD_BONUS_MIN_DELVE = 5;

	/**
	 * Car phase, from the delve 5 and 6 recordings: the boss starts moving 3
	 * ticks after its eye appears and arrives at +6. At delve 6+ it slams on
	 * arrival - or at +3 if the eye is under it already and it doesn't move -
	 * and the slam's damage lands 6 ticks after the slam animation.
	 */
	private static final int EYE_TO_ARRIVAL_TICKS = 6;
	private static final int EYE_TO_SLAM_STATIONARY_TICKS = 3;
	private static final int SLAM_ANIM_TO_DAMAGE_TICKS = 6;

	/**
	 * When the shield phase ends the boss takes a free hit that grows with
	 * how long the shield lasted: about 1 per 2 ticks plus 1, capped at 50.
	 * Fitted to 59 recorded shield phases (37 ticks gave 20, 57 gave 32, 82
	 * gave 42, and every one of 105+ gave 50). Larva kills only matter by
	 * keeping the shield up - the same 4 kills gave 20 to 28 by duration.
	 * It's also capped by the boss' remaining hitpoints.
	 */
	private static final int SHIELD_BONUS_CAP = 50;
	private static final int SHIELD_BONUS_BASE = 1;
	private static final int SHIELD_TICKS_PER_BONUS = 2;

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

	private int shieldStartTick = -1;

	/** Larvae killed during the current shield phase. */
	@Getter
	private int shieldLarvaKills;

	/** Tick the current car slam's damage lands: estimated from the eye, then exact once the slam starts. */
	private int slamDamageTick = -1;

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
		shieldStartTick = -1;
		shieldLarvaKills = 0;
		slamDamageTick = -1;
		eyeLocation = null;
		chargeStart = null;
	}

	void onDelveLevel(int level)
	{
		delve = level;
		resetDelve();
	}

	void onBossForm(int npcId, int tick)
	{
		if (npcId == DoomIds.BOSS_SHIELDED)
		{
			if (phase != Phase.SHIELDED)
			{
				shieldStartTick = tick;
				shieldLarvaKills = 0;
			}
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

	void onLarvaKilled()
	{
		if (phase == Phase.SHIELDED)
		{
			shieldLarvaKills++;
		}
	}

	/** Estimated free hit if the shield ended now, or -1 outside the shield phase. */
	int getShieldBonus(int tick)
	{
		if (phase != Phase.SHIELDED || shieldStartTick < 0 || delve < SHIELD_BONUS_MIN_DELVE)
		{
			return -1;
		}
		return Math.min(SHIELD_BONUS_CAP, (tick - shieldStartTick) / SHIELD_TICKS_PER_BONUS + SHIELD_BONUS_BASE);
	}

	/** Ticks of shield left until the free hit reaches its cap; 0 once it has. */
	int ticksToMaxShieldBonus(int tick)
	{
		int maxTick = shieldStartTick + (SHIELD_BONUS_CAP - SHIELD_BONUS_BASE) * SHIELD_TICKS_PER_BONUS;
		return Math.max(0, maxTick - tick);
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
			case AnimationID.DOM_BURROWED_EXPLOSION:
				slamDamageTick = tick + SLAM_ANIM_TO_DAMAGE_TICKS;
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
		boolean stationary = eye != null && from != null && eye.getX() == from.getX() && eye.getY() == from.getY();
		slamDamageTick = tick + (stationary ? EYE_TO_SLAM_STATIONARY_TICKS : EYE_TO_ARRIVAL_TICKS)
			+ SLAM_ANIM_TO_DAMAGE_TICKS;
	}

	/** True while the burrowed boss is about to charge or charging along its path. */
	boolean isChargePathActive(int tick)
	{
		return eyeTick >= 0 && tick <= eyeTick + EYE_TO_ARRIVAL_TICKS;
	}

	/** Ticks until the current car slam's damage, or -1 if none is coming. */
	int ticksUntilSlam(int tick)
	{
		if (slamDamageTick < 0 || delve < FIRST_SLAM_DELVE)
		{
			return -1;
		}
		int left = slamDamageTick - tick;
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

	/**
	 * Ticks from a Rock Throw to its first follow-up landing, by delve. The
	 * rock splits at +7 (+6 from delve 5) and the follow-ups speed up with
	 * depth; measured across every recorded rock: +13 at delves 1, 3 and 4,
	 * +15 at delve 2, +12 at 5-6 and +10 at 7.
	 */
	private int rockFirstHitTicks()
	{
		if (delve == 2)
		{
			return 15;
		}
		if (delve <= 4)
		{
			return 13;
		}
		return delve <= 6 ? 12 : 10;
	}

	/** Ticks from a Rock Throw to its last follow-up landing: +16 at delves 1-4, +14 at 5-6, +12 at 7. */
	private int rockLastHitTicks()
	{
		if (delve <= 4)
		{
			return 16;
		}
		return delve <= 6 ? 14 : 12;
	}

	/** Style of the rock thrown most recently, while its follow-ups are still to land; otherwise null. */
	AttackStyle getRockCallout(int tick)
	{
		return rockLaunchTick >= 0 && tick <= rockLaunchTick + rockLastHitTicks() ? rockStyle : null;
	}

	/**
	 * Ticks until the boss attacks again after a melee punish, or -1 outside
	 * that window. Measured from the punish (BEAM_CANCEL) to the next attack
	 * animation across all recordings: 6 at delves 1-6 (7 was also seen at
	 * delves 1-2; the earlier value is the one to plan around). Delve 7+ is
	 * unrecorded and takes the wiki's one-tick-shorter delay.
	 */
	int ticksUntilAttackAfterPunish(int tick)
	{
		if (lastPunishTick < 0)
		{
			return -1;
		}
		int delay = delve <= 6 ? 6 : 5;
		int left = lastPunishTick + delay - tick;
		return left >= 0 ? left : -1;
	}

	/**
	 * True if the current rock's follow-ups land while shockwaves are hitting,
	 * so the prayer and the earthen shield both need attention at once.
	 */
	boolean isRockDuringShockwave(int tick)
	{
		if (getRockCallout(tick) == null || !isShockwavePending(tick))
		{
			return false;
		}
		int firstWave = shockwaveSpawnTick + SHOCKWAVE_HIT_TICKS;
		return rockLaunchTick + rockFirstHitTicks() <= lastShockwaveTick()
			&& rockLaunchTick + rockLastHitTicks() >= firstWave;
	}

	/** Ticks until the last shockwave's damage: how long to stay in the earthen shield. */
	int ticksUntilLastWave(int tick)
	{
		return lastShockwaveTick() - tick;
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
