package com.doom;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Recording indicator, so it's obvious a fight is being captured. */
class DoomHelperOverlay extends OverlayPanel
{
	private static final Color REC = new Color(230, 80, 70);

	private final DoomHelperPlugin plugin;
	private final DoomHelperConfig config;

	@Inject
	DoomHelperOverlay(DoomHelperPlugin plugin, DoomHelperConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showRecordingIndicator() || !plugin.isRecording())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Doom - REC")
			.color(REC)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Events")
			.right(Integer.toString(plugin.getRecordedLines()))
			.build());
		panelComponent.setPreferredSize(new Dimension(130, 0));
		return super.render(graphics);
	}
}
