package com.doom;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
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
 * Prompts above the player's head, plus the halberd range ring on the floor.
 *
 * Only shows what needs doing right now: a prayer call only when the prayer
 * is wrong, a charge call only while the boss is charging. No flashing, same
 * as the other helpers here.
 */
class DoomPromptOverlay extends Overlay
{
	private static final Color PUNISH = new Color(255, 150, 60);
	private static final Color SHIELD = new Color(120, 160, 255);
	private static final Color BURROW = new Color(240, 210, 90);
	private static final Color SWAP = new Color(235, 235, 235);
	private static final Color WARN = new Color(255, 140, 60);
	private static final Color RING = new Color(255, 150, 60, 200);

	/** Crystal halberd's special attack cost. */
	private static final int HALBERD_SPEC_COST = 30;

	/** Start nagging about the earthen shield this many ticks before the shockwave. */
	private static final int SHIELD_REMINDER_TICKS = 10;

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

		if (config.prayerPrompt())
		{
			for (DoomTracker.Incoming next : tracker.getIncoming())
			{
				if (next.getLandTick() < tick)
				{
					continue;
				}
				if (plugin.getActiveProtection() != next.getStyle())
				{
					prompts.add(new Prompt("PRAY " + next.getStyle().getLabel() + "  " + (next.getLandTick() - tick),
						next.getStyle().getColor()));
				}
				break;
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
				case SHIELD:
					prompts.add(new Prompt("HIT THE SHIELD", SHIELD));
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

		if (config.shockwavePrompt() && tracker.isShockwavePending(tick) && !tracker.isEarthenShieldMade())
		{
			int left = tracker.ticksUntilShockwave(tick);
			if (left > 0 && left <= SHIELD_REMINDER_TICKS)
			{
				prompts.add(new Prompt("MAKE A SHIELD  " + left, WARN));
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
