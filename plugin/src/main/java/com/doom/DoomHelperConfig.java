package com.doom;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(DoomHelperConfig.GROUP)
public interface DoomHelperConfig extends Config
{
	String GROUP = "doomhelper";

	@ConfigSection(
		name = "Prompts",
		description = "Short calls drawn above your character when you need to act",
		position = 0
	)
	String PROMPTS = "prompts";

	@ConfigSection(
		name = "Recording",
		description = "Fight logs used to build and check the helper's timings",
		position = 10
	)
	String RECORDING = "recording";

	@ConfigItem(
		keyName = "rockCallout",
		name = "Rock type callout",
		description = "When the boss throws a rock, say its type above your character until its follow-ups have landed.",
		position = 1,
		section = PROMPTS
	)
	default boolean rockCallout()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chargePrompt",
		name = "Beam charge calls",
		description = "When the boss charges its beam, say how to interrupt it: halberd punish, or any attack while burrowed.",
		position = 2,
		section = PROMPTS
	)
	default boolean chargePrompt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "halberdRing",
		name = "Halberd range ring",
		description = "During a melee charge, outline the tiles you can punish from with a halberd without being "
			+ "pulled into tongue-lash range (the wiki's radius marker).",
		position = 3,
		section = PROMPTS
	)
	default boolean halberdRing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bowBackPrompt",
		name = "Swap back after punish",
		description = "After a punish, remind you to swap off the halberd with a countdown to the boss' next attack.",
		position = 4,
		section = PROMPTS
	)
	default boolean bowBackPrompt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shockwavePrompt",
		name = "Orb countdown",
		description = "When the volatile earth appear, count down to the last tick you can click the second one and "
			+ "still have the earthen shield up for the first stomp. Allows for your distance to the nearest orb.",
		position = 5,
		section = PROMPTS
	)
	default boolean shockwavePrompt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lastWavePrompt",
		name = "Last wave countdown",
		description = "Once your earthen shield is up, count down to the last shockwave so you know how long to "
			+ "stay with it: 1-2 waves at delves 1-4, 3 at 5-6, 4 at 7, 5 at 8+.",
		position = 6,
		section = PROMPTS
	)
	default boolean lastWavePrompt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rockStompWarning",
		name = "Rock + stomp warning",
		description = "Add \"+ STOMP\" to the rock callout when its follow-ups will land during the shockwaves.",
		position = 7,
		section = PROMPTS
	)
	default boolean rockStompWarning()
	{
		return true;
	}

	@Range(min = -3, max = 3)
	@ConfigItem(
		keyName = "orbTimingOffset",
		name = "Orb countdown offset",
		description = "Shift the orb countdown's zero. Negative if the shield comes up too late when you click on 0, "
			+ "positive if it's up (and gone) too early.",
		position = 8,
		section = PROMPTS
	)
	default int orbTimingOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "carPath",
		name = "Car phase charge path",
		description = "When the burrowed boss' eye appears, shade the lane it will charge through and the spot it "
			+ "lands on, so you can step out of the trample.",
		position = 9,
		section = PROMPTS
	)
	default boolean carPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "slamPrompt",
		name = "Car slam countdown",
		description = "Delve 6+: count down to each car slam's damage from the moment the eye appears.",
		position = 10,
		section = PROMPTS
	)
	default boolean slamPrompt()
	{
		return true;
	}

	@Range(min = 12, max = 40)
	@ConfigItem(
		keyName = "fontSize",
		name = "Prompt size",
		description = "Font size of the prompts above your character.",
		position = 11,
		section = PROMPTS
	)
	default int fontSize()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "showPanel",
		name = "Info panel",
		description = "Panel with delve level, phase and the shockwave timer.",
		position = 7
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordFights",
		name = "Record fights",
		description = "Log every boss animation, projectile, graphic, object and varbit change during a Doom trip to "
			+ ".runelite/doom-helper/recordings. These recordings are what the helper's timings are built from.",
		position = 11,
		section = RECORDING
	)
	default boolean recordFights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRecordingIndicator",
		name = "Show recording indicator",
		description = "Mark the info panel while a recording is running.",
		position = 12,
		section = RECORDING
	)
	default boolean showRecordingIndicator()
	{
		return true;
	}
}
