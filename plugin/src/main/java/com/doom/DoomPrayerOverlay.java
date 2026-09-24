package com.doom;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Boxes the protection prayer the next attack to land needs, in the prayer
 * tab. Stays on whichever attack is next and moves the moment it lands, so
 * it's never blank while something is in flight.
 */
class DoomPrayerOverlay extends Overlay
{
	/** Highest child index to search in the prayer book; it has well under this many components. */
	private static final int MAX_CHILD = 80;

	private static final Map<AttackStyle, String> PRAYER_NAMES = Map.of(
		AttackStyle.MAGIC, "Protect from Magic",
		AttackStyle.RANGED, "Protect from Missiles",
		AttackStyle.MELEE, "Protect from Melee");

	private final Client client;
	private final DoomHelperPlugin plugin;
	private final DoomHelperConfig config;

	/** Component IDs of the three protection prayers, found by name once the prayer book has loaded. */
	private final Map<AttackStyle, Integer> prayerWidgets = new EnumMap<>(AttackStyle.class);

	@Inject
	DoomPrayerOverlay(Client client, DoomHelperPlugin plugin, DoomHelperConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.prayerHighlight() || !plugin.isInFight())
		{
			return null;
		}

		AttackStyle next = plugin.getTracker().getNextPrayer(plugin.getTick());
		if (next == null)
		{
			return null;
		}

		Widget widget = findPrayerWidget(next);
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		Rectangle bounds = widget.getBounds();
		graphics.setStroke(new BasicStroke(3));
		graphics.setColor(next.getColor());
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		return null;
	}

	/**
	 * Looks the prayer up by its name rather than a fixed component ID, so it
	 * keeps working if prayers are added or the book is filtered.
	 */
	private Widget findPrayerWidget(AttackStyle style)
	{
		Integer id = prayerWidgets.get(style);
		if (id != null)
		{
			Widget cached = client.getWidget(id);
			if (cached != null && PRAYER_NAMES.get(style).equalsIgnoreCase(Text.removeTags(cached.getName())))
			{
				return cached;
			}
		}

		int group = InterfaceID.Prayerbook.UNIVERSE >>> 16;
		for (int child = 0; child < MAX_CHILD; child++)
		{
			Widget w = client.getWidget(group, child);
			if (w != null && PRAYER_NAMES.get(style).equalsIgnoreCase(Text.removeTags(w.getName())))
			{
				prayerWidgets.put(style, w.getId());
				return w;
			}
		}
		return null;
	}
}
