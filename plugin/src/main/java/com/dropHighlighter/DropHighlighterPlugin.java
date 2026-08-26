package com.dropHighlighter;

import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Drop Highlighter",
	description = "Pick items off a monster's drop table and light them up on the ground when they drop.",
	tags = {"drops", "loot", "ground", "highlight", "ironman"}
)
public class DropHighlighterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DropHighlighterConfig config;

	@Inject
	private HighlightManager highlights;

	@Inject
	private GroundItemTracker tracker;

	@Inject
	private DropHighlighterOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private DropHighlighterPanel panel;

	@Inject
	private DropTableProvider dropTables;

	private NavigationButton navButton;

	/**
	 * The one place the drop table implementation is named. Swapping in a different source is a
	 * change to this line and nothing else.
	 */
	@Override
	public void configure(Binder binder)
	{
		binder.bind(DropTableProvider.class).to(BundledDropTableProvider.class);
	}

	@Provides
	DropHighlighterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropHighlighterConfig.class);
	}

	@Override
	protected void startUp()
	{
		highlights.reload(config);
		overlayManager.add(overlay);

		BufferedImage icon = ImageUtil.loadImageResource(DropHighlighterPlugin.class,
			"/com/dropHighlighter/panel-icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Drop Highlighter")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Enabling the plugin mid-session means the spawn events for everything already on the
		// ground have long since fired, so seed from the scene once.
		clientThread.invoke(this::rescan);
		log.info("Drop Highlighter started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		tracker.clear();
		log.info("Drop Highlighter stopped");
	}

	/**
	 * Adds a "Highlight Drops" option to any NPC's right-click menu.
	 *
	 * <p>MenuOpened rather than MenuEntryAdded: the latter fires once per entry, so an NPC with
	 * Attack / Talk-to / Examine would collect three copies of the option. This fires once with
	 * the whole menu.
	 *
	 * <p>The entry is MenuAction.RUNELITE — it is handled entirely client side and sends nothing
	 * to the server.
	 *
	 * <p>Gated behind a held modifier key (Ctrl by default) so a plain right click gives exactly
	 * the menu the game would have shown on its own.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.menuModifier().isHeld(client))
		{
			return;
		}

		NPC npc = null;
		for (MenuEntry entry : event.getMenuEntries())
		{
			if (entry.getNpc() != null)
			{
				npc = entry.getNpc();
				break;
			}
		}

		if (npc == null)
		{
			return;
		}

		String name = npc.getName();
		if (name == null || !dropTables.hasDrops(name))
		{
			return;
		}

		final String monsterName = name;
		client.getMenu().createMenuEntry(-1)
			.setOption("Highlight Drops")
			.setTarget("<col=ffff00>" + monsterName + "</col>")
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				// onClick fires on the client thread, but openPanel asserts it is on the EDT.
				// displayMonster posts its rebuild to the EDT queue first and invokeLater is
				// FIFO, so the panel is populated before it is shown.
				panel.displayMonster(monsterName);
				SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
			});
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		tracker.itemSpawned(event.getTile(), event.getItem());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		tracker.itemDespawned(event.getTile(), event.getItem());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		tracker.itemQuantityChanged(event.getTile(), event.getItem());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.HOPPING
			|| state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
		{
			// Tile references belong to the scene being torn down. Holding them past this point
			// would mean projecting stale coordinates onto the new scene.
			tracker.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!DropHighlighterConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		// Only the two keys that define *which* items are highlighted need the scene re-walked.
		// The beam and text settings are read fresh every frame and need nothing.
		String key = event.getKey();
		if (!DropHighlighterConfig.KEY_HIGHLIGHTS.equals(key) && !"seedHighlights".equals(key))
		{
			return;
		}

		highlights.reload(config);
		panel.refreshSelections();
		// This can arrive on the Swing thread when the setting is changed from the config panel,
		// so hop before touching the scene.
		clientThread.invoke(this::rescan);
	}

	private void rescan()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			tracker.clear();
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		tracker.rescan(worldView == null ? null : worldView.getScene());
	}
}
