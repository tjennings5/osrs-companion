package com.cerberus;

import com.combat.AttackClock;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(CerberusHelperConfig.GROUP)
public interface CerberusHelperConfig extends Config
{
	String GROUP = "cerberushelper";

	@ConfigItem(
		keyName = "voiceEnabled",
		name = "Voice cues",
		description = "Speak a short cue one attack before each special. Turn this off to rely on the overlay alone.",
		position = 1
	)
	default boolean voiceEnabled()
	{
		return true;
	}

	@Range(min = -40, max = 6)
	@ConfigItem(
		keyName = "voiceGain",
		name = "Voice volume (dB)",
		description = "Gain applied to the voice cues. 0 is the clip's own level; negative is quieter.",
		position = 2
	)
	default int voiceGain()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "warnTriple",
		name = "Warn: triple attack",
		description = "Cue before the magic/ranged/melee combo. This one fires the whole fight.",
		position = 3
	)
	default boolean warnTriple()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnSouls",
		name = "Warn: summoned souls",
		description = "Cue before the three souls. Only possible once Cerberus is below 400 hitpoints.",
		position = 4
	)
	default boolean warnSouls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnLava",
		name = "Warn: lava pools",
		description = "Cue before the lava pools. Only possible once Cerberus is below 200 hitpoints.",
		position = 5
	)
	default boolean warnLava()
	{
		return true;
	}

	@ConfigItem(
		keyName = "noGhostsMode",
		name = "No Ghosts mode",
		description =
			"Track the soul cycle even while Cerberus is above 400 hitpoints, and call the moment the long "
				+ "soul-free window opens. Turn this off for an ordinary kill where you just fight through the souls.",
		position = 10
	)
	default boolean noGhostsMode()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnGoNow",
		name = "Warn: window open",
		description = "Speak when a soul slot has been cleared above 400 and the burn window is open.",
		position = 11
	)
	default boolean warnGoNow()
	{
		return true;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "weaponMaxHit",
		name = "Your max hit (0 = auto)",
		description =
			"Your real max hit against Cerberus, from an in-game or wiki calculator. Leave at 0 to use the "
				+ "estimate from your gear plus the biggest hit seen this fight. Worth setting if your weapon "
				+ "has an effect the estimate cannot know about, such as a demonbane bonus.",
		position = 11
	)
	default int weaponMaxHit()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "predictiveHold",
		name = "Predict the hold warning",
		description =
			"Also warn as soon as one more hit the size of your biggest so far would take her under 400, "
				+ "rather than only when she reaches the fixed threshold below. Adapts to your gear instead "
				+ "of relying on a number picked in advance.",
		position = 12
	)
	default boolean predictiveHold()
	{
		return true;
	}

	@Range(min = 400, max = 600)
	@ConfigItem(
		keyName = "holdWarnHp",
		name = "Hold warning at (HP)",
		description =
			"During the hold phase, warn when Cerberus reaches this many hitpoints so you can ease off before "
				+ "crossing 400 too early. Set close to 400 if your damage is predictable.",
		position = 12
	)
	default int holdWarnHp()
	{
		return 450;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Small text panel with the attack count and what is coming next. Static — it never flashes.",
		position = 6
	)
	default boolean showOverlay()
	{
		return true;
	}

	@Range(min = 0, max = 2)
	@ConfigItem(
		keyName = "phaseToleranceTicks",
		name = "Schedule tolerance (ticks)",
		description =
			"How far off the 6-tick schedule an attack animation may land and still count as that attack. "
				+ "Anything further out is treated as another component of the triple. Observed offsets are 0 "
				+ "or 1 and the triple's components sit 3-4 ticks out, so 1 separates them; only raise this if "
				+ "attacks are being mistaken for combos.",
		position = 7
	)
	default int phaseToleranceTicks()
	{
		return AttackClock.DEFAULT_TOLERANCE;
	}
}
