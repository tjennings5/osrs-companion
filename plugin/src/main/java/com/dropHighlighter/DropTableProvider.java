package com.dropHighlighter;

import java.util.List;

/**
 * Where drop tables come from. The rest of the plugin only ever talks to this interface, so
 * swapping the bundled stub for a real wiki-backed source is a one-line binding change in
 * {@link DropHighlighterPlugin#configure}.
 *
 * <p>Implementations are called from the client thread and from the Swing thread, and must not
 * block on either. A networked implementation should resolve into a local cache off-thread and
 * answer these two methods from that cache.
 */
interface DropTableProvider
{
	/**
	 * @return the monster's drops, or an empty list if it is unknown. Never null, so callers
	 * never have to distinguish "no such monster" from "monster with no drops".
	 */
	List<DropTableEntry> getDrops(String monsterName);

	/**
	 * Whether it is worth offering the "Highlight Drops" menu entry for this monster. Separate
	 * from {@link #getDrops} because it is called on every right click and must stay cheap.
	 */
	boolean hasDrops(String monsterName);
}
