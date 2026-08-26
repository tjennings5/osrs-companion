package com.farmrun;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Guides the player through an herb farm run: bank checklist, patch order,
 * teleport instructions, and a directional arrow overlay.
 *
 * <p>Display-only — reads game state and renders information. Never sends input,
 * switches prayers, or acts for the player.
 *
 * <p>Not submitted to the Plugin Hub; loaded via the dev-client alongside the MCP bridge.
 */
@Slf4j
@PluginDescriptor(
	name = "Farm Run Guide",
	description = "Guides herb farm runs with bank checklist, patch order, teleport instructions, and a directional arrow",
	tags = {"farming", "herb", "run", "patch", "guide", "teleport"}
)
public class FarmRunPlugin extends Plugin
{
	/** Farming Guild bank WorldPoint — used for auto-open detection. */
	private static final WorldPoint GUILD_BANK = new WorldPoint(1248, 3717, 0);

	/** Distance in tiles at which the panel auto-opens near the Farming Guild. */
	private static final int GUILD_DETECT_RADIUS = 30;

	/** Distance in tiles considered "at the patch" for auto-advance. */
	private static final int AT_PATCH_TILES = 15;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FarmRunOverlay overlay;

	private FarmRunPanel panel;

	@Inject
	private FarmRunConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private EventBus eventBus;

	/** Suppresses repeat "herbs ready" notifications until the next run starts. */
	private boolean herbsReadyNotified = false;

	/** Prevents the panel from re-opening every tick while the player stays near the guild. */
	private boolean autoOpenedThisVisit = false;

	private NavigationButton navButton;
	private Path exportDir;

	@Getter
	private RunMode mode = RunMode.IDLE;

	private RunType runType = RunType.HERB;
	private RunRoute route;
	private int currentStopIndex;
	private TreeRunRoute treeRoute;
	private int treeStopIndex;

	/**
	 * Tracks the last-observed state of the current stop while the player is near it.
	 * Reset to UNKNOWN when the player moves away or advances to a new stop.
	 * Used by the game-tick proximity fallback to detect state transitions.
	 */
	private PatchState lastSeenCurrentStopState = PatchState.UNKNOWN;

	/**
	 * Last observed varbit values for each herb patch's state varbit.
	 * Indexed by VarbitID constant. Populated as VarbitChanged events arrive.
	 */
	private final Map<Integer, Integer> patchVarbitCache = new HashMap<>();

