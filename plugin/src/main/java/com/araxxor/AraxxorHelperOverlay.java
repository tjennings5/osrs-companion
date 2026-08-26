package com.araxxor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Deliberately static: no pulsing, flashing, or colour cycling. Movement in the
 * corner of the eye is the last thing you want while dodging a cleave, so the
 * urgency is carried by the voice cue and this just answers "what's next".
 */
class AraxxorHelperOverlay extends OverlayPanel
{
	private static final Color TITLE = new Color(150, 220, 130);
	private static final Color ENRAGE = new Color(255, 120, 100);
	private static final Color MINION = new Color(180, 200, 255);
	private static final Color SPECIAL = new Color(255, 190, 90);
	private static final Color MUTED = new Color(190, 190, 190);
	private static final Color WARN = new Color(255, 220, 120);

	private final AraxxorHelperPlugin plugin;
	private final AraxxorHelperConfig config;

	@Inject
	AraxxorHelperOverlay(AraxxorHelperPlugin plugin, AraxxorHelperConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || !plugin.isFightActive())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Araxxor")
			.color(plugin.isEnraged() ? ENRAGE : TITLE)
			.build());

		// The single most useful line in the fight, and it is known before he
		// attacks once, so it sits at the top.
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Special")
			.right(plugin.getFightSpecial().getDisplayName())
			.rightColor(SPECIAL)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Attack")
			.right("#" + plugin.getStandardAttacks())
			.build());

		addHatchLine();

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Phase")
			.right(plugin.isEnraged() ? "ENRAGED (4t)" : "Normal (6t)")
			.rightColor(plugin.isEnraged() ? ENRAGE : MUTED)
			.build());

		int hp = plugin.getLastKnownHp();
		int predicted = plugin.getPredictedHp();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("HP (approx)")
			.right(predicted < hp ? hp + " (" + predicted + ")" : Integer.toString(hp))
			.build());

		addMinionLines();

		panelComponent.setPreferredSize(new Dimension(190, 0));
		return super.render(graphics);
	}

	private void addHatchLine()
	{
		AraxxorMinion next = plugin.getNextMinion();
		int in = plugin.getAttacksUntilHatch();

		if (next == null)
		{
			// Eggs not read yet — say so rather than showing a confident blank.
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Next hatch")
				.right("in " + in)
				.rightColor(MUTED)
				.build());
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Next hatch")
			.right(next.getDisplayName() + " in " + in)
			.rightColor(in <= 1 ? WARN : MINION)
			.build());
	}

	private void addMinionLines()
	{
		AraxxorMinion active = plugin.getActiveMinion();
		if (active == null || !config.minionAdvice())
		{
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Out now")
			.right(active.getDisplayName())
			.rightColor(MINION)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left(active.getAdvice())
			.leftColor(MUTED)
			.build());
	}
}
