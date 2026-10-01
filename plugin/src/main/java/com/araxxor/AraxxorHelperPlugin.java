package com.araxxor;

import com.google.inject.Provides;
import com.combat.AttackClock;
import com.combat.FightRecorder;
import com.combat.HealthBar;
import com.combat.XpDamage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcChanged;
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

	/**
	 * A standard attack animation this soon after the last one can't be a new
	 * attack at either speed, so it's the same attack firing its event twice.
	 * Counting it would push every hatch cue an attack early.
	 */
	private static final int DUPLICATE_ATTACK_TICKS = 2;

	/**
	 * Eggs hatch on a clock. In recorded kills the first hatched 3 ticks after
	 * his 3rd standard attack, and each next one 42 ticks after the last
	 * (6 attacks and a special at 6 ticks each). Enrage doesn't reset it: the
	 * hatch due next still came 42 ticks on (6 of 7; the other 41), and the
	 * one after that 38 ticks later (4 of 4).
	 */
	private static final int FIRST_HATCH_AFTER_ATTACK_TICKS = 3;
	private static final int HATCH_INTERVAL_TICKS = 42;
	private static final int ENRAGED_HATCH_INTERVAL_TICKS = 38;

	/** Stop recording after this long with nothing from the fight in the scene. */
	private static final int RECORDING_IDLE_TICKS = 100;

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

	/** Eggs seen this fight by NPC index, used once to work out the hatch order. */
	private final Map<Integer, AraxxorEggCycle.Egg> eggs = new LinkedHashMap<>();

	private final FightRecorder recorder = new FightRecorder("araxxor-helper", "araxxor");

	/** Last tick anything from the fight was in the scene, for closing the recording. */
	private int lastFightTick = -1;

	private int lastHealthRatio = -1;

	private WorldPoint lastPlayerPos;

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

	/** Eggs hatched this fight. */
	private int hatchCount;

	private int lastHatchTick = -1;

	/** Whether the last hatch came after he enraged, which shortens the wait for the next. */
	private boolean lastHatchEnraged;

	private boolean enrageWarned;

	/** Standard-attack number a special was observed on, or -1 until one is seen. */
	private int lastSpecialAttack = -1;

	/** Tick of the last counted standard attack, or -1. */
	private int lastStandardAttackTick = -1;

	/** Whether the special has been announced this fight. */
	private boolean specialAnnounced;

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
		recorder.close();
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
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			recorder.close();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		int id = npc.getId();

		if (isFightNpc(id))
		{
			startRecording();
		}
		rec("NPC_SPAWN " + describe(npc));

		if (id == NpcID.ARAXXOR)
		{
			resetFight("Araxxor spawned");
			araxxor = npc;
			log.debug("ARAX fight start");
			// The eggs can be in the scene before he is, and the reset above
			// forgets them, so pick up whatever is already there.
			scanForEggs();
			return;
		}

		if (AraxxorMinion.isEgg(id))
		{
			addEgg(npc);
			return;
		}

		AraxxorMinion minion = AraxxorMinion.byMinionId(id);
		if (minion != null)
		{
			onMinionHatched(minion);
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC npc = event.getNpc();
		rec("NPC_CHANGE old=" + event.getOld().getId() + " " + describe(npc));
		// In case an egg turns into its araxyte rather than despawning.
		AraxxorMinion minion = AraxxorMinion.byMinionId(npc.getId());
		if (minion != null && AraxxorMinion.isEgg(event.getOld().getId()))
		{
			onMinionHatched(minion);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		int id = event.getNpc().getId();
		rec("NPC_DESPAWN " + describe(event.getNpc()));
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
		Actor actor = event.getActor();
		if (recorder.isOpen() && (actor == client.getLocalPlayer()
			|| (actor instanceof NPC && isFightNpc(((NPC) actor).getId()))))
		{
			rec("ANIM who=" + who(actor) + " id=" + actor.getAnimation()
				+ (actor == araxxor ? " name=" + animationName(actor.getAnimation()) : ""));
		}

		if (araxxor == null || actor != araxxor)
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
		if (special == null && isAcidBall(animation))
		{
			special = AraxxorSpecial.ACID_BALL;
		}
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
		rec("OVERHEAD " + text);
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
		Hitsplat hitsplat = event.getHitsplat();
		if (recorder.isOpen())
		{
			rec("HIT on=" + who(event.getActor()) + " amount=" + hitsplat.getAmount()
				+ " type=" + hitsplat.getHitsplatType() + " mine=" + hitsplat.isMine());
		}

		if (araxxor == null || event.getActor() != araxxor)
		{
			return;
		}

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
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			rec("CHAT " + event.getMessage());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		recordTick(tick);

		if (araxxor == null)
		{
			return;
		}

		clock.onGameTick(tick);
		reconcileWithHealthBar();
		maybeWarnEnrage();
		maybeWarnHatch(tick);
	}

	/** Health the xp says he is on, which may be ahead of the visible hitsplats. */
	public int getPredictedHp()
	{
		return Math.max(0, lastKnownHp - xpDamage.getInFlightDamage());
	}

	/** Standard attacks until the first egg hatches; only meaningful before it has. */
	public int getAttacksUntilHatch()
	{
		return AraxxorEggCycle.attacksUntilNextHatch(standardAttacks);
	}

	/** Tick the next egg should hatch, or -1 before that can be known (his 3rd attack is due). */
	private int predictedHatchTick()
	{
		if (lastHatchTick >= 0)
		{
			return lastHatchTick + (lastHatchEnraged ? ENRAGED_HATCH_INTERVAL_TICKS : HATCH_INTERVAL_TICKS);
		}
		if (standardAttacks == AraxxorEggCycle.FIRST_HATCH_ATTACK - 1 && lastStandardAttackTick >= 0)
		{
			return lastStandardAttackTick + NORMAL_SPEED_TICKS + FIRST_HATCH_AFTER_ATTACK_TICKS;
		}
		return -1;
	}

	/** Ticks until the next egg hatches, or -1 if not yet known. */
	public int getTicksUntilHatch()
	{
		int at = predictedHatchTick();
		return at < 0 ? -1 : Math.max(0, at - client.getTickCount());
	}

	/** The araxyte due out of the next egg, or null if the eggs were never read. */
	public AraxxorMinion getNextMinion()
	{
		return AraxxorEggCycle.typeAt(hatchOrder, hatchCount);
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

	/**
	 * The acid ball plays his ranged attack animation, not the acid cannon one.
	 * Specials come every 6 standard attacks, so a ranged animation on that
	 * slot is the ball - in recordings every one of them was, and counting it
	 * as an attack put each later hatch cue an attack early.
	 */
	private boolean isAcidBall(int animation)
	{
		return animation == AnimationID.NPC_ARAXXOR_01_ATTACK_RANGED_01
			&& standardAttacks > 0 && standardAttacks % SPECIAL_PERIOD == 0
			&& lastSpecialAttack != standardAttacks
			&& (fightSpecial == AraxxorSpecial.ACID_BALL || fightSpecial == AraxxorSpecial.UNKNOWN);
	}

	private void onStandardAttack(int tick)
	{
		if (lastStandardAttackTick >= 0 && tick - lastStandardAttackTick <= DUPLICATE_ATTACK_TICKS)
		{
			rec("STD_DUPLICATE ignored, last at t=" + lastStandardAttackTick);
			return;
		}
		lastStandardAttackTick = tick;

		AttackClock.Event result = clock.onAttackAnimation(tick);
		if (result == AttackClock.Event.SUB_ATTACK)
		{
			// Araxxor has no combo attack, so this means the animation landed off
			// the expected grid. Worth logging - it is the signal that would justify
			// switching to clock-driven counting like Cerberus needed.
			log.debug("ARAX off-schedule attack animation at tick {} (std={})", tick, standardAttacks);
		}

		standardAttacks++;
		rec("STD #" + standardAttacks + " clock=" + result + " nextHatchTick=" + predictedHatchTick());

		if (config.verboseLogging())
		{
			log.debug("ARAX standard attack #{} tick={} clockCount={} nextHatchIn={}",
				standardAttacks, tick, clock.getAttackCount(), getTicksUntilHatch());
		}
	}

	private void onSpecialObserved(AraxxorSpecial special)
	{
		lastSpecialAttack = standardAttacks;

		rec("SPECIAL " + special + " at std=" + standardAttacks);
		if (fightSpecial == AraxxorSpecial.UNKNOWN)
		{
			fightSpecial = special;
			log.debug("ARAX special identified from animation: {}", special);
			announceSpecial("his first special");
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
		rec("ENRAGE hp~" + lastKnownHp);
		playCue("enrage.wav");
	}

	private void onMinionHatched(AraxxorMinion minion)
	{
		activeMinion = minion;
		int tick = client.getTickCount();
		log.debug("ARAX {} hatched at std={} (predicted {})",
			minion, standardAttacks, getNextMinion());
		rec("HATCHED " + minion + " predicted=" + getNextMinion() + " std=" + standardAttacks + " ticksSinceStd="
			+ (lastStandardAttackTick < 0 ? -1 : tick - lastStandardAttackTick)
			+ " predictedTick=" + predictedHatchTick());
		hatchCount++;
		lastHatchTick = tick;
		lastHatchEnraged = enraged;

		// The first egg to hatch is the south-eastern one, whose type sets the
		// special, so this covers a fight where the eggs couldn't be read.
		if (fightSpecial == AraxxorSpecial.UNKNOWN)
		{
			fightSpecial = minion.getSpecial();
			announceSpecial("the first egg");
		}

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

		hatchOrder = AraxxorEggCycle.hatchOrder(new ArrayList<>(eggs.values()));
		AraxxorMinion first = hatchOrder.isEmpty() ? null : hatchOrder.get(0);
		rec("EGGS_READ order=" + hatchOrder);
		if (first != null)
		{
			fightSpecial = first.getSpecial();
			log.debug("ARAX eggs read: order={} special={}", hatchOrder, fightSpecial);
			announceSpecial("the south-east egg");
		}
	}

	private void addEgg(NPC npc)
	{
		WorldPoint p = npc.getWorldLocation();
		AraxxorMinion type = AraxxorMinion.byEggId(npc.getId());
		if (p == null || type == null)
		{
			return;
		}
		eggs.put(npc.getIndex(), new AraxxorEggCycle.Egg(p.getX(), p.getY(), type));
		readEggsIfComplete();
	}

	private void scanForEggs()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		for (NPC npc : wv.npcs())
		{
			if (AraxxorMinion.isEgg(npc.getId()))
			{
				addEgg(npc);
			}
		}
		rec("EGG_SCAN found=" + eggs.size());
	}

	/** Says which special this fight has, once, by voice and in the chatbox. */
	private void announceSpecial(String source)
	{
		if (specialAnnounced || fightSpecial == AraxxorSpecial.UNKNOWN || !config.announceSpecial())
		{
			return;
		}
		specialAnnounced = true;
		rec("ANNOUNCE_SPECIAL " + fightSpecial + " from " + source);
		playCue(fightSpecial.getSoundFile());
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"Araxxor's special this fight: " + fightSpecial.getDisplayName() + " - " + fightSpecial.getAdvice(), null);
	}

	/** Cues the next hatch a set number of ticks before it's due. */
	private void maybeWarnHatch(int tick)
	{
		int at = predictedHatchTick();
		if (!config.warnEggHatch() || at < 0 || warnedHatchIndex == hatchCount)
		{
			return;
		}

		if (tick >= at - config.hatchLeadTicks())
		{
			warnedHatchIndex = hatchCount;
			playCue("egg-soon.wav");
		}
	}

	private void maybeWarnEnrage()
	{
		if (enrageWarned || enraged || !config.warnEnrage())
		{
			return;
		}

		// Tracked HP, not the xp-based prediction: that ran hundreds ahead in
		// recordings (warned at a predicted 310 with him really on 784).
		if (lastKnownHp <= config.enrageWarnHp())
		{
			enrageWarned = true;
			rec("ENRAGE_WARNING predictedHp=" + getPredictedHp() + " knownHp=" + lastKnownHp);
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
		hatchCount = 0;
		lastHatchTick = -1;
		lastHatchEnraged = false;
		lastSpecialAttack = -1;
		lastStandardAttackTick = -1;
		specialAnnounced = false;
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
		rec("CUE " + clip);
		try
		{
			audioPlayer.play(AraxxorHelperPlugin.class, clip, config.voiceGain());
		}
		catch (Exception e)
		{
			log.warn("Could not play Araxxor cue {}", clip, e);
		}
	}

	// ---- recording ----

	private static boolean isFightNpc(int id)
	{
		return id == NpcID.ARAXXOR || id == NpcID.ARAXXOR_DEAD
			|| AraxxorMinion.isEgg(id) || AraxxorMinion.byMinionId(id) != null;
	}

	private void startRecording()
	{
		lastFightTick = client.getTickCount();
		if (recorder.isOpen() || !config.recordFights())
		{
			return;
		}
		Player me = client.getLocalPlayer();
		recorder.open("Araxxor recording - player=" + (me == null ? "?" : me.getName()));
		WorldView wv = client.getTopLevelWorldView();
		if (wv != null)
		{
			for (NPC npc : wv.npcs())
			{
				if (isFightNpc(npc.getId()))
				{
					rec("NPC_PRESENT " + describe(npc));
				}
			}
		}
	}

	/** Per tick: health bar and position changes, flushing, and closing once the fight has left the scene. */
	private void recordTick(int tick)
	{
		if (!recorder.isOpen())
		{
			return;
		}

		boolean fightInScene = araxxor != null;
		WorldView wv = client.getTopLevelWorldView();
		if (!fightInScene && wv != null)
		{
			for (NPC npc : wv.npcs())
			{
				if (isFightNpc(npc.getId()))
				{
					fightInScene = true;
					break;
				}
			}
		}
		if (fightInScene)
		{
			lastFightTick = tick;
		}
		else if (tick - lastFightTick > RECORDING_IDLE_TICKS)
		{
			recorder.close();
			return;
		}

		if (araxxor != null && araxxor.getHealthRatio() != lastHealthRatio)
		{
			lastHealthRatio = araxxor.getHealthRatio();
			rec("BOSS_HP ratio=" + lastHealthRatio + "/" + araxxor.getHealthScale() + " known=" + lastKnownHp);
		}
		Player me = client.getLocalPlayer();
		WorldPoint pos = me == null ? null : me.getWorldLocation();
		if (pos != null && !pos.equals(lastPlayerPos))
		{
			lastPlayerPos = pos;
			rec("PLAYER_POS at=" + pos.getX() + "," + pos.getY());
		}
		rec("TICK");
		recorder.flush();
	}

	private void rec(String line)
	{
		if (recorder.isOpen())
		{
			recorder.write(client.getTickCount(), line);
		}
	}

	private String who(Actor actor)
	{
		if (actor == client.getLocalPlayer())
		{
			return "me";
		}
		if (actor instanceof NPC)
		{
			return ((NPC) actor).getName() + "#" + ((NPC) actor).getIndex();
		}
		return actor == null ? "-" : String.valueOf(actor.getName());
	}

	private static String describe(NPC npc)
	{
		WorldPoint p = npc.getWorldLocation();
		return "id=" + npc.getId() + " name=" + npc.getName() + " idx=" + npc.getIndex()
			+ (p == null ? "" : " at=" + p.getX() + "," + p.getY());
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
