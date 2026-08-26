package com.osrsmcp.araxxor;

import lombok.Getter;

/**
 * Araxxor's three possible special attacks.
 *
 * Which one he uses is fixed for the whole fight and is readable the moment you
 * walk in, from the type of the south-easternmost egg — so unlike most boss
 * timers this is knowledge you have before he has thrown a single attack.
 */
@Getter
enum AraxxorSpecial
{
	ACID_BALL("Acid ball", "Straight line at you - 3x3 wide. Sidestep it.", "acid-ball.wav"),
	ACID_SPLATTER("Acid splatter", "Wide arc of blobs around you. Low damage, leaves pools.", "acid-splatter.wav"),
	ACID_DRIP("Acid drip", "Homes onto you, drips for 6 ticks. Keep moving.", "acid-drip.wav"),

	/** Before the eggs have been read, or if the read failed. */
	UNKNOWN("Unknown", "Eggs not read yet.", null);

	private final String displayName;
	private final String advice;
	private final String soundFile;

	AraxxorSpecial(String displayName, String advice, String soundFile)
	{
		this.displayName = displayName;
		this.advice = advice;
		this.soundFile = soundFile;
	}
}
