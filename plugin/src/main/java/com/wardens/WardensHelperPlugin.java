package com.wardens;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Highlights the tiles that falling boulders and enrage lightning are about to hit in the
 * Tombs of Amascut Wardens fight.
 *
 * <p>Local plugin only. Marking where a boss attack will land is on the Plugin Hub's forbidden
 * list, so this is never going to be submittable.
 */
@Slf4j
@PluginDescriptor(
	name = "Wardens Helper",
	description = "Highlights the tiles falling boulders and lightning will hit in the ToA Wardens fight.",
	tags = {"toa", "tombs", "amascut", "wardens", "boulder", "lightning", "raid"}
)
public class WardensHelperPlugin extends Plugin
{
	/**
	 * Ba-Ba's falling rock graphic, at each speed. The boulder that drops onto the player's own
	 * tile, in her room and from her phantom in phase 3, may instead be thrown as a projectile;
	 * those are caught by who threw them, in {@link #isHighlighted(Projectile)}, so they do not
	 * depend on this list.
	 */
	static final Set<Integer> BOULDER_IDS = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList(
			SpotanimID.TOA_BABA_ROCK_FALL,
			SpotanimID.TOA_BABA_ROCK_FALL_FASTER,
			SpotanimID.TOA_BABA_ROCK_FALL_FASTEST)));

	/**
	 * The frame of the rock fall animation the boulder lands on. All three speeds share the same
	 * 70 frames and only the first 53 (the fall) are sped up; the last 17 are the rubble settling,
	 * about a second after the hit, so the tile stops being a threat here, not when the graphic ends.
	 */
	static final int BOULDER_IMPACT_FRAME = 53;

	/** The growing shadow under a lightning bolt in the enrage, before it strikes. */
	static final Set<Integer> LIGHTNING_IDS = Collections.singleton(SpotanimID.WARDENS_LIGHTNING_WARN);

	/** Ba-Ba herself, so the id logging also runs in her room, which is far quicker to test in. */
	private static final Set<Integer> BABA_NPC_IDS = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList(NpcID.TOA_BABA, NpcID.TOA_BABA_COFFIN, NpcID.TOA_BABA_DIGGING)));

	/** Anything that means we are in phase 3 or the enrage: either Warden, or a phantom. */
	private static final Set<Integer> PHASE3_NPC_IDS = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList(
			NpcID.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE3,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE3,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING,
			NpcID.TOA_WARDENS_ZEBAK,
			NpcID.TOA_WARDENS_BABA,
			NpcID.TOA_WARDENS_KEPHRI,
			NpcID.TOA_WARDENS_AKKHA)));

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WardensHelperConfig config;

	@Inject
	private WardensHelperOverlay overlay;

	/**
	 * Whether a P3 Warden, a phantom or Ba-Ba is in the scene. Recomputed every tick, for the
	 * same reason as the Kalphite plugin: it cannot drift.
	 */
	@Getter
	private boolean inFight;

	/** Ids already written to the log this fight, so each appears once rather than every tick. */
	private final Set<Integer> loggedIds = new HashSet<>();

	@Provides
	WardensHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WardensHelperConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		inFight = false;
		loggedIds.clear();
		log.info("Wardens Helper started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		inFight = false;
		loggedIds.clear();
		log.info("Wardens Helper stopped");
	}

	boolean isHighlighted(int graphicId)
	{
		return (config.highlightBoulders() && BOULDER_IDS.contains(graphicId))
			|| (config.highlightLightning() && LIGHTNING_IDS.contains(graphicId));
	}

	/** Whether a graphic on a tile is still a threat: a highlighted id, and not yet landed. */
	boolean isHighlighted(GraphicsObject graphic)
	{
		int id = graphic.getId();
		return !graphic.finished() && isHighlighted(id)
			&& !(BOULDER_IDS.contains(id) && graphic.getAnimationFrame() >= BOULDER_IMPACT_FRAME);
	}

	/**
	 * Anything Ba-Ba or her phantom throws is a boulder, whatever graphic it happens to use. It is
	 * the same attack in both places, so testing in her room covers phase 3 as well.
	 */
	boolean isHighlighted(Projectile projectile)
	{
		return isHighlighted(projectile.getId())
			|| (config.highlightBoulders() && isBaba(projectile.getSourceActor()));
	}

	private static boolean isBaba(Actor actor)
	{
		if (!(actor instanceof NPC))
		{
			return false;
		}
		int id = ((NPC) actor).getId();
		return id == NpcID.TOA_WARDENS_BABA || BABA_NPC_IDS.contains(id);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean wasInFight = inFight;
		inFight = findFightNpc();
		if (wasInFight && !inFight)
		{
			loggedIds.clear();
		}
	}

	private boolean findFightNpc()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return false;
		}

		for (NPC npc : worldView.npcs())
		{
			if (PHASE3_NPC_IDS.contains(npc.getId()) || BABA_NPC_IDS.contains(npc.getId()))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		int id = event.getGraphicsObject().getId();
		logId("graphic", id, "", isHighlighted(id));
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		Actor source = projectile.getSourceActor();
		String from = source instanceof NPC ? " from npc " + ((NPC) source).getId() : "";
		logId("projectile", projectile.getId(), from, isHighlighted(projectile));
	}

	private void logId(String kind, int id, String from, boolean highlighted)
	{
		if (inFight && config.logGraphicIds() && loggedIds.add(id))
		{
			log.info("Wardens Helper: saw {} id {}{}{}", kind, id, from,
				highlighted ? " (highlighted)" : "");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN
			|| state == GameState.CONNECTION_LOST)
		{
			inFight = false;
			loggedIds.clear();
		}
	}
}
