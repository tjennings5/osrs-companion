package com.doom;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.StringJoiner;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Doom of Mokhaiotl helper. Currently recording-only: while a Doom trip is in
 * progress it writes every relevant game event to a log file (see
 * {@link DoomRecorder}) so the prayer, beam-charge and car-phase helpers can
 * be built from real timings rather than wiki estimates.
 *
 * Display-only, like the other helpers here: it reads game state and never
 * sends input or acts for the player.
 *
 * Never submitted to the Plugin Hub; loaded via the dev-client workflow
 * alongside the OSRS MCP Bridge plugin.
 */
@Slf4j
@PluginDescriptor(
	name = "Doom Helper",
	description = "Doom of Mokhaiotl helper - currently records fight events for building timings",
	tags = {"doom", "mokhaiotl", "delve", "boss", "varlamore", "prayer"}
)
public class DoomHelperPlugin extends Plugin
{
	/**
	 * How long the recording stays open after the player leaves the arena
	 * region. Long enough to cover the between-delve prompt and a quick bank
	 * trip back, short enough that walking away ends the file.
	 */
	private static final int LEAVE_GRACE_TICKS = 200;

	/** Chat types that are the game talking; player and clan chat are never recorded. */
	private static final Set<ChatMessageType> GAME_CHAT = EnumSet.of(
		ChatMessageType.GAMEMESSAGE,
		ChatMessageType.SPAM,
		ChatMessageType.ENGINE,
		ChatMessageType.MESBOX,
		ChatMessageType.DIALOG);

	@Inject
	private Client client;

	@Inject
	private DoomHelperConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DoomHelperOverlay overlay;

	private final DoomRecorder recorder = new DoomRecorder();

	/** Template (non-instance) region the fight was first seen in; presence there keeps the recording open. */
	private int arenaRegion = -1;
	private int lastInArenaTick;

