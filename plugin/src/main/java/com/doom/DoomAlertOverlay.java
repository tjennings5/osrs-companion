package com.doom;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Large alerts in a fixed spot next to the inventory: the rock type and the
 * low prayer reminder. Moved off the player's head because the overhead
 * prayer icon covered them. Alt-drag to reposition like any overlay.
 */
class DoomAlertOverlay extends Overlay
{
	private static final Color PRAYER_POT = new Color(90, 210, 230);

	@RequiredArgsConstructor
	private static final class Line
	{
		final String text;
		final Color color;
	}

	private final DoomHelperPlugin plugin;
	private final DoomHelperConfig config;

	@Inject
	DoomAlertOverlay(DoomHelperPlugin plugin, DoomHelperConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.BOTTOM_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInFight())
		{
			return null;
		}

		List<Line> lines = buildLines();
		if (lines.isEmpty())
		{
			return null;
		}

		Font priorFont = graphics.getFont();
		Object priorAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, (float) config.fontSize()));
		try
		{
			FontMetrics metrics = graphics.getFontMetrics();
			int width = 0;
			for (Line line : lines)
			{
				width = Math.max(width, metrics.stringWidth(line.text));
			}

			int y = metrics.getAscent();
			for (Line line : lines)
			{
				// Right-aligned so the text grows away from the inventory.
				int x = width - metrics.stringWidth(line.text);
				graphics.setColor(Color.BLACK);
				graphics.drawString(line.text, x + 1, y + 1);
				graphics.setColor(line.color);
				graphics.drawString(line.text, x, y);
				y += metrics.getHeight();
			}
			return new Dimension(width + 1, lines.size() * metrics.getHeight());
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

	private List<Line> buildLines()
	{
		int tick = plugin.getTick();
		DoomTracker tracker = plugin.getTracker();
		List<Line> lines = new ArrayList<>(2);

		if (config.rockCallout())
		{
			AttackStyle rock = tracker.getRockCallout(tick);
			if (rock != null)
			{
				boolean stomp = config.rockStompWarning() && tracker.isRockDuringShockwave(tick);
				lines.add(new Line(rock.getLabel() + " ROCK" + (stomp ? " + STOMP" : ""), rock.getColor()));
			}
		}

		if (config.prayerPotPrompt() && plugin.getPrayerPoints() < config.prayerPotThreshold())
		{
			lines.add(new Line("DRINK PRAYER  " + plugin.getPrayerPoints(), PRAYER_POT));
		}

		return lines;
	}
}
