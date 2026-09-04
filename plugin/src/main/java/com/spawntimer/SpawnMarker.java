package com.spawntimer;

import net.runelite.api.coords.WorldPoint;

/**
 * An NPC tagged to learn its spawn tile and respawn interval.
 *
 * <p>Tagging never trusts the NPC's position at the moment you tag it — it's usually mid-wander
 * or mid-fight by then, nowhere near its actual spawn point. Instead the tile itself is learned
 * from the first respawn observed after tagging: OSRS very often reuses the exact same NPC slot
 * index for a respawn at its spawn point, which is the strongest signal; failing that, whichever
 * same-named NPC spawns near where the tagged one died is taken as the respawn. Once one cycle
 * has been observed, {@link #tileConfirmed} is true and every cycle after that matches the exact
 * tile, which is far more precise than continuing to guess.
 *
 * <p>Only {@code npcName}, {@code tileConfirmed}, the coordinates, and {@code learnedTicks} are
 * persisted to config (via Gson, which skips {@code transient} fields by default) — the rest is
 * runtime tracking state rebuilt from the scene on every plugin startup.
 */
class SpawnMarker
{
	final String npcName;

	/** Whether a confirmed respawn has told us the real spawn tile yet. */
	boolean tileConfirmed;

	int worldX;
	int worldY;
	int plane;

	/** -1 until a full death-to-respawn cycle has been observed. */
	int learnedTicks = -1;

	/** Scene index of the NPC currently believed to be the tracked one, or -1 while waiting. */
	transient int trackedNpcIndex = -1;

	/** True from the moment the tracked NPC dies until a respawn is recognized. */
	transient boolean waiting;

	/** Tick the tracked NPC died on, for computing both the countdown and the learned interval. */
	transient int diedOnTick = -1;

	/** Index the tracked NPC had at death, in case OSRS reuses it for the respawn. */
	transient int diedIndex = -1;

	/** Where the tracked NPC died, used to recognize a nearby respawn before the tile is known. */
	transient WorldPoint deathLocation;

	SpawnMarker(String npcName)
	{
		this.npcName = npcName;
	}

	WorldPoint worldPoint()
	{
		return new WorldPoint(worldX, worldY, plane);
	}

	/** Resets runtime state to "an NPC is here right now", as when tagging or re-seeding. */
	void markPresent(int npcIndex)
	{
		trackedNpcIndex = npcIndex;
		waiting = false;
		diedOnTick = -1;
		diedIndex = -1;
		deathLocation = null;
	}

	/** Resets runtime state to "the tracked NPC just died, start waiting for a respawn". */
	void markDead(int npcIndex, WorldPoint location, int tick)
	{
		trackedNpcIndex = -1;
		waiting = true;
		diedIndex = npcIndex;
		deathLocation = location;
		diedOnTick = tick;
	}

	/** Confirms the spawn tile from a recognized respawn and marks the NPC present there. */
	void confirmTile(WorldPoint point, int npcIndex, int ticksElapsed)
	{
		tileConfirmed = true;
		worldX = point.getX();
		worldY = point.getY();
		plane = point.getPlane();
		learnedTicks = ticksElapsed;
		markPresent(npcIndex);
	}
}
