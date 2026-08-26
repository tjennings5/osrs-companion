package com.araxxor;

import com.google.inject.Provides;
import com.combat.AttackClock;
import com.combat.HealthBar;
import com.combat.XpDamage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Helper for Araxxor, built around the fact that most of this fight is knowable
 * in advance rather than reacted to.
 *
 * Three things drive it:
 * <ul>
 *   <li><b>The special is fixed per fight.</b> Which of the three specials he
 *       uses is decided by the south-easternmost egg and never changes, so it
 *       can be announced before he attacks once.</li>
 *   <li><b>Eggs hatch on a counter.</b> The first after 3 standard attacks, then
 *       every 6, clockwise from that same south-eastern egg — so the next
 *       araxyte and when it arrives are both predictable.</li>
 *   <li><b>Enrage is a fixed threshold.</b> At 255 hitpoints his attack speed
 *       goes 6 ticks to 4 and melee becomes the dodgeable cleave.</li>
 * </ul>
 *
 * <p>Attack counting takes animations at face value here, unlike the Cerberus
 * helper which cannot: she plays a DEFEND animation that masks attacks, and
 * Araxxor has no such animation in the game's data. The tick clock still runs
 * alongside, both to drive the enrage speed change and so the logs show whether
 * the two ever disagree — if they do, the clock is the fallback.
 */
@Slf4j
@PluginDescriptor(
	name = "Araxxor Helper",
	description = "Egg cycle, fight special, enrage and cleave cues for Araxxor",
	tags = {"araxxor", "araxyte", "boss", "slayer"}
)
public class AraxxorHelperPlugin extends Plugin
{
	private static final int MAX_HP = 1020;

	/** He enrages below this; the wiki is explicit and it does not vary. */
	private static final int ENRAGE_HP = 255;

	private static final int NORMAL_SPEED_TICKS = 6;
	private static final int ENRAGE_SPEED_TICKS = 4;

	/** Standard attacks between specials. The phase is learned from the first one seen. */
	private static final int SPECIAL_PERIOD = 6;

	/**
	 * No published xp bonus for Araxxor, so start at the plain rate; XpDamage
	 * re-derives the real one from hitsplats within the first hundred damage.
	 */
	private static final double ARAXXOR_XP_MULTIPLIER = 1.0;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AraxxorHelperOverlay overlay;

	@Inject
	private AraxxorHelperConfig config;

	@Inject
	private AudioPlayer audioPlayer;

	private NPC araxxor;

	private AttackClock clock = new AttackClock(NORMAL_SPEED_TICKS, AttackClock.DEFAULT_TOLERANCE);

	private final XpDamage xpDamage = new XpDamage(ARAXXOR_XP_MULTIPLIER);

	private final Map<Skill, Integer> lastCombatXp = new EnumMap<>(Skill.class);

	/** Eggs seen this fight, used once to work out the hatch order. */
	private final List<AraxxorEggCycle.Egg> eggs = new ArrayList<>();

	@Getter
	private List<AraxxorMinion> hatchOrder = new ArrayList<>();

	/**
	 * Standard attacks only. Specials do not advance the egg cycle, which is why
	 * this is tracked separately from the clock's own count.
	 */
	@Getter
	private int standardAttacks;

	@Getter
	private AraxxorSpecial fightSpecial = AraxxorSpecial.UNKNOWN;

	@Getter
	private boolean enraged;

	@Getter
	private int lastKnownHp = MAX_HP;

	private boolean hpExact;

	@Getter
	private AraxxorMinion activeMinion;

	/** Which hatch we have already spoken for, so a cue fires once per egg. */
	private int warnedHatchIndex = -1;

	private boolean enrageWarned;

	/** Standard-attack number a special was observed on, or -1 until one is seen. */
	private int lastSpecialAttack = -1;

	@Provides
	AraxxorHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AraxxorHelperConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		resetFight("plugin started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		resetFight("plugin stopped");
	}

