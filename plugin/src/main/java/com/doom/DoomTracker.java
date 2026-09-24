package com.doom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

	@Getter
	@RequiredArgsConstructor
	static final class Incoming
	{
		private final AttackStyle style;
		private final int landTick;
		/** True for a Rock Throw follow-up predicted from the launch, before its real projectile exists. */
		private final boolean predicted;
	}

	/**
	 * Ticks from a Rock Throw launch to its first follow-up projectile landing.
	 * The rock splits 7 ticks after launch, the follow-ups appear at +10 and
	 * the first lands at +13 on every delve recorded so far (1-4).
	 */
	private static final int ROCK_FIRST_HIT_TICKS = 13;

	/**
	 * Volatile earth spawn to the boss' shockwave slam animation: +19 in every
	 * recorded delve (1-5). Damage follows a tick or two later; counting to the
	 * animation keeps the timer on the safe side.
	 */
	private static final int SHOCKWAVE_HIT_TICKS = 19;

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

	private final List<Incoming> incoming = new ArrayList<>();

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
		lastPunishTick = -1;
		shockwaveSpawnTick = -1;
		earthenShieldMade = false;
		eyeTick = -1;
		eyeLocation = null;
		chargeStart = null;
		incoming.clear();
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

	void onStandardProjectile(AttackStyle style, int landTick)
	{
		// The real follow-ups of a Rock Throw have arrived; drop its prediction.
		incoming.removeIf(i -> i.isPredicted() && landTick >= i.getLandTick());
		incoming.add(new Incoming(style, landTick, false));
		incoming.sort(Comparator.comparingInt(Incoming::getLandTick));
	}

	void onRockLaunch(AttackStyle style, int tick)
	{
		incoming.add(new Incoming(style, tick + ROCK_FIRST_HIT_TICKS, true));
		incoming.sort(Comparator.comparingInt(Incoming::getLandTick));
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
		incoming.removeIf(i -> i.getLandTick() < tick);
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

	/** Attacks still in flight, earliest landing first. */
	List<Incoming> getIncoming()
	{
		return incoming;
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
