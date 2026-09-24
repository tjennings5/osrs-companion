package com.doom;

import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;

/**
 * Game IDs for the Doom of Mokhaiotl fight, taken from RuneLite's gameval
 * constants (the game's own internal names, prefixed DOM_ / VFX_).
 *
 * The names tell us what each ID is, but not its timing - that's what the
 * recorder is for. The label maps exist so recordings read as
 * "STANDARD_PROJECTILE_MAGIC" rather than a bare 3379.
 */
final class DoomIds
{
	private DoomIds()
	{
	}

	static final int BOSS = NpcID.DOM_BOSS;
	static final int BOSS_SHIELDED = NpcID.DOM_BOSS_SHIELDED;
	static final int BOSS_BURROWED = NpcID.DOM_BOSS_BURROWED;

	/** Every NPC that belongs to the fight. Seeing any of them starts a recording. */
	static final Set<Integer> FIGHT_NPCS = Set.of(
		NpcID.DOM_BOSS,
		NpcID.DOM_BOSS_SHIELDED,
		NpcID.DOM_BOSS_BURROWED,
		NpcID.DOM_DEMONIC_ENERGY,
		NpcID.DOM_DEMONIC_ENERGY_RANGE,
		NpcID.DOM_DEMONIC_ENERGY_MAGE,
		NpcID.DOM_DEMONIC_ENERGY_MELEE,
		NpcID.DOM_SHOCKWAVE_PATH_NODE,
		NpcID.DOM_SHOCKWAVE_SHIELD,
		NpcID.DOM_DEMONIC_ENERGY_GIANT_RANGE,
		NpcID.DOM_DEMONIC_ENERGY_GIANT_MAGE);

	/** Graphics object marking where the burrowed boss will charge to. */
	static final int BURROW_EYE = SpotanimID.VFX_DOOM_BOSS_BURROWED_TELEGRAPH_SPAWN;

	static final Set<Integer> BOSS_FORMS = Set.of(BOSS, BOSS_SHIELDED, BOSS_BURROWED);

	static final Map<Integer, String> NPC_NAMES = Map.ofEntries(
		Map.entry(NpcID.DOM_BOSS, "BOSS"),
		Map.entry(NpcID.DOM_BOSS_SHIELDED, "BOSS_SHIELDED"),
		Map.entry(NpcID.DOM_BOSS_BURROWED, "BOSS_BURROWED"),
		Map.entry(NpcID.DOM_DEMONIC_ENERGY, "LARVA"),
		Map.entry(NpcID.DOM_DEMONIC_ENERGY_RANGE, "LARVA_RANGE"),
		Map.entry(NpcID.DOM_DEMONIC_ENERGY_MAGE, "LARVA_MAGE"),
		Map.entry(NpcID.DOM_DEMONIC_ENERGY_MELEE, "LARVA_MELEE"),
		Map.entry(NpcID.DOM_SHOCKWAVE_PATH_NODE, "VOLATILE_EARTH"),
		Map.entry(NpcID.DOM_SHOCKWAVE_SHIELD, "EARTHEN_SHIELD"),
		Map.entry(NpcID.DOM_DEMONIC_ENERGY_GIANT_RANGE, "GIANT_LARVA_RANGE"),
		Map.entry(NpcID.DOM_DEMONIC_ENERGY_GIANT_MAGE, "GIANT_LARVA_MAGE"));

