package com.spawntimer;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(SpawnTimerConfig.GROUP)
public interface SpawnTimerConfig extends Config
{
	String GROUP = "spawntimer";

	@ConfigItem(
		keyName = "trackingColor",
		name = "Tracked NPC outline",
		description = "Outline colour drawn around the NPC currently being tracked, so it's clear "
			+ "which one Ctrl+right-click actually tagged.",
		position = 0
	)
	@Alpha
	default Color trackingColor()
	{
		return new Color(80, 255, 140);
	}

	@ConfigItem(
		keyName = "learningColor",
		name = "Learning colour",
		description = "Tile and text colour the first time a tracked spawn is waited out, before "
			+ "the respawn tile and interval are known.",
		position = 1
	)
	@Alpha
	default Color learningColor()
	{
		return new Color(255, 200, 60);
	}

	@ConfigItem(
		keyName = "countingColor",
		name = "Counting down colour",
		description = "Tile and text colour while counting down to a known respawn, and the tile "
			+ "outline once confirmed while the NPC is present.",
		position = 2
	)
	@Alpha
	default Color countingColor()
	{
		return new Color(90, 190, 255);
	}

	@ConfigItem(
		keyName = "overdueColor",
		name = "Overdue colour",
		description = "Tile and text colour once the learned respawn time has passed without the "
			+ "NPC reappearing.",
		position = 3
	)
	@Alpha
	default Color overdueColor()
	{
		return new Color(255, 80, 80);
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "Size of the countdown text over a tracked spawn point.",
		position = 4
	)
	@Range(min = 8, max = 48)
	default int fontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "showTileOutline",
		name = "Show tile outline",
		description = "Draw an outline on the confirmed spawn tile itself, not just the countdown "
			+ "text.",
		position = 5
	)
	default boolean showTileOutline()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug chat messages",
		description = "Print every death/respawn matching decision to the chatbox, for figuring "
			+ "out why a timer picked the tile or NPC it did. Noisy — meant to be turned off again "
			+ "once you're done diagnosing something.",
		position = 6
	)
	default boolean debugLogging()
	{
		return false;
	}
}
