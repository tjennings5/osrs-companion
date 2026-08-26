package com.cerberus;

import com.google.inject.Provides;
import com.combat.AttackClock;
import com.combat.HealthBar;
import com.combat.XpDamage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Tracks Cerberus' attack sequence and calls out what is coming, with explicit
 * support for the wiki's "No Ghosts" strategy.
 *
 * Display-only: it reads the boss' animations and health, and speaks or draws
 * what it saw. It never sends input, switches prayers, or acts for the player.
 *
 * Attack tracking is a 6-tick clock ({@link AttackClock}) rather than a
 * raw animation count, because her DEFEND animation masks attack animations
 * whenever she is taking damage. Animations correct the clock instead of
 * driving it, and two of them double as ground truth for the attack number:
 * a triple (multiple animations in one slot) can only be attacks 1, 11, 21...
 * and a howl can only be a real soul slot.
 *
 * Never submitted to the Plugin Hub; loaded via the dev-client workflow
 * alongside the OSRS MCP Bridge plugin.
 */
@Slf4j
@PluginDescriptor(
	name = "Cerberus Helper",
	description = "Attack counter, No Ghosts timing, and voice cues for Cerberus' triple/souls/lava specials",
	tags = {"cerberus", "slayer", "boss", "timer", "prayer", "ghosts"}
)
public class CerberusHelperPlugin extends Plugin
{
	/** Cerberus' full health, used to turn the health bar ratio back into hitpoints. */
	private static final int MAX_HP = 600;

	/** Her attack speed in ticks, from the wiki infobox and confirmed against a recorded fight. */
	private static final int ATTACK_SPEED_TICKS = 6;

	/** Cerberus' experience multiplier, from xpbonus = 15 on her wiki infobox. */
	private static final double CERBERUS_XP_MULTIPLIER = 1.15;

	/** Souls only become possible below the first; lava below the second. From the wiki's mechanics table. */
	static final int SOULS_HP_THRESHOLD = 400;
	private static final int LAVA_HP_THRESHOLD = 200;

	private static final int TRIPLE_PERIOD = 10;
	private static final int SOULS_PERIOD = 7;
	private static final int LAVA_PERIOD = 5;

	/**
	 * How many free attacks a gap has to offer before it is worth calling "go".
	 *
	 * Soul slots are 7 apart, so most gaps are only 7 attacks — not enough to
	 * finish her from 400. The exception is the gap after attack 14: attack 21
	 * is a combo slot, and combo outranks souls, so 21 is consumed and the next
	 * real soul attack is 28. That single 14-attack window is the entire basis
	 * of the No Ghosts strategy, and this threshold is what distinguishes it.
	 */
	private static final int USEFUL_WINDOW = 10;

	/** How far the counter may be corrected by an anchor before the mismatch is treated as unexplained. */
	static final int MAX_RESYNC_DRIFT = 3;

	/** Black mask / slayer helmet damage bonus, which always applies here since Cerberus needs a task. */
	private static final double SLAYER_HELM_MULTIPLIER = 1.15;

	/** Highest attack-style strength contribution; assumed, since overestimating errs safe. */
	private static final int MAX_STYLE_BONUS = 3;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CerberusHelperOverlay overlay;

	@Inject
	private CerberusHelperConfig config;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ItemManager itemManager;

	private NPC cerberus;

	private AttackClock clock = new AttackClock(ATTACK_SPEED_TICKS, AttackClock.DEFAULT_TOLERANCE);

	private final XpDamage xpDamage = new XpDamage(CERBERUS_XP_MULTIPLIER);

	/** Last seen total XP per combat skill, so gains can be turned into deltas. */
	private final Map<Skill, Integer> lastCombatXp = new EnumMap<>(Skill.class);

	@Getter
	private int lastKnownHp = MAX_HP;

	/**
	 * True once hitsplats are driving {@link #lastKnownHp} rather than the health
	 * bar. The bar is quantised — Cerberus reports in steps of a few hitpoints —
	 * so near the 400 boundary it can be several points stale in either
	 * direction, which is exactly where precision matters.
	 */
	@Getter
	private boolean hpExact;