	static final Map<Integer, String> ANIMATION_NAMES = Map.ofEntries(
		Map.entry(AnimationID.DOM_IDLE, "IDLE"),
		Map.entry(AnimationID.DOM_STANDARD_RANGE_ATTACK, "STANDARD_ATTACK"),
		Map.entry(AnimationID.DOM_ROCK_THROW_ATTACK, "ROCK_THROW"),
		Map.entry(AnimationID.DOM_BEAM_CHARGE, "BEAM_CHARGE"),
		Map.entry(AnimationID.DOM_BEAM_CHARGE_LOOP, "BEAM_CHARGE_LOOP"),
		Map.entry(AnimationID.DOM_BEAM_CANCEL, "BEAM_CANCEL"),
		Map.entry(AnimationID.DOM_BEAM_FIRE, "BEAM_FIRE"),
		Map.entry(AnimationID.DOM_AREA_CHARGE, "AREA_CHARGE"),
		Map.entry(AnimationID.DOM_AREA_CHARGE_LOOP, "AREA_CHARGE_LOOP"),
		Map.entry(AnimationID.DOOM_AREA_SLAM, "AREA_SLAM"),
		Map.entry(AnimationID.DOOM_AREA_SLAM_RETURN_IDLE, "AREA_SLAM_RETURN_IDLE"),
		Map.entry(AnimationID.DOM_MELEE_ATTACK, "MELEE_ATTACK"),
		Map.entry(AnimationID.DOM_BURROWED_MOVEMENT, "BURROWED_MOVEMENT"),
		Map.entry(AnimationID.DOM_BURROWED_EMERGE, "BURROWED_EMERGE"),
		Map.entry(AnimationID.DOM_BURROWED_EXPLOSION, "BURROWED_EXPLOSION"),
		Map.entry(AnimationID.DOM_BURROW, "BURROW"),
		Map.entry(AnimationID.DOM_BURROW_IDLE, "BURROW_IDLE"),
		Map.entry(AnimationID.DOM_DESPAWN, "DESPAWN"));

	static final Map<Integer, String> SPOTANIM_NAMES = Map.ofEntries(
		Map.entry(SpotanimID.VFX_STANDARD_PROJECTILE_MELEE, "STANDARD_PROJECTILE_MELEE"),
		Map.entry(SpotanimID.VFX_STANDARD_PROJECTILE_MAGIC, "STANDARD_PROJECTILE_MAGIC"),
		Map.entry(SpotanimID.VFX_STANDARD_PROJECTILE_RANGE, "STANDARD_PROJECTILE_RANGE"),
		Map.entry(SpotanimID.VFX_STANDARD_IMPACT_MELEE, "STANDARD_IMPACT_MELEE"),
		Map.entry(SpotanimID.VFX_STANDARD_IMPACT_MAGIC, "STANDARD_IMPACT_MAGIC"),
		Map.entry(SpotanimID.VFX_STANDARD_IMPACT_RANGE, "STANDARD_IMPACT_RANGE"),
		Map.entry(SpotanimID.VFX_ROCK_PROJECTILE_LAUNCH_RANGE, "ROCK_LAUNCH_RANGE"),
		Map.entry(SpotanimID.VFX_ROCK_PROJECTILE_LAUNCH_MAGIC, "ROCK_LAUNCH_MAGIC"),
		Map.entry(SpotanimID.VFX_ROCK_PROJECTILE_SPLIT_RANGE, "ROCK_SPLIT_RANGE"),
		Map.entry(SpotanimID.VFX_ROCK_PROJECTILE_SPLIT_MAGIC, "ROCK_SPLIT_MAGIC"),
		Map.entry(SpotanimID.VFX_ROCK_PROJECTILE_IMPACT, "ROCK_IMPACT"),
		Map.entry(SpotanimID.VFX_AREA_SLAM_01, "AREA_SLAM_01"),
		Map.entry(SpotanimID.VFX_AREA_SLAM_02, "AREA_SLAM_02"),
		Map.entry(SpotanimID.VFX_AREA_SLAM_03, "AREA_SLAM_03"),
		Map.entry(SpotanimID.VFX_AREA_SLAM_04, "AREA_SLAM_04"),
		Map.entry(SpotanimID.VFX_DOOM_AREA_SLAM, "DOOM_AREA_SLAM"),
		Map.entry(SpotanimID.VFX_BEAM_ATTACK_HEAD_01, "BEAM_HEAD"),
		Map.entry(SpotanimID.VFX_BEAM_MIDDLE_SEGMENT_01, "BEAM_MIDDLE"),
		Map.entry(SpotanimID.VFX_BEAM_END_SEGMENT_01, "BEAM_END"),
		Map.entry(SpotanimID.VFX_BEAM_CHARGE_UP_01, "BEAM_CHARGE_UP"),
		Map.entry(SpotanimID.VFX_BEAM_IMPACT_01, "BEAM_IMPACT"),
		Map.entry(SpotanimID.VFX_BEAM_CHARGE_UP_BURROW_01, "BEAM_CHARGE_UP_BURROW"),
		Map.entry(SpotanimID.VFX_DOOM_BOSS_BURROWED_MOVEMENT_TELEGRAPH, "BURROW_EYE_TELEGRAPH"),
		Map.entry(SpotanimID.VFX_DOOM_BOSS_BURROWED_TELEGRAPH_SPAWN, "BURROW_EYE_SPAWN"),
		Map.entry(SpotanimID.VFX_DOM_BURROWED_MOVEMENT, "BURROWED_MOVEMENT"),
		Map.entry(SpotanimID.VFX_DOM_BURROWED_EMERGE, "BURROWED_EMERGE"),
		Map.entry(SpotanimID.VFX_DOM_BURROWED_EXPLOSION, "BURROWED_EXPLOSION"),
		Map.entry(SpotanimID.VFX_DOM_BURROWED_EXPLOSION_AOE, "BURROWED_EXPLOSION_AOE"),
		Map.entry(SpotanimID.VFX_DOM_BURROW, "BURROW"),
		Map.entry(SpotanimID.VFX_DOM_BURROW_IDLE, "BURROW_IDLE"),
		Map.entry(SpotanimID.VFX_DOM_DESPAWN, "DESPAWN"),
		Map.entry(SpotanimID.VFX_DOOM_BOSS_BLOOD_PROJECTILE, "ACID_PROJECTILE"),
		Map.entry(SpotanimID.VFX_DOOM_MOUND, "MOUND"),
		Map.entry(SpotanimID.VFX_DOOM_MOUND_BEAM_CANCEL, "MOUND_BEAM_CANCEL"),
		Map.entry(SpotanimID.VFX_DOOM_MOUND_MELEE_ATTACK, "MOUND_MELEE_ATTACK"),
		Map.entry(SpotanimID.VFX_DOOM_MOUND_BURROWED_EMERGE, "MOUND_EMERGE"),
		Map.entry(SpotanimID.VFX_DOOM_MOUND_BURROW, "MOUND_BURROW"),
		Map.entry(SpotanimID.VFX_DOOM_MOUND_DESPAWN, "MOUND_DESPAWN"),
		Map.entry(SpotanimID.VFX_DEMONIC_GRUB_SPAWN, "LARVA_SPAWN"));

