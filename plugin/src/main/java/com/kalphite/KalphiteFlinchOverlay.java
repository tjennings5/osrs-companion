package com.kalphite;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the flinch countdown over the player's head.
 *
 * <p>Deliberately plain: a number that ticks down, and a word when it hits zero. No flashing or
 * colour cycling — movement at the edge of vision is the last thing you want while watching for
 * the moment to step back in.
 */
class KalphiteFlinchOverlay extends Overlay
{
	private final Client client;
	private final KalphiteFlinchConfig config;
	private final KalphiteFlinchPlugin plugin;

	@Inject
	KalphiteFlinchOverlay(Client client, KalphiteFlinchConfig config, KalphiteFlinchPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isActive())
		{
			return null;
		}

		FlinchTimer timer = plugin.getTimer();
		boolean ready = timer.isReady();
		if (ready && !config.showReady())
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		LocalPoint location = player.getLocalLocation();
		if (location == null)
		{
			return null;
		}

		String text = ready
			? config.readyText()
			: Integer.toString(timer.getTicksRemaining());

		Font priorFont = graphics.getFont();
		Object priorAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD,
			(float) config.fontSize()));

		try
		{
			int height = player.getLogicalHeight() + config.heightOffset();
			Point anchor = Perspective.getCanvasTextLocation(client, graphics, location, text,
				height);
			if (anchor == null)
			{
				return null;
			}

			// Shadow first so the number stays readable against the sand.
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, anchor.getX() + 1, anchor.getY() + 1);
			graphics.setColor(ready ? config.readyColor() : config.waitColor());
			graphics.drawString(text, anchor.getX(), anchor.getY());
		}
		finally
		{
			graphics.setFont(priorFont);
			if (priorAntialiasing != null)
			{
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, priorAntialiasing);
			}
		}

		return null;
	}
}
