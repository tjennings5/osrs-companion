package com.farmrun;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Simple text overlay showing the current patch and teleport to use.
 * Appears in the top-right corner; the user can drag it.
 */
class FarmRunOverlay extends Overlay
{
	private static final int PAD = 6;

	private static final Color BG_COLOR      = new Color(0, 0, 0, 160);
	private static final Color HEADER_COLOR  = new Color(255, 200, 0);
	private static final Color PATCH_COLOR   = Color.WHITE;
	private static final Color TELEPORT_COLOR = new Color(180, 220, 255);

	private static final Font HEADER_FONT  = new Font(Font.SANS_SERIF, Font.BOLD, 11);
	private static final Font CONTENT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

	private final FarmRunPlugin plugin;

	@Inject
	FarmRunOverlay(FarmRunPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.CANVAS_TOP_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String patchName;
		String teleportLine;
		PatchState patchState;

		PatchStop target = plugin.getCurrentTarget();
		if (target != null)
		{
			patchName   = target.getPatch().getDisplayName();
			teleportLine = target.getTeleport() != null
				? "Use: " + target.getTeleport().getDisplayName()
				: "No teleport found";
			patchState  = target.getLastKnownState();
		}
		else
		{
			TreePatchStop treeTarget = plugin.getCurrentTreeTarget();
			if (treeTarget == null)
			{
				return null;
			}
			patchName   = treeTarget.getPatch().getDisplayName();
			teleportLine = treeTarget.getTeleport() != null
				? "Use: " + treeTarget.getTeleport().getDisplayName()
				: "No teleport found";
			patchState  = treeTarget.getLastKnownState();
		}
		String stateLine = patchState.getDisplayName();

		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		graphics.setFont(HEADER_FONT);
		FontMetrics fmH = graphics.getFontMetrics();
		graphics.setFont(CONTENT_FONT);
		FontMetrics fmC = graphics.getFontMetrics();

		int contentWidth = Math.max(fmH.stringWidth("Next patch:"),
			Math.max(fmC.stringWidth(patchName),
			Math.max(fmC.stringWidth(teleportLine),
			         fmC.stringWidth(stateLine))));
		int totalWidth  = contentWidth + PAD * 2;
		int lineHeight  = fmC.getHeight();
		int totalHeight = PAD + fmH.getHeight() + lineHeight * 3 + PAD;

		// Background
		graphics.setColor(BG_COLOR);
		graphics.fillRect(0, 0, totalWidth, totalHeight);

		int x = PAD;
		int y = PAD;

		graphics.setFont(HEADER_FONT);
		graphics.setColor(HEADER_COLOR);
		graphics.drawString("Next patch:", x, y + fmH.getAscent());
		y += fmH.getHeight() + 2;

		graphics.setFont(CONTENT_FONT);
		graphics.setColor(PATCH_COLOR);
		graphics.drawString(patchName, x, y + fmC.getAscent());
		y += lineHeight;

		graphics.setColor(TELEPORT_COLOR);
		graphics.drawString(teleportLine, x, y + fmC.getAscent());
		y += lineHeight;

		graphics.setColor(stateColor(patchState));
		graphics.drawString(stateLine, x, y + fmC.getAscent());

		return new Dimension(totalWidth, totalHeight);
	}

	private static Color stateColor(PatchState state)
	{
		switch (state)
		{
			case HARVESTABLE: return new Color(100, 220, 100);
			case DISEASED:    return new Color(220, 80, 80);
			case GROWING:     return new Color(220, 180, 60);
			default:          return Color.LIGHT_GRAY;
		}
	}
}
