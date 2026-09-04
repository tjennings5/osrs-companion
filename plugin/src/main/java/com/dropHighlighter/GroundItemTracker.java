package com.dropHighlighter;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Keeps a small map of only the ground items we actually care about, maintained from spawn and
 * despawn events. The overlay iterates this map, never the scene, so per-frame work stays flat
 * no matter how much loot is lying around.
 *
 * <p>An item selected under a specific monster does not light up the instant it spawns. Ground
 * items carry no record of which NPC dropped them, so an id match alone can't tell a genuine
 * Kraken death rune from a clue reward or another monster's drop that happens to share the item
 * id. Instead a spawn that might be relevant sits in {@link #pending} until {@link #confirmDrop}
 * — called from {@code NpcLootReceived}, which the client fires a tick or so after the items
 * themselves spawn — either promotes it into {@link #tiles} with the right monster's colour, or
 * it quietly expires in {@link #expirePending} having never been confirmed. Source-agnostic
 * selections (the manual config string, and anything imported from the old flat config) skip this
 * entirely and render the instant they spawn, same as before scoping existed.
 *
 * <p>Keyed by {@link Tile} identity rather than by coordinates. Tile instances are stable for the
 * life of a scene and sidestep the question of what {@code getWorldLocation()} means inside an
 * instance, where several distinct tiles can report the same template coordinate.
 *
 * <p>Not thread safe: every entry point must be called on the client thread.
 */
@Slf4j
@Singleton
class GroundItemTracker
{
	/** Ticks a provisional spawn waits for an {@code NpcLootReceived} match before it's dropped. */
	private static final int PENDING_TIMEOUT_TICKS = 3;

	private final HighlightManager highlights;
	private final ItemManager itemManager;
	private final Client client;

	private final Map<Tile, List<TrackedItem>> tiles = new HashMap<>();

	/** Same-tile spawns awaiting an {@code NpcLootReceived} to say whether they should render. */
	private final Map<WorldPoint, List<PendingItem>> pending = new HashMap<>();

	@Inject
	GroundItemTracker(HighlightManager highlights, ItemManager itemManager, Client client)
	{
		this.highlights = highlights;
		this.itemManager = itemManager;
		this.client = client;
	}

	@Value
	static class TrackedItem
	{
		/** Kept for its live quantity and for identity matching on despawn. */
		TileItem tileItem;

		/**
		 * The canonical id, not the raw dropped one. Noted and placeholder variants have their
		 * own ids, so a monster dropping 30 noted seaweed spawns an item that would never match
		 * a highlight stored against plain seaweed.
		 */
		int itemId;

		/** Resolved once, at confirmation time, so a tile's colour can't disagree with itself. */
		Color color;
	}

	@Value
	private static class PendingItem
	{
		Tile tile;
		TileItem tileItem;
		int itemId;
		int spawnedOnTick;
	}

	Map<Tile, List<TrackedItem>> getTiles()
	{
		return tiles;
	}

	void itemSpawned(Tile tile, TileItem item)
	{
		if (tile == null || item == null)
		{
			return;
		}

		int itemId = itemManager.canonicalize(item.getId());

		if (highlights.isSourceAgnostic(itemId))
		{
			addTracked(tile, item, itemId, highlights.agnosticColor(itemId));
			return;
		}

		if (!highlights.isHighlighted(itemId))
		{
			return;
		}

		WorldPoint point = tile.getWorldLocation();
		if (point == null)
		{
			return;
		}
		pending.computeIfAbsent(point, p -> new ArrayList<>())
			.add(new PendingItem(tile, item, itemId, client.getTickCount()));
	}

	void itemDespawned(Tile tile, TileItem item)
	{
		if (tile == null || item == null)
		{
			return;
		}

		List<TrackedItem> onTile = tiles.get(tile);
		if (onTile != null)
		{
			// Identity, not item id: two of the same item on one tile are separate TileItem
			// instances, and despawning one must not remove the other.
			onTile.removeIf(tracked -> tracked.getTileItem() == item);
			if (onTile.isEmpty())
			{
				tiles.remove(tile);
			}
		}

		WorldPoint point = tile.getWorldLocation();
		List<PendingItem> waiting = point == null ? null : pending.get(point);
		if (waiting != null)
		{
			waiting.removeIf(p -> p.getTileItem() == item);
			if (waiting.isEmpty())
			{
				pending.remove(point);
			}
		}
	}

	/**
	 * Quantity lives on the TileItem, which we hold by reference, so a stack changing size needs
	 * no bookkeeping. This exists for the case where the client swaps in a different TileItem
	 * instance for the same stack, which would otherwise leave us pointing at a dead object.
	 */
	void itemQuantityChanged(Tile tile, TileItem item)
	{
		if (tile == null || item == null)
		{
			return;
		}

		List<TrackedItem> onTile = tiles.get(tile);
		if (onTile != null && onTile.stream().anyMatch(t -> t.getTileItem() == item))
		{
			return;
		}

		WorldPoint point = tile.getWorldLocation();
		List<PendingItem> waiting = point == null ? null : pending.get(point);
		if (waiting != null && waiting.stream().anyMatch(p -> p.getTileItem() == item))
		{
			return;
		}

		itemSpawned(tile, item);
	}

	/**
	 * Called from {@code NpcLootReceived} for each item a kill dropped. {@code ItemStack} itself
	 * carries no reliable location — RuneLite's own loot correlation doesn't set one for the vast
	 * majority of NPCs — so the drop area is the dying NPC's own footprint instead, same as
	 * RuneLite uses internally to do that correlation in the first place.
	 *
	 * <p>Every matching provisional spawn inside that area is either promoted to render with the
	 * given colour, or — if the monster hasn't actually selected this item — left to be discarded
	 * by {@link #expirePending} rather than assumed to be from some other, unrelated table.
	 */
	void confirmDrop(WorldArea dropArea, int itemId, String npcName)
	{
		if (pending.isEmpty())
		{
			return;
		}

		Color color = highlights.scopedColor(npcName, itemId);

		for (Iterator<Map.Entry<WorldPoint, List<PendingItem>>> tileIt = pending.entrySet().iterator();
			tileIt.hasNext();)
		{
			Map.Entry<WorldPoint, List<PendingItem>> entry = tileIt.next();
			if (!dropArea.contains(entry.getKey()))
			{
				continue;
			}

			List<PendingItem> waiting = entry.getValue();
			Iterator<PendingItem> it = waiting.iterator();
			while (it.hasNext())
			{
				PendingItem candidate = it.next();
				if (candidate.getItemId() != itemId)
				{
					continue;
				}

				it.remove();
				if (color != null)
				{
					addTracked(candidate.getTile(), candidate.getTileItem(), itemId, color);
				}
				break;
			}
			if (waiting.isEmpty())
			{
				tileIt.remove();
			}
		}
	}

	/** Drops provisional spawns nothing ever confirmed — not a monster kill, or an unrelated one. */
	void expirePending(int currentTick)
	{
		if (pending.isEmpty())
		{
			return;
		}
		pending.values().forEach(list ->
			list.removeIf(p -> currentTick - p.getSpawnedOnTick() > PENDING_TIMEOUT_TICKS));
		pending.values().removeIf(List::isEmpty);
	}

	private void addTracked(Tile tile, TileItem item, int itemId, Color color)
	{
		if (color == null)
		{
			return;
		}
		List<TrackedItem> onTile = tiles.computeIfAbsent(tile, t -> new ArrayList<>(1));
		onTile.add(new TrackedItem(item, itemId, color));
		sort(onTile);
		log.debug("Tracking highlighted drop {} at {}", itemId, tile.getWorldLocation());
	}

	/** Scene teardown. Tile references do not survive a region load or a world hop. */
	void clear()
	{
		tiles.clear();
		pending.clear();
	}

	/**
	 * Rebuilds from the current scene. Only for when the highlight set itself changes — spawn
	 * events already cover everything else, and this is the one place a full scene walk is
	 * justified because it is user-driven and rare.
	 *
	 * <p>Only source-agnostic items come back from a rescan — by the time the highlight set
	 * changes, whatever {@code NpcLootReceived} would have confirmed a per-monster item against is
	 * long gone, so there is nothing honest to correlate it with.
	 */
	void rescan(Scene scene)
	{
		tiles.clear();
		pending.clear();
		if (scene == null || !highlights.hasSourceAgnosticHighlights())
		{
			return;
		}

		Tile[][][] all = scene.getTiles();
		for (Tile[][] plane : all)
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					List<TileItem> groundItems = tile.getGroundItems();
					if (groundItems == null)
					{
						continue;
					}
					for (TileItem item : groundItems)
					{
						int itemId = itemManager.canonicalize(item.getId());
						if (highlights.isSourceAgnostic(itemId))
						{
							addTracked(tile, item, itemId, highlights.agnosticColor(itemId));
						}
					}
				}
			}
		}
		log.debug("Rescanned scene, {} tile(s) now hold highlighted drops", tiles.size());
	}

	/** Highlight-set order first, then item id so a tile's label stack never jitters. */
	private void sort(List<TrackedItem> onTile)
	{
		if (onTile.size() > 1)
		{
			onTile.sort(Comparator
				.comparingInt((TrackedItem t) -> highlights.priorityOf(t.getItemId()))
				.thenComparingInt(TrackedItem::getItemId));
		}
	}
}
