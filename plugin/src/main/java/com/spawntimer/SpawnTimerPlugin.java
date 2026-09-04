package com.spawntimer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Ctrl+right-click an NPC to tag it, and once it dies and a same-named NPC reappears, its spawn
 * tile and respawn interval are known and shown as a countdown for every cycle after.
 *
 * <p>Unlike NPC Indicators' spawn timers, which need every instance of an NPC tagged before it
 * will time anything, this only ever tracks the one you tagged. It also never trusts that NPC's
 * position at tag time as the spawn tile — by the time you're tagging it, it's usually already
 * wandered or is mid-fight. Instead the tile itself is learned, the same way NPC Indicators does
 * it internally: OSRS very often reuses the exact same scene index for a respawn at its spawn
 * point, which is the strongest signal; failing that, whichever same-named NPC spawns near where
 * the tagged one died is taken as the respawn. See {@link SpawnMarker} for the detail.
 *
 * <p>Runs on the real {@code NpcSpawned}/{@code NpcDespawned} events rather than scanning the
 * scene every tick — in an area with many identically-named NPCs (a goblin camp, say) a
 * tick-scan approach kept mistaking an unrelated goblin for the tracked one's respawn.
 */
@Slf4j
@PluginDescriptor(
	name = "NPC Spawn Timer",
	description = "Ctrl+right-click an NPC to track it, then see its spawn tile and a respawn "
		+ "countdown once it's died and come back once.",
	tags = {"npc", "spawn", "timer", "respawn", "indicator"}
)
public class SpawnTimerPlugin extends Plugin
{
	private static final String MARKERS_KEY = "markers";
	private static final Type MARKER_LIST_TYPE = new TypeToken<ArrayList<SpawnMarker>>()
	{
	}.getType();

	/**
	 * How far a same-named NPC may spawn from the death tile to be recognized as the respawn,
	 * before the real tile is confirmed. Originally matched NPC Indicators' 15-tile view-distance
	 * constant, but that's sized for "is this actor even visible", not "which of several nearby
	 * spawn points is this" — in a goblin camp with spawns a handful of tiles apart it happily
	 * matched the wrong one. Tightened down to something closer to how far a spawn point and the
	 * tile you killed something on actually tend to be.
	 */
	private static final int SEARCH_RADIUS = 8;

	/**
	 * How far a respawn may land from the confirmed tile and still count as the same spawn point.
	 * Not every NPC respawns on the exact same tile every cycle — some rock back and forth over an
	 * adjacent tile or two due to movement on the same tick they spawn — so requiring an exact
	 * match made the timer stop working the first time that happened. Kept to 1 rather than
	 * something more generous so it can't reach into a neighbouring, unrelated spawn point.
	 */
	private static final int CONFIRMED_TOLERANCE = 1;

	/**
	 * How far from the player a despawn has to happen to be believed as a real death. {@code
	 * NpcDespawned} fires just as readily when a live NPC wanders out of the loaded scene as it
	 * does on an actual kill, and a real kill always happens right where you're standing — so a
	 * despawn far from the player is treated as "left the scene", not "died", and doesn't touch
	 * the marker at all. Matches the view-distance NPC Indicators uses for the same distinction.
	 */
	private static final int VIEW_RANGE = 15;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SpawnTimerConfig config;

	@Inject
	private SpawnTimerOverlay overlay;

	@Inject
	private Gson gson;

	private final List<SpawnMarker> markers = new ArrayList<>();

