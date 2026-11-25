package com.cursorlock;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(CursorLockConfig.GROUP)
public interface CursorLockConfig extends Config
{
	String GROUP = "cursorlock";

	enum LockArea
	{
		/** The whole RuneLite window: title bar, game and sidebar. */
		WINDOW,
		/** Just the game view - the sidebar and title bar are off limits while locked. */
		GAME
	}

	@ConfigItem(
		keyName = "toggleKey",
		name = "Toggle hotkey",
		description = "Locks or unlocks the cursor to the RuneLite window. Only works while RuneLite "
			+ "is the focused window.",
		position = 0
	)
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "lockArea",
		name = "Lock to",
		description = "Window: the cursor can reach the whole RuneLite window, sidebar included. "
			+ "Game: the cursor is held inside the game view only.",
		position = 1
	)
	default LockArea lockArea()
	{
		return LockArea.WINDOW;
	}

	@ConfigItem(
		keyName = "releaseOnFocusLoss",
		name = "Unlock when unfocused",
		description = "Free the cursor whenever RuneLite isn't the focused window (alt-tab, clicking "
			+ "the taskbar) and lock it again when you come back. Turn this off if another program "
			+ "steals focus and drags the cursor away - the lock then holds until you press the "
			+ "hotkey. Ctrl+Alt+Del always frees the cursor.",
		position = 2
	)
	default boolean releaseOnFocusLoss()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lockOnLogin",
		name = "Lock on login",
		description = "Lock the cursor automatically when you log in. Logging out always unlocks it.",
		position = 3
	)
	default boolean lockOnLogin()
	{
		return false;
	}

	enum Indicator
	{
		ALWAYS,
		WHEN_LOCKED,
		NEVER
	}

	@ConfigItem(
		keyName = "indicator",
		name = "Show indicator",
		description = "On-screen box showing the lock state. Green = locked, yellow = paused "
			+ "(locked, but RuneLite isn't focused or the window is being dragged), grey = unlocked. "
			+ "Alt-drag the box to move it.",
		position = 4
	)
	default Indicator indicator()
	{
		return Indicator.ALWAYS;
	}

	@ConfigItem(
		keyName = "chatMessage",
		name = "Chat message on toggle",
		description = "Print a game message saying whether the cursor is now locked or unlocked.",
		position = 5
	)
	default boolean chatMessage()
	{
		return true;
	}
}
