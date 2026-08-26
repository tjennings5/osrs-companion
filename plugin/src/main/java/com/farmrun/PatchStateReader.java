package com.osrsmcp.farmrun;

/**
 * Decodes herb patch farming varbit values into a simple {@link PatchState}.
 *
 * <p>The encoding used by the game's PatchImplementation.HERB:
 * <ul>
 *   <li>0–3: weeds (effectively empty — no herb planted)</li>
 *   <li>Every herb then occupies 7 slots: 4 growing stages, then 3 harvestable stages</li>
 *   <li>Growing block for herb index i: [4 + i*7, 4 + i*7 + 3]</li>
 *   <li>Harvestable block for herb index i: [4 + i*7 + 4, 4 + i*7 + 6]</li>
 * </ul>
 * Values outside any valid range are returned as UNKNOWN.
 */
class PatchStateReader
{
	// How many growing stages each herb has (4), and how many harvestable stages (3).
	private static final int GROW_STAGES = 4;
	private static final int HARVEST_STAGES = 3;
	private static final int SLOTS_PER_HERB = GROW_STAGES + HARVEST_STAGES; // 7

	// First value above the weed range.
	private static final int CROP_START = 4;

	// Number of herbs encoded in the varbit space.
	// Guam(0), Marrentill(1), Tarromin(2), Harralander(3), Ranarr(4),
	// Toadflax(5), Irit(6), Avantoe(7), Kwuarm(8), Snapdragon(9),
	// Cadantine(10), Lantadyme(11), Dwarf Weed(12), Torstol(13)
	private static final int HERB_COUNT = 14;

	private PatchStateReader()
	{
	}

	static PatchState decode(int varbitValue)
	{
		if (varbitValue < 0)
		{
			return PatchState.UNKNOWN;
		}
		if (varbitValue <= 3)
		{
			return PatchState.EMPTY;
		}

		int offset = varbitValue - CROP_START;
		if (offset >= HERB_COUNT * SLOTS_PER_HERB)
		{
			return PatchState.UNKNOWN;
		}

		int stageWithinHerb = offset % SLOTS_PER_HERB;
		return stageWithinHerb < GROW_STAGES ? PatchState.GROWING : PatchState.HARVESTABLE;
	}

	/**
	 * Decode state specifically for the configured herb. Returns GROWING or HARVESTABLE only when
	 * the varbit corresponds to the expected herb type; EMPTY for weeds; UNKNOWN for anything else
	 * (including a different herb, which shouldn't occur at a well-run patch but is worth handling).
	 */
	static PatchState decodeFor(int varbitValue, HerbSeed seed)
	{
		if (varbitValue < 0)
		{
			return PatchState.UNKNOWN;
		}
		if (varbitValue <= 3)
		{
			return PatchState.EMPTY;
		}

		int herbIndex = seed.getHerbVarbitIndex();
		int herbStart = CROP_START + herbIndex * SLOTS_PER_HERB;
		int herbEnd = herbStart + SLOTS_PER_HERB - 1;

		if (varbitValue < herbStart || varbitValue > herbEnd)
		{
			// Something else is planted — treat as GROWING (conservatively)
			return PatchState.GROWING;
		}

		int stageWithinHerb = varbitValue - herbStart;
		return stageWithinHerb < GROW_STAGES ? PatchState.GROWING : PatchState.HARVESTABLE;
	}

	/**
	 * Simplified tree patch state decoder. Tree varbit stage counts differ per tree type
	 * and require exact RuneLite FarmingWorld data to decode precisely; this uses a
	 * conservative approximation until the exact stage boundaries are verified in-game.
	 */
	static PatchState decodeTree(int varbitValue)
	{
		if (varbitValue <= 0)
		{
			return PatchState.EMPTY;
		}
		return PatchState.GROWING;
	}

	/**
	 * True when the varbit value represents a herb that was just planted (first grow stage),
	 * used for auto-advancing herb runs after planting.
	 */
	static boolean isJustPlanted(int previousValue, int newValue)
	{
		if (newValue < CROP_START)
		{
			return false;
		}
		// Moving from weeds (0-3) to a crop (>=4) means we just planted something.
		return previousValue <= 3;
	}

	/**
	 * True when a tree was just planted. Tree patches use a different encoding than herbs:
	 * 0 = empty/stump, 1+ = growth stages. Unlike herbs, values 1-3 are valid planted states.
	 */
	static boolean isJustPlantedTree(int previousValue, int newValue)
	{
		return previousValue == 0 && newValue > 0;
	}
}
