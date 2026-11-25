package com.cursorlock;

import com.google.inject.Provides;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import java.awt.Canvas;
import java.awt.Frame;
import java.awt.Window;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.OSType;

/**
 * Confines the mouse cursor to the RuneLite window, toggled by a hotkey.
 *
 * <p>Uses the Win32 ClipCursor call, the same thing full-screen games use. It doesn't move or
 * click the mouse - it only limits where the cursor can go - so it stays clear of input
 * automation. Windows drops the clip whenever any other program calls ClipCursor (Chromium does
 * this, which is how a browser can yank the cursor away), so while locked the clip is re-applied
 * on a short timer rather than set once.
 *
 * <p>All state lives on the Swing event thread: the hotkey, the timer and the window queries all
 * run there, so nothing here needs locking.
 */
@Slf4j
@PluginDescriptor(
	name = "Cursor Lock",
	description = "Locks the mouse cursor inside the RuneLite window. Toggle with a hotkey (Ctrl+L by default).",
	tags = {"cursor", "mouse", "lock", "confine", "clip", "window", "monitor"}
)
public class CursorLockPlugin extends Plugin
{
	/** How often the clip is checked and re-applied. Fast enough that a stolen clip is back before you notice. */
	private static final int REFRESH_MS = 100;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private CursorLockConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CursorLockOverlay overlay;

	enum Status
	{
		/** Cursor is held inside RuneLite right now. */
		LOCKED,
		/** Lock is on, but suspended this moment (RuneLite unfocused, minimised or being dragged). */
		PAUSED,
		UNLOCKED
	}

	/** What the user asked for via the hotkey. Volatile because the overlay reads it from the client thread. */
	private volatile boolean locked;

	/** Whether we currently hold a clip, so we only release a clip that is ours. */
	private volatile boolean clipped;

	private Timer timer;

	/**
	 * Set at the login screen, cleared on the next LOGGED_IN. World hops also pass through
	 * LOGGED_IN but never the login screen, so this keeps "lock on login" from re-locking a cursor
	 * the user unlocked by hand before hopping.
	 */
	private boolean awaitingLogin = true;

	/** Frees the cursor if RuneLite exits while locked, since the clip outlives the window otherwise. */
	private final Thread shutdownHook = new Thread(() ->
	{
		if (clipped)
		{
			WinCursor.release();
		}
	}, "cursor-lock-release");

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.toggleKey())
	{
		@Override
		public void hotkeyPressed()
		{
			SwingUtilities.invokeLater(CursorLockPlugin.this::toggle);
		}
	};

	@Provides
	CursorLockConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CursorLockConfig.class);
	}

	@Override
	protected void startUp()
	{
		if (OSType.getOSType() != OSType.Windows)
		{
			log.info("Cursor Lock only works on Windows; doing nothing");
			return;
		}

		keyManager.registerKeyListener(hotkeyListener);
		overlayManager.add(overlay);
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		SwingUtilities.invokeLater(() ->
		{
			boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
			awaitingLogin = !loggedIn;
			locked = config.lockOnLogin() && loggedIn;
			timer = new Timer(REFRESH_MS, e -> refresh());
			timer.start();
		});
	}

	@Override
	protected void shutDown()
	{
		if (OSType.getOSType() != OSType.Windows)
		{
			return;
		}

		keyManager.unregisterKeyListener(hotkeyListener);
		overlayManager.remove(overlay);
		try
		{
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		}
		catch (IllegalStateException ignored)
		{
			// Already shutting down - the hook is about to run anyway.
		}
		SwingUtilities.invokeLater(() ->
		{
			if (timer != null)
			{
				timer.stop();
				timer = null;
			}
			locked = false;
			unclip();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		SwingUtilities.invokeLater(() ->
		{
			if (timer == null)
			{
				return;
			}
			if (state == GameState.LOGIN_SCREEN)
			{
				awaitingLogin = true;
				if (locked)
				{
					locked = false;
					refresh();
				}
			}
			else if (state == GameState.LOGGED_IN && awaitingLogin)
			{
				awaitingLogin = false;
				if (config.lockOnLogin() && !locked)
				{
					locked = true;
					refresh();
				}
			}
		});
	}

	Status getStatus()
	{
		if (!locked)
		{
			return Status.UNLOCKED;
		}
		return clipped ? Status.LOCKED : Status.PAUSED;
	}

	private void toggle()
	{
		locked = !locked;
		refresh();
		if (config.chatMessage())
		{
			String message = locked ? "Cursor locked to RuneLite." : "Cursor unlocked.";
			clientThread.invokeLater(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
				}
			});
		}
	}

	private void refresh()
	{
		try
		{
			RECT target = locked ? targetRect() : null;
			if (target == null)
			{
				unclip();
			}
			else
			{
				WinCursor.clip(target);
				clipped = true;
			}
		}
		catch (RuntimeException | LinkageError e)
		{
			log.warn("Cursor Lock failed, unlocking", e);
			locked = false;
			unclip();
		}
	}

	/** The rect to clip to right now, or null if the cursor should be free this tick. */
	private RECT targetRect()
	{
		Canvas canvas = client.getCanvas();
		Window window = canvas == null ? null : SwingUtilities.getWindowAncestor(canvas);
		if (window == null || !window.isShowing())
		{
			return null;
		}
		if (window instanceof Frame && (((Frame) window).getExtendedState() & Frame.ICONIFIED) != 0)
		{
			// A minimised window's rect is parked far off screen; clipping to it would be a disaster.
			return null;
		}

		HWND frameHwnd = WinCursor.hwnd(window);
		if (frameHwnd == null)
		{
			return null;
		}
		if (config.releaseOnFocusLoss() && !WinCursor.isForeground(frameHwnd))
		{
			return null;
		}
		if (WinCursor.inMoveSize(frameHwnd))
		{
			// Dragging the window by its title bar: Windows runs its own clip for the move, and
			// fighting it would pin the window in place. The timer picks the new position up after.
			return null;
		}

		if (config.lockArea() == CursorLockConfig.LockArea.GAME)
		{
			HWND canvasHwnd = WinCursor.hwnd(canvas);
			if (canvasHwnd != null)
			{
				return WinCursor.windowRect(canvasHwnd);
			}
		}
		return WinCursor.visibleWindowRect(frameHwnd);
	}

	private void unclip()
	{
		if (clipped)
		{
			WinCursor.release();
			clipped = false;
		}
	}
}
