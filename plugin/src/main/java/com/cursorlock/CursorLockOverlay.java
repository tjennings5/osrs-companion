package com.cursorlock;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Small status box showing whether the cursor is locked. Alt-drag to move it like any other
 * RuneLite overlay.
 */
class CursorLockOverlay extends OverlayPanel
{
	private static final Color LOCKED = new Color(0x4CD964);
	private static final Color PAUSED = new Color(0xFFCC00);
	private static final Color UNLOCKED = new Color(0x9A9A9A);

	private final CursorLockPlugin plugin;
	private final CursorLockConfig config;

	@Inject
	CursorLockOverlay(CursorLockPlugin plugin, CursorLockConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		CursorLockPlugin.Status status = plugin.getStatus();
		switch (config.indicator())
		{
			case NEVER:
				return null;
			case WHEN_LOCKED:
				if (status == CursorLockPlugin.Status.UNLOCKED)
				{
					return null;
				}
				break;
			default:
				break;
		}

		String text;
		Color color;
		switch (status)
		{
			case LOCKED:
				text = "Locked";
				color = LOCKED;
				break;
			case PAUSED:
				text = "Paused";
				color = PAUSED;
				break;
			default:
				text = "Unlocked";
				color = UNLOCKED;
				break;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Cursor:")
			.right(text)
			.rightColor(color)
			.build());
		return super.render(graphics);
	}
}
