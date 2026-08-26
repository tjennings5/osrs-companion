package com.farmrun;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Teleport methods relevant to herb runs, in priority order per patch.
 *
 * <p>{@link #isAvailable} checks inventory and bank for item-based teleports, or verifies
 * rune/spellbook availability for spell-based teleports. Standard spellbook spells skip
 * the varbit check (assumed).
 */
public enum Teleport
{
	// --- Farming Guild ---
	// Farming cape requires 99 Farming; guard so players below 99 can't have it selected
	FARMING_CAPE("Farming cape", 99, new int[]{ItemID.SKILLCAPE_FARMING, ItemID.SKILLCAPE_FARMING_TRIMMED}),
	SKILLS_NECKLACE("Skills necklace", new int[]{
		ItemID.JEWL_NECKLACE_OF_SKILLS, ItemID.JEWL_NECKLACE_OF_SKILLS_1,
		ItemID.JEWL_NECKLACE_OF_SKILLS_2, ItemID.JEWL_NECKLACE_OF_SKILLS_3,
		ItemID.JEWL_NECKLACE_OF_SKILLS_4, ItemID.JEWL_NECKLACE_OF_SKILLS_5,
		ItemID.JEWL_NECKLACE_OF_SKILLS_6}),

	// --- Trollheim ---
	STONY_BASALT("Stony basalt", new int[]{ItemID.STRONGHOLD_TELEPORT_BASALT}),
	TROLLHEIM_TABLET("Trollheim tablet", new int[]{ItemID._61_TROLLHEIM_TELEPORT}),
	TROLLHEIM_SPELL("Trollheim teleport", 0, new int[]{ItemID.FIRERUNE, ItemID.LAWRUNE}, new int[]{2, 2}),

	// --- Ardougne ---
	ARDOUGNE_CAPE("Ardougne cape", new int[]{ItemID.ARDY_CAPE_EASY, ItemID.ARDY_CAPE_MEDIUM, ItemID.ARDY_CAPE_HARD, ItemID.ARDY_CAPE_ELITE}),
	ARDOUGNE_TABLET("Ardougne tablet", new int[]{ItemID._51_ARDOUGNE_TELEPORT, ItemID.POH_TABLET_ARDOUGNETELEPORT}),
	ARDOUGNE_SPELL("Ardougne teleport", 0, new int[]{ItemID.WATERRUNE, ItemID.LAWRUNE}, new int[]{2, 2}),

	// --- Catherby ---
	LUNAR_CATHERBY("Lunar Catherby teleport", 2, new int[]{ItemID.WATERRUNE, ItemID.AIRRUNE, ItemID.ASTRALRUNE}, new int[]{10, 10, 1}),
	CAMELOT_TABLET("Camelot tablet", new int[]{ItemID._45_CAMELOT_TELEPORT, ItemID.POH_TABLET_CAMELOTTELEPORT}),
	CAMELOT_SPELL("Camelot teleport", 0, new int[]{ItemID.AIRRUNE, ItemID.LAWRUNE}, new int[]{5, 1}),

	// --- Morytania (Port Phasmatys area) ---
	ECTOPHIAL("Ectophial", new int[]{ItemID.ECTOPHIAL}),
	KHARYLL_TABLET("Kharyll tablet", new int[]{ItemID._66_KHARYLLYL_TELEPORT}),
	KHARYLL_SPELL("Kharyll teleport", 1, new int[]{ItemID.BLOODRUNE, ItemID.LAWRUNE}, new int[]{1, 2}),
	MORYTANIA_LEGS("Morytania legs 2+", new int[]{ItemID.MORYTANIA_LEGS_MEDIUM, ItemID.MORYTANIA_LEGS_HARD, ItemID.MORYTANIA_LEGS_ELITE}),

	// --- Falador ---
	EXPLORERS_RING("Explorer's ring 2+", new int[]{ItemID.LUMBRIDGE_RING_MEDIUM, ItemID.LUMBRIDGE_RING_HARD, ItemID.LUMBRIDGE_RING_ELITE}),
	FALADOR_TABLET("Falador tablet", new int[]{ItemID._37_FALADOR_TELEPORT, ItemID.POH_TABLET_FALADORTELEPORT}),
	FALADOR_SPELL("Falador teleport", 0, new int[]{ItemID.WATERRUNE, ItemID.AIRRUNE, ItemID.LAWRUNE}, new int[]{1, 3, 1}),

	// --- Hosidius ---
	XERIC_TALISMAN("Xeric's Talisman", new int[]{ItemID.XERIC_TALISMAN}),

	// --- Weiss ---
	ICY_BASALT("Icy basalt", new int[]{ItemID.WEISS_TELEPORT_BASALT}),

	// --- Harmony Island ---
	HARMONY_SCROLL("Harmony Island teleport", new int[]{ItemID.TELETAB_HARMONY}),

	// --- Ortus Farm (Varlamore) ---
	// Quetzal whistle fast-travels to the Hunter Guild area, adjacent to Ortus Farm
	QUETZAL_WHISTLE("Quetzal whistle", new int[]{
		ItemID.HG_QUETZALWHISTLE_BASIC, ItemID.HG_QUETZALWHISTLE_ENHANCED,
		ItemID.HG_QUETZALWHISTLE_PERFECTED, ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE}),
	// Fairy ring AJP drops the player near Ortus Farm; requires dramen staff unless Fairytale II is complete
	FAIRY_RING_AJP("Fairy ring (AJP)", new int[]{ItemID.DRAMEN_STAFF}),
	FAIRY_RING_CLR("Fairy ring (CLR)", new int[]{ItemID.DRAMEN_STAFF}),

	// --- Lumbridge (tree patch) ---
	LUMBRIDGE_TABLET("Lumbridge tablet", new int[]{ItemID._31_LUMBRIDGE_TELEPORT, ItemID.POH_TABLET_LUMBRIDGETELEPORT}),
	LUMBRIDGE_SPELL("Lumbridge teleport", 0, new int[]{ItemID.AIRRUNE, ItemID.EARTHRUNE, ItemID.LAWRUNE}, new int[]{3, 1, 1}),

	// --- Varrock (tree patch) ---
	VARROCK_TABLET("Varrock tablet", new int[]{ItemID._25_VARROCK_TELEPORT, ItemID.POH_TABLET_VARROCKTELEPORT}),
	VARROCK_SPELL("Varrock teleport", 0, new int[]{ItemID.AIRRUNE, ItemID.FIRERUNE, ItemID.LAWRUNE}, new int[]{3, 1, 1}),

	// --- Falador tree patch ---
	RING_OF_WEALTH("Ring of Wealth", new int[]{
		ItemID.RING_OF_WEALTH_I, ItemID.RING_OF_WEALTH, ItemID.RING_OF_WEALTH_5,
		ItemID.RING_OF_WEALTH_4, ItemID.RING_OF_WEALTH_3, ItemID.RING_OF_WEALTH_2,
		ItemID.RING_OF_WEALTH_1}),

	// --- Taverley tree patch (Ring of Dueling → Castle Wars → Hot Air Balloon) ---
	RING_OF_DUELING("Ring of Dueling → Balloon", new int[]{
		ItemID.RING_OF_DUELING_8, ItemID.RING_OF_DUELING_7, ItemID.RING_OF_DUELING_6,
		ItemID.RING_OF_DUELING_5, ItemID.RING_OF_DUELING_4, ItemID.RING_OF_DUELING_3,
		ItemID.RING_OF_DUELING_2, ItemID.RING_OF_DUELING_1}),

	// --- Taverley (Games Necklace → Burthorpe) ---
	GAMES_NECKLACE("Games necklace", new int[]{
		ItemID.NECKLACE_OF_MINIGAMES_8, ItemID.NECKLACE_OF_MINIGAMES_7,
		ItemID.NECKLACE_OF_MINIGAMES_6, ItemID.NECKLACE_OF_MINIGAMES_5,
		ItemID.NECKLACE_OF_MINIGAMES_4, ItemID.NECKLACE_OF_MINIGAMES_3,
		ItemID.NECKLACE_OF_MINIGAMES_2, ItemID.NECKLACE_OF_MINIGAMES_1}),

	// --- Gnome Stronghold (Slayer Ring → Stronghold Slayer Cave) ---
	SLAYER_RING("Slayer ring", new int[]{
		ItemID.SLAYER_RING_ETERNAL, ItemID.SLAYER_RING_8, ItemID.SLAYER_RING_7,
		ItemID.SLAYER_RING_6, ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_4,
		ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_1}),

	// --- Ape Atoll (tree patch) ---
	APE_ATOLL_TELEPORT("Ape Atoll teleport", 2 /* SPELLBOOK_LUNAR */,
		new int[]{ItemID.FIRERUNE, ItemID.WATERRUNE, ItemID.ASTRALRUNE}, new int[]{2, 2, 1}),

	// --- POH portal / nexus routes ---
	// House teleport spell: 1 law + 1 earth + 1 air, standard spellbook (runes go in runepouch).
	// 1 dust rune satisfies both earth and air. chooseTeleport additionally verifies config/varbit
	// for the specific POH feature.
	POH_TROLLHEIM("House → Trollheim portal", 0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_ARDOUGNE("House → Ardougne portal",   0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_CAMELOT("House → Camelot portal",     0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_KHARYLL("House → Kharyll portal",     0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_FALADOR("House → Falador portal",     0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_XERIC("House → Xeric portal",         0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_JEWELRY_BOX("House → jewelry box",    0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_VARROCK("House → Varrock portal",    0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true),
	POH_LUMBRIDGE("House → Lumbridge portal",0, new int[]{ItemID.LAWRUNE, ItemID.EARTHRUNE, ItemID.AIRRUNE}, new int[]{1, 1, 1}, true);

	// Spellbook constants (VarbitID.SPELLBOOK values)
	static final int SPELLBOOK_STANDARD = 0;
	static final int SPELLBOOK_ANCIENT = 1;
	static final int SPELLBOOK_LUNAR = 2;
	private static final int NOT_A_SPELL = -1;

	/**
	 * Maps RUNE_POUCH_TYPE_* varbit value → ItemID.
	 * Index 0 = empty. Values match the game's internal rune enum ordering.
	 */
	static final int[] RUNE_POUCH_TYPE_TO_ITEM_ID = {
		-1,                 // 0 empty
		ItemID.AIRRUNE,     // 1
		ItemID.WATERRUNE,   // 2
		ItemID.EARTHRUNE,   // 3
		ItemID.FIRERUNE,    // 4
		ItemID.MINDRUNE,    // 5
		ItemID.CHAOSRUNE,   // 6
		ItemID.DEATHRUNE,   // 7
		ItemID.BLOODRUNE,   // 8
		ItemID.SOULRUNE,    // 9
		ItemID.NATURERUNE,  // 10
		ItemID.LAWRUNE,     // 11
		ItemID.COSMICRUNE,  // 12
		ItemID.ASTRALRUNE,  // 13
		ItemID.WRATHRUNE,   // 14
		ItemID.MISTRUNE,    // 15
		ItemID.DUSTRUNE,    // 16
		ItemID.MUDRUNE,     // 17
		ItemID.SMOKERUNE,   // 18
		ItemID.STEAMRUNE,   // 19
		ItemID.LAVARUNE,    // 20
		ItemID.AETHERRUNE,  // 21
	};

	private final String displayName;
	private final int spellbook;
	private final int[] itemIds;
	private final int[] runeIds;
	private final int[] runeCounts;
	private final boolean poh;
	/** Minimum Farming level required to use this teleport (0 = no requirement). */
	private final int minFarmingLevel;

	/** Item-based teleport (tablet or jewelry). */
	Teleport(String displayName, int[] itemIds)
	{
		this(displayName, 0, itemIds);
	}

	/** Item-based teleport with a minimum Farming level requirement (e.g. 99 for Farming cape). */
	Teleport(String displayName, int minFarmingLevel, int[] itemIds)
	{
		this.displayName = displayName;
		this.spellbook = NOT_A_SPELL;
		this.itemIds = itemIds;
		this.runeIds = new int[0];
		this.runeCounts = new int[0];
		this.poh = false;
		this.minFarmingLevel = minFarmingLevel;
	}


	/** Spell-based teleport — requires the specified spellbook and rune quantities. */
	Teleport(String displayName, int spellbook, int[] runeIds, int[] runeCounts)
	{
		this(displayName, spellbook, runeIds, runeCounts, false);
	}

	Teleport(String displayName, int spellbook, int[] runeIds, int[] runeCounts, boolean poh)
	{
		this.displayName = displayName;
		this.spellbook = spellbook;
		this.itemIds = new int[0];
		this.runeIds = runeIds;
		this.runeCounts = runeCounts;
		this.poh = poh;
		this.minFarmingLevel = 0;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int[] getItemIds()
	{
		return itemIds.clone();
	}

	public int[] getRuneIds()
	{
		return runeIds.clone();
	}

	public int[] getRuneCounts()
	{
		return runeCounts.clone();
	}

	public boolean isSpellBased()
	{
		return spellbook != NOT_A_SPELL;
	}

	public boolean isPohRoute()
	{
		return poh;
	}

	/**
	 * Returns true if the player can use this teleport based on items in inventory or bank
	 * (for item teleports), or available runes including pouch (for spell teleports).
	 *
	 * <p>Standard spellbook spells skip the varbit check — assumed. Non-standard spells
	 * (ancient, lunar) still verify the active spellbook.
	 */
	public boolean isAvailable(ItemContainer inventory, ItemContainer bank, Client client)
	{
		if (minFarmingLevel > 0 && client.getRealSkillLevel(Skill.FARMING) < minFarmingLevel)
		{
			return false;
		}
		if (spellbook == NOT_A_SPELL)
		{
			return hasAnyItem(inventory, itemIds) || hasAnyItem(bank, itemIds);
		}

		// Non-standard spells require the correct spellbook active
		if (spellbook != SPELLBOOK_STANDARD
			&& client.getVarbitValue(VarbitID.SPELLBOOK) != spellbook)
		{
			return false;
		}

		Map<Integer, Integer> pouch = countRunePouch(client);
		return hasRunes(inventory, bank, pouch, runeIds, runeCounts);
	}

	/** Reads the rune pouch contents and returns a map of item ID → quantity. */
	static Map<Integer, Integer> countRunePouch(Client client)
	{
		Map<Integer, Integer> result = new HashMap<>();
		int[][] slots = {
			{VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
			{VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
			{VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
		};
		for (int[] slot : slots)
		{
			int type = client.getVarbitValue(slot[0]);
			int qty = client.getVarbitValue(slot[1]);
			if (type > 0 && type < RUNE_POUCH_TYPE_TO_ITEM_ID.length && qty > 0)
			{
				result.merge(RUNE_POUCH_TYPE_TO_ITEM_ID[type], qty, Integer::sum);
			}
		}
		return result;
	}

	private static boolean hasAnyItem(ItemContainer container, int[] ids)
	{
		if (container == null)
		{
			return false;
		}
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			for (int id : ids)
			{
				if (item.getId() == id)
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasRunes(ItemContainer inventory, ItemContainer bank,
		Map<Integer, Integer> pouch, int[] runeIds, int[] counts)
	{
		for (int i = 0; i < runeIds.length; i++)
		{
			int needed = counts[i];
			int available = countItem(inventory, runeIds[i])
				+ countItem(bank, runeIds[i])
				+ pouch.getOrDefault(runeIds[i], 0);
			if (available < needed)
			{
				return false;
			}
		}
		return true;
	}

	private static int countItem(ItemContainer container, int itemId)
	{
		if (container == null)
		{
			return 0;
		}
		int total = 0;
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() == itemId)
			{
				total += item.getQuantity();
			}
		}
		return total;
	}
}