	/** Largest single hit landed this fight, used to predict whether one more will cross 400. */
	@Getter
	private int biggestHit;

	/** Max hit estimated from equipment and levels, refreshed as gear and prayers change. */
	@Getter
	private int estimatedMaxHit;

	@Getter
	private CerberusSpecial nextSpecial = CerberusSpecial.TRIPLE;

	/** True once a long soul-free window has been entered — i.e. time to burn her down. */
	@Getter
	private boolean windowOpen;

	/** Attack number the open window ends on (the next soul attack). */
	@Getter
	private int windowEndsAt;

	private boolean holdWarned;

	@Provides
	CerberusHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CerberusHelperConfig.class);
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
		return cerberus != null;
	}

	public int getAttackCount()
	{
		return clock.getAttackCount();
	}

	/** The next attack number that will actually summon souls, assuming she is under 400. */
	public int getNextSoulsAttack()
	{
		return nextSoulsAttack(getAttackCount());
	}

	public int getAttacksLeftInWindow()
	{
		return Math.max(0, windowEndsAt - getAttackCount());
	}

	/**
	 * True when she is already under 400 but no useful window has opened — the
	 * No Ghosts attempt is blown and the wiki's advice is to reset the fight by
	 * walking back over the entrance flames.
	 */
	public boolean isDroppedBelowEarly()
	{
		return config.noGhostsMode()
			&& !windowOpen
			&& getAttackCount() > 0
			&& getPredictedHp() < SOULS_HP_THRESHOLD;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			resetFight("game state " + event.getGameState());
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (isCerberus(event.getNpc()))
		{
			cerberus = event.getNpc();
			resetCounters();
			log.debug("Cerberus spawned (id {}) - tracking", event.getNpc().getId());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc() == cerberus)
		{
			resetFight("Cerberus despawned");
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (cerberus == null)
		{
			return;
		}

		reconcileWithHealthBar();
		estimatedMaxHit = estimateMaxHit();

		// The clock supplies the attacks that DEFEND hid. Without this the counter
		// stalls for as long as she is being damaged.
		if (clock.onGameTick(client.getTickCount()) == AttackClock.Event.ATTACK)
		{
			log.debug("CERBTIMING tick={} source=clock hp={} outcome=attack-{}",
				client.getTickCount(), lastKnownHp, getAttackCount());
			onAttackRegistered(-1);
		}

		maybeWarnHold();
	}

	/**
	 * Applies damage at the moment the XP lands, which for ranged is a tick or
	 * two before the projectile arrives and the hitsplat appears.
	 *
	 * This is the earliest the client can possibly know a hit happened: the
	 * damage roll and the XP award happen together when the attack is thrown.
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!isCombatXpSkill(event.getSkill()))
		{
			return;
		}

		Integer previous = lastCombatXp.put(event.getSkill(), event.getXp());
		if (previous == null || cerberus == null || !hpExact)
		{
			// First reading of this skill only establishes the baseline.
			return;
		}

		int before = xpDamage.getInFlightDamage();
		xpDamage.addXp(event.getXp() - previous);
		int inFlight = xpDamage.getInFlightDamage();
		if (inFlight <= before)
		{
			return;
		}

		log.debug("CERBTIMING xp in-flight={} hp={} predicted={} (rate {}{})",
			inFlight, lastKnownHp, getPredictedHp(),
			String.format("%.2f", xpDamage.getXpPerDamage()), xpDamage.isCalibrated() ? ", measured" : ", default");
		maybeWarnHold();
	}

	/**
	 * Hitsplats are the fallback for damage that awards no combat XP to us, and
	 * they refine the biggest-hit figure.
	 *
	 * Once XP is driving the health count this must not subtract as well, or every
	 * hit would be counted twice — the XP arrived first and already accounted for
	 * it.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (cerberus == null || event.getActor() != cerberus || !hpExact)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (hitsplat.getHitsplatType() == HitsplatID.HEAL)
		{
			return;
		}

		int damage = hitsplat.getAmount();
		if (damage <= 0)
		{
			return;
		}

		// Hitsplats are authoritative for health. XP only ever predicts what is
		// already in the air on top of this, so a wrong XP rate cannot accumulate.
		biggestHit = Math.max(biggestHit, damage);
		lastKnownHp = Math.max(0, lastKnownHp - damage);
		xpDamage.addConfirmedDamage(damage);

		// Type is logged so that if the health count drifts again it is possible
		// to tell which splats caused it — a burn or another over-time effect
		// showing damage that is not actually applied would look exactly like
		// this and is otherwise invisible.
		log.debug("CERBTIMING hitsplat type={} mine={} damage={} hp={} totalConfirmed={}",
			hitsplat.getHitsplatType(), hitsplat.isMine(), damage, lastKnownHp, xpDamage.getConfirmedDamage());

		maybeWarnHold();
	}

	/**
	 * Health once the hits already launched land — the number the hold decision
	 * has to be made against, since by the time they land it is too late to stop
	 * attacking.
	 */
	public int getPredictedHp()
	{
		return Math.max(0, lastKnownHp - xpDamage.getInFlightDamage());
	}

	private static boolean isCombatXpSkill(Skill skill)
	{
		return skill == Skill.ATTACK
			|| skill == Skill.STRENGTH
			|| skill == Skill.DEFENCE
			|| skill == Skill.RANGED;
	}

	/**
	 * Seeds the exact count from the health bar, and corrects it if the two ever
	 * drift apart — a hitsplat landing before we started tracking, or damage from
	 * a source that produced no hitsplat, would otherwise skew it for the rest of
	 * the fight.
	 */
	private void reconcileWithHealthBar()
	{
		int ratio = cerberus.getHealthRatio();
		int scale = cerberus.getHealthScale();
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

		// Correct in both directions, but only when the count is outside what the
		// bar could possibly mean.
		//
		// This used to correct downwards only, to stop the lagging bar from
		// erasing the lead that reading XP buys. That is no longer a concern:
		// lastKnownHp is driven purely by landed hitsplats and never runs ahead of
		// the bar — only getPredictedHp() does, and it is derived on top. Leaving
		// the guard one-way meant any per-hit overshoot accumulated with nothing
		// able to pull it back.
		int corrected = HealthBar.clampToBand(lastKnownHp, ratio, scale, MAX_HP);
		if (corrected != lastKnownHp)
		{
			log.debug("CERBTIMING hp resync: tracked {} outside bar band [{}, {}] - correcting to {}",
				lastKnownHp, HealthBar.minHealth(ratio, scale, MAX_HP),
				HealthBar.maxHealth(ratio, scale, MAX_HP), corrected);
			lastKnownHp = corrected;
		}
	}

	private void maybeWarnHold()
	{
		if (!config.noGhostsMode() || windowOpen || holdWarned || getAttackCount() == 0)
		{
			return;
		}
		if (!shouldWarnHold(getPredictedHp(), expectedIncomingHit(), config.holdWarnHp(), config.predictiveHold()))
		{
			return;
		}

		holdWarned = true;
		log.debug("CERBTIMING hold-warning at hp={} predicted={} (planning around a {} hit)",
			lastKnownHp, getPredictedHp(), expectedIncomingHit());
		if (config.voiceEnabled())
		{
			play("hold-damage.wav");
		}
	}

	/**
	 * The biggest single hit worth planning around.
	 *
	 * Takes the largest of the three available estimates so that no single one
	 * being wrong can make the warning late: the manual override if set, the max
	 * hit computed from gear, and the biggest hit actually landed this fight.
	 * The computed figure covers the opening attacks before anything has been
	 * observed; the observed figure covers everything the formula does not model.
	 */
	public int expectedIncomingHit()
	{
		return Math.max(Math.max(config.weaponMaxHit(), estimatedMaxHit), biggestHit);
	}

	/**
	 * Max hit from current gear, levels and prayers.
	 *
	 * Both styles are computed and the larger taken rather than trying to
	 * classify the equipped weapon — for a "could this cross 400" question the
	 * conservative answer is the right one.
	 */
	private int estimateMaxHit()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return 0;
		}

		int meleeStr = 0;
		int rangedStr = 0;
		for (Item item : equipment.getItems())
		{
			if (item.getId() <= 0)
			{
				continue;
			}
			ItemStats stats = itemManager.getItemStats(item.getId());
			if (stats == null || stats.getEquipment() == null)
			{
				continue;
			}
			meleeStr += stats.getEquipment().getStr();
			rangedStr += stats.getEquipment().getRstr();
		}

		// At Cerberus you are always on a hellhounds task, so a black mask or
		// slayer helmet is always contributing its bonus here.
		double gearMultiplier = wearingBlackMask(equipment) ? SLAYER_HELM_MULTIPLIER : 1.0;

		// The style bonus tops out at 3 (accurate/aggressive). Assuming the
		// maximum overestimates by at most 3 levels, which errs the safe way.
		int melee = CerberusMaxHit.maxHit(client.getBoostedSkillLevel(Skill.STRENGTH), meleeStr,
			meleePrayerMultiplier(), MAX_STYLE_BONUS, gearMultiplier);
		int ranged = CerberusMaxHit.maxHit(client.getBoostedSkillLevel(Skill.RANGED), rangedStr,
			rangedPrayerMultiplier(), MAX_STYLE_BONUS, gearMultiplier);
		return CerberusMaxHit.bestOf(melee, ranged);
	}

	private boolean wearingBlackMask(ItemContainer equipment)
	{
		Item head = equipment.getItem(EquipmentInventorySlot.HEAD.getSlotIdx());
		if (head == null || head.getId() <= 0)
		{
			return false;
		}
		String name = itemManager.getItemComposition(head.getId()).getName();
		return name != null && (name.startsWith("Black mask") || name.startsWith("Slayer helmet"));
	}

	private double meleePrayerMultiplier()
	{
		if (client.isPrayerActive(Prayer.PIETY))
		{
			return 1.23;
		}
		if (client.isPrayerActive(Prayer.CHIVALRY))
		{
			return 1.18;
		}
		if (client.isPrayerActive(Prayer.ULTIMATE_STRENGTH))
		{
			return 1.15;
		}
		if (client.isPrayerActive(Prayer.SUPERHUMAN_STRENGTH))
		{
			return 1.10;
		}
		if (client.isPrayerActive(Prayer.BURST_OF_STRENGTH))
		{
			return 1.05;
		}
		return 1.0;
	}

	private double rangedPrayerMultiplier()
	{
		if (client.isPrayerActive(Prayer.RIGOUR))
		{
			return 1.23;
		}
		if (client.isPrayerActive(Prayer.EAGLE_EYE))
		{
			return 1.15;
		}
		if (client.isPrayerActive(Prayer.HAWK_EYE))
		{
			return 1.10;
		}
		if (client.isPrayerActive(Prayer.SHARP_EYE))
		{
			return 1.05;
		}
		return 1.0;
	}

	/**
	 * Whether to call "hold damage" now.
	 *
	 * The fixed threshold alone is reactive — by the time she reaches it, the next
	 * hit may already have been launched. The predictive arm asks the question
	 * that actually matters: would one more hit the size of your biggest so far
	 * put her under 400? That scales with your gear and the fight, instead of
	 * relying on a number guessed in advance.
	 */
	static boolean shouldWarnHold(int hp, int biggestHit, int warnAtHp, boolean predictive)
	{
		if (hp < SOULS_HP_THRESHOLD)
		{
			// Already across the line; isDroppedBelowEarly covers this case.
			return false;
		}
		if (hp <= warnAtHp)
		{
			return true;
		}
		return predictive && biggestHit > 0 && hp - biggestHit <= SOULS_HP_THRESHOLD;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor != cerberus || cerberus == null)
		{
			return;
		}

		int animation = cerberus.getAnimation();
		int tick = client.getTickCount();

		if (animation == AnimationID.CERBERUS_DEATH)
		{
			log.debug("CERBTIMING tick={} anim={} name=DEATH hp={} outcome=death-reset", tick, animation, lastKnownHp);
			resetFight("Cerberus died");
			return;
		}

		// She only sits or idles once the fight is over; stop the clock rather
		// than letting it invent attacks while she is resetting.
		if (animation == AnimationID.CERBERUS_STAND_TO_SIT || animation == AnimationID.CERBERUS_IDLE_SITTING)
		{
			log.debug("CERBTIMING tick={} anim={} name={} hp={} outcome=fight-over",
				tick, animation, animationName(animation), lastKnownHp);
			resetCounters();
			return;
		}

		if (!isAttackAnimation(animation))
		{
			log.debug("CERBTIMING tick={} anim={} name={} hp={} outcome=ignored-non-attack",
				tick, animation, animationName(animation), lastKnownHp);
			return;
		}

		AttackClock.Event result = clock.onAttackAnimation(tick);
		log.debug("CERBTIMING tick={} anim={} name={} hp={} next={} outcome={}-{}",
			tick, animation, animationName(animation), lastKnownHp, clock.getNextAttackTick(),
			result == AttackClock.Event.SUB_ATTACK ? "sub-attack-of" : "attack",
			getAttackCount());

		if (result == AttackClock.Event.SUB_ATTACK)
		{
			onTripleObserved();
			return;
		}

		onAttackRegistered(animation);
	}

	private void onAttackRegistered(int animation)
	{
		checkSoulAnchor(animation);
		updateNoGhostsWindow();
		announceNext();
	}

	/**
	 * A second animation inside one attack slot only happens on the triple, and
	 * triples are attacks 1, 11, 21... — so this pins the attack number exactly.
	 *
	 * This is the anchor that matters for No Ghosts: it works while she is above
	 * 400, which is the whole hold phase, and is the only ground truth available
	 * there since souls cannot fire.
	 */
	private void onTripleObserved()
	{
		int counted = getAttackCount();
		if (isTripleSlot(counted))
		{
			log.debug("CERBTIMING triple-confirmed at attack-{} - counter agrees", counted);
			return;
		}

		int corrected = nearestTripleSlot(counted, MAX_RESYNC_DRIFT);
		if (corrected < 0)
		{
			log.warn("CERBTIMING triple at attack-{}, which is no combo slot and none is within {} - "
				+ "counter is badly out of sync", counted, MAX_RESYNC_DRIFT);
			return;
		}

		log.warn("CERBTIMING resync: triple means this is attack-{}, counter said attack-{} (drift {})",
			corrected, counted, corrected - counted);
		clock.resyncAttackCount(corrected);
		recomputeWindow();
	}

	/**
	 * Uses the howl as ground truth once she is under 400.
	 *
	 * A soul slot that passes with no howl is the documented 10% skip. It costs
	 * nothing — the cycle runs on attack number, not on souls appearing — but it
	 * is worth being able to see in a recorded fight.
	 */
	private void checkSoulAnchor(int animation)
	{
		if (animation != AnimationID.CERBERUS_HOWL)
		{
			if (isSoulSlot(getAttackCount()) && lastKnownHp < SOULS_HP_THRESHOLD)
			{
				log.debug("CERBTIMING souls-skipped at attack-{} (the documented 10% roll)", getAttackCount());
			}
			return;
		}

		windowOpen = false;

		if (isSoulSlot(getAttackCount()))
		{
			log.debug("CERBTIMING souls-confirmed at attack-{} - counter agrees", getAttackCount());
			return;
		}

		int corrected = nearestSoulSlot(getAttackCount(), MAX_RESYNC_DRIFT);
		if (corrected < 0)
		{
			log.warn("CERBTIMING howl at attack-{}, which is no soul slot and none is within {} - "
				+ "counter is badly out of sync, or the attack model is wrong", getAttackCount(), MAX_RESYNC_DRIFT);
			return;
		}

		log.warn("CERBTIMING resync: howl means this is attack-{}, counter said attack-{} (drift {})",
			corrected, getAttackCount(), corrected - getAttackCount());
		clock.resyncAttackCount(corrected);
	}

	private void updateNoGhostsWindow()
	{
		if (!config.noGhostsMode())
		{
			return;
		}

		int n = getAttackCount();
		if (windowOpen && n >= windowEndsAt)
		{
			windowOpen = false;
		}

		if (!isSoulSlot(n) || lastKnownHp < SOULS_HP_THRESHOLD)
		{
			return;
		}

		int next = nextSoulsAttack(n);
		if (next - n < USEFUL_WINDOW)
		{
			log.debug("Cleared soul slot #{} above 400, but only {} attacks until #{} - not calling it",
				n, next - n, next);
			return;
		}

		windowOpen = true;
		windowEndsAt = next;
		log.debug("No Ghosts window open: {} attacks until souls at #{}", next - n, next);

		if (config.voiceEnabled() && config.warnGoNow())
		{
			play("go-now.wav");
		}
	}

	private void recomputeWindow()
	{
		if (windowOpen && getAttackCount() >= windowEndsAt)
		{
			windowOpen = false;
		}
	}

	private void announceNext()
	{
		nextSpecial = predictSpecial(getAttackCount() + 1, lastKnownHp);

		if (!config.voiceEnabled() || nextSpecial.getSound() == null)
		{
			return;
		}
		if (nextSpecial == CerberusSpecial.TRIPLE && !config.warnTriple())
		{
			return;
		}
		if (nextSpecial == CerberusSpecial.SOULS && !config.warnSouls())
		{
			return;
		}
		if (nextSpecial == CerberusSpecial.LAVA && !config.warnLava())
		{
			return;
		}

		play(nextSpecial.getSound());
	}

	static CerberusSpecial predictSpecial(int attackNumber, int hp)
	{
		if (attackNumber < 1)
		{
			return CerberusSpecial.NORMAL;
		}
		if (isTripleSlot(attackNumber))
		{
			return CerberusSpecial.TRIPLE;
		}
		if (attackNumber % SOULS_PERIOD == 0 && hp < SOULS_HP_THRESHOLD)
		{
			return CerberusSpecial.SOULS;
		}
		if (attackNumber % LAVA_PERIOD == 0 && hp < LAVA_HP_THRESHOLD)
		{
			return CerberusSpecial.LAVA;
		}
		return CerberusSpecial.NORMAL;
	}

	static boolean isTripleSlot(int attackNumber)
	{
		return attackNumber >= 1 && (attackNumber - 1) % TRIPLE_PERIOD == 0;
	}

	/**
	 * Whether souls actually fire on this attack (health permitting), as opposed
	 * to the slot being consumed by a higher-priority triple. Attack 21 is the
	 * canonical case: divisible by seven, but a combo slot, so no souls.
	 */
	static boolean isSoulSlot(int attackNumber)
	{
		return attackNumber >= 1 && attackNumber % SOULS_PERIOD == 0 && !isTripleSlot(attackNumber);
	}

	static int nearestSoulSlot(int n, int maxDrift)
	{
		return nearestSlot(n, maxDrift, true);
	}

	static int nearestTripleSlot(int n, int maxDrift)
	{
		return nearestSlot(n, maxDrift, false);
	}

	/**
	 * The nearest slot of the given kind, or -1 if none is within {@code maxDrift}.
	 *
	 * Refusing to guess past the limit is deliberate: snapping across a larger
	 * gap would manufacture a confident wrong answer out of what is more likely
	 * a broken model than a miscount.
	 */
	private static int nearestSlot(int n, int maxDrift, boolean soul)
	{
		for (int d = 0; d <= maxDrift; d++)
		{
			if (n - d >= 1 && (soul ? isSoulSlot(n - d) : isTripleSlot(n - d)))
			{
				return n - d;
			}
			if (soul ? isSoulSlot(n + d) : isTripleSlot(n + d))
			{
				return n + d;
			}
		}
		return -1;
	}

	/** The first attack strictly after {@code after} on which souls would fire. */
	static int nextSoulsAttack(int after)
	{
		for (int n = Math.max(0, after) + 1; n <= after + 100; n++)
		{
			if (isSoulSlot(n))
			{
				return n;
			}
		}
		return -1;
	}

	private void play(String clip)
	{
		try
		{
			audioPlayer.play(CerberusHelperPlugin.class, clip, config.voiceGain());
		}
		catch (Exception e)
		{
			log.warn("Could not play Cerberus cue {}", clip, e);
		}
	}

	private void resetFight(String reason)
	{
		if (cerberus != null || getAttackCount() != 0)
		{
			log.debug("Resetting Cerberus tracking: {}", reason);
		}
		cerberus = null;
		resetCounters();
	}

	private void resetCounters()
	{
		clock = new AttackClock(ATTACK_SPEED_TICKS, config == null
			? AttackClock.DEFAULT_TOLERANCE
			: config.phaseToleranceTicks());
		lastKnownHp = MAX_HP;
		hpExact = false;
		biggestHit = 0;
		estimatedMaxHit = 0;
		xpDamage.reset();
		// Keep the XP baselines: clearing them would make the first gain of the
		// next fight look like the player's entire lifetime XP.
		nextSpecial = CerberusSpecial.TRIPLE; // attack 1 is always the triple
		windowOpen = false;
		windowEndsAt = 0;
		holdWarned = false;
	}

	private static boolean isCerberus(NPC npc)
	{
		int id = npc.getId();
		return id == NpcID.CERBERUS_ATTACKING
			|| id == NpcID.CERBERUS_SITTING
			|| id == NpcID.CERBERUS_RESETTING;
	}

	/** Readable name for the timing log, so a recorded fight can be read without an ID table to hand. */
	private static String animationName(int animation)
	{
		switch (animation)
		{
			case AnimationID.CERBERUS_BITE:
				return "BITE_melee";
			case AnimationID.CERBERUS_ATTACK_RANGE:
				return "RANGE";
			case AnimationID.CERBERUS_FIRE_BREATH:
				return "MAGIC";
			case AnimationID.CERBERUS_HOWL:
				return "HOWL_souls";
			case AnimationID.CERBERUS_SPECIAL_ATTACK_FLAME:
				return "FLAME_lava";
			case AnimationID.CERBERUS_SPECIAL_ATTACK_SPRAY:
				return "SPRAY";
			case AnimationID.CERBERUS_DEFEND:
				return "DEFEND";
			case AnimationID.CERBERUS_IDLE:
				return "IDLE";
			case AnimationID.CERBERUS_IDLE_SITTING:
				return "IDLE_SITTING";
			case AnimationID.CERBERUS_IDLE_TO_STAND:
				return "STANDING_UP";
			case AnimationID.CERBERUS_STAND_TO_SIT:
				return "SITTING_DOWN";
			case AnimationID.CERBERUS_WALK:
				return "WALK";
			case -1:
				return "ANIM_END";
			default:
				return "UNKNOWN";
		}
	}

	private static boolean isAttackAnimation(int animation)
	{
		return animation == AnimationID.CERBERUS_BITE
			|| animation == AnimationID.CERBERUS_ATTACK_RANGE
			|| animation == AnimationID.CERBERUS_FIRE_BREATH
			|| animation == AnimationID.CERBERUS_HOWL
			|| animation == AnimationID.CERBERUS_SPECIAL_ATTACK_FLAME
			|| animation == AnimationID.CERBERUS_SPECIAL_ATTACK_SPRAY;
	}
}
