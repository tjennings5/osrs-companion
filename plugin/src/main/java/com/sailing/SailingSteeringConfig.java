package com.sailing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("sailingsteering")
public interface SailingSteeringConfig extends Config
{
	@Range(min = 30, max = 400)
	@ConfigItem(
		keyName = "radius",
		name = "Arrow distance",
		description = "How far from the boat, in pixels, the replacement arrow is drawn",
		position = 0
	)
	default int radius()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "arrowLength",
		name = "Arrow length",
		description = "Length of the arrow itself, in pixels",
		position = 1
	)
	default int arrowLength()
	{
		return 60;
	}

	@Range(min = 2, max = 20)
	@ConfigItem(
		keyName = "thickness",
		name = "Arrow size",
		description = "Overall thickness of the arrow - scales both the shaft width and the arrowhead size",
		position = 2
	)
	default int thickness()
	{
		return 5;
	}
}
