package com.farmrun;

import lombok.Getter;
import lombok.Setter;

class TreePatchStop
{
    @Getter private final TreePatch patch;
    @Getter private final Teleport teleport;
    @Getter @Setter private PatchState lastKnownState = PatchState.UNKNOWN;

    TreePatchStop(TreePatch patch, Teleport teleport)
    {
        this.patch = patch;
        this.teleport = teleport;
    }
}
