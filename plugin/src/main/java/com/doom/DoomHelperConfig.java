package com.doom;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DoomHelperConfig.GROUP)
public interface DoomHelperConfig extends Config
{
	String GROUP = "doomhelper";

	@ConfigItem(
		keyName = "recordFights",
		name = "Record fights",
		description = "Log every boss animation, projectile, graphic, object and varbit change during a Doom trip to "
			+ ".runelite/doom-helper/recordings. These recordings are what the helper's timings are built from.",
		position = 1
	)
	default boolean recordFights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRecordingIndicator",
		name = "Show recording indicator",
		description = "Small panel showing that a recording is running and how many events it has captured.",
		position = 2
	)
	default boolean showRecordingIndicator()
	{
		return true;
	}
}
