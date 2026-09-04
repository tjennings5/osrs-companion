package com.spawntimer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws a highlight on the NPC actively being tracked, and a countdown or "confirmed" marker on
 * its spawn tile once one's been learned.
 */
class SpawnTimerOverlay extends Overlay
{
	private final Client client;
	private final SpawnTimerConfig config;
	private final SpawnTimerPlugin plugin;

	@Inject
	SpawnTimerOverlay(Client client, SpawnTimerConfig config, SpawnTimerPlugin plugin)
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
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		Font priorFont = graphics.getFont();
		Object priorAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD,
			(float) config.fontSize()));

		try
		{
			for (SpawnMarker marker : plugin.getMarkers())
			{
				if (marker.trackedNpcIndex >= 0)
				{
					renderTrackedNpc(graphics, worldView, marker);
				}
				else if (marker.waiting)
				{
					renderWaiting(graphics, marker);
				}
			}
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

	/** Outlines the live NPC so it's obvious which instance Ctrl+right-click actually tagged. */
	private void renderTrackedNpc(Graphics2D graphics, WorldView worldView, SpawnMarker marker)
	{
		NPC npc = worldView.npcs().byIndex(marker.trackedNpcIndex);
		if (npc == null)
		{
			return;
		}

		Shape hull = npc.getConvexHull();
		if (hull != null)
		{
			OverlayUtil.renderPolygon(graphics, hull, config.trackingColor());
		}

		if (marker.tileConfirmed && config.showTileOutline())
		{
			drawTileOutline(graphics, marker.worldPoint(), config.countingColor());
		}
	}

	/** Draws the countdown (once the tile is known) or a "searching" marker (before it is). */
	private void renderWaiting(Graphics2D graphics, SpawnMarker marker)
	{
		if (!marker.tileConfirmed)
		{
			if (marker.deathLocation != null)
			{
				renderText(graphics, marker.deathLocation, "...", config.learningColor(), false);
			}
			return;
		}

		int remaining = marker.learnedTicks - (client.getTickCount() - marker.diedOnTick);
		boolean overdue = remaining <= 0;
		Color color = overdue ? config.overdueColor() : config.countingColor();

		if (config.showTileOutline())
		{
			drawTileOutline(graphics, marker.worldPoint(), color);
		}

		String text = overdue ? "?" : Integer.toString(remaining);
		renderText(graphics, marker.worldPoint(), text, color, true);
	}

	private void drawTileOutline(Graphics2D graphics, WorldPoint point,
		Color color)
	{
		LocalPoint location = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
		if (location == null)
		{
			return;
		}
		Polygon poly = Perspective.getCanvasTilePoly(client, location);
		if (poly == null)
		{
			return;
		}
		graphics.setStroke(new BasicStroke(2));
		graphics.setColor(color);
		graphics.drawPolygon(poly);
	}

	private void renderText(Graphics2D graphics, WorldPoint point,
		String text, Color color, boolean shadow)
	{
		LocalPoint location = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
		if (location == null)
		{
			return;
		}
		Point anchor = Perspective.getCanvasTextLocation(client, graphics, location, text, 0);
		if (anchor == null)
		{
			return;
		}

		if (shadow)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, anchor.getX() + 1, anchor.getY() + 1);
		}
		graphics.setColor(color);
		graphics.drawString(text, anchor.getX(), anchor.getY());
	}
}
