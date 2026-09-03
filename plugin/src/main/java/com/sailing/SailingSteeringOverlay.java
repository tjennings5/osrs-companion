package com.sailing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws a bigger steering-direction arrow further from the boat than the vanilla one, which gets
 * blocked by the hull.
 *
 * <p>Only drawn while actively navigating (after clicking "Navigate" on the helm, until "Escape"),
 * not just hovering it — {@link SailingSteeringPlugin} tracks that click and this reads it.
 *
 * <p>The 16 lock directions are true compass headings (world-fixed, not screen-fixed), so they
 * have to be found by actually projecting a world-space point in each heading and comparing
 * on-screen angles — a naive "snap the screen angle to 16 slices" doesn't work because the
 * screen-space angle from the boat to a fixed world heading changes as the camera rotates or
 * pitches. Projecting real points sidesteps needing to know the exact camera-yaw formula/sign.
 */
public class SailingSteeringOverlay extends Overlay
{
	private static final Color ARROW_COLOR = new Color(212, 160, 48); // OSRS-gold, matches launcher theme
	private static final double ARROWHEAD_HALF_ANGLE = Math.toRadians(24);
	/** Arrowhead length scales with shaft thickness, at the same ratio the original fixed sizes had. */
	private static final float ARROWHEAD_LENGTH_PER_THICKNESS = 4f;
	private static final int HEADING_COUNT = 16;

	/** World-unit radius (in local units, 128 per tile) used to probe each heading's screen angle. */
	private static final int HEADING_PROBE_RADIUS = 5 * 128;

	private final Client client;
	private final SailingSteeringConfig config;
	private final SailingSteeringPlugin plugin;

	@Inject
	private SailingSteeringOverlay(Client client, SailingSteeringConfig config, SailingSteeringPlugin plugin)
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
		if (!plugin.isNavigating())
		{
			return null;
		}

		WorldEntity boat = findLocalBoat();
		if (boat == null)
		{
			return null;
		}

		WorldView topLevel = client.getTopLevelWorldView();
		LocalPoint boatLocal = boat.getLocalLocation();
		Point boatScreen = Perspective.localToCanvas(client, boatLocal, topLevel.getPlane());
		if (boatScreen == null)
		{
			return null;
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse.getX() < 0 || mouse.getY() < 0)
		{
			return null;
		}

		double mouseAngle = Math.atan2(mouse.getY() - boatScreen.getY(), mouse.getX() - boatScreen.getX());

		Double angle = null;
		double bestDiff = Double.MAX_VALUE;
		for (int i = 0; i < HEADING_COUNT; i++)
		{
			double heading = i * (2 * Math.PI / HEADING_COUNT);
			// Compass bearing (0 = north = +y in world space, clockwise positive) to a world offset.
			int worldDx = (int) Math.round(Math.sin(heading) * HEADING_PROBE_RADIUS);
			int worldDy = (int) Math.round(Math.cos(heading) * HEADING_PROBE_RADIUS);
			LocalPoint probeLocal = boatLocal.plus(worldDx, worldDy);
			Point probeScreen = Perspective.localToCanvas(client, probeLocal, topLevel.getPlane());
			if (probeScreen == null)
			{
				continue;
			}

			double probeAngle = Math.atan2(probeScreen.getY() - boatScreen.getY(), probeScreen.getX() - boatScreen.getX());
			double diff = Math.abs(angularDistance(mouseAngle, probeAngle));
			if (diff < bestDiff)
			{
				bestDiff = diff;
				angle = probeAngle;
			}
		}
		if (angle == null)
		{
			return null;
		}

		int radius = config.radius();
		int length = config.arrowLength();
		float thickness = config.thickness();
		double arrowheadLength = thickness * ARROWHEAD_LENGTH_PER_THICKNESS;
		// The triangle's tip-to-base depth along its centerline is shorter than arrowheadLength
		// itself (that's the slant length to the base corners), so pulling the shaft back by the
		// full arrowheadLength leaves a sliver of canvas between the two. Pull back by the actual
		// centerline depth, with a couple pixels of overlap so anti-aliasing doesn't reintroduce a
		// hairline gap.
		double shaftPullback = arrowheadLength * Math.cos(ARROWHEAD_HALF_ANGLE) - 2;

		double cos = Math.cos(angle);
		double sin = Math.sin(angle);

		double startX = boatScreen.getX() + cos * radius;
		double startY = boatScreen.getY() + sin * radius;
		double tipX = boatScreen.getX() + cos * (radius + length);
		double tipY = boatScreen.getY() + sin * (radius + length);
		// Stop the shaft short of the tip so the arrowhead's own taper covers it fully - a shaft
		// drawn all the way to the tip pokes out past the head near the point, since the head's
		// width there is narrower than the shaft's stroke width.
		double shaftEndX = tipX - cos * shaftPullback;
		double shaftEndY = tipY - sin * shaftPullback;

		graphics.setColor(ARROW_COLOR);
		graphics.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		graphics.draw(new Line2D.Double(startX, startY, shaftEndX, shaftEndY));
		drawArrowHead(graphics, tipX, tipY, angle, arrowheadLength);

		return null;
	}

	/** Shortest signed distance from {@code a} to {@code b}, both radians, wrapped to [-pi, pi]. */
	private static double angularDistance(double a, double b)
	{
		double diff = (b - a) % (2 * Math.PI);
		if (diff > Math.PI)
		{
			diff -= 2 * Math.PI;
		}
		else if (diff < -Math.PI)
		{
			diff += 2 * Math.PI;
		}
		return diff;
	}

	private static void drawArrowHead(Graphics2D graphics, double tipX, double tipY, double angle, double length)
	{
		int[] xs = new int[3];
		int[] ys = new int[3];
		xs[0] = (int) tipX;
		ys[0] = (int) tipY;
		xs[1] = (int) (tipX - length * Math.cos(angle - ARROWHEAD_HALF_ANGLE));
		ys[1] = (int) (tipY - length * Math.sin(angle - ARROWHEAD_HALF_ANGLE));
		xs[2] = (int) (tipX - length * Math.cos(angle + ARROWHEAD_HALF_ANGLE));
		ys[2] = (int) (tipY - length * Math.sin(angle + ARROWHEAD_HALF_ANGLE));

		graphics.fill(new Polygon(xs, ys, 3));
	}

	/**
	 * The boat is a separate sub-scene (WorldView); being on one means the local player's world
	 * view differs from the client's top-level one. From there we find the WorldEntity in the
	 * top-level view whose owned WorldView matches, which is the boat itself.
	 */
	private WorldEntity findLocalBoat()
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		WorldView playerView = client.getLocalPlayer().getWorldView();
		WorldView topLevel = client.getTopLevelWorldView();
		if (playerView == null || topLevel == null || playerView.getId() == topLevel.getId())
		{
			return null;
		}

		for (WorldEntity entity : topLevel.worldEntities())
		{
			WorldView entityView = entity.getWorldView();
			if (entityView != null && entityView.getId() == playerView.getId())
			{
				return entity;
			}
		}
		return null;
	}
}