	@Provides
	SpawnTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpawnTimerConfig.class);
	}

	List<SpawnMarker> getMarkers()
	{
		return markers;
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		loadMarkers();
		// Config loads before the scene does, so seeding against the world has to wait for a
		// tick rather than happening here — otherwise every marker looks despawned on startup.
		clientThread.invoke(this::seedMarkers);
		log.info("NPC Spawn Timer started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		markers.clear();
		log.info("NPC Spawn Timer stopped");
	}

	private void loadMarkers()
	{
		markers.clear();
		String json = configManager.getConfiguration(SpawnTimerConfig.GROUP, MARKERS_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		List<SpawnMarker> saved = gson.fromJson(json, MARKER_LIST_TYPE);
		if (saved != null)
		{
			markers.addAll(saved);
		}
	}

	private void saveMarkers()
	{
		configManager.setConfiguration(SpawnTimerConfig.GROUP, MARKERS_KEY, gson.toJson(markers));
	}

	/**
	 * Reconciles freshly-loaded markers against whatever is actually in the scene.
	 *
	 * <p>A confirmed marker resumes tracking if its NPC is standing right there, or starts a
	 * fresh wait otherwise. An unconfirmed marker (still on its first, never-finished learning
	 * cycle when the client closed) has no tile to check against, so it just re-attaches to the
	 * first same-named NPC found — a reasonable guess, since untracking and re-tagging is one
	 * click if it guessed wrong.
	 */
	private void seedMarkers()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}
		for (SpawnMarker marker : markers)
		{
			if (marker.tileConfirmed)
			{
				NPC npc = findNpcAt(worldView, marker.npcName, marker.worldPoint());
				if (npc != null)
				{
					marker.markPresent(npc.getIndex());
				}
				else
				{
					// Unknown how far into the cycle we already are; starting the count from now
					// undershoots the first countdown after a restart but self-corrects next cycle.
					marker.waiting = true;
					marker.diedOnTick = client.getTickCount();
					marker.diedIndex = -1;
					marker.deathLocation = marker.worldPoint();
				}
			}
			else
			{
				NPC npc = findUnclaimedNpcNamed(worldView, marker.npcName, markers);
				if (npc != null)
				{
					marker.markPresent(npc.getIndex());
				}
			}
		}
	}

	/**
	 * Adds "Track Spawn" / "Untrack Spawn" when Ctrl is held over an NPC's Examine entry, and
	 * "Unmark Spawn Tile" over a confirmed marker's Walk-here entry.
	 *
	 * <p>Hooking the Examine/Walk entries specifically — rather than just grabbing whatever NPC
	 * or tile the cursor is over — and inserting at the same point NPC Indicators inserts its Tag
	 * option is what keeps this entry sitting just above Examine, below Attack, instead of
	 * becoming the new default left-click action.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!client.isKeyPressed(KeyCode.KC_CONTROL))
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		if (entry.getType() == MenuAction.EXAMINE_NPC)
		{
			NPC npc = entry.getNpc();
			if (npc != null)
			{
				addTrackEntry(npc, entry);
			}
		}
		else if (entry.getType() == MenuAction.WALK)
		{
			addUnmarkEntry(entry);
		}
	}

	/**
	 * Tracking is per NPC instance, not per name — multiple goblins, say, can each have their own
	 * marker at once. So the menu only cares whether the exact NPC under the cursor is the one a
	 * marker is currently following; every other same-named NPC still offers "Track Spawn".
	 */
	private void addTrackEntry(NPC npc, MenuEntry sourceEntry)
	{
		String name = npc.getName();
		if (name == null)
		{
			return;
		}

		SpawnMarker existing = findMarkerTracking(npc.getIndex());
		String option = existing == null ? "Track Spawn" : "Untrack Spawn";

		final NPC targetNpc = npc;
		client.getMenu().createMenuEntry(-1)
			.setOption(option)
			.setTarget(sourceEntry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				if (existing == null)
				{
					trackSpawn(targetNpc);
				}
				else
				{
					unmarkSpawn(existing);
				}
			});
	}

	private void addUnmarkEntry(MenuEntry sourceEntry)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		WorldPoint location = WorldPoint.fromScene(worldView, sourceEntry.getParam0(),
			sourceEntry.getParam1(), worldView.getPlane());
		SpawnMarker existing = findMarkerAt(location);
		if (existing == null)
		{
			return;
		}

		client.getMenu().createMenuEntry(-1)
			.setOption("Unmark Spawn Tile")
			.setTarget("<col=ffff00>" + existing.npcName + "</col>")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> unmarkSpawn(existing));
	}

	private void trackSpawn(NPC npc)
	{
		String name = npc.getName();
		if (name == null)
		{
			return;
		}

		SpawnMarker marker = new SpawnMarker(name);
		marker.markPresent(npc.getIndex());
		markers.add(marker);
		saveMarkers();

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Tracking " + name
			+ " — its spawn tile will show once it's died and come back.", null);
	}

	private void unmarkSpawn(SpawnMarker marker)
	{
		markers.remove(marker);
		saveMarkers();
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Stopped tracking "
			+ marker.npcName + "'s spawn.", null);
	}

	private SpawnMarker findMarkerTracking(int npcIndex)
	{
		for (SpawnMarker marker : markers)
		{
			if (marker.trackedNpcIndex == npcIndex)
			{
				return marker;
			}
		}
		return null;
	}

	private SpawnMarker findMarkerAt(WorldPoint point)
	{
		if (point == null)
		{
			return null;
		}
		for (SpawnMarker marker : markers)
		{
			if (marker.tileConfirmed && marker.worldPoint().equals(point))
			{
				return marker;
			}
		}
		return null;
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		for (SpawnMarker marker : markers)
		{
			if (marker.trackedNpcIndex != npc.getIndex())
			{
				continue;
			}

			WorldPoint despawnPoint = npc.getWorldLocation();
			Player local = client.getLocalPlayer();
			if (local == null || despawnPoint == null
				|| despawnPoint.distanceTo(local.getWorldLocation()) > VIEW_RANGE)
			{
				// Almost certainly left the loaded scene rather than actually died. Leave the
				// marker as "present" with its now-stale index — the overlay already tolerates
				// that (byIndex resolves to null and it just doesn't render), and NPC slot
				// indices are stable, so the same NPC coming back into range resolves again with
				// no reconnection logic needed. Treating this as a death instead was corrupting
				// timers: every wander-off started a bogus respawn race that a same-named
				// neighbour's real death and respawn would then win, in a goblin camp especially.
				debugLog(marker.npcName + " (index " + npc.getIndex() + ") left the scene" +
					(despawnPoint == null ? "" : " at " + despawnPoint)
					+ " — not treating it as a death");
				return;
			}

			marker.markDead(npc.getIndex(), despawnPoint, client.getTickCount());
			debugLog(marker.npcName + " (index " + npc.getIndex() + ") died at " + despawnPoint);
			return;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		String name = npc.getName();
		if (name == null)
		{
			return;
		}
		WorldPoint spawnLocation = npc.getWorldLocation();

		// Index reuse is unambiguous — OSRS very often hands a respawn the exact same slot the
		// original had, so if any waiting marker's dead NPC had this index, nothing else can be a
		// better match regardless of distance.
		for (SpawnMarker marker : markers)
		{
			if (marker.waiting && marker.npcName.equals(name) && npc.getIndex() == marker.diedIndex)
			{
				confirm(marker, npc, spawnLocation, "index " + npc.getIndex() + " reused");
				return;
			}
		}

		// Otherwise, the closest candidate wins rather than the first one found in list order —
		// with several same-named markers waiting at once (a goblin camp, say), first-found could
		// just as easily be a neighbouring, unrelated spawn point as the right one.
		SpawnMarker best = null;
		int bestDistance = Integer.MAX_VALUE;
		String bestReason = null;
		for (SpawnMarker marker : markers)
		{
			if (!marker.waiting || !marker.npcName.equals(name) || !marker.tileConfirmed)
			{
				continue;
			}
			int distance = spawnLocation.distanceTo(marker.worldPoint());
			if (distance <= CONFIRMED_TOLERANCE && distance < bestDistance)
			{
				best = marker;
				bestDistance = distance;
				bestReason = "confirmed tile, " + distance + " tile(s) away";
			}
		}
		if (best == null)
		{
			for (SpawnMarker marker : markers)
			{
				if (!marker.waiting || !marker.npcName.equals(name) || marker.tileConfirmed
					|| marker.deathLocation == null)
				{
					continue;
				}
				int distance = spawnLocation.distanceTo(marker.deathLocation);
				if (distance <= SEARCH_RADIUS && distance < bestDistance)
				{
					best = marker;
					bestDistance = distance;
					bestReason = "nearest to death location, " + distance + " tile(s) away";
				}
			}
		}

		if (best != null)
		{
			confirm(best, npc, spawnLocation, bestReason);
		}
		else
		{
			debugLog(name + " spawned at " + spawnLocation
				+ " — no waiting marker matched it closely enough");
		}
	}

	private void confirm(SpawnMarker marker, NPC npc, WorldPoint spawnLocation, String reason)
	{
		int ticksElapsed = client.getTickCount() - marker.diedOnTick;
		marker.confirmTile(spawnLocation, npc.getIndex(), ticksElapsed);
		saveMarkers();
		debugLog(marker.npcName + " respawn confirmed via " + reason + " at " + spawnLocation
			+ " (" + ticksElapsed + " tick(s))");
	}

	private void debugLog(String message)
	{
		log.debug(message);
		if (config.debugLogging())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Spawn Timer] " + message, null);
		}
	}

	private static NPC findNpcAt(WorldView worldView, String name, WorldPoint point)
	{
		for (NPC npc : worldView.npcs())
		{
			if (name.equals(npc.getName())
				&& point.distanceTo(npc.getWorldLocation()) <= CONFIRMED_TOLERANCE)
			{
				return npc;
			}
		}
		return null;
	}

	/** First same-named NPC not already claimed by another marker, for re-seeding on restart. */
	private static NPC findUnclaimedNpcNamed(WorldView worldView, String name, List<SpawnMarker> markers)
	{
		for (NPC npc : worldView.npcs())
		{
			if (!name.equals(npc.getName()))
			{
				continue;
			}
			boolean claimed = false;
			for (SpawnMarker marker : markers)
			{
				if (marker.trackedNpcIndex == npc.getIndex())
				{
					claimed = true;
					break;
				}
			}
			if (!claimed)
			{
				return npc;
			}
		}
		return null;
	}
}
