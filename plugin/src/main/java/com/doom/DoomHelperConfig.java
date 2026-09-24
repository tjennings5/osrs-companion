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
		keyName = "prayerPrompt",
		name = "Prayer calls",
		description = "Show which protection prayer the next projectile needs when you aren't already praying it, "
			+ "including the Rock Throw follow-ups as soon as the rock is thrown.",
		position = 1,
		section = PROMPTS
	)
	default boolean prayerPrompt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chargePrompt",
		name = "Beam charge calls",
		description = "When the boss charges its beam, say how to interrupt it: halberd punish, hit the shield, or any attack.",
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
		name = "Shockwave shield reminder",
		description = "Remind you to break two volatile earth if no earthen shield exists shortly before the shockwave.",
		position = 5,
		section = PROMPTS
	)
	default boolean shockwavePrompt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "carPath",
		name = "Car phase charge path",
		description = "When the burrowed boss' eye appears, shade the lane it will charge through and the spot it "
			+ "lands on, so you can step out of the trample.",
		position = 6,
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
		position = 7,
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
		position = 8,
		section = PROMPTS
	)
	default int fontSize()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "showPanel",
		name = "Info panel",
		description = "Panel with delve level, phase, incoming attacks and the shockwave timer.",
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
