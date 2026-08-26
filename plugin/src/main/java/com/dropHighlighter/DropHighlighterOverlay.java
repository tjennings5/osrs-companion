package com.dropHighlighter;

import com.dropHighlighter.GroundItemTracker.TrackedItem;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws a soft column of light over every tile holding a highlighted drop, with the item names
 * stacked above it.
 *
 * <p>One beam per tile, not one per item. Several beams on the same tile would sit within a pixel
 * or two of each other and read as a single muddy smear; the names carry the per-item colour
 * instead, and the beam takes the colour of the highest-priority item on that tile.
 */
class DropHighlighterOverlay extends Overlay
{
	/**
	 * How wide the top of the beam is relative to its base. Near 1 so it reads as a column of
	 * light rather than a spike; the fade to transparent does the work of ending it.
	 */
	private static final float TIP_WIDTH_RATIO = 0.8f;

	/** Nested passes used to fake soft edges. More is smoother and costs another fill each. */
	private static final int BEAM_LAYERS = 4;

	/** Ground pool radius, as a multiple of the beam's half width. */
	private static final float GROUND_GLOW_SCALE = 3.5f;

	/** Gap in pixels between the top of the beam and the first label. */
	private static final int LABEL_GAP = 4;

	private final Client client;
	private final DropHighlighterConfig config;
	private final GroundItemTracker tracker;
	private final HighlightManager highlights;
	private final ItemManager itemManager;

	/** Item names never change, so this only ever grows to the size of the highlight set. */
	private final Map<Integer, String> nameCache = new HashMap<>();

	@Inject
	DropHighlighterOverlay(Client client, DropHighlighterConfig config, GroundItemTracker tracker,
		HighlightManager highlights, ItemManager itemManager)
	{
		this.client = client;
		this.config = config;
		this.tracker = tracker;
		this.highlights = highlights;
		this.itemManager = itemManager;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<Tile, List<TrackedItem>> tiles = tracker.getTiles();
		if (tiles.isEmpty())
		{
			return null;
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		WorldPoint playerLocation = local.getWorldLocation();
		int plane = worldView.getPlane();
		int maxDistance = config.maxRenderDistance();

		Object priorAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		Font priorFont = graphics.getFont();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, config.fontSize()));

