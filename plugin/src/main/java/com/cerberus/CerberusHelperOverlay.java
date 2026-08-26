package com.cerberus;

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
 * corner of the eye is the last thing you want while switching prayers, so the
 * urgency is carried by the voice cue and this just answers "what's next".
 */
class CerberusHelperOverlay extends OverlayPanel
{
	private static final Color TRIPLE = new Color(255, 130, 90);
	private static final Color SOULS = new Color(150, 190, 255);
	private static final Color LAVA = new Color(255, 190, 90);
	private static final Color NORMAL = new Color(190, 190, 190);
	private static final Color GO = new Color(120, 230, 120);

	private final CerberusHelperPlugin plugin;
	private final CerberusHelperConfig config;

	@Inject
	CerberusHelperOverlay(CerberusHelperPlugin plugin, CerberusHelperConfig config)
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

		CerberusSpecial next = plugin.getNextSpecial();

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Cerberus")
			.color(TRIPLE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Attack")
			.right("#" + plugin.getAttackCount())
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Next")
			.right(next.getDisplayName())
			.rightColor(colourFor(next))
			.build());

		if (config.noGhostsMode())
		{
			addNoGhostsLines();
		}

		// Both conditional specials are gated on her health, so showing it makes
		// it obvious *why* souls or lava are not being called yet.
		panelComponent.getChildren().add(LineComponent.builder()
			.left("HP (approx)")
			.right(plugin.getPredictedHp() < plugin.getLastKnownHp()
				? plugin.getLastKnownHp() + " (" + plugin.getPredictedHp() + ")"
				: Integer.toString(plugin.getLastKnownHp()))
			.build());

		if (next == CerberusSpecial.SOULS || next == CerberusSpecial.LAVA)
		{
			// Worth stating outright: acting as if it is guaranteed will get you
			// hit on the one attack in ten where she rolls a normal instead.
			panelComponent.getChildren().add(LineComponent.builder()
				.left("(10% chance she skips)")
				.leftColor(NORMAL)
				.build());
		}

		panelComponent.setPreferredSize(new Dimension(160, 0));
		return super.render(graphics);
	}

	/**
	 * The No Ghosts read-out. Three states, and which one you are in is the only
	 * thing that matters mid-fight: hold damage, burn now, or you have already
	 * blown it and should reset.
	 */
	private void addNoGhostsLines()
	{
		if (plugin.isDroppedBelowEarly())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Under 400 early")
				.right("RESET")
				.rightColor(LAVA)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Walk the flames to reset")
				.leftColor(NORMAL)
				.build());
			return;
		}

		if (plugin.isWindowOpen())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("BURN")
				.right(plugin.getAttacksLeftInWindow() + " left")
				.rightColor(GO)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Souls at")
				.right("#" + plugin.getWindowEndsAt())
				.rightColor(SOULS)
				.build());
			return;
		}

		int soulsAt = plugin.getNextSoulsAttack();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("HOLD > 400")
			.right("until #" + soulsAt)
			.rightColor(SOULS)
			.build());
	}

	private static Color colourFor(CerberusSpecial special)
	{
		switch (special)
		{
			case TRIPLE:
				return TRIPLE;
			case SOULS:
				return SOULS;
			case LAVA:
				return LAVA;
			default:
				return NORMAL;
		}
	}
}
