package com.osrsmcp.farmrun;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.gameval.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsService;
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
	private PluginManager pluginManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	/** Item IDs to show when the bank filter is active (populated when entering BANKING mode). */
	private final Set<Integer> bankFilterIds = new HashSet<>();

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

		// Clean up any bank-tag config entries left behind by a previous crash
		clearBankTagsConfig();
		reset();
		log.info("Farm Run Guide started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
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
				// Read current varbit values so patch states show immediately rather than "Unknown"
				seedPatchStates(newRoute);
				herbsReadyNotified = false;
				buildBankFilterIds(newRoute, inventory, bank);
				// Register the tag and open it immediately if the bank is already open
				registerFarmRunTag(newRoute, bank);
				if (bank != null)
				{
					BankTagsService svc = bankTagsService();
					if (svc != null)
					{
						svc.openBankTag("farm-run", BankTagsService.OPTION_ALLOW_MODIFICATIONS);
					}
				}

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
		bankFilterIds.clear();
		closeFarmRunTag();
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
				buildTreeBankFilterIds(newRoute, inventory, bank);
				registerTreeFarmRunTag(newRoute, bank);
				if (bank != null)
				{
					BankTagsService svc = bankTagsService();
					if (svc != null)
					{
						svc.openBankTag("farm-run", BankTagsService.OPTION_ALLOW_MODIFICATIONS);
					}
				}

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
		bankFilterIds.clear();
		closeFarmRunTag();
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
			log.debug("Farming varbit {} changed {} → {}", varbitId, previousValue, newValue);

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
	 * <p>Herb patches: weeds (0–3) → crop stage (≥4).
	 * <p>Tree patches: 0 (empty/stump) → positive (sapling), OR grown value (high) → sapling
	 * value (low). The herb early-return is skipped for tree runs so trees with low initial
	 * stage values (1–3) are not incorrectly filtered out.
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
					log.info("Farm run: planted at {} — actual varbit {} differs from declared {}; update HerbPatch if needed",
						herbTarget.getPatch().getDisplayName(), varbitId, herbTarget.getPatch().getStateVarbit());
				}
				log.debug("Farm run: auto-advance after planting at {}", herbTarget.getPatch().getDisplayName());
				advance("planted herb");
			}
			return;
		}

		// Tree run — detect planting as: 0→positive (fresh after chop) OR high→low positive
		// (replant without going through stump). Growing a stage always increases the varbit,
		// so only a decrease (or from zero) indicates a new sapling was planted.
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
					log.info("Tree run: planted at {} — actual varbit {} differs from declared {}; update TreePatch if needed",
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
	 * transitioned to GROWING (from a non-growing state), advance. This catches cases where
	 * the VarbitChanged event fires but the declared stateVarbit ID is wrong, so
	 * checkAutoAdvance didn't fire because the varbit was filtered by isFarmingTransmitSlot
	 * but not matched by proximity.
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
			// Player moved away — reset so re-arrival doesn't trigger a spurious advance
			lastSeenCurrentStopState = PatchState.UNKNOWN;
			return;
		}

		// Only advance when the state transitions TO growing (not already growing on arrival)
		boolean justBecameGrowing = currentState == PatchState.GROWING
			&& lastSeenCurrentStopState != PatchState.GROWING
			&& lastSeenCurrentStopState != PatchState.UNKNOWN;

		lastSeenCurrentStopState = currentState;

		if (justBecameGrowing)
		{
			log.debug("Farm run: proximity fallback advance — state transitioned to GROWING");
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
		bankFilterIds.clear();
		herbsReadyNotified = false;
		autoOpenedThisVisit = false;
		closeFarmRunTag();
		patchVarbitCache.clear();
		panel.showIdle();
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

	private void buildTreeBankFilterIds(TreeRunRoute route, ItemContainer inventory, ItemContainer bank)
	{
		bankFilterIds.clear();

		TreeSapling sapling = config.treeSapling();
		bankFilterIds.add(sapling.getSaplingItemId());
		bankFilterIds.add(ItemID.SPADE);

		// All axe tiers — bank tag shows whichever the player has
		for (int axeId : new int[]{ItemID.CRYSTAL_AXE, ItemID.INFERNAL_AXE, ItemID.DRAGON_AXE, ItemID.RUNE_AXE})
		{
			bankFilterIds.add(axeId);
		}

		if (config.treePayWatcher())
		{
			bankFilterIds.add(sapling.getPaymentItemId());
		}
		if (config.treePayRemoval())
		{
			bankFilterIds.add(ItemID.COINS);
		}

		// Graceful
		for (int[] slot : RunRoute.GRACEFUL_SLOT_IDS)
		{
			for (int id : slot) bankFilterIds.add(id);
		}

		// Teleport items and runes
		boolean hasSpellTeleport = false;
		for (TreePatchStop stop : route.getStops())
		{
			Teleport tp = stop.getTeleport();
			if (tp == null) continue;
			if (!tp.isSpellBased())
			{
				// Add the lowest-charge variant actually present in bank/inventory
				int found = findLowestChargeVariant(inventory, bank, tp.getItemIds());
				if (found != -1) bankFilterIds.add(found);
			}
			else
			{
				hasSpellTeleport = true;
				for (int id : tp.getRuneIds()) bankFilterIds.add(id);
			}
		}
		if (hasSpellTeleport)
		{
			bankFilterIds.add(ItemID.BH_RUNE_POUCH);
			bankFilterIds.add(ItemID.BH_RUNE_POUCH_TROUVER);
			bankFilterIds.add(ItemID.DIVINE_RUNE_POUCH);
			bankFilterIds.add(ItemID.DIVINE_RUNE_POUCH_TROUVER);
			if (bank != null && RunRoute.hasItemId(bank, ItemID.DUSTRUNE))
			{
				bankFilterIds.remove((Integer) ItemID.EARTHRUNE);
				bankFilterIds.remove((Integer) ItemID.AIRRUNE);
				bankFilterIds.add(ItemID.DUSTRUNE);
			}
		}

		log.debug("Tree run: bank filter set, {} distinct item IDs", bankFilterIds.size());
	}

	private void registerTreeFarmRunTag(TreeRunRoute route, ItemContainer bank)
	{
		clearBankTagsConfig();

		StringBuilder written = new StringBuilder();
		for (int itemId : bankFilterIds)
		{
			String key = "item_" + itemId;
			String existing = configManager.getConfiguration("banktags", key);
			String updated;
			if (existing == null || existing.isEmpty())
			{
				updated = "farm-run";
			}
			else if (!containsTag(existing, "farm-run"))
			{
				updated = existing + ",farm-run";
			}
			else
			{
				continue;
			}
			configManager.setConfiguration("banktags", key, updated);
			if (written.length() > 0) written.append(',');
			written.append(itemId);
		}
		if (written.length() > 0)
		{
			configManager.setConfiguration("farmrun", "pendingTagCleanup", written.toString());
		}
	}

	/** Populates bankFilterIds with every item the player needs to pull from the bank. */
	private void buildBankFilterIds(RunRoute route, ItemContainer inventory, ItemContainer bank)
	{
		bankFilterIds.clear();

		// Seed
		bankFilterIds.add(config.herbSeed().getSeedItemId());

		// Compost — bottomless bucket (only the filled form) takes priority; empty bucket not needed
		if (RunRoute.hasItemId(bank, ItemID.BOTTOMLESS_COMPOST_BUCKET)
			|| RunRoute.hasItemId(bank, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED))
		{
			bankFilterIds.add(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
		}
		else if (config.compostType() == CompostType.ULTRACOMPOST)
		{
			bankFilterIds.add(ItemID.BUCKET_ULTRACOMPOST);
		}
		else if (config.compostType() == CompostType.SUPERCOMPOST)
		{
			bankFilterIds.add(ItemID.BUCKET_SUPERCOMPOST);
		}

		// Tools
		bankFilterIds.add(ItemID.DIBBER);
		bankFilterIds.add(ItemID.SPADE);

		// Magic secateurs
		bankFilterIds.add(ItemID.FAIRY_ENCHANTED_SECATEURS);

		// Graceful pieces — all color variants
		for (int[] slot : RunRoute.GRACEFUL_SLOT_IDS)
		{
			for (int id : slot)
			{
				bankFilterIds.add(id);
			}
		}

		// Teleport items and runes from each stop
		boolean hasSpellTeleport = false;
		for (PatchStop stop : route.getStops())
		{
			Teleport tp = stop.getTeleport();
			if (tp == null)
			{
				continue;
			}
			if (!tp.isSpellBased())
			{
				// Add the lowest-charge variant actually present in bank/inventory
				int found = findLowestChargeVariant(inventory, bank, tp.getItemIds());
				if (found != -1) bankFilterIds.add(found);
			}
			else
			{
				hasSpellTeleport = true;
				for (int id : tp.getRuneIds())
				{
					bankFilterIds.add(id);
				}
			}
		}

		if (hasSpellTeleport)
		{
			// Rune pouch — any variant the player might carry runes in
			bankFilterIds.add(ItemID.BH_RUNE_POUCH);
			bankFilterIds.add(ItemID.BH_RUNE_POUCH_TROUVER);
			bankFilterIds.add(ItemID.DIVINE_RUNE_POUCH);
			bankFilterIds.add(ItemID.DIVINE_RUNE_POUCH_TROUVER);
			// Dust rune covers both earth and air — substitute it and remove the individuals
			if (bank != null && RunRoute.hasItemId(bank, ItemID.DUSTRUNE))
			{
				bankFilterIds.remove((Integer) ItemID.EARTHRUNE);
				bankFilterIds.remove((Integer) ItemID.AIRRUNE);
				bankFilterIds.add(ItemID.DUSTRUNE);
			}
		}

		log.debug("Farm run: bank filter set, {} distinct item IDs", bankFilterIds.size());
	}

	/**
	 * When the bank opens while in BANKING mode, rebuild the checklist with full bank data
	 * (handles the case where the player clicked Start Run before opening the bank) and
	 * activate the bank item filter.
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
					buildTreeBankFilterIds(refreshed, inventory, bank);
					registerTreeFarmRunTag(refreshed, bank);
					BankTagsService svc = bankTagsService();
					if (svc != null)
					{
						svc.openBankTag("farm-run", BankTagsService.OPTION_ALLOW_MODIFICATIONS);
					}
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
					buildBankFilterIds(refreshed, inventory, bank);
					// Re-register so the lambda sees the updated bankFilterIds, then open
					registerFarmRunTag(refreshed, bank);
					BankTagsService svc = bankTagsService();
					if (svc != null)
					{
						svc.openBankTag("farm-run", BankTagsService.OPTION_ALLOW_MODIFICATIONS);
					}
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

	/**
	 * Writes each item in bankFilterIds into the Bank Tags config under the "farm-run" tag
	 * and writes a layout that organises items by category. The item IDs written are persisted
	 * to our own config so a crash during a run doesn't leave orphaned tags in bank-tags data.
	 */
	private void registerFarmRunTag(RunRoute route, ItemContainer bank)
	{
		// Clean up any leftovers from a previous session before writing new ones
		clearBankTagsConfig();

		StringBuilder written = new StringBuilder();
		for (int itemId : bankFilterIds)
		{
			String key = "item_" + itemId;
			String existing = configManager.getConfiguration("banktags", key);
			String updated;
			if (existing == null || existing.isEmpty())
			{
				updated = "farm-run";
			}
			else if (!containsTag(existing, "farm-run"))
			{
				updated = existing + ",farm-run";
			}
			else
			{
				continue; // already tagged
			}
			configManager.setConfiguration("banktags", key, updated);
			if (written.length() > 0) written.append(',');
			written.append(itemId);
		}
		// Persist the list so startUp() can clean up if the plugin crashes
		if (written.length() > 0)
		{
			configManager.setConfiguration("farmrun", "pendingTagCleanup", written.toString());
		}

		// Write the visual layout for this tag
		if (route != null)
		{
			writeLayout(route, bank);
		}
	}

	/**
	 * Builds and saves a Bank Tags layout for "farm-run".
	 *
	 * <p>Sections (each full 8-column rows, separated by a blank gap row):
	 * <ol>
	 *   <li>Graceful — 4 rows, equipment-tab positions (cols 0-1)</li>
	 *   <li>Tools &amp; compost — 2-high block</li>
	 *   <li>Runes — 2-high block (only when route includes a spell teleport)</li>
	 *   <li>Teleports — 2-high block</li>
	 * </ol>
	 */
	private void writeLayout(RunRoute route, ItemContainer bank)
	{
		// Graceful pieces
		int hood   = findFirstInBank(bank, RunRoute.GRACEFUL_SLOT_IDS[0]);
		int cape   = findFirstInBank(bank, RunRoute.GRACEFUL_SLOT_IDS[1]);
		int top    = findFirstInBank(bank, RunRoute.GRACEFUL_SLOT_IDS[2]);
		int legs   = findFirstInBank(bank, RunRoute.GRACEFUL_SLOT_IDS[3]);
		int gloves = findFirstInBank(bank, RunRoute.GRACEFUL_SLOT_IDS[4]);
		int boots  = findFirstInBank(bank, RunRoute.GRACEFUL_SLOT_IDS[5]);

		// Amulet/neck slot: skills necklace or Xeric's talisman (both worn at the amulet slot)
		// Ring slot: explorer's ring — placed in their equipment-tab positions in the armour grid
		int neckSlot = -1;
		int ringSlot = -1;
		Set<Teleport> wearablesInGrid = new HashSet<>();
		for (PatchStop stop : route.getStops())
		{
			Teleport tp = stop.getTeleport();
			if (tp == null) continue;
			if ((tp == Teleport.SKILLS_NECKLACE || tp == Teleport.XERIC_TALISMAN) && neckSlot == -1)
			{
				neckSlot = findFirstInBank(bank, tp.getItemIds());
				wearablesInGrid.add(tp);
			}
			else if (tp == Teleport.EXPLORERS_RING && ringSlot == -1)
			{
				ringSlot = findFirstInBank(bank, tp.getItemIds());
				wearablesInGrid.add(tp);
			}
		}

		// Tools and compost
		List<Integer> toolItems = new ArrayList<>();
		addIfFound(toolItems, findFirstInBank(bank, config.herbSeed().getSeedItemId()));
		addIfFound(toolItems, findFirstInBank(bank, ItemID.DIBBER));
		addIfFound(toolItems, findFirstInBank(bank, ItemID.SPADE));
		addIfFound(toolItems, findFirstInBank(bank, ItemID.FAIRY_ENCHANTED_SECATEURS));
		addIfFound(toolItems, findCompostItem(bank));

		// Runes (only when route includes a spell-based teleport)
		boolean hasSpell = false;
		for (PatchStop stop : route.getStops())
		{
			Teleport tp = stop.getTeleport();
			if (tp != null && tp.isSpellBased()) { hasSpell = true; break; }
		}
		List<Integer> runeItems = new ArrayList<>();
		if (hasSpell)
		{
			addIfFound(runeItems, findFirstInBank(bank,
				ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER,
				ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER));
			addIfFound(runeItems, findFirstInBank(bank, ItemID.LAWRUNE));
			if (RunRoute.hasItemId(bank, ItemID.DUSTRUNE))
				addIfFound(runeItems, ItemID.DUSTRUNE);
			else
			{
				addIfFound(runeItems, findFirstInBank(bank, ItemID.EARTHRUNE));
				addIfFound(runeItems, findFirstInBank(bank, ItemID.AIRRUNE));
			}
		}

		// Non-wearable teleports (item-based, route order, deduplicated, present in bank)
		List<Integer> teleportItems = new ArrayList<>();
		Set<Integer> seenTeleports = new HashSet<>();
		for (PatchStop stop : route.getStops())
		{
			Teleport tp = stop.getTeleport();
			if (tp == null || tp.isSpellBased() || wearablesInGrid.contains(tp)) continue;
			int found = findFirstInBank(bank, tp.getItemIds());
			if (found != -1 && seenTeleports.add(found))
				teleportItems.add(found);
		}

		// Build flat layout list (8 items per row)
		List<Integer> layout = new ArrayList<>();

		// Section 1 – Graceful (cols 0-2) + Tools (cols 3-7), 4 rows
		//   col: 0       1      2        3-7
		//   r0: -1      hood   -1        [tools row 0: seed, dibber, spade]
		//   r1: cape    top    neck      [tools row 1: secateurs, compost]
		//   r2: -1      legs   -1        -1...
		//   r3: gloves  boots  ring      -1...
		int[] r0 = {-1,     hood,  -1,       -1, -1, -1, -1, -1};
		int[] r1 = {cape,   top,   neckSlot, -1, -1, -1, -1, -1};
		int[] r2 = {-1,     legs,  -1,       -1, -1, -1, -1, -1};
		int[] r3 = {gloves, boots, ringSlot, -1, -1, -1, -1, -1};

		int toolCols = toolItems.isEmpty() ? 0 : (toolItems.size() + 1) / 2;
		for (int i = 0; i < toolItems.size(); i++)
		{
			int toolRow = i / toolCols;
			int toolCol = 3 + (i % toolCols);
			if (toolCol < 8)
			{
				if (toolRow == 0) r0[toolCol] = toolItems.get(i);
				else if (toolRow == 1) r1[toolCol] = toolItems.get(i);
			}
		}
		for (int v : r0) layout.add(v);
		for (int v : r1) layout.add(v);
		for (int v : r2) layout.add(v);
		for (int v : r3) layout.add(v);

		// Section 2 – Runes (cols 0-3) and teleports (cols 4-7) side by side with a natural gap
		if (!runeItems.isEmpty() || !teleportItems.isEmpty())
		{
			layoutGap(layout);
			int[] rtRow0 = {-1, -1, -1, -1, -1, -1, -1, -1};
			int[] rtRow1 = {-1, -1, -1, -1, -1, -1, -1, -1};

			int runeCols = runeItems.isEmpty() ? 0 : (runeItems.size() + 1) / 2;
			for (int i = 0; i < runeItems.size(); i++)
			{
				int col = i % runeCols;
				if (i < runeCols) rtRow0[col] = runeItems.get(i);
				else              rtRow1[col] = runeItems.get(i);
			}

			int teleCols = teleportItems.isEmpty() ? 0 : (teleportItems.size() + 1) / 2;
			for (int i = 0; i < teleportItems.size(); i++)
			{
				int col = 4 + (i % teleCols);
				if (i < teleCols) rtRow0[col] = teleportItems.get(i);
				else              rtRow1[col] = teleportItems.get(i);
			}

			for (int v : rtRow0) layout.add(v);
			for (int v : rtRow1) layout.add(v);
		}

		// Serialise and save
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < layout.size(); i++)
		{
			if (i > 0) sb.append(',');
			sb.append(layout.get(i));
		}
		configManager.setConfiguration("banktags", "layout_farm-run", sb.toString());
	}

	/** Appends {@code items} followed by -1 padding to reach the next row boundary (8 cols). */
	private static void layoutRow(List<Integer> layout, int... items)
	{
		for (int item : items) layout.add(item);
		while (layout.size() % 8 != 0) layout.add(-1);
	}

	/** Appends one full blank row (8 empty slots). */
	private static void layoutGap(List<Integer> layout)
	{
		for (int i = 0; i < 8; i++) layout.add(-1);
	}

	/**
	 * Appends {@code items} in a 2-row grid: ceil(N/2) columns wide.
	 * Row 0 holds items 0..cols-1, row 1 holds items cols..2*cols-1, remainder -1.
	 */
	private static void layoutTwoHigh(List<Integer> layout, List<Integer> items)
	{
		if (items.isEmpty()) return;
		int cols = (items.size() + 1) / 2;
		for (int row = 0; row < 2; row++)
		{
			for (int col = 0; col < cols; col++)
			{
				int idx = row * cols + col;
				layout.add(idx < items.size() ? items.get(idx) : -1);
			}
			while (layout.size() % 8 != 0) layout.add(-1);
		}
	}

	private static void addIfFound(List<Integer> list, int id)
	{
		if (id != -1) list.add(id);
	}

	private int findCompostItem(ItemContainer bank)
	{
		if (bank == null) return -1;
		if (RunRoute.hasItemId(bank, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)) return ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
		if (RunRoute.hasItemId(bank, ItemID.BUCKET_ULTRACOMPOST))             return ItemID.BUCKET_ULTRACOMPOST;
		if (RunRoute.hasItemId(bank, ItemID.BUCKET_SUPERCOMPOST))             return ItemID.BUCKET_SUPERCOMPOST;
		return -1;
	}

	/**
	 * Scans {@code ids} from the end (lowest-charge) toward the front and returns the first ID
	 * found in the bank or inventory. Returns -1 if none of the variants are present anywhere.
	 * The Teleport arrays are ordered highest-charge-first, so scanning in reverse finds the
	 * lowest charge the player actually has, keeping the bank tag minimal and accurate.
	 */
	private static int findLowestChargeVariant(ItemContainer inventory, ItemContainer bank, int[] ids)
	{
		for (int i = ids.length - 1; i >= 0; i--)
		{
			if (RunRoute.hasItemId(bank, ids[i]) || RunRoute.hasItemId(inventory, ids[i]))
			{
				return ids[i];
			}
		}
		return -1;
	}

	/** Returns the first item ID from {@code ids} found in {@code bank}, or -1 if none. */
	private static int findFirstInBank(ItemContainer bank, int... ids)
	{
		if (bank == null) return -1;
		for (int id : ids)
		{
			if (RunRoute.hasItemId(bank, id)) return id;
		}
		return -1;
	}

	/**
	 * Looks up BankTagsService at runtime, after BankTagsPlugin has started.
	 * Returns null if Bank Tags is not installed or not yet running.
	 */
	private BankTagsService bankTagsService()
	{
		return pluginManager.getPlugins().stream()
			.filter(p -> p instanceof BankTagsService)
			.map(p -> (BankTagsService) p)
			.findFirst()
			.orElse(null);
	}

	/** Removes the "farm-run" tag from all banktags config entries and closes the tag view. */
	private void closeFarmRunTag()
	{
		clearBankTagsConfig();
		BankTagsService svc = bankTagsService();
		if (svc != null)
		{
			svc.closeBankTag();
		}
	}

	/** Strips "farm-run" from every banktags item entry that was written by this plugin. */
	private void clearBankTagsConfig()
	{
		// Always remove the layout — the key is fixed so no need to track it separately
		configManager.unsetConfiguration("banktags", "layout_farm-run");

		String saved = configManager.getConfiguration("farmrun", "pendingTagCleanup");
		if (saved == null || saved.isEmpty())
		{
			return;
		}
		for (String part : saved.split(","))
		{
			part = part.trim();
			if (part.isEmpty()) continue;
			String key = "item_" + part;
			String existing = configManager.getConfiguration("banktags", key);
			if (existing == null) continue;
			String cleaned = removeTag(existing, "farm-run");
			if (cleaned.isEmpty())
				configManager.unsetConfiguration("banktags", key);
			else
				configManager.setConfiguration("banktags", key, cleaned);
		}
		configManager.unsetConfiguration("farmrun", "pendingTagCleanup");
	}

	private static boolean containsTag(String csv, String tag)
	{
		for (String t : csv.split(","))
		{
			if (t.trim().equalsIgnoreCase(tag)) return true;
		}
		return false;
	}

	private static String removeTag(String csv, String tag)
	{
		StringBuilder sb = new StringBuilder();
		for (String t : csv.split(","))
		{
			String trimmed = t.trim();
			if (trimmed.equalsIgnoreCase(tag)) continue;
			if (sb.length() > 0) sb.append(',');
			sb.append(trimmed);
		}
		return sb.toString();
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
		// Primary slots A–E (4771–4775) and extended slots A1–F2 (4953–4964), F–P (7904–7914)
		return (varbitId >= 4771 && varbitId <= 4775)
			|| (varbitId >= 4953 && varbitId <= 4964)
			|| (varbitId >= 7904 && varbitId <= 7914);
	}

	/** Programmatically generated 16×16 herb/leaf icon for the navigation toolbar. */
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
