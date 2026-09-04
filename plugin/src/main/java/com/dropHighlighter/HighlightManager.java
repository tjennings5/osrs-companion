package com.dropHighlighter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * The single source of truth for which items are highlighted, in what colour, and for which
 * monster.
 *
 * <p>Selections are stored per monster, because the panel's checkboxes should reflect what you
 * picked for the thing you are looking at, and — the whole point of this class — because
 * {@link GroundItemTracker} uses that same per-monster scoping to only light up an item when it
 * was actually dropped by a monster that has it selected; see {@link #scopedColor}. Ground items
 * carry no record of which NPC dropped them, so {@code GroundItemTracker} correlates a spawn
 * against {@code NpcLootReceived} to learn the source before ever rendering anything.
 *
 * <p>The manual config string is the one exception: it has no monster to correlate against, so it
 * stays source-agnostic and highlights regardless of what dropped it — see
 * {@link #isSourceAgnostic}.
 *
 * <p>{@link #effective}, the flattened union of every selection, exists only for the cheap
 * "is this item selected by *something*" pre-filter and for the panel's "currently highlighted"
 * view — it is not what decides whether a given ground item actually lights up.
 *
 * <p>Where the same item is selected under two monsters in different colours, the first monster
 * to have selected it wins in the union. Deterministic, and it keeps priority order stable rather
 * than flipping about based on map iteration order.
 *
 * <p>All parsing is static and client-free so it can be unit tested without a running client.
 */
@Slf4j
@Singleton
class HighlightManager
{
	private final ConfigManager configManager;
	private final Gson gson;

	/** monster name -> (item id -> colour). Insertion ordered at both levels. Persisted. */
	private Map<String, Map<Integer, Color>> selected = new LinkedHashMap<>();

	/** Flattened union of {@link #selected} plus the manual config string. */
	private volatile Map<Integer, Color> effective = Collections.emptyMap();

	/**
	 * The subset of {@link #effective} that isn't tied to a specific monster: the manual config
	 * string, and whatever was imported from the flat, pre-per-monster config shape. These render
	 * the moment they touch the ground, same as before this class could scope by source — there is
	 * no monster to correlate them against.
	 */
	private volatile Map<Integer, Color> agnostic = Collections.emptyMap();

	/** item id -> position in {@link #effective}, so callers don't have to walk the map. */
	private volatile Map<Integer, Integer> priorities = Collections.emptyMap();

	@Inject
	HighlightManager(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Re-reads both inputs from config. Call on startup and on any change to the config group. */
	void reload(DropHighlighterConfig config)
	{
		selected = fromStorage(configManager.getConfiguration(DropHighlighterConfig.GROUP,
			DropHighlighterConfig.KEY_HIGHLIGHTS), gson);

		Map<Integer, Color> agnosticMerged = new LinkedHashMap<>(parseSeed(config.seedHighlights()));

		Map<Integer, Color> merged = new LinkedHashMap<>();
		for (Map<Integer, Color> perMonster : selected.values())
		{
			// putIfAbsent: first monster to claim an item owns its colour.
			perMonster.forEach(merged::putIfAbsent);
		}
		agnosticMerged.forEach(merged::putIfAbsent);

		Map<Integer, Integer> order = new HashMap<>(merged.size());
		int i = 0;
		for (Integer itemId : merged.keySet())
		{
			order.put(itemId, i++);
		}

		agnostic = Collections.unmodifiableMap(agnosticMerged);
		effective = Collections.unmodifiableMap(merged);
		priorities = order;
		log.debug("Highlight set reloaded: {} item(s) across {} table(s)",
			merged.size(), selected.size());
	}

	/**
	 * Whether this item id is selected anywhere at all — under some monster, or the source-agnostic
	 * set. Used only to decide whether a freshly spawned ground item is worth provisionally
	 * tracking while its actual source is still being resolved; it says nothing about whether the
	 * item should render.
	 */
	boolean isHighlighted(int itemId)
	{
		return effective.containsKey(itemId);
	}

	Color colorFor(int itemId)
	{
		return effective.get(itemId);
	}

	/**
	 * True for the manual config string and whatever was imported from the old flat config shape —
	 * these have no monster to correlate against, so they highlight regardless of what dropped
	 * them, same as before this class could scope by source.
	 */
	boolean isSourceAgnostic(int itemId)
	{
		return agnostic.containsKey(itemId);
	}

	Color agnosticColor(int itemId)
	{
		return agnostic.get(itemId);
	}

	/** Colour for this item when dropped specifically by {@code npcName}, or null if it wasn't selected under that monster. */
	Color scopedColor(String npcName, int itemId)
	{
		if (npcName == null)
		{
			return null;
		}
		Map<Integer, Color> forMonster = selected.get(npcName);
		return forMonster == null ? null : forMonster.get(itemId);
	}

	/** Lower sorts first. Unknown ids sort last rather than throwing. */
	int priorityOf(int itemId)
	{
		Integer p = priorities.get(itemId);
		return p == null ? Integer.MAX_VALUE : p;
	}

	boolean isEmpty()
	{
		return effective.isEmpty();
	}

	/** Whether there is anything a scene rescan could actually restore. See {@link #agnostic}. */
	boolean hasSourceAgnosticHighlights()
	{
		return !agnostic.isEmpty();
	}

	/** What this one monster has selected, for rendering its checkbox and swatch state. */
	Map<Integer, Color> getSelected(String monsterName)
	{
		Map<Integer, Color> forMonster = selected.get(monsterName);
		return forMonster == null ? Collections.emptyMap() : Collections.unmodifiableMap(forMonster);
	}

	/**
	 * Every item selected anywhere, in priority order. Backs the panel's "currently highlighted"
	 * view, which exists so a beam can always be traced back to something you can switch off —
	 * without it, an item ticked under a monster you have since forgotten stays lit forever.
	 *
	 * <p>Excludes the manual config string: those are not the panel's to remove.
	 */
	Map<Integer, Color> getAllSelected()
	{
		Map<Integer, Color> all = new LinkedHashMap<>();
		for (Map<Integer, Color> perMonster : selected.values())
		{
			perMonster.forEach(all::putIfAbsent);
		}
		return all;
	}

	/** Clears an item from every table at once. */
	void deselectEverywhere(int itemId)
	{
		Map<String, Map<Integer, Color>> next = copy(selected);

		boolean removed = false;
		for (Map<Integer, Color> items : next.values())
		{
			removed |= items.remove(itemId) != null;
		}
		if (!removed)
		{
			return;
		}

		next.values().removeIf(Map::isEmpty);
		persist(next);
	}

	/**
	 * Clears every panel selection across every monster in one go. Does not touch the manual
	 * config string — same as {@link #getAllSelected}, that one is not the panel's to remove.
	 */
	void clearAll()
	{
		if (selected.isEmpty())
		{
			return;
		}
		persist(Collections.emptyMap());
	}

	/** Recolours an item everywhere it is selected, so the union can't disagree with itself. */
	void recolourEverywhere(int itemId, Color color)
	{
		Map<String, Map<Integer, Color>> next = copy(selected);
		next.values().forEach(items -> items.computeIfPresent(itemId, (id, old) -> color));
		persist(next);
	}

	void select(String monsterName, int itemId, Color color)
	{
		if (monsterName == null)
		{
			return;
		}
		Map<String, Map<Integer, Color>> next = copy(selected);
		next.computeIfAbsent(monsterName, m -> new LinkedHashMap<>()).put(itemId, color);
		persist(next);
	}

	void deselect(String monsterName, int itemId)
	{
		if (monsterName == null || !getSelected(monsterName).containsKey(itemId))
		{
			return;
		}
		Map<String, Map<Integer, Color>> next = copy(selected);
		Map<Integer, Color> forMonster = next.get(monsterName);
		forMonster.remove(itemId);
		if (forMonster.isEmpty())
		{
			next.remove(monsterName);
		}
		persist(next);
	}

	private static Map<String, Map<Integer, Color>> copy(Map<String, Map<Integer, Color>> source)
	{
		Map<String, Map<Integer, Color>> copy = new LinkedHashMap<>();
		source.forEach((monster, items) -> copy.put(monster, new LinkedHashMap<>(items)));
		return copy;
	}

	/**
	 * Writes to config, which fires a ConfigChanged that drives {@link #reload}. We don't update
	 * the flattened view here — letting the single config-change path own that keeps the panel
	 * and the overlay from ever disagreeing about what is highlighted.
	 */
	private void persist(Map<String, Map<Integer, Color>> next)
	{
		if (next.isEmpty())
		{
			configManager.unsetConfiguration(DropHighlighterConfig.GROUP,
				DropHighlighterConfig.KEY_HIGHLIGHTS);
			return;
		}
		configManager.setConfiguration(DropHighlighterConfig.GROUP,
			DropHighlighterConfig.KEY_HIGHLIGHTS, toStorage(next).toString());
	}

	// ------------------------------------------------------------------
	// Static, client-free parsing. Unit tested.
	// ------------------------------------------------------------------

	/**
	 * Parses {@code "526:#FF0000, 2:#00FFFF"}. Malformed pairs are skipped rather than aborting
	 * the whole string — a typo mid-way through should not silently drop the entries either side
	 * of it, since the field is edited by hand while the client is running.
	 */
	static Map<Integer, Color> parseSeed(String raw)
	{
		Map<Integer, Color> out = new LinkedHashMap<>();
		if (raw == null || raw.trim().isEmpty())
		{
			return out;
		}

		for (String pair : raw.split(","))
		{
			String trimmed = pair.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			int colon = trimmed.indexOf(':');
			if (colon <= 0 || colon == trimmed.length() - 1)
			{
				log.debug("Skipping manual highlight with no itemId:colour split: '{}'", trimmed);
				continue;
			}

			Integer itemId = parseItemId(trimmed.substring(0, colon).trim());
			Color color = parseHex(trimmed.substring(colon + 1).trim());
			if (itemId == null || color == null)
			{
				log.debug("Skipping unparseable manual highlight: '{}'", trimmed);
				continue;
			}

			out.put(itemId, color);
		}
		return out;
	}

	private static Integer parseItemId(String s)
	{
		try
		{
			int id = Integer.parseInt(s);
			return id >= 0 ? id : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Accepts {@code #RRGGBB}, {@code RRGGBB}, {@code #AARRGGBB} and {@code AARRGGBB}. */
	static Color parseHex(String s)
	{
		if (s == null)
		{
			return null;
		}
		String hex = s.trim();
		if (hex.startsWith("#"))
		{
			hex = hex.substring(1);
		}
		if (hex.length() != 6 && hex.length() != 8)
		{
			return null;
		}

		try
		{
			// Long, not Integer: an 8-digit value with the high bit set overflows a signed int.
			long value = Long.parseLong(hex, 16);
			return new Color((int) value, hex.length() == 8);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Always emits {@code #RRGGBB}; alpha is owned by the beam opacity setting, not the swatch. */
	static String toHex(Color color)
	{
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	static JsonObject toStorage(Map<String, Map<Integer, Color>> selections)
	{
		JsonObject root = new JsonObject();
		selections.forEach((monster, items) ->
		{
			JsonObject forMonster = new JsonObject();
			items.forEach((id, color) -> forMonster.addProperty(Integer.toString(id), toHex(color)));
			root.add(monster, forMonster);
		});
		return root;
	}

	/** Reads the stored selections: monster name -> (item id -> colour). */
	static Map<String, Map<Integer, Color>> fromStorage(String json, Gson gson)
	{
		Map<String, Map<Integer, Color>> out = new LinkedHashMap<>();
		if (json == null || json.trim().isEmpty())
		{
			return out;
		}

		JsonObject root;
		try
		{
			root = gson.fromJson(json, JsonObject.class);
		}
		catch (JsonSyntaxException e)
		{
			// A corrupt config value should cost the user their highlight list, not the ability
			// to start the plugin at all.
			log.warn("Could not parse stored highlights, starting from empty", e);
			return out;
		}

		if (root == null)
		{
			return out;
		}

		for (Map.Entry<String, JsonElement> entry : root.entrySet())
		{
			if (!entry.getValue().isJsonObject())
			{
				continue;
			}
			Map<Integer, Color> items = readColorMap(entry.getValue().getAsJsonObject());
			if (!items.isEmpty())
			{
				out.put(entry.getKey(), items);
			}
		}

		return out;
	}

	private static Map<Integer, Color> readColorMap(JsonObject object)
	{
		Map<Integer, Color> items = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet())
		{
			Integer itemId = parseItemId(entry.getKey());
			Color color = entry.getValue().isJsonPrimitive()
				? parseHex(entry.getValue().getAsString()) : null;
			if (itemId != null && color != null)
			{
				items.put(itemId, color);
			}
		}
		return items;
	}
}