	/**
	 * Style of a Rock Throw's launched rock, or null if it isn't one. Recordings
	 * show the first follow-up projectile to land is always this style, with
	 * any further ones alternating.
	 */
	static AttackStyle rockLaunchStyle(int id)
	{
		switch (id)
		{
			case SpotanimID.VFX_ROCK_PROJECTILE_LAUNCH_MAGIC:
				return AttackStyle.MAGIC;
			case SpotanimID.VFX_ROCK_PROJECTILE_LAUNCH_RANGE:
				return AttackStyle.RANGED;
			default:
				return null;
		}
	}

	/** Rock-throw debris projectiles (3388-3403) aren't worth a name each. */
	static String spotanimName(int id)
	{
		String name = SPOTANIM_NAMES.get(id);
		if (name != null)
		{
			return name;
		}
		if (id >= SpotanimID.VFX_ROCK_PROJECTILE_PROJECTILE01 && id <= SpotanimID.VFX_ROCK_PROJECTILE_PROJECTILE_ALT08)
		{
			return "ROCK_DEBRIS";
		}
		if (id >= SpotanimID.VFX_DOOM_BOSS_BLOOD_SPLAT_N && id <= SpotanimID.VFX_DOOM_BOSS_BLOOD_SPLAT_DESPAWN_SW)
		{
			return "ACID_SPLAT";
		}
		if (id >= SpotanimID.VFX_DEMONIC_GRUB_EXPLOSION01 && id <= SpotanimID.VFX_DEMONIC_GRUB_ABSORPTION_SPLAT_DIAGONAL01)
		{
			return "LARVA_FX";
		}
		return null;
	}
}
