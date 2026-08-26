package com.osrsmcp.farmrun;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * Each herb patch with its location, access requirements, patch-state varbit, and
 * ordered list of teleport options (best first).
 *
 * <p>Varbit IDs are the {@code FARMING_TRANSMIT_*} slots populated by the server when the player
 * is in range of each patch region — they are not globally unique per patch, but the region lookup
 * in the client makes them unambiguous while the player is in that region.
 *
 * <p>WorldPoints are approximate patch-tile coordinates; they are used for the directional arrow
 * and "near patch" detection and do not need to be exact to the tile.
 *
 * <p>Run order (routeIndex) defines the recommended visiting sequence for an efficient herb run:
 * Guild → Trollheim → Ardougne → Catherby → Morytania → Falador → Hosidius → Weiss → Harmony.
 */
public enum HerbPatch
{
	FARMING_GUILD(
		"Farming Guild",
		new WorldPoint(1238, 3727, 0),
		null,               // no quest requirement
		65,                 // 65 Farming to enter
		VarbitID.FARMING_TRANSMIT_E, // herb is slot E in the guild region
		0,
		Teleport.POH_JEWELRY_BOX, Teleport.FARMING_CAPE, Teleport.SKILLS_NECKLACE
	),
	TROLLHEIM(
		"Trollheim",
		new WorldPoint(2826, 3693, 0),
		Quest.MY_ARMS_BIG_ADVENTURE,
		61,
		VarbitID.FARMING_TRANSMIT_A, // only patch in Troll Stronghold region
		1,
		Teleport.POH_TROLLHEIM, Teleport.STONY_BASALT, Teleport.TROLLHEIM_SPELL, Teleport.TROLLHEIM_TABLET
	),
	ARDOUGNE(
		"Ardougne",
		new WorldPoint(2670, 3374, 0),
		Quest.PLAGUE_CITY,
		0,
		VarbitID.FARMING_TRANSMIT_D,
		2,
		Teleport.ARDOUGNE_CAPE, Teleport.POH_ARDOUGNE, Teleport.ARDOUGNE_SPELL, Teleport.ARDOUGNE_TABLET
	),
	CATHERBY(
		"Catherby",
		new WorldPoint(2813, 3463, 0),
		null,
		0,
		VarbitID.FARMING_TRANSMIT_D,
		3,
		Teleport.POH_CAMELOT, Teleport.LUNAR_CATHERBY, Teleport.CAMELOT_SPELL, Teleport.CAMELOT_TABLET
	),
	MORYTANIA(
		"Morytania",
		new WorldPoint(3605, 3529, 0),
		Quest.PRIEST_IN_PERIL,
		0,
		VarbitID.FARMING_TRANSMIT_D,
		4,
		Teleport.ECTOPHIAL, Teleport.POH_KHARYLL, Teleport.KHARYLL_SPELL, Teleport.KHARYLL_TABLET, Teleport.MORYTANIA_LEGS
	),
	FALADOR(
		"Falador",
		new WorldPoint(3058, 3311, 0),
		null,
		0,
		VarbitID.FARMING_TRANSMIT_D,
		5,
		Teleport.EXPLORERS_RING, Teleport.POH_FALADOR, Teleport.FALADOR_SPELL, Teleport.FALADOR_TABLET
	),
	HOSIDIUS(
		"Hosidius",
		new WorldPoint(1736, 3551, 0),
		null,   // access is a favour check, handled separately
		0,
		VarbitID.FARMING_TRANSMIT_D,
		6,
		Teleport.XERIC_TALISMAN, Teleport.POH_XERIC
	),
	WEISS(
		"Weiss",
		new WorldPoint(2849, 3934, 0),
		Quest.MAKING_FRIENDS_WITH_MY_ARM,
		0,
		VarbitID.FARMING_TRANSMIT_A,
		7,
		Teleport.ICY_BASALT
	),
	HARMONY(
		"Harmony Island",
		new WorldPoint(3789, 2837, 0),
		Quest.THE_GREAT_BRAIN_ROBBERY,
		0,
		VarbitID.FARMING_TRANSMIT_B,
		VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE,
		8,
		Teleport.HARMONY_SCROLL
	),
	// Varlamore herb patch at Ortus Farm. FARMING_TRANSMIT_F is a best guess — verify in-game.
	ORTUS_FARM(
		"Ortus Farm",
		new WorldPoint(1574, 3099, 0),
		Quest.CHILDREN_OF_THE_SUN,
		0,
		VarbitID.FARMING_TRANSMIT_F,
		9,
		Teleport.QUETZAL_WHISTLE, Teleport.FAIRY_RING_AJP
	);

	private final String displayName;
	private final WorldPoint location;
	private final Quest accessQuest;
	private final int accessFarmingLevel;
	/** Farming transmit varbit ID that holds this patch's state when in range. */
	private final int stateVarbit;
	/** Varbit that must equal 1 to access this patch (0 = no diary requirement). */
	private final int accessDiaryVarbit;
	/** Position in the recommended visiting order. */
	private final int routeIndex;
	private final List<Teleport> teleportPriority;

	HerbPatch(String displayName, WorldPoint location, Quest accessQuest,
		int accessFarmingLevel, int stateVarbit, int routeIndex, Teleport... teleports)
	{
		this(displayName, location, accessQuest, accessFarmingLevel, stateVarbit, 0, routeIndex, teleports);
	}

	HerbPatch(String displayName, WorldPoint location, Quest accessQuest,
		int accessFarmingLevel, int stateVarbit, int accessDiaryVarbit, int routeIndex, Teleport... teleports)
	{
		this.displayName = displayName;
		this.location = location;
		this.accessQuest = accessQuest;
		this.accessFarmingLevel = accessFarmingLevel;
		this.stateVarbit = stateVarbit;
		this.accessDiaryVarbit = accessDiaryVarbit;
		this.routeIndex = routeIndex;
		this.teleportPriority = Arrays.asList(teleports);
	}

	public int getAccessDiaryVarbit()
	{
		return accessDiaryVarbit;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public WorldPoint getLocation()
	{
		return location;
	}

	public Quest getAccessQuest()
	{
		return accessQuest;
	}

	public int getAccessFarmingLevel()
	{
		return accessFarmingLevel;
	}

	public int getStateVarbit()
	{
		return stateVarbit;
	}

	public int getRouteIndex()
	{
		return routeIndex;
	}

	public List<Teleport> getTeleportPriority()
	{
		return teleportPriority;
	}

	/**
	 * Hosidius requires 65% favour (no quest) — this flag separates it from quest-gated patches
	 * so the access check can call the correct client method.
	 */
	public boolean requiresHosidiusFavour()
	{
		return this == HOSIDIUS;
	}

	/** Distance in tiles from the given point to this patch's location. */
	public int distanceTo(WorldPoint point)
	{
		return location.distanceTo2D(point);
	}

	public static HerbPatch[] inRouteOrder()
	{
		HerbPatch[] patches = values().clone();
		Arrays.sort(patches, (a, b) -> Integer.compare(a.routeIndex, b.routeIndex));
		return patches;
	}
}
