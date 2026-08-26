package com.osrsmcp.farmrun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// Fancy Jewelry Box (tier 2) includes Skills Necklace → Farming Guild via POH
// Ornate Jewelry Box (tier 3) also qualifies

class RunRoute
{
	private static final int HOSIDIUS_FAVOUR_THRESHOLD = 650;

	/**
	 * All graceful piece variants grouped by slot (hood, cape, top, legs, gloves, boots).
	 * Every color recolor for each slot is listed so the check works regardless of which
	 * Zeah house or cosmetic recolor the player chose.
	 */
	static final int[][] GRACEFUL_SLOT_IDS = {
		// Hood
		{ItemID.GRACEFUL_HOOD, ItemID.ZEAH_GRACEFUL_HOOD_HOSIDIUS, ItemID.ZEAH_GRACEFUL_HOOD_ARCEUUS,
			ItemID.ZEAH_GRACEFUL_HOOD_KOUREND, ItemID.ZEAH_GRACEFUL_HOOD_LOVAKENGJ,
			ItemID.ZEAH_GRACEFUL_HOOD_PISCARILIUS, ItemID.ZEAH_GRACEFUL_HOOD_SHAYZIEN,
			ItemID.GRACEFUL_HOOD_ADVENTURER, ItemID.GRACEFUL_HOOD_HALLOWED,
			ItemID.GRACEFUL_HOOD_SKILLCAPECOLOUR, ItemID.GRACEFUL_HOOD_TRAILBLAZER,
			ItemID.GRACEFUL_HOOD_WYRM},
		// Cape
		{ItemID.GRACEFUL_CAPE, ItemID.ZEAH_GRACEFUL_CAPE_HOSIDIUS, ItemID.ZEAH_GRACEFUL_CAPE_ARCEUUS,
			ItemID.ZEAH_GRACEFUL_CAPE_KOUREND, ItemID.ZEAH_GRACEFUL_CAPE_LOVAKENGJ,
			ItemID.ZEAH_GRACEFUL_CAPE_PISCARILIUS, ItemID.ZEAH_GRACEFUL_CAPE_SHAYZIEN,
			ItemID.GRACEFUL_CAPE_ADVENTURER, ItemID.GRACEFUL_CAPE_HALLOWED,
			ItemID.GRACEFUL_CAPE_SKILLCAPECOLOUR, ItemID.GRACEFUL_CAPE_TRAILBLAZER,
			ItemID.GRACEFUL_CAPE_WYRM},
		// Top
		{ItemID.GRACEFUL_TOP, ItemID.ZEAH_GRACEFUL_TOP_HOSIDIUS, ItemID.ZEAH_GRACEFUL_TOP_ARCEUUS,
			ItemID.ZEAH_GRACEFUL_TOP_KOUREND, ItemID.ZEAH_GRACEFUL_TOP_LOVAKENGJ,
			ItemID.ZEAH_GRACEFUL_TOP_PISCARILIUS, ItemID.ZEAH_GRACEFUL_TOP_SHAYZIEN,
			ItemID.GRACEFUL_TOP_ADVENTURER, ItemID.GRACEFUL_TOP_HALLOWED,
			ItemID.GRACEFUL_TOP_SKILLCAPECOLOUR, ItemID.GRACEFUL_TOP_TRAILBLAZER,
			ItemID.GRACEFUL_TOP_WYRM},
		// Legs
		{ItemID.GRACEFUL_LEGS, ItemID.ZEAH_GRACEFUL_LEGS_HOSIDIUS, ItemID.ZEAH_GRACEFUL_LEGS_ARCEUUS,
			ItemID.ZEAH_GRACEFUL_LEGS_KOUREND, ItemID.ZEAH_GRACEFUL_LEGS_LOVAKENGJ,
			ItemID.ZEAH_GRACEFUL_LEGS_PISCARILIUS, ItemID.ZEAH_GRACEFUL_LEGS_SHAYZIEN,
			ItemID.GRACEFUL_LEGS_ADVENTURER, ItemID.GRACEFUL_LEGS_HALLOWED,
			ItemID.GRACEFUL_LEGS_SKILLCAPECOLOUR, ItemID.GRACEFUL_LEGS_TRAILBLAZER,
			ItemID.GRACEFUL_LEGS_WYRM},
		// Gloves
		{ItemID.GRACEFUL_GLOVES, ItemID.ZEAH_GRACEFUL_GLOVES_HOSIDIUS, ItemID.ZEAH_GRACEFUL_GLOVES_ARCEUUS,
			ItemID.ZEAH_GRACEFUL_GLOVES_KOUREND, ItemID.ZEAH_GRACEFUL_GLOVES_LOVAKENGJ,
			ItemID.ZEAH_GRACEFUL_GLOVES_PISCARILIUS, ItemID.ZEAH_GRACEFUL_GLOVES_SHAYZIEN,
			ItemID.GRACEFUL_GLOVES_ADVENTURER, ItemID.GRACEFUL_GLOVES_HALLOWED,
			ItemID.GRACEFUL_GLOVES_SKILLCAPECOLOUR, ItemID.GRACEFUL_GLOVES_TRAILBLAZER,
			ItemID.GRACEFUL_GLOVES_WYRM},
		// Boots
		{ItemID.GRACEFUL_BOOTS, ItemID.ZEAH_GRACEFUL_BOOTS_HOSIDIUS, ItemID.ZEAH_GRACEFUL_BOOTS_ARCEUUS,
			ItemID.ZEAH_GRACEFUL_BOOTS_KOUREND, ItemID.ZEAH_GRACEFUL_BOOTS_LOVAKENGJ,
			ItemID.ZEAH_GRACEFUL_BOOTS_PISCARILIUS, ItemID.ZEAH_GRACEFUL_BOOTS_SHAYZIEN,
			ItemID.GRACEFUL_BOOTS_ADVENTURER, ItemID.GRACEFUL_BOOTS_HALLOWED,
			ItemID.GRACEFUL_BOOTS_SKILLCAPECOLOUR, ItemID.GRACEFUL_BOOTS_TRAILBLAZER,
			ItemID.GRACEFUL_BOOTS_WYRM},
	};

