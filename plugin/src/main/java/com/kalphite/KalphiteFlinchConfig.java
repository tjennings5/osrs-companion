package com.kalphite;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KalphiteFlinchConfig.GROUP)
public interface KalphiteFlinchConfig extends Config
{
	String GROUP = "kalphiteflinch";

	@ConfigItem(
		keyName = "flinchTicks",
		name = "Flinch interval (ticks)",
		description = "How many game ticks to count down after each attack. Adjustable because "
			+ "the right number depends on your weapon speed and how far you step out.",
		position = 0
	)
	@Range(min = 1, max = 30)
	default int flinchTicks()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "showReady",
		name = "Show when ready",
		description = "Keep showing a marker once the countdown reaches zero, instead of hiding it.",
		position = 1
	)
	default boolean showReady()
	{
		return true;
	}

	@ConfigItem(
		keyName = "readyText",
		name = "Ready text",
		description = "What to show once the countdown has elapsed.",
		position = 2
	)
	default String readyText()
	{
		return "ATTACK";
	}

	@ConfigItem(
		keyName = "onlyNearQueen",
		name = "Only near the Queen",
		description = "Only run while a Kalphite Queen is in the scene. Turn off to use the timer "
			+ "on anything you are attacking.",
		position = 3
	)
	default boolean onlyNearQueen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "Size of the countdown above your character.",
		position = 4
	)
	@Range(min = 8, max = 48)
	default int fontSize()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "heightOffset",
		name = "Height above player",
		description = "How far above your character the countdown sits, in game units.",
		position = 5
	)
	@Range(min = 0, max = 400)
	default int heightOffset()
	{
		return 60;
	}

	@Alpha
	@ConfigItem(
		keyName = "waitColor",
		name = "Counting down colour",
		description = "Colour while you are waiting.",
		position = 6
	)
	default Color waitColor()
	{
		return new Color(255, 80, 80);
	}

	@Alpha
	@ConfigItem(
		keyName = "readyColor",
		name = "Ready colour",
		description = "Colour once you can attack again.",
		position = 7
	)
	default Color readyColor()
	{
		return new Color(90, 255, 110);
	}
}
