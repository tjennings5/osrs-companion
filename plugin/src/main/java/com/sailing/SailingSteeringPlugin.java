package com.sailing;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Draws a bigger sailing steering-direction arrow further from the boat, since the vanilla one
 * gets blocked by the hull and has no toggle to hide it. Doesn't suppress the vanilla arrow (no
 * public hook to do that) — this just gives you a second, unobstructed one to steer by instead.
 *
 * <p>Shown only while actively navigating: the helm's context menu has "Navigate" and "Escape"
 * options (confirmed live), which is how the game itself enters/exits steering mode, so this
 * tracks that same click rather than just hovering the helm.
 */
@PluginDescriptor(
	name = "Sailing Steering Arrow",
	description = "Draws a bigger steering direction arrow further from the boat, unobstructed by the hull",
	tags = {"sailing", "boat", "steering", "arrow"}
)
public class SailingSteeringPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SailingSteeringOverlay overlay;

	private boolean navigating;

	@Provides
	SailingSteeringConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SailingSteeringConfig.class);
	}

	@Override
	protected void startUp()
	{
		navigating = false;
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
	}

	boolean isNavigating()
	{
		return navigating;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.GAME_OBJECT_FIRST_OPTION
			&& event.getMenuAction() != MenuAction.GAME_OBJECT_FOURTH_OPTION)
		{
			return;
		}

		String option = event.getMenuOption();
		if ("Navigate".equals(option))
		{
			navigating = true;
		}
		else if ("Escape".equals(option))
		{
			navigating = false;
		}
	}
}
