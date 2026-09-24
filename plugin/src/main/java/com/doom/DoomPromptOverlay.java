package com.doom;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Prompts above the player's head, plus floor markings: the halberd range
 * ring and the car phase charge lane.
 *
 * Only shows what needs doing right now: the rock type while its follow-ups
 * are in the air, a charge call only while the boss is charging. Prayers are
 * called on the prayer tab instead ({@link DoomPrayerOverlay}). No flashing,
 * same as the other helpers here.
 */
class DoomPromptOverlay extends Overlay
{
	private static final Color PUNISH = new Color(255, 150, 60);
	private static final Color BURROW = new Color(240, 210, 90);
	private static final Color SWAP = new Color(235, 235, 235);
	private static final Color WARN = new Color(255, 140, 60);
	private static final Color OK = new Color(120, 230, 120);
	private static final Color PRAYER_POT = new Color(90, 210, 230);
	private static final Color RING = new Color(255, 150, 60, 200);
	private static final Color PATH_FILL = new Color(240, 210, 90, 60);
	private static final Color PATH_EDGE = new Color(240, 210, 90, 200);
	private static final Color SLAM = new Color(255, 110, 90);

	/** The boss is 5x5, so its lane and landing square extend this many tiles either side of its centre. */
	private static final double BOSS_HALF_WIDTH = 2.5;

	/** Crystal halberd's special attack cost. */
	private static final int HALBERD_SPEC_COST = 30;

	/**
	 * Halberd ring half-width in tiles from the boss' centre tile. The boss is
	 * 5x5, so 4 tiles from centre is 2 from its edge: halberd reach, one tile
	 * short of the tongue lash. Same as the wiki's Radius Markers setup.
	 */
	private static final int HALBERD_RING_RADIUS = 4;

	@RequiredArgsConstructor
	private static final class Prompt
	{
		final String text;
		final Color color;
	}

	private final Client client;
	private final DoomHelperPlugin plugin;
	private final DoomHelperConfig config;

	@Inject
	DoomPromptOverlay(Client client, DoomHelperPlugin plugin, DoomHelperConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInFight())
		{
			return null;
		}

		DoomTracker tracker = plugin.getTracker();
		DoomTracker.Charge charge = tracker.getCharge();

		if (charge == DoomTracker.Charge.MELEE && config.halberdRing())
		{
			drawHalberdRing(graphics);
		}

		if (config.carPath() && tracker.isChargePathActive(plugin.getTick()))
		{
			drawChargePath(graphics, tracker.getChargeStart(), tracker.getEyeLocation());
		}

