package com.osrsmcp.kalphite;

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
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Counts down the gap between flinch attacks on the Kalphite Queen.
 *
 * <p>Local plugin only. Flinch timing helpers are on the Plugin Hub's forbidden list, so this is
 * never going to be submittable; see AGENTS.md under Boss &amp; Combat Restrictions.
 */
@Slf4j
@PluginDescriptor(
	name = "Kalphite Flinch",
	description = "Counts down the ticks until you can flinch the Kalphite Queen again.",
	tags = {"kalphite", "queen", "kq", "flinch", "timer", "boss"}
)
public class KalphiteFlinchPlugin extends Plugin
{
	/**
	 * Both phases, plus the clan cup and Leagues variants. She changes id when she rears up, so
	 * matching only the crawling form would drop the timer half way through the fight.
	 */
	private static final Set<Integer> QUEEN_IDS = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList(
			NpcID.KALPHITE_QUEEN,
			NpcID.KALPHITE_FLYINGQUEEN,
			NpcID.CLANCUP_KALPHITE_QUEEN,
			NpcID.CLANCUP_KALPHITE_FLYINGQUEEN,
			NpcID.LEAGUE_6_KALPHITE_QUEEN)));

	/**
	 * Fallback identification. Ids are exact but go stale the moment Jagex adds a variant, and a
	 * silently empty id set means the whole plugin does nothing with no error to show for it.
	 */
	private static final String QUEEN_NAME = "Kalphite Queen";

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KalphiteFlinchConfig config;

	@Inject
	private KalphiteFlinchOverlay overlay;

	@Getter
	private final FlinchTimer timer = new FlinchTimer();

	/**
	 * Whether a queen is in the scene, recomputed once per game tick.
	 *
	 * <p>Derived rather than accumulated from spawn and despawn events. Event-driven tracking has
	 * to be seeded for anything already present when the plugin starts, and that seeding is
	 * exactly what failed before: enable the plugin while standing in her lair and no spawn event
	 * ever arrives, so the first kill goes untimed and only the respawn fixes it. Re-deriving each
	 * tick cannot drift, and the npc list is small enough that walking it is free next to the
	 * per-frame work the overlay already does.
	 */
	private boolean queenInScene;

	@Provides
	KalphiteFlinchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KalphiteFlinchConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		timer.reset();
		queenInScene = false;
		log.info("Kalphite Flinch started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		timer.reset();
		queenInScene = false;
		log.info("Kalphite Flinch stopped");
	}

	/** Whether the overlay should be drawing at all. */
	boolean isActive()
	{
		return !config.onlyNearQueen() || queenInScene;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean wasPresent = queenInScene;
		queenInScene = findQueenInScene();

		if (wasPresent && !queenInScene)
		{
			// She died or we left. A countdown left running would greet us on the next kill.
			timer.reset();
			return;
		}

		timer.tick();
	}

	/** Client thread only, which every game tick is. */
	private boolean findQueenInScene()
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
			if (isQueen(npc))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The first of the two attack signals. Fires on the tick you swing, which is what the flinch
	 * interval is measured from, and works for every weapon without a list of attack animations
	 * to keep up to date.
	 *
	 * <p>Anything that animates you while already counting down — eating, a potion — is ignored,
	 * because {@link FlinchTimer#start} refuses to restart a running countdown.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!isActive() || event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player.getAnimation() == -1 || !isFightingQueen(player.getInteracting()))
		{
			return;
		}

		startCountdown("animation " + player.getAnimation());
	}

	/**
	 * The second signal, as a safety net. Covers the case where the animation is missed — the
	 * same attack animation replayed back to back does not always raise an event.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!isActive() || !event.getHitsplat().isMine() || !isQueen(event.getActor()))
		{
			return;
		}
		startCountdown("hitsplat");
	}

	private void startCountdown(String source)
	{
		if (timer.start(config.flinchTicks()))
		{
			log.debug("Flinch countdown started from {} ({} ticks)", source, config.flinchTicks());
		}
	}

	/**
	 * When "only near the Queen" is off the timer follows whatever you are attacking, so any live
	 * target counts.
	 */
	private boolean isFightingQueen(Actor target)
	{
		return target != null && (!config.onlyNearQueen() || isQueen(target));
	}

	private static boolean isQueen(Actor actor)
	{
		if (!(actor instanceof NPC))
		{
			return false;
		}
		NPC npc = (NPC) actor;
		return QUEEN_IDS.contains(npc.getId()) || QUEEN_NAME.equals(npc.getName());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.HOPPING
			|| state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
		{
			timer.reset();
			queenInScene = false;
		}
	}
}
