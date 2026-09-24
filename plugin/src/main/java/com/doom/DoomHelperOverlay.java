package com.doom;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Info panel: delve, phase and the shockwave timer.
 * Static like the Cerberus panel - the prompts above the player carry the
 * urgency, this answers "what's coming".
 */
class DoomHelperOverlay extends OverlayPanel
{
	private static final Color TITLE = new Color(212, 160, 48);
	private static final Color REC = new Color(230, 80, 70);
	private static final Color NORMAL = new Color(190, 190, 190);
	private static final Color SHIELD = new Color(120, 160, 255);
	private static final Color BURROW = new Color(230, 200, 90);
	private static final Color WARN = new Color(255, 140, 60);
	private static final Color OK = new Color(120, 230, 120);

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
		if (!config.showPanel() || !plugin.isInFight())
		{
			return null;
		}

		DoomTracker tracker = plugin.getTracker();
		int tick = plugin.getTick();

		boolean rec = config.showRecordingIndicator() && plugin.isRecording();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Doom - Delve " + tracker.getDelve() + (rec ? "  REC" : ""))
			.color(rec ? REC : TITLE)
			.build());

		DoomTracker.Phase phase = tracker.getPhase();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Phase")
			.right(phaseText(phase))
			.rightColor(phase == DoomTracker.Phase.SHIELDED ? SHIELD : phase == DoomTracker.Phase.BURROWED ? BURROW : NORMAL)
			.build());

		if (tracker.isShockwavePending(tick))
		{
			int left = tracker.ticksUntilShockwave(tick);
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Shockwave x" + tracker.shockwaveCount())
				.right(left > 0 ? Integer.toString(left) : "NOW")
				.rightColor(tracker.isEarthenShieldMade() ? OK : WARN)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Earthen shield")
				.right(tracker.isEarthenShieldMade() ? "up" : "not made")
				.rightColor(tracker.isEarthenShieldMade() ? OK : WARN)
				.build());
		}

		panelComponent.setPreferredSize(new Dimension(160, 0));
		return super.render(graphics);
	}

	private static String phaseText(DoomTracker.Phase phase)
	{
		switch (phase)
		{
			case SHIELDED:
				return "Shielded";
			case BURROWED:
				return "Burrowed";
			default:
				return "Normal";
		}
	}
}
