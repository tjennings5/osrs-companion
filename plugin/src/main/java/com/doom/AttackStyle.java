package com.doom;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The styles a Rock Throw's rock comes in, matching their in-game colours. */
@Getter
@RequiredArgsConstructor
enum AttackStyle
{
	MAGIC("MAGE", new Color(90, 170, 255)),
	RANGED("RANGE", new Color(110, 220, 110));

	private final String label;
	private final Color color;
}
