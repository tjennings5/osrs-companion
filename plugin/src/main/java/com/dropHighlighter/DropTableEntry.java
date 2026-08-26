package com.dropHighlighter;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * One row of a monster's drop table.
 *
 * <p>Deliberately has no value, price, or alch field. This plugin is built for a Group Ironman
 * account where nothing is bought or sold, so what an item is "worth" is the player's call and
 * no price feed is consulted anywhere.
 *
 * <p>{@code rarity} is free text ("Always", "1/512", "Rare") rather than a parsed fraction — the
 * panel only ever displays it, and a real wiki-backed provider will hand over strings in whatever
 * shape the source uses.
 */
@Value
@AllArgsConstructor
class DropTableEntry
{
	int itemId;
	String itemName;
	String rarity;
}