	static final String[] GRACEFUL_SLOT_NAMES = {
		"Graceful hood", "Graceful cape", "Graceful top",
		"Graceful legs", "Graceful gloves", "Graceful boots",
	};

	/** Human-readable names for rune item IDs used in checklist display. */
	private static final Map<Integer, String> RUNE_NAMES;
	static
	{
		Map<Integer, String> m = new LinkedHashMap<>();
		m.put(ItemID.AIRRUNE,    "Air rune");
		m.put(ItemID.WATERRUNE,  "Water rune");
		m.put(ItemID.EARTHRUNE,  "Earth rune");
		m.put(ItemID.FIRERUNE,   "Fire rune");
		m.put(ItemID.MINDRUNE,   "Mind rune");
		m.put(ItemID.CHAOSRUNE,  "Chaos rune");
		m.put(ItemID.DEATHRUNE,  "Death rune");
		m.put(ItemID.BLOODRUNE,  "Blood rune");
		m.put(ItemID.SOULRUNE,   "Soul rune");
		m.put(ItemID.NATURERUNE, "Nature rune");
		m.put(ItemID.LAWRUNE,    "Law rune");
		m.put(ItemID.COSMICRUNE, "Cosmic rune");
		m.put(ItemID.ASTRALRUNE, "Astral rune");
		m.put(ItemID.WRATHRUNE,  "Wrath rune");
		RUNE_NAMES = Collections.unmodifiableMap(m);
	}

	private final List<PatchStop> stops;
	private final List<BankItem> bankChecklist;

	RunRoute(Client client, ItemContainer inventory, ItemContainer bank, FarmRunConfig config)
	{
		stops = buildStops(client, inventory, bank, config);
		Map<Integer, Integer> runePouch = Teleport.countRunePouch(client);
		bankChecklist = buildChecklist(config, stops, inventory, bank, runePouch);
	}

	List<PatchStop> getStops()
	{
		return stops;
	}

	List<BankItem> getBankChecklist()
	{
		return bankChecklist;
	}

	int size()
	{
		return stops.size();
	}

	private static List<PatchStop> buildStops(Client client, ItemContainer inventory,
		ItemContainer bank, FarmRunConfig config)
	{
		List<PatchStop> result = new ArrayList<>();
		for (HerbPatch patch : HerbPatch.inRouteOrder())
		{
			if (!isAccessible(patch, client))
			{
				continue;
			}
			Teleport bestTeleport = chooseTeleport(patch, inventory, bank, client, config);
			result.add(new PatchStop(patch, bestTeleport));
		}
		return Collections.unmodifiableList(result);
	}

