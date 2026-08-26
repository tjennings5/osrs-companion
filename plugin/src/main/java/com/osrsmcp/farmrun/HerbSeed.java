package com.osrsmcp.farmrun;

import net.runelite.api.gameval.ItemID;

/**
 * Herb seeds in varbit encoding order (Guam=0 through Torstol=13), matching
 * PatchImplementation.HERB's slot assignment in the game's farming varbit.
 */
public enum HerbSeed
{
	GUAM("Guam", ItemID.GUAM_SEED, 0),
	MARRENTILL("Marrentill", ItemID.MARRENTILL_SEED, 1),
	TARROMIN("Tarromin", ItemID.TARROMIN_SEED, 2),
	HARRALANDER("Harralander", ItemID.HARRALANDER_SEED, 3),
	RANARR("Ranarr", ItemID.RANARR_SEED, 4),
	TOADFLAX("Toadflax", ItemID.TOADFLAX_SEED, 5),
	IRIT("Irit", ItemID.IRIT_SEED, 6),
	AVANTOE("Avantoe", ItemID.AVANTOE_SEED, 7),
	KWUARM("Kwuarm", ItemID.KWUARM_SEED, 8),
	SNAPDRAGON("Snapdragon", ItemID.SNAPDRAGON_SEED, 9),
	CADANTINE("Cadantine", ItemID.CADANTINE_SEED, 10),
	LANTADYME("Lantadyme", ItemID.LANTADYME_SEED, 11),
	DWARF_WEED("Dwarf Weed", ItemID.DWARF_WEED_SEED, 12),
	TORSTOL("Torstol", ItemID.TORSTOL_SEED, 13);

	private final String displayName;
	private final int seedItemId;
	/** Index into the game's herb varbit encoding (Guam=0, ..., Torstol=13). */
	private final int herbVarbitIndex;

	HerbSeed(String displayName, int seedItemId, int herbVarbitIndex)
	{
		this.displayName = displayName;
		this.seedItemId = seedItemId;
		this.herbVarbitIndex = herbVarbitIndex;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int getSeedItemId()
	{
		return seedItemId;
	}

	public int getHerbVarbitIndex()
	{
		return herbVarbitIndex;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
