package com.dropHighlighter;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Asserts properties of the generated table rather than its exact contents, so regenerating from
 * a newer wiki snapshot doesn't break the build over a rarity that shifted by a decimal place.
 */
public class BundledDropTableProviderTest
{
	private BundledDropTableProvider provider;

	@Before
	public void setUp()
	{
		provider = new BundledDropTableProvider(new Gson());
	}

	@Test
	public void loadsTheGeneratedTableForTheWholeGame()
	{
		// Sanity floor: the generator produced ~2000 monsters. Anything near zero means the
		// resource failed to load and every lookup is silently returning empty.
		assertTrue(provider.hasDrops("Cave kraken"));
		assertTrue(provider.hasDrops("Hill Giant"));
		assertTrue(provider.hasDrops("Abyssal demon"));
		assertTrue(provider.hasDrops("Zulrah"));
	}

	@Test
	public void caveKrakenCarriesItsSignatureDrops()
	{
		List<DropTableEntry> drops = provider.getDrops("Cave kraken");

		assertTrue(drops.size() > 20);
		assertEquals(12004, idOf(drops, "Kraken tentacle"));
		assertEquals(11908, idOf(drops, "Uncharged trident"));
		assertEquals(401, idOf(drops, "Seaweed"));
	}

	/**
	 * A monster's table should be what that monster specifically drops. Shared pools are the same
	 * items on hundreds of NPCs and bury the handful worth stopping for.
	 */
	@Test
	public void monsterTablesExcludeSharedLootPools()
	{
		for (String monster : new String[]{"Cave kraken", "Waterfiend", "Hill Giant"})
		{
			for (DropTableEntry drop : provider.getDrops(monster))
			{
				String name = drop.getItemName();
				assertFalse(monster + " still lists herb-table item " + name,
					name.startsWith("Grimy "));
				assertFalse(monster + " still lists seed-table item " + name,
					name.endsWith(" seed") || name.endsWith(" seeds"));
			}
		}
		// The rare drop table chain went with them.
		assertTrue(provider.getDrops("Cave kraken").stream()
			.noneMatch(d -> "Dragon spear".equals(d.getItemName())));
	}

	/**
	 * Crystal seeds are named like farming seeds but are uniques from a single source each.
	 * Matching the shared seed pool on the name suffix alone swept them into the pool and deleted
	 * them from the monsters that actually drop them, which is the worst possible failure here:
	 * a chase item silently impossible to highlight.
	 */
	@Test
	public void uniquesNamedLikeSeedsStayOnTheirMonster()
	{
		assertTrue(provider.getDrops("Zalcano").stream()
			.anyMatch(d -> "Crystal tool seed".equals(d.getItemName())));
		assertTrue(provider.getDrops("Reward Chest (The Gauntlet)").stream()
			.anyMatch(d -> "Crystal weapon seed".equals(d.getItemName())));
		assertTrue(provider.getDrops("Elf Warrior").stream()
			.anyMatch(d -> "Crystal teleport seed".equals(d.getItemName())));

		for (DropTableEntry drop : provider.getDrops("Seed drop table"))
		{
			assertFalse("crystal seed leaked into the shared pool: " + drop.getItemName(),
				drop.getItemName().toLowerCase().contains("crystal"));
		}
	}

	@Test
	public void sharedPoolsAreAvailableAsTheirOwnTables()
	{
		// Reachable from the panel's dropdown, so a specific herb or seed can still be picked.
		for (String pool : new String[]{"Rare drop table", "Herb drop table", "Seed drop table"})
		{
			assertTrue(pool + " is missing", provider.hasDrops(pool));
			assertFalse(pool + " is empty", provider.getDrops(pool).isEmpty());
		}
		assertTrue(provider.getDrops("Herb drop table").stream()
			.allMatch(d -> d.getItemName().startsWith("Grimy ")));
	}

