package com.wardens;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(WardensHelperConfig.GROUP)
public interface WardensHelperConfig extends Config
{
	String GROUP = "wardenshelper";

	@ConfigItem(
		keyName = "highlightBoulders",
		name = "Highlight boulders",
		description = "Mark the tile each falling boulder will land on. Covers the Ba-Ba phantom "
			+ "in phase 3, and Ba-Ba's own room.",
		position = 0
	)
	default boolean highlightBoulders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightLightning",
		name = "Highlight enrage lightning",
		description = "Mark the tile each lightning bolt will strike once the Warden enrages.",
		position = 1
	)
	default boolean highlightLightning()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "tileColor",
		name = "Tile colour",
		description = "Outline colour of a tile that is about to be hit.",
		position = 2
	)
	default Color tileColor()
	{
		return new Color(255, 0, 0);
	}

	@ConfigItem(
		keyName = "fillOpacity",
		name = "Fill opacity",
		description = "How solid the inside of the tile is, from 0 (outline only) to 255.",
		position = 3
	)
	@Range(min = 0, max = 255)
	default int fillOpacity()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "logGraphicIds",
		name = "Log attack ids",
		description = "Write each new graphic and projectile id seen during the Wardens to the "
			+ "RuneLite log. Used to find the right id if a tile is not being highlighted.",
		position = 4
	)
	default boolean logGraphicIds()
	{
		return true;
	}
}
