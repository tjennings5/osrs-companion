package com.test;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
	name = "Auto Update Test",
	description = "Temporary plugin to verify auto-update works - delete after testing",
	tags = {"test"}
)
public class AutoUpdateTestPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.info("Auto Update Test plugin loaded - auto-update is working!");
	}

	@Override
	protected void shutDown()
	{
		log.info("Auto Update Test plugin unloaded.");
	}
}