	/**
	 * Regression guard for the variant trap that made this data pipeline necessary: several
	 * sources resolve "Adamant spear" to 3174, which is the Karambwan-poisoned variant. The
	 * plain spear a cave kraken actually drops is 1245.
	 */
	@Test
	public void itemNamesResolveToTheirPlainVariant()
	{
		assertEquals(1245, idOf(provider.getDrops("Cave kraken"), "Adamant spear"));
	}

	@Test
	public void raritiesAreOrderedRarestFirst()
	{
		// The plugin colours a shared tile's beam from the first listed item, so this ordering
		// is behaviour, not presentation.
		for (String monster : new String[]{"Cave kraken", "Zulrah", "Vorkath"})
		{
			double previous = 0;
			for (DropTableEntry drop : provider.getDrops(monster))
			{
				double chance = chanceOf(drop.getRarity());
				assertTrue(monster + " is out of order at " + drop.getItemName()
					+ " (" + drop.getRarity() + ")", chance >= previous);
				previous = chance;
			}
		}
	}

	/** Parses the leading "a/b" of a rarity string into a probability; unknown sorts last. */
	private static double chanceOf(String rarity)
	{
		String head = rarity.split(" ")[0].replace(",", "");
		int slash = head.indexOf('/');
		if (slash < 0)
		{
			return Double.MAX_VALUE;
		}
		try
		{
			return Double.parseDouble(head.substring(0, slash))
				/ Double.parseDouble(head.substring(slash + 1));
		}
		catch (NumberFormatException e)
		{
			return Double.MAX_VALUE;
		}
	}

	@Test
	public void noMonsterListsTheSameItemTwice()
	{
		// A duplicate would render two identical labels stacked on one tile.
		for (String monster : new String[]{"Cave kraken", "Zulrah", "Hill Giant", "Goblin"})
		{
			Set<Integer> seen = new HashSet<>();
			for (DropTableEntry drop : provider.getDrops(monster))
			{
				assertTrue(monster + " lists item " + drop.getItemId() + " twice",
					seen.add(drop.getItemId()));
			}
		}
	}

	@Test
	public void everyEntryIsUsable()
	{
		for (String monster : new String[]{"Cave kraken", "Zulrah", "Vorkath", "Goblin"})
		{
			List<DropTableEntry> drops = provider.getDrops(monster);
			assertFalse(monster + " has no drops", drops.isEmpty());
			for (DropTableEntry drop : drops)
			{
				// A non-positive id would show a blank icon and never match a ground item.
				assertTrue(monster + " has a bad item id", drop.getItemId() > 0);
				assertNotNull(monster + " has an unnamed drop", drop.getItemName());
				assertFalse(monster + " has an unnamed drop", drop.getItemName().isEmpty());
				assertNotNull(monster + " has a drop with no rarity", drop.getRarity());
			}
		}
	}

	@Test
	public void lookupIsCaseAndWhitespaceInsensitive()
	{
		// NPC names arrive from the client with whatever casing Jagex used.
		assertFalse(provider.getDrops("cave kraken").isEmpty());
		assertFalse(provider.getDrops("CAVE KRAKEN").isEmpty());
		assertFalse(provider.getDrops("  Cave kraken  ").isEmpty());
		assertTrue(provider.hasDrops("hill GIANT"));
	}

	@Test
	public void unknownMonstersYieldAnEmptyListNotNull()
	{
		List<DropTableEntry> drops = provider.getDrops("Definitely Not A Monster");

		assertNotNull(drops);
		assertTrue(drops.isEmpty());
		assertFalse(provider.hasDrops("Definitely Not A Monster"));
	}

	@Test
	public void nullMonsterNameIsHandled()
	{
		assertTrue(provider.getDrops(null).isEmpty());
		assertFalse(provider.hasDrops(null));
	}

	private static int idOf(List<DropTableEntry> drops, String itemName)
	{
		return drops.stream()
			.filter(d -> itemName.equals(d.getItemName()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no drop named " + itemName))
			.getItemId();
	}
}
