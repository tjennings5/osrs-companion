package com.osrsmcp.cerberus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The specials Cerberus can open an attack slot with, in the priority order the
 * game resolves them when more than one is due on the same attack
 * (triple > souls > lava).
 */
@Getter
@RequiredArgsConstructor
public enum CerberusSpecial
{
	TRIPLE("Triple attack", "combo-next.wav"),
	SOULS("Summoned souls", "souls-next.wav"),
	LAVA("Lava pools", "lava-next.wav"),
	NORMAL("Normal attack", null);

	private final String displayName;
	private final String sound;
}