		List<Prompt> prompts = buildPrompts(tracker, charge);
		if (!prompts.isEmpty())
		{
			drawPrompts(graphics, prompts);
		}
		return null;
	}

	/** Most important first; drawn closest to the player's head. */
	private List<Prompt> buildPrompts(DoomTracker tracker, DoomTracker.Charge charge)
	{
		int tick = plugin.getTick();
		List<Prompt> prompts = new ArrayList<>(3);

		if (config.prayerPotPrompt() && plugin.getPrayerPoints() < config.prayerPotThreshold())
		{
			prompts.add(new Prompt("DRINK PRAYER  " + plugin.getPrayerPoints(), PRAYER_POT));
		}

		if (config.rockCallout())
		{
			AttackStyle rock = tracker.getRockCallout(tick);
			if (rock != null)
			{
				boolean stomp = config.rockStompWarning() && tracker.isRockDuringShockwave(tick);
				prompts.add(new Prompt(rock.getLabel() + " ROCK" + (stomp ? " + STOMP" : ""), rock.getColor()));
			}
		}

		if (config.slamPrompt())
		{
			int slam = tracker.ticksUntilSlam(tick);
			if (slam >= 0)
			{
				prompts.add(new Prompt("SLAM " + slam + " - GET BEHIND A ROCK", SLAM));
			}
		}

		if (config.chargePrompt())
		{
			switch (charge)
			{
				case MELEE:
					prompts.add(new Prompt(plugin.getSpecPercent() >= HALBERD_SPEC_COST
						? "PUNISH: HALBERD SPEC" : "PUNISH: HALBERD", PUNISH));
					break;
				case BURROW:
					prompts.add(new Prompt("ANY ATTACK", BURROW));
					break;
				default:
					break;
			}
		}

		if (config.bowBackPrompt() && charge != DoomTracker.Charge.MELEE && plugin.isHalberdEquipped())
		{
			int left = tracker.ticksUntilAttackAfterPunish(tick);
			if (left >= 0)
			{
				prompts.add(new Prompt("BOW BACK  " + left, SWAP));
			}
		}

		if (config.lastWavePrompt() && tracker.isShockwavePending(tick) && tracker.isEarthenShieldMade())
		{
			prompts.add(new Prompt("LAST WAVE  " + tracker.ticksUntilLastWave(tick), OK));
		}

		if (config.shockwavePrompt() && tracker.isShockwavePending(tick) && !tracker.isEarthenShieldMade())
		{
			int left = tracker.ticksToBreakOrb(tick, plugin.getOrbHitDelay(), config.orbTimingOffset());
			if (left >= 0)
			{
				prompts.add(new Prompt("BREAK 2ND ORB  " + left, WARN));
			}
		}

		return prompts;
	}

	private void drawPrompts(Graphics2D graphics, List<Prompt> prompts)
	{
		Player player = client.getLocalPlayer();
		LocalPoint location = player == null ? null : player.getLocalLocation();
		if (location == null)
		{
			return;
		}

		Font priorFont = graphics.getFont();
		Object priorAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, (float) config.fontSize()));

		try
		{
			int lineHeight = graphics.getFontMetrics().getHeight();
			int height = player.getLogicalHeight() + 40;
			for (int i = 0; i < prompts.size(); i++)
			{
				Prompt prompt = prompts.get(i);
				Point anchor = Perspective.getCanvasTextLocation(client, graphics, location, prompt.text, height);
				if (anchor == null)
				{
					return;
				}
				int y = anchor.getY() - i * lineHeight;
				graphics.setColor(Color.BLACK);
				graphics.drawString(prompt.text, anchor.getX() + 1, y + 1);
				graphics.setColor(prompt.color);
				graphics.drawString(prompt.text, anchor.getX(), y);
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
	}

	/** Square outline HALBERD_RING_RADIUS tiles out from the boss' centre, traced tile by tile so it hugs the floor. */
	private void drawHalberdRing(Graphics2D graphics)
	{
		NPC boss = plugin.findBoss();
		LocalPoint center = boss == null ? null : boss.getLocalLocation();
		if (center == null)
		{
			return;
		}

		int tile = Perspective.LOCAL_TILE_SIZE;
		int half = HALBERD_RING_RADIUS * tile + tile / 2;
		int minX = center.getX() - half;
		int minY = center.getY() - half;
		int maxX = center.getX() + half;
		int maxY = center.getY() + half;
		int plane = client.getTopLevelWorldView().getPlane();
		int steps = (maxX - minX) / tile;

		graphics.setColor(RING);
		graphics.setStroke(new BasicStroke(2));
		for (int i = 0; i < steps; i++)
		{
			int a = minX + i * tile;
			int b = a + tile;
			int c = minY + i * tile;
			int d = c + tile;
			segment(graphics, a, minY, b, minY, center, plane);
			segment(graphics, a, maxY, b, maxY, center, plane);
			segment(graphics, minX, c, minX, d, center, plane);
			segment(graphics, maxX, c, maxX, d, center, plane);
		}
	}

	/**
	 * Shades the 5-wide lane from where the boss is to its eye, plus the 5x5
	 * square it lands on. The arena floor is flat, so projecting the four
	 * corners is enough.
	 */
	private void drawChargePath(Graphics2D graphics, LocalPoint from, LocalPoint to)
	{
		if (to == null)
		{
			return;
		}
		int plane = client.getTopLevelWorldView().getPlane();
		double half = BOSS_HALF_WIDTH * Perspective.LOCAL_TILE_SIZE;

		Polygon landing = quad(to, plane,
			to.getX() - half, to.getY() - half, to.getX() + half, to.getY() - half,
			to.getX() + half, to.getY() + half, to.getX() - half, to.getY() + half);

		Polygon lane = null;
		if (from != null && (from.getX() != to.getX() || from.getY() != to.getY()))
		{
			double dx = to.getX() - from.getX();
			double dy = to.getY() - from.getY();
			double len = Math.hypot(dx, dy);
			// Perpendicular offset for the lane's sides.
			double px = -dy / len * half;
			double py = dx / len * half;
			lane = quad(to, plane,
				from.getX() + px, from.getY() + py, to.getX() + px, to.getY() + py,
				to.getX() - px, to.getY() - py, from.getX() - px, from.getY() - py);
		}

		graphics.setStroke(new BasicStroke(2));
		for (Polygon poly : new Polygon[]{lane, landing})
		{
			if (poly == null)
			{
				continue;
			}
			graphics.setColor(PATH_FILL);
			graphics.fill(poly);
			graphics.setColor(PATH_EDGE);
			graphics.draw(poly);
		}
	}

	/** Projects four local-space corners to a screen polygon, or null if any is off screen. */
	private Polygon quad(LocalPoint ref, int plane, double... xy)
	{
		Polygon poly = new Polygon();
		for (int i = 0; i < xy.length; i += 2)
		{
			Point p = Perspective.localToCanvas(client,
				new LocalPoint((int) xy[i], (int) xy[i + 1], ref.getWorldView()), plane);
			if (p == null)
			{
				return null;
			}
			poly.addPoint(p.getX(), p.getY());
		}
		return poly;
	}

	private void segment(Graphics2D graphics, int x1, int y1, int x2, int y2, LocalPoint ref, int plane)
	{
		Point p1 = Perspective.localToCanvas(client, new LocalPoint(x1, y1, ref.getWorldView()), plane);
		Point p2 = Perspective.localToCanvas(client, new LocalPoint(x2, y2, ref.getWorldView()), plane);
		if (p1 != null && p2 != null)
		{
			graphics.drawLine(p1.getX(), p1.getY(), p2.getX(), p2.getY());
		}
	}
}
