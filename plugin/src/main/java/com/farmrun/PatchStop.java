package com.osrsmcp.farmrun;

import lombok.Getter;
import lombok.Setter;

public class PatchStop
{
	@Getter
	private final HerbPatch patch;

	/** Best available teleport for this stop, or null if none was found. */
	@Getter
	private final Teleport teleport;

	/** Last known patch state, updated as VarbitChanged events arrive. */
	@Getter
	@Setter
	private PatchState lastKnownState;

	public PatchStop(HerbPatch patch, Teleport teleport)
	{
		this.patch = patch;
		this.teleport = teleport;
		this.lastKnownState = PatchState.UNKNOWN;
	}
}
