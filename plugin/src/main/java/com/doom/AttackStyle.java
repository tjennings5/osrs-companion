package com.doom;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Prayer;

/** The three styles the Doom's projectiles come in, matching their in-game colours. */
@Getter
@RequiredArgsConstructor
enum AttackStyle
{
	MAGIC("MAGE", new Color(90, 170, 255), Prayer.PROTECT_FROM_MAGIC),
	RANGED("RANGE", new Color(110, 220, 110), Prayer.PROTECT_FROM_MISSILES),
	MELEE("MELEE", new Color(255, 95, 85), Prayer.PROTECT_FROM_MELEE);

	private final String label;
	private final Color color;
	private final Prayer prayer;
}
