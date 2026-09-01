package com.bridge;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(OsrsMcpBridgeConfig.GROUP)
public interface OsrsMcpBridgeConfig extends Config
{
	String GROUP = "osrsmcpbridge";

	@ConfigItem(
		keyName = "ingestUrl",
		name = "Bridge ingest URL",
		description =
			"Base URL of the osrs-mcp server's ingest endpoint (e.g. https://your-server/ingest). "
				+ "Leave blank to export to the local JSON file only, with no push over the network.",
		position = 1
	)
	default String ingestUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "ingestToken",
		name = "Bridge ingest token",
		description = "Bearer token for the ingest URL above. Required if the URL is set.",
		position = 2,
		secret = true
	)
	default String ingestToken()
	{
		return "";
	}
}
