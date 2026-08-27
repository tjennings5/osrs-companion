package com.bridge;

import com.dropHighlighter.DropHighlighterPlugin;
import com.araxxor.AraxxorHelperPlugin;
import com.cerberus.CerberusHelperPlugin;
import com.farmrun.FarmRunPlugin;
import com.hello.HelloPlugin;
import com.kalphite.KalphiteFlinchPlugin;
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
			FarmRunPlugin.class,
			HelloPlugin.class);
		RuneLite.main(args);
	}
}
