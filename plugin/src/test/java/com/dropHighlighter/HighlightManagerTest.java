package com.dropHighlighter;

import com.google.gson.Gson;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HighlightManagerTest
{
	private final Gson gson = new Gson();

	@Test
	public void parsesASingleWellFormedPair()
	{
		Map<Integer, Color> parsed = HighlightManager.parseSeed("526:#FF0000");

		assertEquals(1, parsed.size());
		assertEquals(Color.RED, parsed.get(526));
	}

	@Test
	public void parsesMultiplePairsAndToleratesWhitespace()
	{
		Map<Integer, Color> parsed = HighlightManager.parseSeed("  526 : #FF0000 ,2:#00FFFF  ");

		assertEquals(2, parsed.size());
		assertEquals(Color.RED, parsed.get(526));
		assertEquals(Color.CYAN, parsed.get(2));
	}

	@Test
	public void preservesInsertionOrderBecauseItIsThePriorityOrder()
	{
		Map<Integer, Color> parsed = HighlightManager.parseSeed("99:#FFFFFF, 1:#000000, 50:#FF0000");

		assertEquals("[99, 1, 50]", parsed.keySet().toString());
	}

	@Test
	public void acceptsHexWithoutALeadingHash()
	{
		assertEquals(Color.RED, HighlightManager.parseSeed("526:FF0000").get(526));
	}

	@Test
	public void malformedPairsAreSkippedWithoutDroppingTheirNeighbours()
	{
		Map<Integer, Color> parsed = HighlightManager.parseSeed(
			"526:#FF0000, garbage, 7:notacolour, :#FF0000, 99:, abc:#00FF00, 2:#00FFFF");

		assertEquals(2, parsed.size());
		assertEquals(Color.RED, parsed.get(526));
		assertEquals(Color.CYAN, parsed.get(2));
	}

	@Test
	public void emptyAndNullInputYieldAnEmptyMap()
	{
		assertTrue(HighlightManager.parseSeed(null).isEmpty());
		assertTrue(HighlightManager.parseSeed("").isEmpty());
		assertTrue(HighlightManager.parseSeed("   ").isEmpty());
		assertTrue(HighlightManager.parseSeed(",,,").isEmpty());
	}

	@Test
	public void aRepeatedItemIdKeepsTheLastColour()
	{
		Map<Integer, Color> parsed = HighlightManager.parseSeed("526:#FF0000, 526:#00FFFF");

		assertEquals(1, parsed.size());
		assertEquals(Color.CYAN, parsed.get(526));
	}

	@Test
	public void negativeItemIdsAreRejected()
	{
		assertTrue(HighlightManager.parseSeed("-1:#FF0000").isEmpty());
	}

	@Test
	public void eightDigitHexCarriesItsAlphaWithoutOverflowing()
	{
		Color parsed = HighlightManager.parseHex("#80FF0000");

		assertEquals(0x80, parsed.getAlpha());
		assertEquals(0xFF, parsed.getRed());
	}

	@Test
	public void hexOfTheWrongLengthIsRejected()
	{
		assertNull(HighlightManager.parseHex("#FFF"));
		assertNull(HighlightManager.parseHex("#FF00"));
		assertNull(HighlightManager.parseHex("#FF00000"));
		assertNull(HighlightManager.parseHex(null));
	}

	@Test
	public void toHexAlwaysEmitsSixUppercaseDigits()
	{
		assertEquals("#FF0000", HighlightManager.toHex(Color.RED));
		assertEquals("#000000", HighlightManager.toHex(Color.BLACK));
		// Alpha belongs to the beam opacity setting, so a translucent swatch still round-trips
		// as its plain RGB.
		assertEquals("#00FF00", HighlightManager.toHex(new Color(0, 255, 0, 12)));
	}

	@Test
	public void storageRoundTripsPerMonsterPreservingOrder()
	{
		Map<String, Map<Integer, Color>> original = new LinkedHashMap<>();
		Map<Integer, Color> kraken = new LinkedHashMap<>();
		kraken.put(12004, Color.CYAN);
		kraken.put(560, Color.RED);
		original.put("Cave kraken", kraken);
		Map<Integer, Color> waterfiend = new LinkedHashMap<>();
		waterfiend.put(571, new Color(255, 215, 0));
		original.put("Waterfiend", waterfiend);

		String json = HighlightManager.toStorage(original).toString();
		Map<String, Map<Integer, Color>> restored = HighlightManager.fromStorage(json, gson);

		assertEquals(original, restored);
		assertEquals("[Cave kraken, Waterfiend]", restored.keySet().toString());
		assertEquals("[12004, 560]", restored.get("Cave kraken").keySet().toString());
	}

	@Test
	public void oneMonstersSelectionsAreIndependentOfAnothers()
	{
		Map<String, Map<Integer, Color>> restored = HighlightManager.fromStorage(
			"{\"Cave kraken\":{\"560\":\"#FF0000\"},\"Waterfiend\":{\"571\":\"#00FFFF\"}}", gson);

		assertEquals(Color.RED, restored.get("Cave kraken").get(560));
		// The whole point of per-monster storage: a death rune ticked on the kraken must not
		// come back already ticked on the waterfiend.
		assertNull(restored.get("Waterfiend").get(560));
	}

	@Test
	public void flatLegacyConfigIsMigratedInsteadOfDiscarded()
	{
		// Written before selections were per monster. Folding it under a named group keeps those
		// highlights rendering rather than silently going dark on upgrade.
		Map<String, Map<Integer, Color>> restored = HighlightManager.fromStorage(
			"{\"526\":\"#FF0000\",\"4151\":\"#00FFFF\"}", gson);

		assertEquals(1, restored.size());
		Map<Integer, Color> legacy = restored.get(HighlightManager.LEGACY_GROUP);
		assertNotNull(legacy);
		assertEquals(Color.RED, legacy.get(526));
		assertEquals(Color.CYAN, legacy.get(4151));
	}

	@Test
	public void corruptStoredJsonDegradesToEmptyRatherThanThrowing()
	{
		assertTrue(HighlightManager.fromStorage("{not json", gson).isEmpty());
		assertTrue(HighlightManager.fromStorage("[1,2,3]", gson).isEmpty());
		assertTrue(HighlightManager.fromStorage("", gson).isEmpty());
		assertTrue(HighlightManager.fromStorage(null, gson).isEmpty());
	}

	@Test
	public void unparseableEntriesInStoredJsonAreDroppedIndividually()
	{
		Map<String, Map<Integer, Color>> restored = HighlightManager.fromStorage(
			"{\"Cave kraken\":{\"560\":\"#FF0000\",\"oops\":\"#00FF00\",\"2\":\"nope\","
				+ "\"12004\":\"#00FFFF\"}}", gson);

		Map<Integer, Color> kraken = restored.get("Cave kraken");
		assertEquals(2, kraken.size());
		assertEquals(Color.RED, kraken.get(560));
		assertEquals(Color.CYAN, kraken.get(12004));
	}
}
