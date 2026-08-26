package com.dropHighlighter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;

/**
 * Keeps a small map of only the ground items we actually care about, maintained from spawn and
 * despawn events. The overlay iterates this map, never the scene, so per-frame work stays flat
 * no matter how much loot is lying around.
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
	private final HighlightManager highlights;
	private final ItemManager itemManager;

	private final Map<Tile, List<TrackedItem>> tiles = new HashMap<>();

	@Inject
	GroundItemTracker(HighlightManager highlights, ItemManager itemManager)
	{
		this.highlights = highlights;
		this.itemManager = itemManager;
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
		if (!highlights.isHighlighted(itemId))
		{
			return;
		}

		List<TrackedItem> onTile = tiles.computeIfAbsent(tile, t -> new ArrayList<>(1));
		onTile.add(new TrackedItem(item, itemId));
		sort(onTile);
		log.debug("Tracking highlighted drop {} at {}", itemId, tile.getWorldLocation());
	}

	void itemDespawned(Tile tile, TileItem item)
	{
		if (tile == null || item == null)
		{
			return;
		}

		List<TrackedItem> onTile = tiles.get(tile);
		if (onTile == null)
		{
			return;
		}

		// Identity, not item id: two of the same item on one tile are separate TileItem
		// instances, and despawning one must not remove the other.
		onTile.removeIf(tracked -> tracked.getTileItem() == item);
		if (onTile.isEmpty())
		{
			tiles.remove(tile);
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
		itemSpawned(tile, item);
	}

	/** Scene teardown. Tile references do not survive a region load or a world hop. */
	void clear()
	{
		tiles.clear();
	}

	/**
	 * Rebuilds from the current scene. Only for when the highlight set itself changes — spawn
	 * events already cover everything else, and this is the one place a full scene walk is
	 * justified because it is user-driven and rare.
	 */
	void rescan(Scene scene)
	{
		tiles.clear();
		if (scene == null || highlights.isEmpty())
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
						itemSpawned(tile, item);
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