	public boolean isFightActive()
	{
		return araxxor != null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGING_IN
			|| event.getGameState() == GameState.HOPPING)
		{
			resetFight("game state " + event.getGameState());
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		int id = npc.getId();

		if (id == NpcID.ARAXXOR)
		{
			araxxor = npc;
			resetFight("Araxxor spawned");
			araxxor = npc;
			log.debug("ARAX fight start");
			return;
		}

		if (AraxxorMinion.isEgg(id))
		{
			WorldPoint p = npc.getWorldLocation();
			if (p != null)
			{
				eggs.add(new AraxxorEggCycle.Egg(p.getX(), p.getY(), AraxxorMinion.byEggId(id)));
				readEggsIfComplete();
			}
			return;
		}

		AraxxorMinion minion = AraxxorMinion.byMinionId(id);
		if (minion != null)
		{
			onMinionHatched(minion);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		int id = event.getNpc().getId();
		if (id == NpcID.ARAXXOR || id == NpcID.ARAXXOR_DEAD)
		{
			resetFight("Araxxor despawned");
			return;
		}
		AraxxorMinion minion = AraxxorMinion.byMinionId(id);
		if (minion != null && minion == activeMinion)
		{
			activeMinion = null;
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (araxxor == null || event.getActor() != araxxor)
		{
			return;
		}

		int animation = araxxor.getAnimation();
		int tick = client.getTickCount();

		if (config.verboseLogging())
		{
			log.debug("ARAX anim={} ({}) tick={} std={} hp={} enraged={}",
				animation, animationName(animation), tick, standardAttacks, lastKnownHp, enraged);
		}

		if (isEnrageTransition(animation))
		{
			onEnrage(tick);
			return;
		}

		AraxxorSpecial special = specialFor(animation);
		if (special != null)
		{
			onSpecialObserved(special);
			return;
		}

		if (isStandardAttack(animation))
		{
			onStandardAttack(tick);
		}
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		if (araxxor == null || event.getActor() != araxxor)
		{
			return;
		}

		// "Skree!" is the cleave tell. Hooking the overhead text rather than an
		// animation matters: the shout is the same signal a player reacts to, and
		// it arrives without waiting for the animation to be assigned.
		String text = event.getOverheadText();
		if (text != null && text.toLowerCase().contains("skree"))
		{
			log.debug("ARAX cleave tell at tick {}", client.getTickCount());
			if (config.warnCleave())
			{
				playCue("dodge.wav");
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (araxxor == null || event.getActor() != araxxor)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine())
		{
			return;
		}

		int damage = hitsplat.getAmount();
		lastKnownHp = Math.max(0, lastKnownHp - damage);
		xpDamage.addConfirmedDamage(damage);
		maybeWarnEnrage();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (araxxor == null)
		{
			return;
		}

		Skill skill = event.getSkill();
		if (!isCombatSkill(skill))
		{
			return;
		}

		Integer previous = lastCombatXp.put(skill, event.getXp());
		if (previous == null)
		{
			return;
		}

		// Ranged rolls damage and awards xp at launch, so this runs ahead of the
		// hitsplat and buys back the travel time on the enrage warning.
		xpDamage.addXp(event.getXp() - previous);
		maybeWarnEnrage();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (araxxor == null)
		{
			return;
		}

		clock.onGameTick(client.getTickCount());
		reconcileWithHealthBar();
		maybeWarnEnrage();
	}

	/** Health the xp says he is on, which may be ahead of the visible hitsplats. */
	public int getPredictedHp()
	{
		return Math.max(0, lastKnownHp - xpDamage.getInFlightDamage());
	}

	/** Standard attacks until the next egg hatches. */
	public int getAttacksUntilHatch()
	{
		return AraxxorEggCycle.attacksUntilNextHatch(standardAttacks);
	}

	/** The araxyte due out of the next egg, or null if the eggs were never read. */
	public AraxxorMinion getNextMinion()
	{
		return AraxxorEggCycle.typeAt(hatchOrder, AraxxorEggCycle.hatchesSoFar(standardAttacks));
	}

	/** Standard attacks until the next special, or -1 until one has been observed. */
	public int getAttacksUntilSpecial()
	{
		if (lastSpecialAttack < 0)
		{
			return -1;
		}
		int since = standardAttacks - lastSpecialAttack;
		int remaining = SPECIAL_PERIOD - (since % SPECIAL_PERIOD);
		return remaining == 0 ? SPECIAL_PERIOD : remaining;
	}

	private void onStandardAttack(int tick)
	{
		AttackClock.Event result = clock.onAttackAnimation(tick);
		if (result == AttackClock.Event.SUB_ATTACK)
		{
			// Araxxor has no combo attack, so this means the animation landed off
			// the expected grid. Worth logging - it is the signal that would justify
			// switching to clock-driven counting like Cerberus needed.
			log.debug("ARAX off-schedule attack animation at tick {} (std={})", tick, standardAttacks);
		}

		standardAttacks++;

		if (config.verboseLogging())
		{
			log.debug("ARAX standard attack #{} tick={} clockCount={} nextHatchIn={}",
				standardAttacks, tick, clock.getAttackCount(), getAttacksUntilHatch());
		}

		maybeWarnHatch();
	}

	private void onSpecialObserved(AraxxorSpecial special)
	{
		lastSpecialAttack = standardAttacks;

		if (fightSpecial == AraxxorSpecial.UNKNOWN)
		{
			fightSpecial = special;
			log.debug("ARAX special identified from animation: {}", special);
		}
		else if (fightSpecial != special)
		{
			// The eggs said one thing and he did another. Never expected - log it
			// loudly rather than quietly trusting the wrong one.
			log.warn("ARAX special mismatch: eggs predicted {} but observed {}", fightSpecial, special);
			fightSpecial = special;
		}
	}

	private void onEnrage(int tick)
	{
		if (enraged)
		{
			return;
		}
		enraged = true;
		clock.setAttackSpeed(ENRAGE_SPEED_TICKS, tick);
		log.debug("ARAX enrage at tick {} hp~{}", tick, lastKnownHp);
		playCue("enrage.wav");
	}

	private void onMinionHatched(AraxxorMinion minion)
	{
		activeMinion = minion;
		log.debug("ARAX {} hatched at std={} (predicted {})",
			minion, standardAttacks, getNextMinion());

		if (config.minionAdvice())
		{
			playCue(minion.getSoundFile());
		}
	}

	/**
	 * Works out the hatch order once all nine eggs are known, and with it the
	 * special for the whole fight.
	 */
	private void readEggsIfComplete()
	{
		if (eggs.size() < AraxxorEggCycle.TOTAL_EGGS || !hatchOrder.isEmpty())
		{
			return;
		}

		hatchOrder = AraxxorEggCycle.hatchOrder(eggs);
		AraxxorMinion first = hatchOrder.isEmpty() ? null : hatchOrder.get(0);
		if (first != null)
		{
			fightSpecial = first.getSpecial();
			log.debug("ARAX eggs read: order={} special={}", hatchOrder, fightSpecial);
			if (config.announceSpecial())
			{
				playCue(fightSpecial.getSoundFile());
			}
		}
	}

	private void maybeWarnHatch()
	{
		if (!config.warnEggHatch())
		{
			return;
		}

		int index = AraxxorEggCycle.hatchesSoFar(standardAttacks);
		if (index == warnedHatchIndex)
		{
			return;
		}

		if (getAttacksUntilHatch() <= config.hatchLeadAttacks())
		{
			warnedHatchIndex = index;
			playCue("egg-soon.wav");
		}
	}

	private void maybeWarnEnrage()
	{
		if (enrageWarned || enraged || !config.warnEnrage())
		{
			return;
		}

		if (getPredictedHp() <= config.enrageWarnHp())
		{
			enrageWarned = true;
			playCue("enrage-soon.wav");
		}
	}

	/**
	 * Corrects tracked health against the health bar, in both directions.
	 *
	 * The bar only identifies a band, so a tracked value inside that band is left
	 * alone - it is more precise than anything the bar could supply.
	 */
	private void reconcileWithHealthBar()
	{
		int ratio = araxxor.getHealthRatio();
		int scale = araxxor.getHealthScale();
		if (ratio < 0 || scale <= 0)
		{
			return;
		}

		if (!hpExact)
		{
			lastKnownHp = HealthBar.estimate(ratio, scale, MAX_HP);
			hpExact = true;
			return;
		}

		int corrected = HealthBar.clampToBand(lastKnownHp, ratio, scale, MAX_HP);
		if (corrected != lastKnownHp)
		{
			log.debug("ARAX hp resync: tracked {} outside band [{}, {}] - correcting to {}",
				lastKnownHp, HealthBar.minHealth(ratio, scale, MAX_HP),
				HealthBar.maxHealth(ratio, scale, MAX_HP), corrected);
			lastKnownHp = corrected;
		}

		// Belt and braces: the animation is the primary enrage signal, but the
		// threshold is fixed, so health catches it if the animation is ever missed.
		if (!enraged && lastKnownHp > 0 && lastKnownHp <= ENRAGE_HP)
		{
			onEnrage(client.getTickCount());
		}
	}

	private void resetFight(String reason)
	{
		if (araxxor != null || standardAttacks != 0)
		{
			log.debug("Resetting Araxxor tracking: {}", reason);
		}
		araxxor = null;
		clock = new AttackClock(NORMAL_SPEED_TICKS, AttackClock.DEFAULT_TOLERANCE);
		xpDamage.reset();
		lastCombatXp.clear();
		eggs.clear();
		hatchOrder = new ArrayList<>();
		standardAttacks = 0;
		fightSpecial = AraxxorSpecial.UNKNOWN;
		enraged = false;
		enrageWarned = false;
		warnedHatchIndex = -1;
		lastSpecialAttack = -1;
		activeMinion = null;
		lastKnownHp = MAX_HP;
		hpExact = false;
	}

	private void playCue(String clip)
	{
		if (clip == null || !config.voiceEnabled())
		{
			return;
		}
		try
		{
			audioPlayer.play(AraxxorHelperPlugin.class, clip, config.voiceGain());
		}
		catch (Exception e)
		{
			log.warn("Could not play Araxxor cue {}", clip, e);
		}
	}

	static boolean isCombatSkill(Skill skill)
	{
		return skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE
			|| skill == Skill.RANGED || skill == Skill.MAGIC || skill == Skill.HITPOINTS;
	}

	static boolean isStandardAttack(int animation)
	{
		return animation == AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_01
			|| animation == AnimationID.NPC_ARAXXOR_01_ATTACK_RANGED_01
			|| animation == AnimationID.NPC_ARAXXOR_01_ATTACK_MAGIC_01
			|| animation == AnimationID.NPC_ARAXXOR_01_ATTACK_SLOW_MELEE_01
			|| animation == AnimationID.NPC_ARAXXOR_01_ATTACK_SLOW_RANGED_01
			|| animation == AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_ENRAGED_01;
	}

	static boolean isEnrageTransition(int animation)
	{
		return animation == AnimationID.NPC_ARAXXOR_01_ENRAGE_TRANSITION_01
			|| animation == AnimationID.NPC_ARAXXOR_01_ENRAGE_TRANSITION_02;
	}

	static AraxxorSpecial specialFor(int animation)
	{
		if (animation == AnimationID.NPC_ARAXXOR_01_ACID_CANNON_01)
		{
			return AraxxorSpecial.ACID_BALL;
		}
		if (animation == AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_SPRAY_01)
		{
			return AraxxorSpecial.ACID_SPLATTER;
		}
		if (animation == AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_LEAK_01)
		{
			return AraxxorSpecial.ACID_DRIP;
		}
		return null;
	}

	static String animationName(int animation)
	{
		if (isStandardAttack(animation))
		{
			return "standard-attack";
		}
		if (isEnrageTransition(animation))
		{
			return "enrage-transition";
		}
		AraxxorSpecial special = specialFor(animation);
		if (special != null)
		{
			return "special:" + special;
		}
		if (animation == AnimationID.NPC_ARAXXOR_01_IDLE_01)
		{
			return "idle";
		}
		if (animation == AnimationID.NPC_ARAXXOR_01_WALK_01
			|| animation == AnimationID.NPC_ARAXXOR_01_RUN_01)
		{
			return "move";
		}
		if (animation == AnimationID.NPC_ARAXXOR_01_DEATH_01
			|| animation == AnimationID.NPC_ARAXXOR_01_DEATH_LOOP_01
			|| animation == AnimationID.NPC_ARAXXOR_01_DEATH_LOOT_01)
		{
			return "death";
		}
		if (animation == AnimationID.NPC_ARAXXOR_01_SPAWN_01)
		{
			return "spawn";
		}
		return "other";
	}
}
