package com.wardens;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Perspective;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines every tile an incoming boulder or lightning bolt is headed for.
 *
 * <p>Reads the live graphics and projectile lists each frame instead of tracking spawns, so a
 * tile disappears the moment the game removes its warning and nothing can be left behind. A
 * boulder's tile clears when it lands, before its rubble animation finishes.
 * Ba-Ba's rockfall and the enrage lightning are graphics sitting on the tile; the phantom's
 * boulders are projectiles, highlighted at the tile they are thrown at.
 */
class WardensHelperOverlay extends Overlay
{
	private final Client client;
	private final WardensHelperConfig config;
	private final WardensHelperPlugin plugin;

	@Inject
	WardensHelperOverlay(Client client, WardensHelperConfig config, WardensHelperPlugin plugin)
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

		Color border = config.tileColor();
		Color fill = new Color(border.getRed(), border.getGreen(), border.getBlue(),
			config.fillOpacity());

		for (GraphicsObject graphic : worldView.getGraphicsObjects())
		{
			if (plugin.isHighlighted(graphic))
			{
				renderTile(graphics, graphic.getLocation(), border, fill);
			}
		}

		for (Projectile projectile : client.getProjectiles())
		{
			if (plugin.isHighlighted(projectile))
			{
				renderTile(graphics, projectile.getTarget(), border, fill);
			}
		}

		return null;
	}

	private void renderTile(Graphics2D graphics, LocalPoint location, Color border, Color fill)
	{
		if (location == null)
		{
			return;
		}
		Polygon poly = Perspective.getCanvasTilePoly(client, location);
		if (poly != null)
		{
			OverlayUtil.renderPolygon(graphics, poly, border, fill, new BasicStroke(2));
		}
	}
}