	private static boolean isAccessible(HerbPatch patch, Client client)
	{
		if (patch.getAccessFarmingLevel() > 0
			&& client.getRealSkillLevel(Skill.FARMING) < patch.getAccessFarmingLevel())
		{
			return false;
		}
		if (patch.getAccessQuest() != null
			&& patch.getAccessQuest().getState(client) != QuestState.FINISHED)
		{
			return false;
		}
		if (patch.getAccessDiaryVarbit() != 0
			&& client.getVarbitValue(patch.getAccessDiaryVarbit()) != 1)
		{
			return false;
		}
		if (patch.requiresHosidiusFavour()
			&& client.getVarbitValue(VarbitID.ZEAH_HOSIDIUS) < HOSIDIUS_FAVOUR_THRESHOLD)
		{
			return false;
		}
		return true;
	}

	private static Teleport chooseTeleport(HerbPatch patch, ItemContainer inventory,
		ItemContainer bank, Client client, FarmRunConfig config)
	{
		Teleport bestGuess = null;
		for (Teleport teleport : patch.getTeleportPriority())
		{
			if (teleport.isPohRoute())
			{
				if (isPohTeleportAvailable(teleport, inventory, bank, client, config))
				{
					return teleport;
				}
				// Accept a POH route as bestGuess if the POH feature is confirmed even when
				// runes are missing — so they appear in the bank checklist to be restocked.
				if (bestGuess == null && isPohFeatureConfirmed(teleport, client, config))
				{
					bestGuess = teleport;
				}
			}
			else if (teleport.isAvailable(inventory, bank, client))
			{
				return teleport;
			}
			else if (bank == null && bestGuess == null)
			{
				// Bank is closed — can't verify item teleports. Record first candidate
				// as a best-guess. onWidgetLoaded re-evaluates precisely when bank opens.
				bestGuess = teleport;
			}
		}
		return bestGuess;
	}

	/**
	 * Returns true if the player has enough runes for the house teleport spell (checked via
	 * {@link Teleport#isAvailable}) AND the relevant POH feature is confirmed (varbit or config).
	 */
	static boolean isPohTeleportAvailable(Teleport teleport, ItemContainer inventory,
		ItemContainer bank, Client client, FarmRunConfig config)
	{
		// Rune check: 1 law + 1 air for Teleport to House (same as isAvailable for spell)
		if (!teleport.isAvailable(inventory, bank, client))
		{
			return false;
		}
		return isPohFeatureConfirmed(teleport, client, config);
	}

	/**
	 * Returns true if the POH feature for this teleport is confirmed via varbit or config,
	 * WITHOUT checking rune availability. Used for the null-bank best-guess path.
	 */
	static boolean isPohFeatureConfirmed(Teleport teleport, Client client, FarmRunConfig config)
	{
		// Jewelry box: auto-detected via varbit — Fancy (2) or Ornate (3) has Skills necklace
		if (teleport == Teleport.POH_JEWELRY_BOX)
		{
			return client.getVarbitValue(VarbitID.POH_JEWELLERYBOX_MULTI) >= 2;
		}

		// All other POH portals: user must confirm via config checkbox
		if (teleport == Teleport.POH_TROLLHEIM) return config.pohTrollheim();
		if (teleport == Teleport.POH_ARDOUGNE)  return config.pohArdougne();
		if (teleport == Teleport.POH_CAMELOT)   return config.pohCamelot();
		if (teleport == Teleport.POH_KHARYLL)   return config.pohKharyll();
		if (teleport == Teleport.POH_FALADOR)   return config.pohFalador();
		if (teleport == Teleport.POH_XERIC)     return config.pohXeric();
		if (teleport == Teleport.POH_VARROCK)   return config.pohVarrock();
		if (teleport == Teleport.POH_LUMBRIDGE) return config.pohLumbridge();

		return false;
	}

