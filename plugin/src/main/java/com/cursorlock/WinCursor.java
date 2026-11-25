package com.cursorlock;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.awt.Component;

/**
 * Thin wrapper over the Win32 calls the lock needs. All rectangles are in physical screen pixels,
 * which is what ClipCursor wants - asking Swing for bounds instead would give logical pixels and
 * be off by the UI scale factor.
 */
final class WinCursor
{
	private interface User32Ex extends StdCallLibrary
	{
		User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean ClipCursor(RECT rect);

		boolean GetClipCursor(RECT rect);
	}

	private interface Dwmapi extends StdCallLibrary
	{
		Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class, W32APIOptions.DEFAULT_OPTIONS);

		int DwmGetWindowAttribute(HWND hwnd, int attribute, RECT value, int size);
	}

	/** The window as drawn, without the invisible resize borders Windows 10+ adds around it. */
	private static final int DWMWA_EXTENDED_FRAME_BOUNDS = 9;

	/** Set while the user is dragging or resizing a window through the system move/size loop. */
	private static final int GUI_INMOVESIZE = 0x2;

	private WinCursor()
	{
	}

	static HWND hwnd(Component component)
	{
		long id = Native.getComponentID(component);
		return id == 0 ? null : new HWND(new Pointer(id));
	}

	/** Bounds of a top-level window as the user sees it. */
	static RECT visibleWindowRect(HWND hwnd)
	{
		RECT rect = new RECT();
		try
		{
			if (Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, DWMWA_EXTENDED_FRAME_BOUNDS, rect, rect.size()) == 0)
			{
				return rect;
			}
		}
		catch (UnsatisfiedLinkError ignored)
		{
			// No DWM - fall through to the plain window rect.
		}
		return User32.INSTANCE.GetWindowRect(hwnd, rect) ? rect : null;
	}

	/** Bounds of a child control (the game canvas has its own native window). */
	static RECT windowRect(HWND hwnd)
	{
		RECT rect = new RECT();
		return User32.INSTANCE.GetWindowRect(hwnd, rect) ? rect : null;
	}

	static boolean isForeground(HWND hwnd)
	{
		return hwnd.equals(User32.INSTANCE.GetForegroundWindow());
	}

	/** True while a window on this thread is being dragged or resized by its title bar or edges. */
	static boolean inMoveSize(HWND hwnd)
	{
		int thread = User32.INSTANCE.GetWindowThreadProcessId(hwnd, null);
		WinUser.GUITHREADINFO info = new WinUser.GUITHREADINFO();
		return User32.INSTANCE.GetGUIThreadInfo(thread, info) && (info.flags & GUI_INMOVESIZE) != 0;
	}

	/** Clip to the rect, skipping the call when the cursor is already clipped to exactly that. */
	static void clip(RECT rect)
	{
		RECT current = new RECT();
		if (User32Ex.INSTANCE.GetClipCursor(current)
			&& current.left == rect.left && current.top == rect.top
			&& current.right == rect.right && current.bottom == rect.bottom)
		{
			return;
		}
		User32Ex.INSTANCE.ClipCursor(rect);
	}

	static void release()
	{
		User32Ex.INSTANCE.ClipCursor(null);
	}
}