	/** Scene loads dump every object in view as a spawn; those are noise, not fight events. */
	private boolean sceneLoading;

	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());

	private String lastPlayerPos;
	private String lastPrayers;
	private String lastBossState;

	@Provides
	DoomHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DoomHelperConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		stopRecording("plugin stopped");
	}

	boolean isRecording()
	{
		return recorder.isOpen();
	}

	int getRecordedLines()
	{
		return recorder.getLineCount();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (DoomHelperConfig.GROUP.equals(event.getGroup()) && !config.recordFights())
		{
			stopRecording("recording turned off");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		sceneLoading = state == GameState.LOADING;
		if (recorder.isOpen())
		{
			rec("GAMESTATE " + state);
		}
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			stopRecording("logged out");
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		Player me = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (me == null || wv == null)
		{
			return;
		}

		NPC boss = null;
		boolean fightNpcPresent = false;
		for (NPC npc : wv.npcs())
		{
			if (DoomIds.FIGHT_NPCS.contains(npc.getId()))
			{
				fightNpcPresent = true;
				if (DoomIds.BOSS_FORMS.contains(npc.getId()))
				{
					boss = npc;
				}
			}
		}

		WorldPoint myTemplatePos = templatePoint(me.getLocalLocation());
		int myRegion = myTemplatePos == null ? -1 : myTemplatePos.getRegionID();

		if (!recorder.isOpen())
		{
			if (!fightNpcPresent || !config.recordFights())
			{
				return;
			}
			startRecording(me, myRegion);
		}

		if (fightNpcPresent || myRegion == arenaRegion)
		{
			lastInArenaTick = tick;
		}
		else if (tick - lastInArenaTick > LEAVE_GRACE_TICKS)
		{
			stopRecording("left the arena");
			return;
		}

		recordPlayerState(me, myTemplatePos);
		recordBossState(boss);
		recordNewProjectiles(me);
		rec("TICK");
		recorder.flush();
	}

	private void startRecording(Player me, int region)
	{
		arenaRegion = region;
		lastInArenaTick = client.getTickCount();
		seenProjectiles.clear();
		lastPlayerPos = null;
		lastPrayers = null;
		lastBossState = null;

		recorder.open(String.format("Doom recording - player=%s region=%d instance=%s",
			me.getName(), region, client.getTopLevelWorldView().isInstance()));
		rec("WEAPON " + describeWeapon());
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (DoomIds.FIGHT_NPCS.contains(npc.getId()))
			{
				rec("NPC_PRESENT " + describeNpc(npc));
			}
		}
	}

	private void stopRecording(String reason)
	{
		if (!recorder.isOpen())
		{
			return;
		}
		rec("STOP reason=" + reason);
		recorder.close();
		arenaRegion = -1;
		seenProjectiles.clear();
	}

	// ---- per-tick state snapshots (logged only when they change) ----

	private void recordPlayerState(Player me, WorldPoint pos)
	{
		String posText = pos == null ? "?" : pos.getX() + "," + pos.getY() + "," + pos.getPlane();
		if (!posText.equals(lastPlayerPos))
		{
			rec("PLAYER_POS at=" + posText);
			lastPlayerPos = posText;
		}

		StringJoiner prayers = new StringJoiner("+");
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
		{
			prayers.add("MAGE");
		}
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
		{
			prayers.add("RANGE");
		}
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))
		{
			prayers.add("MELEE");
		}
		String prayerText = prayers.length() == 0 ? "NONE" : prayers.toString();
		if (!prayerText.equals(lastPrayers))
		{
			rec("PRAYER " + prayerText);
			lastPrayers = prayerText;
		}
	}

	private void recordBossState(NPC boss)
	{
		String state = boss == null ? "absent"
			: String.format("form=%s hp=%d/%d at=%s", DoomIds.NPC_NAMES.get(boss.getId()),
				boss.getHealthRatio(), boss.getHealthScale(), loc(boss.getLocalLocation()));
		if (!state.equals(lastBossState))
		{
			rec("BOSS " + state);
			lastBossState = state;
		}
	}

	private void recordNewProjectiles(Player me)
	{
		Set<Projectile> live = new HashSet<>();
		int now = client.getGameCycle();
		for (Projectile p : client.getProjectiles())
		{
			live.add(p);
			if (!seenProjectiles.add(p))
			{
				continue;
			}
			Actor source = p.getSourceActor();
			Actor target = p.getTargetActor();
			rec(String.format("PROJ id=%d name=%s src=%s tgt=%s from=%s to=%s cycles=%d..%d now=%d land_in=%.1ft",
				p.getId(), orDash(DoomIds.spotanimName(p.getId())),
				describeActor(source, me), describeActor(target, me),
				worldLoc(p.getSourcePoint()), worldLoc(p.getTargetPoint()),
				p.getStartCycle(), p.getEndCycle(), now, (p.getEndCycle() - now) / 30.0));
		}
		seenProjectiles.retainAll(live);
	}

	// ---- event subscribers ----

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (recorder.isOpen())
		{
			rec("NPC_SPAWN " + describeNpc(event.getNpc()));
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (recorder.isOpen())
		{
			rec("NPC_DESPAWN " + describeNpc(event.getNpc()));
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (recorder.isOpen())
		{
			rec("NPC_CHANGE old=" + event.getOld().getId() + " " + describeNpc(event.getNpc()));
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!recorder.isOpen())
		{
			return;
		}
		Actor actor = event.getActor();
		int anim = actor.getAnimation();
		if (actor == client.getLocalPlayer())
		{
			rec("ANIM who=me id=" + anim);
		}
		else if (actor instanceof NPC && DoomIds.FIGHT_NPCS.contains(((NPC) actor).getId()))
		{
			rec(String.format("ANIM who=%s id=%d name=%s", describeActor(actor, null), anim,
				orDash(DoomIds.ANIMATION_NAMES.get(anim))));
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		if (!recorder.isOpen())
		{
			return;
		}
		Actor actor = event.getActor();
		boolean mine = actor == client.getLocalPlayer();
		if (!mine && !(actor instanceof NPC && DoomIds.FIGHT_NPCS.contains(((NPC) actor).getId())))
		{
			return;
		}
		StringJoiner ids = new StringJoiner(",");
		for (ActorSpotAnim spot : actor.getSpotAnims())
		{
			String name = DoomIds.spotanimName(spot.getId());
			ids.add(name == null ? Integer.toString(spot.getId()) : spot.getId() + ":" + name);
		}
		rec("SPOTANIM who=" + (mine ? "me" : describeActor(actor, null)) + " ids=" + ids);
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (recorder.isOpen())
		{
			int id = event.getGraphicsObject().getId();
			rec(String.format("GFX id=%d name=%s at=%s", id, orDash(DoomIds.spotanimName(id)),
				loc(event.getGraphicsObject().getLocation())));
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (recorder.isOpen())
		{
			rec(String.format("HIT on=%s amount=%d type=%d mine=%s",
				describeActor(event.getActor(), client.getLocalPlayer()),
				event.getHitsplat().getAmount(), event.getHitsplat().getHitsplatType(), event.getHitsplat().isMine()));
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (recorder.isOpen() && event.getSource() == client.getLocalPlayer())
		{
			rec("TARGET " + describeActor(event.getTarget(), client.getLocalPlayer()));
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (recorder.isOpen() && event.getContainerId() == InventoryID.WORN)
		{
			rec("WEAPON " + describeWeapon());
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!recorder.isOpen())
		{
			return;
		}
		if (event.getVarbitId() != -1)
		{
			rec("VARBIT id=" + event.getVarbitId() + " value=" + event.getValue());
		}
		else
		{
			rec("VARP id=" + event.getVarpId() + " value=" + event.getValue());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (recorder.isOpen() && GAME_CHAT.contains(event.getType()))
		{
			rec("CHAT type=" + event.getType() + " msg=" + event.getMessage().replace('\n', ' '));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (recorder.isOpen())
		{
			rec("WIDGET_OPEN group=" + event.getGroupId());
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (recorder.isOpen())
		{
			rec("WIDGET_CLOSE group=" + event.getGroupId());
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		recObject("OBJ_SPAWN", "game", event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		recObject("OBJ_DESPAWN", "game", event.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		recObject("OBJ_SPAWN", "ground", event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		recObject("OBJ_DESPAWN", "ground", event.getGroundObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		recObject("OBJ_SPAWN", "wall", event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		recObject("OBJ_DESPAWN", "wall", event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		recObject("OBJ_SPAWN", "decor", event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		recObject("OBJ_DESPAWN", "decor", event.getDecorativeObject());
	}

	private void recObject(String kind, String layer, TileObject object)
	{
		if (recorder.isOpen() && !sceneLoading)
		{
			rec(String.format("%s layer=%s id=%d at=%s", kind, layer, object.getId(), loc(object.getLocalLocation())));
		}
	}

	// ---- formatting helpers ----

	private void rec(String line)
	{
		recorder.write(client.getTickCount(), line);
	}

	/**
	 * Converts a scene position to its template (non-instanced) world point.
	 * The arena is an instance, so raw world coordinates change every visit;
	 * template coordinates are stable and can be compared across recordings.
	 */
	private WorldPoint templatePoint(LocalPoint lp)
	{
		return lp == null ? null : WorldPoint.fromLocalInstance(client, lp);
	}

	private String loc(LocalPoint lp)
	{
		WorldPoint wp = templatePoint(lp);
		return wp == null ? "?" : wp.getX() + "," + wp.getY() + "," + wp.getPlane();
	}

	private String worldLoc(WorldPoint wp)
	{
		return wp == null ? "?" : loc(LocalPoint.fromWorld(client.getTopLevelWorldView(), wp));
	}

	private String describeNpc(NPC npc)
	{
		String name = DoomIds.NPC_NAMES.get(npc.getId());
		return String.format("id=%d name=%s idx=%d at=%s", npc.getId(),
			name != null ? name : npc.getName(), npc.getIndex(), loc(npc.getLocalLocation()));
	}

	private String describeActor(Actor actor, Player me)
	{
		if (actor == null)
		{
			return "-";
		}
		if (actor == me || actor == client.getLocalPlayer())
		{
			return "me";
		}
		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			String name = DoomIds.NPC_NAMES.get(npc.getId());
			return (name != null ? name : "npc" + npc.getId()) + "#" + npc.getIndex();
		}
		return "player";
	}

	private String describeWeapon()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item weapon = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null || weapon.getId() <= 0)
		{
			return "id=-1 name=none";
		}
		return "id=" + weapon.getId() + " name=" + client.getItemDefinition(weapon.getId()).getName().replace(' ', '_');
	}

	private static String orDash(String s)
	{
		return s == null ? "-" : s;
	}
}