		try
		{
			for (Map.Entry<Tile, List<TrackedItem>> entry : tiles.entrySet())
			{
				Tile tile = entry.getKey();
				if (tile.getPlane() != plane)
				{
					continue;
				}

				WorldPoint tileLocation = tile.getWorldLocation();
				if (tileLocation == null || tileLocation.distanceTo(playerLocation) > maxDistance)
				{
					continue;
				}

				LocalPoint localPoint = tile.getLocalLocation();
				if (localPoint == null)
				{
					continue;
				}

				renderTile(graphics, tile, localPoint, entry.getValue());
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

	private void renderTile(Graphics2D graphics, Tile tile, LocalPoint localPoint,
		List<TrackedItem> items)
	{
		if (items.isEmpty())
		{
			return;
		}

		int plane = tile.getPlane();
		int height = config.beamHeight();

		Point base = Perspective.localToCanvas(client, localPoint, plane, 0);
		Point tip = Perspective.localToCanvas(client, localPoint, plane, height);
		if (base == null || tip == null)
		{
			return;
		}

		Color beamColor = highlights.colorFor(items.get(0).getItemId());
		if (beamColor != null)
		{
			drawBeam(graphics, base, tip, beamColor);
		}

		if (config.showItemNames())
		{
			drawLabels(graphics, localPoint, height, items);
		}
	}

	/**
	 * A soft column of light, modelled on the shaft over the Chambers of Xeric reward chest.
	 *
	 * <p>Two things make it read as light rather than as a drawn shape. It stays roughly a column
	 * instead of tapering to a point, and its edges are soft: the body is painted as several
	 * nested layers, widest and faintest first, so alpha accumulates towards a bright core. Real
	 * light has no outline, and a single hard-edged polygon always looks like a triangle.
	 *
	 * <p>Layering is used rather than a horizontal gradient because the beam is not axis-aligned
	 * on screen — it leans with the camera, and a horizontal paint would not lean with it.
	 */
	private void drawBeam(Graphics2D graphics, Point base, Point tip, Color color)
	{
		// GradientPaint rejects two identical control points, which happens when the camera is
		// looking straight down and the beam projects onto a single pixel.
		if (base.getX() == tip.getX() && base.getY() == tip.getY())
		{
			return;
		}

		int opacity = config.beamOpacity();
		float halfWidth = config.beamWidth() / 2f;

		// Glow pool where the shaft meets the ground; the brightest part of the effect.
		float glowRadius = halfWidth * GROUND_GLOW_SCALE;
		graphics.setPaint(new RadialGradientPaint(
			new Point2D.Float(base.getX(), base.getY()), Math.max(glowRadius, 1f),
			new float[]{0f, 1f},
			new Color[]{withAlpha(color, opacity), withAlpha(color, 0)}));
		graphics.fill(new Ellipse2D.Float(
			base.getX() - glowRadius, base.getY() - glowRadius / 2f,
			glowRadius * 2f, glowRadius));

		for (int layer = 0; layer < BEAM_LAYERS; layer++)
		{
			// Widest layer first at a fraction of the alpha, narrowing and brightening inward.
			float scale = 1f - (layer / (float) BEAM_LAYERS);
			float baseHalf = halfWidth * (0.35f + scale);
			float tipHalf = baseHalf * TIP_WIDTH_RATIO;
			int layerAlpha = Math.round(opacity / (float) BEAM_LAYERS);

			Path2D.Float beam = new Path2D.Float();
			beam.moveTo(base.getX() - baseHalf, base.getY());
			beam.lineTo(base.getX() + baseHalf, base.getY());
			beam.lineTo(tip.getX() + tipHalf, tip.getY());
			beam.lineTo(tip.getX() - tipHalf, tip.getY());
			beam.closePath();

			graphics.setPaint(new GradientPaint(
				base.getX(), base.getY(), withAlpha(color, layerAlpha),
				tip.getX(), tip.getY(), withAlpha(color, 0)));
			graphics.fill(beam);
		}
	}

	/**
	 * Labels stack upward from the beam tip with the highest-priority item on top, which is the
	 * reading order the beam's own colour already implies.
	 */
	private void drawLabels(Graphics2D graphics, LocalPoint localPoint, int height,
		List<TrackedItem> items)
	{
		int lineHeight = graphics.getFontMetrics().getHeight();
		int count = items.size();

		for (int i = 0; i < count; i++)
		{
			TrackedItem item = items.get(i);
			Color color = highlights.colorFor(item.getItemId());
			if (color == null)
			{
				continue;
			}

			String text = label(item);

			// Re-anchored per item because the helper centres on the string's own width.
			Point anchor = Perspective.getCanvasTextLocation(client, graphics, localPoint, text,
				height);
			if (anchor == null)
			{
				continue;
			}

			int x = anchor.getX();
			int y = anchor.getY() - LABEL_GAP - (count - 1 - i) * lineHeight;

			if (config.textShadow())
			{
				graphics.setColor(Color.BLACK);
				graphics.drawString(text, x + 1, y + 1);
			}
			graphics.setColor(color);
			graphics.drawString(text, x, y);
		}
	}

	private String label(TrackedItem item)
	{
		String name = nameCache.computeIfAbsent(item.getItemId(),
			id -> itemManager.getItemComposition(id).getName());

		int quantity = item.getTileItem().getQuantity();
		if (config.showQuantity() && quantity > 1)
		{
			return name + " (" + quantity + ")";
		}
		return name;
	}

	private static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(alpha));
	}

	private static int clamp(int alpha)
	{
		return Math.max(0, Math.min(255, alpha));
	}
}
