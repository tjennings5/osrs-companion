package com.farmrun;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("farmrun")
public interface FarmRunConfig extends Config
{
	@ConfigSection(
		name = "Player-Owned House",
		description = "Which POH features to use as teleports. Requires 1 law + 1 earth + 1 air rune (or 1 law + 1 dust rune) for the house teleport spell.",
		position = 10
	)
	String pohSection = "poh";

	@ConfigItem(
		keyName = "herbSeed",
		name = "Herb seed",
		description = "Which herb you are planting on this run"
	)
	default HerbSeed herbSeed()
	{
		return HerbSeed.RANARR;
	}

	@ConfigItem(
		keyName = "treeSapling",
		name = "Tree sapling",
		description = "Which tree sapling you are planting on the tree run"
	)
	default TreeSapling treeSapling()
	{
		return TreeSapling.YEW;
	}

	@ConfigItem(
		keyName = "compostType",
		name = "Compost",
		description = "Compost type to include in the bank checklist"
	)
	default CompostType compostType()
	{
		return CompostType.ULTRACOMPOST;
	}

	@ConfigItem(
		keyName = "payFarmer",
		name = "Pay farmer",
		description = "Include a farmer payment reminder in the bank checklist"
	)
	default boolean payFarmer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoOpenNearGuild",
		name = "Auto-open near guild",
		description = "Automatically open the farm run panel when you are near the Farming Guild"
	)
	default boolean autoOpenNearGuild()
	{
		return true;
	}

	// --- POH portals ---

	@ConfigItem(
		keyName = "pohTrollheim",
		name = "Trollheim portal",
		description = "POH has a Trollheim portal or nexus entry",
		section = pohSection
	)
	default boolean pohTrollheim()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohArdougne",
		name = "Ardougne portal",
		description = "POH has an Ardougne portal or nexus entry",
		section = pohSection
	)
	default boolean pohArdougne()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohCamelot",
		name = "Camelot portal",
		description = "POH has a Camelot portal or nexus entry (Catherby patch)",
		section = pohSection
	)
	default boolean pohCamelot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohKharyll",
		name = "Kharyll portal",
		description = "POH has a Kharyll portal or nexus entry (Morytania patch)",
		section = pohSection
	)
	default boolean pohKharyll()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohFalador",
		name = "Falador portal",
		description = "POH has a Falador portal or nexus entry",
		section = pohSection
	)
	default boolean pohFalador()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohXeric",
		name = "Xeric's Talisman portal",
		description = "POH nexus has Xeric's Talisman stored (Hosidius patch)",
		section = pohSection
	)
	default boolean pohXeric()
	{
		return false;
	}

	@ConfigItem(
		keyName = "treePayWatcher",
		name = "Pay tree watcher",
		description = "Include farmer protection payment items in the tree run bank checklist (e.g. cactus spines for yew)"
	)
	default boolean treePayWatcher()
	{
		return true;
	}

	@ConfigItem(
		keyName = "treePayRemoval",
		name = "Pay for tree removal",
		description = "Include coins in the tree run bank checklist for paying the farmer to instantly remove a grown tree"
	)
	default boolean treePayRemoval()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohVarrock",
		name = "Varrock portal",
		description = "POH has a Varrock portal or nexus entry (Varrock tree patch)",
		section = pohSection
	)
	default boolean pohVarrock()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohLumbridge",
		name = "Lumbridge portal",
		description = "POH has a Lumbridge portal or nexus entry (Lumbridge tree patch)",
		section = pohSection
	)
	default boolean pohLumbridge()
	{
		return false;
	}

	@ConfigItem(
		keyName = "herbSetupName",
		name = "Herb run setup name",
		description = "Name of your Inventory Setups setup for herb runs. Leave blank to skip bank filtering."
	)
	default String herbSetupName()
	{
		return "Herb Run";
	}

	@ConfigItem(
		keyName = "treeSetupName",
		name = "Tree run setup name",
		description = "Name of your Inventory Setups setup for tree runs. Leave blank to skip bank filtering."
	)
	default String treeSetupName()
	{
		return "Tree Run";
	}
}
