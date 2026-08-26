package com.araxxor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(AraxxorHelperConfig.GROUP)
public interface AraxxorHelperConfig extends Config
{
	String GROUP = "araxxorhelper";

	@ConfigItem(
		keyName = "voiceEnabled",
		name = "Voice cues",
		description = "Speak short cues for hatches, enrage and the cleave. Turn off to rely on the overlay alone.",
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
		keyName = "announceSpecial",
		name = "Announce the fight's special",
		description =
			"Name the special attack for this fight as soon as the eggs are read. It is fixed for the whole "
				+ "fight, so this tells you what you are dealing with before he attacks once.",
		position = 3
	)
	default boolean announceSpecial()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnEggHatch",
		name = "Warn: egg hatching",
		description = "Cue before the next egg hatches, naming which araxyte is coming.",
		position = 4
	)
	default boolean warnEggHatch()
	{
		return true;
	}

	@Range(min = 1, max = 3)
	@ConfigItem(
		keyName = "hatchLeadAttacks",
		name = "Hatch warning lead (attacks)",
		description = "How many standard attacks ahead of the hatch to warn. 1 gives the least noise, 2-3 more time to reposition.",
		position = 5
	)
	default int hatchLeadAttacks()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "minionAdvice",
		name = "Show minion handling advice",
		description =
			"Show what to do with the araxyte that just hatched - notably that a Mirrorback recoils 50% if you "
				+ "melee it point-blank, and how far to stand from a Ruptura.",
		position = 6
	)
	default boolean minionAdvice()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnEnrage",
		name = "Warn: enrage approaching",
		description =
			"Cue as Araxxor nears 255 hitpoints, where he enrages: attack speed goes from 6 ticks to 4 and his "
				+ "melee becomes the dodgeable cleave.",
		position = 7
	)
	default boolean warnEnrage()
	{
		return true;
	}

	@Range(min = 255, max = 500)
	@ConfigItem(
		keyName = "enrageWarnHp",
		name = "Enrage warning at (HP)",
		description = "Hitpoints at which to give the enrage warning. Enrage itself is fixed at 255.",
		position = 8
	)
	default int enrageWarnHp()
	{
		return 320;
	}

	@ConfigItem(
		keyName = "warnCleave",
		name = "Warn: cleave (Skree!)",
		description =
			"Cue the instant Araxxor shouts Skree! in the enrage phase, which is your signal to step out of the "
				+ "1x3 cleave. This is the highest-value cue in the fight and the tightest on time.",
		position = 9
	)
	default boolean warnCleave()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Small text panel with the attack count, next hatch and phase. Static - it never flashes.",
		position = 10
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "verboseLogging",
		name = "Verbose logging",
		description =
			"Log every animation, hatch and attack with tick numbers, so a real fight can be replayed to "
				+ "calibrate the counter. Worth leaving on for the first few kills.",
		position = 11
	)
	default boolean verboseLogging()
	{
		return true;
	}
}