	@Provides
	FarmRunConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmRunConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);

		panel = new FarmRunPanel(this);

		navButton = NavigationButton.builder()
			.tooltip("Farm Run Guide")
			.icon(buildIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		exportDir = RuneLite.RUNELITE_DIR.toPath().resolve("osrs-mcp-bridge");

		reset();
		log.info("Farm Run Guide started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		clearSetup();
		reset();
		log.info("Farm Run Guide stopped");
	}

	// --- Public API called by the panel ---

	public void startRun()
	{
		// Button fires on EDT; client.getItemContainer / skill levels / quest states
		// all require the client thread, so dispatch there and bounce back to EDT for UI.
		clientThread.invokeLater(() ->
		{
			try
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INV);
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				RunRoute newRoute = new RunRoute(client, inventory, bank, config);
				log.debug("Farm run: route built, size={}", newRoute.size());
				seedPatchStates(newRoute);
				herbsReadyNotified = false;
				activateSetup(config.herbSetupName());

				SwingUtilities.invokeLater(() ->
				{
					route = newRoute;
					if (route.size() == 0)
					{
						log.debug("Farm run: no accessible patches found");
						panel.showIdle();
						return;
					}
					mode = RunMode.BANKING;
					currentStopIndex = 0;
					log.info("Farm run started: {} patches, seed={}", route.size(), config.herbSeed().getDisplayName());
					panel.showBanking(route.getBankChecklist());
					exportState();
				});
			}
			catch (Exception e)
			{
				log.error("Farm run: failed to start herb run", e);
			}
		});
	}

	public void beginRoute()
	{
		if (runType == RunType.TREE)
		{
			beginTreeRoute();
			return;
		}
		if (route == null || route.size() == 0)
		{
			return;
		}
		clearSetup();
		mode = RunMode.ACTIVE;
		currentStopIndex = 0;
		refreshActivePanel();
		exportState();
		// Close the side panel — removing the selected button collapses the sidebar;
		// immediately re-adding it puts the icon back without reopening the panel.
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.removeNavigation(navButton);
			clientToolbar.addNavigation(navButton);
		});
	}

	public void startTreeRun()
	{
		clientThread.invokeLater(() ->
		{
			try
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INV);
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				TreeRunRoute newRoute = new TreeRunRoute(client, inventory, bank, config);
				log.debug("Tree run: route built, size={}", newRoute.size());
				seedTreePatchStates(newRoute);
				activateSetup(config.treeSetupName());

				SwingUtilities.invokeLater(() ->
				{
					treeRoute = newRoute;
					runType = RunType.TREE;
					if (treeRoute.size() == 0)
					{
						log.debug("Tree run: no accessible patches found");
						panel.showIdle();
						return;
					}
					mode = RunMode.BANKING;
					treeStopIndex = 0;
					log.info("Tree run started: {} patches, sapling={}", treeRoute.size(), config.treeSapling().getDisplayName());
					panel.showBanking(treeRoute.getBankChecklist());
					exportState();
				});
			}
			catch (Exception e)
			{
				log.error("Farm run: failed to start tree run", e);
			}
		});
	}

	public void beginTreeRoute()
	{
		if (treeRoute == null || treeRoute.size() == 0)
		{
			return;
		}
		clearSetup();
		mode = RunMode.ACTIVE;
		treeStopIndex = 0;
		refreshActivePanel();
		exportState();
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.removeNavigation(navButton);
			clientToolbar.addNavigation(navButton);
		});
	}

	public void skipCurrentStop()
	{
		if (mode == RunMode.ACTIVE && runType == RunType.TREE)
		{
			treeAdvance("manual skip");
		}
		else
		{
			advance("manual skip");
		}
	}

	public void cancelRun()
	{
		reset();
	}

	// --- Event subscriptions ---

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (mode == RunMode.IDLE && config.autoOpenNearGuild())
		{
			checkAutoOpenGuild();
		}

		if (mode == RunMode.ACTIVE)
		{
			// Keep patch states fresh from live varbits each tick
			refreshCurrentStopState();
			// Fallback: advance by proximity+state in case the varbit event was missed or
			// has a wrong slot ID declared on the patch.
			checkProximityAdvance();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbitId = event.getVarbitId();
		int newValue = event.getValue();

		// Cache all farming transmit slot values; we don't know which patch is which
		// until we check proximity, but caching them all is cheap.
		if (isFarmingTransmitSlot(varbitId))
		{
			Integer previousValue = patchVarbitCache.put(varbitId, newValue);
			log.debug("Farming varbit {} changed {} -> {}", varbitId, previousValue, newValue);

			if (mode == RunMode.ACTIVE)
			{
				updateStopStates();
				checkAutoAdvance(varbitId, previousValue != null ? previousValue : -1, newValue);
			}

			// Notify once when any patch transitions to harvestable outside of an active run
			if (!herbsReadyNotified && mode != RunMode.ACTIVE
				&& PatchStateReader.decodeFor(newValue, config.herbSeed()) == PatchState.HARVESTABLE)
			{
				herbsReadyNotified = true;
				notifier.notify("Your herbs are ready to harvest!");
			}
		}
	}

	/**
	 * When the bank opens while in BANKING mode, rebuild the checklist with full bank data
	 * (handles the case where the player clicked Start Run before opening the bank).
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Bank interface group ID = 12
		if (event.getGroupId() != 12 || mode != RunMode.BANKING)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			try
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INV);
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				if (runType == RunType.TREE)
				{
					TreeRunRoute refreshed = new TreeRunRoute(client, inventory, bank, config);
					seedTreePatchStates(refreshed);
					SwingUtilities.invokeLater(() ->
					{
						treeRoute = refreshed;
						panel.showBanking(refreshed.getBankChecklist());
					});
				}
				else
				{
					RunRoute refreshed = new RunRoute(client, inventory, bank, config);
					seedPatchStates(refreshed);
					SwingUtilities.invokeLater(() ->
					{
						route = refreshed;
						panel.showBanking(route.getBankChecklist());
					});
				}
			}
			catch (Exception e)
			{
				log.error("Farm run: failed to rebuild route on bank open", e);
			}
		});
	}

	// --- Internal helpers ---

	/** Returns the current herb patch target, or null if not on an active herb run. */
	public PatchStop getCurrentTarget()
	{
		if (mode != RunMode.ACTIVE || runType != RunType.HERB
			|| route == null || currentStopIndex >= route.size())
		{
			return null;
		}
		return route.getStops().get(currentStopIndex);
	}

	/** Returns the current tree patch target, or null if not on an active tree run. */
	public TreePatchStop getCurrentTreeTarget()
	{
		if (mode != RunMode.ACTIVE || runType != RunType.TREE
			|| treeRoute == null || treeStopIndex >= treeRoute.size())
		{
			return null;
		}
		return treeRoute.getStops().get(treeStopIndex);
	}

	private void checkAutoOpenGuild()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		boolean nearGuild = player.getWorldLocation().distanceTo2D(GUILD_BANK) <= GUILD_DETECT_RADIUS;
		if (!nearGuild)
		{
			// Reset so the panel can open again next time the player approaches
			autoOpenedThisVisit = false;
			return;
		}

		// Only open once per visit, and only when at least one herb patch is harvestable
		if (!autoOpenedThisVisit && anyHerbReady())
		{
			autoOpenedThisVisit = true;
			SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
		}
	}

	/** Returns true if any herb patch currently shows a harvestable state for the configured seed. */
	private boolean anyHerbReady()
	{
		for (HerbPatch patch : HerbPatch.values())
		{
			int value = client.getVarbitValue(patch.getStateVarbit());
			if (PatchStateReader.decodeFor(value, config.herbSeed()) == PatchState.HARVESTABLE)
			{
				return true;
			}
		}
		return false;
	}

	private void refreshCurrentStopState()
	{
		if (route != null)
		{
			for (PatchStop stop : route.getStops())
			{
				Integer cached = patchVarbitCache.get(stop.getPatch().getStateVarbit());
				if (cached != null)
				{
					stop.setLastKnownState(PatchStateReader.decodeFor(cached, config.herbSeed()));
				}
			}
		}
		if (treeRoute != null)
		{
			for (TreePatchStop stop : treeRoute.getStops())
			{
				Integer cached = patchVarbitCache.get(stop.getPatch().getStateVarbit());
				if (cached != null)
				{
					stop.setLastKnownState(PatchStateReader.decodeTree(cached));
				}
			}
		}
	}

	private void updateStopStates()
	{
		refreshCurrentStopState();
		if (mode == RunMode.ACTIVE)
		{
			refreshActivePanel();
		}
	}

	/**
	 * Auto-advances when a farming varbit change indicates the player just planted at the
	 * current target patch. Proximity is the primary guard against false advances.
	 *
	 * <p>Herb patches: weeds (0-3) to crop stage (>=4).
	 * <p>Tree patches: 0 (empty/stump) to positive (sapling), OR grown value (high) to sapling
	 * value (low).
	 */
	private void checkAutoAdvance(int varbitId, int previousValue, int newValue)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint pos = player.getWorldLocation();

		// Herb run — only advance on the herb "just planted" signal
		if (runType == RunType.HERB)
		{
			if (!PatchStateReader.isJustPlanted(previousValue, newValue))
			{
				return;
			}
			PatchStop herbTarget = getCurrentTarget();
			if (herbTarget != null && herbTarget.getPatch().distanceTo(pos) <= AT_PATCH_TILES)
			{
				if (herbTarget.getPatch().getStateVarbit() != varbitId)
				{
					log.info("Farm run: planted at {} - actual varbit {} differs from declared {}; update HerbPatch if needed",
						herbTarget.getPatch().getDisplayName(), varbitId, herbTarget.getPatch().getStateVarbit());
				}
				log.debug("Farm run: auto-advance after planting at {}", herbTarget.getPatch().getDisplayName());
				advance("planted herb");
			}
			return;
		}

		// Tree run — detect planting as: 0->positive (fresh after chop) OR high->low positive
		if (runType == RunType.TREE)
		{
			boolean treeJustPlanted = newValue > 0
				&& (previousValue == 0 || previousValue > newValue);
			if (!treeJustPlanted)
			{
				return;
			}
			TreePatchStop treeTarget = getCurrentTreeTarget();
			if (treeTarget != null && treeTarget.getPatch().distanceTo(pos) <= AT_PATCH_TILES)
			{
				if (treeTarget.getPatch().getStateVarbit() != varbitId)
				{
					log.info("Tree run: planted at {} - actual varbit {} differs from declared {}; update TreePatch if needed",
						treeTarget.getPatch().getDisplayName(), varbitId, treeTarget.getPatch().getStateVarbit());
				}
				log.debug("Tree run: auto-advance after planting at {}", treeTarget.getPatch().getDisplayName());
				treeAdvance("planted tree");
			}
		}
	}

	private void advance(String reason)
	{
		lastSeenCurrentStopState = PatchState.UNKNOWN;
		currentStopIndex++;
		if (currentStopIndex >= route.size())
		{
			log.debug("Farm run complete ({})", reason);
			mode = RunMode.IDLE;
			route = null;
			panel.showIdle();
			exportState();
			return;
		}
		log.debug("Farm run: advancing to stop {} ({})", currentStopIndex, reason);
		refreshActivePanel();
		exportState();
	}

	private void treeAdvance(String reason)
	{
		lastSeenCurrentStopState = PatchState.UNKNOWN;
		treeStopIndex++;
		if (treeStopIndex >= treeRoute.size())
		{
			log.debug("Tree run complete ({})", reason);
			mode = RunMode.IDLE;
			treeRoute = null;
			panel.showIdle();
			exportState();
			return;
		}
		log.debug("Tree run: advancing to stop {} ({})", treeStopIndex, reason);
		refreshActivePanel();
		exportState();
	}

	/**
	 * Game-tick fallback: if the player is near the current target and the patch just
	 * transitioned to GROWING, advance.
	 */
	private void checkProximityAdvance()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint pos = player.getWorldLocation();

		PatchState currentState;
		boolean nearby;

		if (runType == RunType.HERB)
		{
			PatchStop target = getCurrentTarget();
			if (target == null)
			{
				lastSeenCurrentStopState = PatchState.UNKNOWN;
				return;
			}
			nearby = target.getPatch().distanceTo(pos) <= AT_PATCH_TILES;
			currentState = target.getLastKnownState();
		}
		else
		{
			TreePatchStop target = getCurrentTreeTarget();
			if (target == null)
			{
				lastSeenCurrentStopState = PatchState.UNKNOWN;
				return;
			}
			nearby = target.getPatch().distanceTo(pos) <= AT_PATCH_TILES;
			currentState = target.getLastKnownState();
		}

		if (!nearby)
		{
			lastSeenCurrentStopState = PatchState.UNKNOWN;
			return;
		}

		boolean justBecameGrowing = currentState == PatchState.GROWING
			&& lastSeenCurrentStopState != PatchState.GROWING
			&& lastSeenCurrentStopState != PatchState.UNKNOWN;

		lastSeenCurrentStopState = currentState;

		if (justBecameGrowing)
		{
			log.debug("Farm run: proximity fallback advance - state transitioned to GROWING");
			if (runType == RunType.TREE)
			{
				treeAdvance("proximity fallback");
			}
			else
			{
				advance("proximity fallback");
			}
		}
	}

	private void refreshActivePanel()
	{
		if (mode == RunMode.ACTIVE && runType == RunType.TREE && treeRoute != null)
		{
			panel.showTreeActive(treeRoute.getStops(), treeStopIndex);
			return;
		}
		if (route == null)
		{
			return;
		}
		panel.showActive(route.getStops(), currentStopIndex);
	}

	private void reset()
	{
		mode = RunMode.IDLE;
		runType = RunType.HERB;
		route = null;
		currentStopIndex = 0;
		treeRoute = null;
		treeStopIndex = 0;
		lastSeenCurrentStopState = PatchState.UNKNOWN;
		herbsReadyNotified = false;
		autoOpenedThisVisit = false;
		clearSetup();
		patchVarbitCache.clear();
		if (panel != null)
		{
			panel.showIdle();
		}
		exportState();
	}

	/**
	 * Reads current varbit values for each stop and populates both the varbit cache and the
	 * stop's lastKnownState so the panel shows real states immediately rather than "Unknown".
	 * Must be called on the client thread.
	 */
	private void seedPatchStates(RunRoute route)
	{
		for (PatchStop stop : route.getStops())
		{
			int varbitId = stop.getPatch().getStateVarbit();
			int value = client.getVarbitValue(varbitId);
			patchVarbitCache.put(varbitId, value);
			stop.setLastKnownState(PatchStateReader.decodeFor(value, config.herbSeed()));
		}
	}

	private void seedTreePatchStates(TreeRunRoute route)
	{
		for (TreePatchStop stop : route.getStops())
		{
			int varbitId = stop.getPatch().getStateVarbit();
			int value = client.getVarbitValue(varbitId);
			patchVarbitCache.put(varbitId, value);
			stop.setLastKnownState(PatchStateReader.decodeTree(value));
		}
	}

	/**
	 * Activates the named Inventory Setups setup via the plugin message API.
	 * Does nothing if the name is blank (user chose to skip bank filtering).
	 */
	private void activateSetup(String setupName)
	{
		if (setupName == null || setupName.trim().isEmpty())
		{
			return;
		}
		Map<String, Object> data = new HashMap<>();
		data.put("setup", setupName.trim());
		eventBus.post(new PluginMessage("inventory-setups", "view", data));
		log.debug("Farm run: activated Inventory Setups setup '{}'", setupName.trim());
	}

	/** Clears the active Inventory Setups setup via the plugin message API. */
	private void clearSetup()
	{
		eventBus.post(new PluginMessage("inventory-setups", "clear", new HashMap<>()));
	}

	/** Writes a farm-run sidecar JSON file into the MCP bridge export directory. */
	private void exportState()
	{
		if (exportDir == null)
		{
			return;
		}
		Player player = client.getLocalPlayer();
		String username = (player != null && player.getName() != null)
			? player.getName().replaceAll("[^a-zA-Z0-9 _-]", "_")
			: "unknown";

		FarmRunExport export = new FarmRunExport();
		export.mode = mode.name();
		export.seed = config.herbSeed().getDisplayName();

		if (route != null)
		{
			List<FarmRunExport.PatchEntry> entries = new ArrayList<>();
			for (int i = 0; i < route.getStops().size(); i++)
			{
				PatchStop stop = route.getStops().get(i);
				FarmRunExport.PatchEntry entry = new FarmRunExport.PatchEntry();
				entry.name = stop.getPatch().getDisplayName();
				entry.teleport = stop.getTeleport() != null ? stop.getTeleport().getDisplayName() : null;
				entry.state = stop.getLastKnownState().getDisplayName();
				entry.done = i < currentStopIndex;
				entry.current = i == currentStopIndex && mode == RunMode.ACTIVE;
				entries.add(entry);
			}
			export.route = entries;
			export.bankChecklist = route.getBankChecklist();
		}

		Gson prettyGson = gson.newBuilder().setPrettyPrinting().create();
		String json = prettyGson.toJson(export);

		executor.execute(() ->
		{
			try
			{
				Files.createDirectories(exportDir);
				String filename = username + "-farmrun.json";
				Path tmp = exportDir.resolve(filename + ".tmp");
				Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
				Files.move(tmp, exportDir.resolve(filename),
					StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException e)
			{
				log.debug("Farm run: failed to export state", e);
			}
		});
	}

	/** Plain data bag serialized to JSON for the MCP server. */
	private static class FarmRunExport
	{
		String mode;
		String seed;
		List<PatchEntry> route;
		List<BankItem> bankChecklist;

		static class PatchEntry
		{
			String name;
			String teleport;
			String state;
			boolean done;
			boolean current;
		}
	}

	/** True for the FARMING_TRANSMIT_* varbit IDs that encode patch states. */
	private static boolean isFarmingTransmitSlot(int varbitId)
	{
		// Primary slots A-E (4771-4775) and extended slots A1-F2 (4953-4964), F-P (7904-7914)
		return (varbitId >= 4771 && varbitId <= 4775)
			|| (varbitId >= 4953 && varbitId <= 4964)
			|| (varbitId >= 7904 && varbitId <= 7914);
	}

	/** Programmatically generated 16x16 herb/leaf icon for the navigation toolbar. */
	private static BufferedImage buildIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		// Stem
		g.setColor(new Color(100, 180, 60));
		g.fillRect(7, 6, 2, 9);

		// Left leaf
		int[] lx = {3, 7, 7};
		int[] ly = {5, 3, 9};
		g.fillPolygon(lx, ly, 3);

		// Right leaf
		int[] rx = {13, 9, 9};
		int[] ry = {5, 3, 9};
		g.fillPolygon(rx, ry, 3);

		// Small top bud
		g.setColor(new Color(130, 210, 70));
		g.fillOval(6, 1, 4, 5);

		g.dispose();
		return image;
	}
}
