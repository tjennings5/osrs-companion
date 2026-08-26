package com.osrsmcp.farmrun;

import java.awt.Color;

public enum PatchState
{
	EMPTY("Empty", new Color(128, 128, 128)),
	GROWING("Growing", new Color(100, 180, 100)),
	HARVESTABLE("Ready!", new Color(80, 220, 80)),
	DISEASED("Diseased", new Color(220, 160, 0)),
	UNKNOWN("Unknown", new Color(100, 100, 100));

	private final String displayName;
	private final Color color;

	PatchState(String displayName, Color color)
	{
		this.displayName = displayName;
		this.color = color;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Color getColor()
	{
		return color;
	}
}
