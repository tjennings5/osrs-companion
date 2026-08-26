package com.dropHighlighter;

import net.runelite.api.Client;
import net.runelite.api.KeyCode;

/**
 * Which key has to be held for the "Highlight Drops" entry to appear on an NPC's menu.
 *
 * <p>Defaults to Ctrl so the ordinary right-click menu stays exactly as it was. {@link #NONE} is
 * available for anyone who would rather always have the option.
 *
 * <p>Public, and it has to be: RuneLite implements {@link DropHighlighterConfig} with a JDK
 * dynamic proxy that lives in {@code com.sun.proxy}, and a proxy cannot return a type it has no
 * access to. Package-private here throws IllegalAccessError on the first config read.
 */
public enum MenuModifier
{
	CTRL("Ctrl", KeyCode.KC_CONTROL),
	SHIFT("Shift", KeyCode.KC_SHIFT),
	ALT("Alt", KeyCode.KC_ALT),
	NONE("Always show", -1);

	private final String label;
	private final int keyCode;

	MenuModifier(String label, int keyCode)
	{
		this.label = label;
		this.keyCode = keyCode;
	}

	boolean isHeld(Client client)
	{
		return keyCode < 0 || client.isKeyPressed(keyCode);
	}

	@Override
	public String toString()
	{
		return label;
	}
}
