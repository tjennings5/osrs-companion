package com.osrsmcp.bridge;

import com.dropHighlighter.DropHighlighterPlugin;
import com.osrsmcp.araxxor.AraxxorHelperPlugin;
import com.osrsmcp.cerberus.CerberusHelperPlugin;
import com.osrsmcp.farmrun.FarmRunPlugin;
import com.osrsmcp.kalphite.KalphiteFlinchPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsMcpBridgePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(
			OsrsMcpBridgePlugin.class,
			CerberusHelperPlugin.class,
			AraxxorHelperPlugin.class,
			DropHighlighterPlugin.class,
			KalphiteFlinchPlugin.class,
			FarmRunPlugin.class);
		RuneLite.main(args);
	}
}
