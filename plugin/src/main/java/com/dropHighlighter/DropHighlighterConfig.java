package com.dropHighlighter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(DropHighlighterConfig.GROUP)
public interface DropHighlighterConfig extends Config
{
	String GROUP = "drophighlighter";

	/**
	 * Where the panel's item -> colour selections are persisted. Not declared as a
	 * {@link ConfigItem} on purpose: it is machine-written JSON, and surfacing it in
	 * the settings UI would only invite it being hand-edited into an unparseable state.
	 */
	String KEY_HIGHLIGHTS = "highlights";

	@ConfigSection(
		name = "Beam",
		description = "Shape and colour of the light beam drawn over a highlighted drop.",
		position = 0
	)
	String beamSection = "beam";

	@ConfigSection(
		name = "Text",
		description = "The item name label drawn above the beam.",
		position = 1
	)
	String textSection = "text";

	@ConfigSection(
		name = "Menu",
		description = "The \"Highlight Drops\" option on an NPC's right-click menu.",
		position = 2
	)
	String menuSection = "menu";

	@ConfigSection(
		name = "Testing",
		description = "Manual overrides, useful before the side panel is in use.",
		position = 3,
		closedByDefault = true
	)
	String testingSection = "testing";

	@ConfigItem(
		keyName = "menuModifier",
		name = "Show menu entry on",
		description = "Which key to hold when right-clicking an NPC for \"Highlight Drops\" to "
			+ "appear. Keeps the normal right-click menu untouched.",
		section = menuSection,
		position = 0
	)
	default MenuModifier menuModifier()
	{
		return MenuModifier.CTRL;
	}

	@ConfigItem(
		keyName = "beamHeight",
		name = "Beam height",
		description = "How far the beam reaches above the tile, in game units. A tile is 128 units across.",
		section = beamSection,
		position = 0
	)
	@Range(min = 50, max = 600)
	default int beamHeight()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "beamWidth",
		name = "Beam width",
		description = "Width of the column of light in pixels. The drawn glow spreads a little "
			+ "wider than this on each side.",
		section = beamSection,
		position = 1
	)
	@Range(min = 1, max = 40)
	default int beamWidth()
	{
		return 12;
	}

	@ConfigItem(
		keyName = "beamOpacity",
		name = "Beam opacity",
		description = "Opacity of the beam at ground level, 0-255. It fades to fully transparent at the top.",
		section = beamSection,
		position = 2
	)
	@Range(min = 0, max = 255)
	default int beamOpacity()
	{
		return 180;
	}

	@ConfigItem(
		keyName = "maxRenderDistance",
		name = "Max render distance",
		description = "Only draw beams within this many tiles of you.",
		section = beamSection,
		position = 3
	)
	@Range(min = 1, max = 104)
	default int maxRenderDistance()
	{
		return 32;
	}

	@ConfigItem(
		keyName = "showItemNames",
		name = "Show item names",
		description = "Draw the item name above the beam, in that item's assigned colour.",
		section = textSection,
		position = 0
	)
	default boolean showItemNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showQuantity",
		name = "Show quantity",
		description = "Append the stack size to the item name when there is more than one.",
		section = textSection,
		position = 1
	)
	default boolean showQuantity()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "Size of the item name label.",
		section = textSection,
		position = 2
	)
	@Range(min = 8, max = 24)
	default int fontSize()
	{
		return 12;
	}

	@ConfigItem(
		keyName = "textShadow",
		name = "Text shadow",
		description = "Draw a black drop shadow behind the label so it stays readable over bright ground.",
		section = textSection,
		position = 3
	)
	default boolean textShadow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "seedHighlights",
		name = "Manual highlights",
		description = "Comma-separated itemId:#RRGGBB pairs, e.g. \"526:#FF0000, 2:#00FFFF\". "
			+ "Merged with whatever the side panel has selected; the panel wins on conflicts.",
		section = testingSection,
		position = 0
	)
	default String seedHighlights()
	{
		return "";
	}
}