	private static List<BankItem> buildChecklist(FarmRunConfig config, List<PatchStop> stops,
		ItemContainer inventory, ItemContainer bank, Map<Integer, Integer> runePouch)
	{
		if (stops.isEmpty())
		{
			return Collections.emptyList();
		}

		int patchCount = stops.size();
		List<BankItem> items = new ArrayList<>();

		// --- Tools ---
		// Show if not in inventory. When bank is open and neither bank nor inventory has them,
		// they're assumed stored with the tool leprechaun. When bank is null (closed), we
		// show them as a safe default since they're always needed for planting.
		if (!hasItemId(inventory, ItemID.DIBBER)
			&& (bank == null || hasItemId(bank, ItemID.DIBBER)))
		{
			items.add(new BankItem("Seed dibber", 1));
		}
		if (!hasItemId(inventory, ItemID.SPADE)
			&& (bank == null || hasItemId(bank, ItemID.SPADE)))
		{
			items.add(new BankItem("Spade", 1));
		}

		// --- Seeds ---
		if (!hasItemId(inventory, config.herbSeed().getSeedItemId()))
		{
			items.add(new BankItem(config.herbSeed().getDisplayName() + " seed", patchCount));
		}

		// --- Compost ---
		// Bottomless bucket is always preferred. When bank is null (closed), assume the player
		// has one — bank-open refresh will correct to regular compost if they don't.
		boolean hasBottomless = bank == null
			|| hasItemId(inventory, ItemID.BOTTOMLESS_COMPOST_BUCKET)
			|| hasItemId(bank, ItemID.BOTTOMLESS_COMPOST_BUCKET)
			|| hasItemId(inventory, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)
			|| hasItemId(bank, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
		if (hasBottomless)
		{
			items.add(new BankItem("Bottomless compost bucket", 1));
		}
		else if (config.compostType() == CompostType.ULTRACOMPOST)
		{
			items.add(new BankItem("Ultracompost", patchCount));
		}
		else if (config.compostType() == CompostType.SUPERCOMPOST)
		{
			items.add(new BankItem("Supercompost", patchCount));
		}

		// --- Magic secateurs ---
		if (!hasItemId(inventory, ItemID.FAIRY_ENCHANTED_SECATEURS))
		{
			items.add(new BankItem("Magic secateurs", 1));
		}

		// --- Graceful outfit (any color variant; slot-by-slot check) ---
		// When bank is null (closed), show pieces not worn/in inventory as best guess.
		// onWidgetLoaded refreshes precisely once the bank is open.
		for (int slot = 0; slot < GRACEFUL_SLOT_IDS.length; slot++)
		{
			boolean inInventory = hasAnyItemId(inventory, GRACEFUL_SLOT_IDS[slot]);
			boolean inBank = hasAnyItemId(bank, GRACEFUL_SLOT_IDS[slot]);
			if (!inInventory && (bank == null || inBank))
			{
				items.add(new BankItem(GRACEFUL_SLOT_NAMES[slot], 1));
			}
		}

		// --- Teleport items (item-based, deduplicated) ---
		Set<String> addedTeleports = new LinkedHashSet<>();
		for (PatchStop stop : stops)
		{
			Teleport tp = stop.getTeleport();
			if (tp == null || tp.isSpellBased())
			{
				continue;
			}
			if (addedTeleports.add(tp.getDisplayName()))
			{
				items.add(new BankItem(tp.getDisplayName(), 1));
			}
		}

		// --- Rune requirements for standard-spellbook spell teleports ---
		// Aggregate total runes needed across all selected spell stops, then subtract
		// what's already in inventory + bank + rune pouch. Show deficit only.
		Map<Integer, Integer> runesNeeded = new LinkedHashMap<>();
		for (PatchStop stop : stops)
		{
			Teleport tp = stop.getTeleport();
			if (tp == null || !tp.isSpellBased())
			{
				continue;
			}
			int[] ids = tp.getRuneIds();
			int[] counts = tp.getRuneCounts();
			for (int i = 0; i < ids.length; i++)
			{
				runesNeeded.merge(ids[i], counts[i], Integer::sum);
			}
		}
		for (Map.Entry<Integer, Integer> entry : runesNeeded.entrySet())
		{
			int runeId = entry.getKey();
			int needed = entry.getValue();
			int have = countItem(inventory, runeId)
				+ countItem(bank, runeId)
				+ runePouch.getOrDefault(runeId, 0);
			int deficit = needed - have;
			if (deficit > 0)
			{
				String name = RUNE_NAMES.getOrDefault(runeId, "Rune (id " + runeId + ")");
				items.add(new BankItem(name + " (combo rune ok)", deficit));
			}
		}

		// --- Farmer payment ---
		if (config.payFarmer())
		{
			items.add(new BankItem("Payment for " + config.herbSeed().getDisplayName() + " (see wiki)", patchCount));
		}

		return Collections.unmodifiableList(items);
	}

	static boolean hasItemId(ItemContainer container, int itemId)
	{
		if (container == null)
		{
			return false;
		}
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() == itemId)
			{
				return true;
			}
		}
		return false;
	}

	/** Returns true if the container holds any item whose ID appears in {@code ids}. */
	static boolean hasAnyItemId(ItemContainer container, int[] ids)
	{
		if (container == null)
		{
			return false;
		}
		for (Item item : container.getItems())
		{
			if (item == null)
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
