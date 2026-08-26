package com.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Local-only companion export for the osrs-mcp Claude Desktop tool.
 *
 * This plugin does not render anything in-game and makes no gameplay
 * decisions or recommendations — it only snapshots inventory/equipment/bank
 * contents and skill levels to a local JSON file. All "what should I wear"
 * logic lives entirely in the external osrs-mcp Python server, not here.
 *
 * Never submitted to the Plugin Hub; loaded via `./gradlew run` (dev client)
 * per the example-plugin template's documented workflow.
 */
@Slf4j
@PluginDescriptor(
	name = "OSRS MCP Bridge",
	description = "Exports inventory/equipment/bank/stats to a local JSON file for the osrs-mcp companion tool",
	tags = {"export", "json", "companion", "mcp"}
)
public class OsrsMcpBridgePlugin extends Plugin
{
	private static final String EXPORT_SUBDIR = "osrs-mcp-bridge";
	private static final long FLUSH_INTERVAL_SECONDS = 2;

	// ExternalPluginManager instantiates this class twice per session
	// (loadExternalPlugins() runs more than once, with no dedup for the
	// loadBuiltin dev-testing path this plugin is loaded through) — this
	// static flag ensures only the first instance actually starts exporting,
	// so we don't end up with two schedulers racing to write the same file.
	//
	// Note: PluginManager.instantiate() requires a plain no-arg constructor
	// for this loading path (confirmed via NoSuchMethodException when a
	// parameterized @Inject constructor was tried) and does field injection
	// separately afterward, so initialization has to stay in startUp() —
	// it can't move to the constructor, where these fields aren't populated
	// yet.
	private static final AtomicBoolean STARTED = new AtomicBoolean(false);

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	private Gson prettyGson;
	private Path exportDir;
	private ScheduledFuture<?> flushTask;
	private final AtomicReference<ExportSnapshot> pending = new AtomicReference<>();

	@Override
	protected void startUp()
	{
		if (!STARTED.compareAndSet(false, true))
		{
			log.debug("OSRS MCP Bridge: duplicate instance started, not starting a second exporter");
			return;
		}

		prettyGson = gson.newBuilder().setPrettyPrinting().create();
		exportDir = RuneLite.RUNELITE_DIR.toPath().resolve(EXPORT_SUBDIR);
		flushTask = executor.scheduleWithFixedDelay(this::flushPending, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
		log.debug("OSRS MCP Bridge started, exporting to {}", exportDir);
	}

	@Override
	protected void shutDown()
	{
		if (flushTask != null)
		{
			flushTask.cancel(false);
			flushTask = null;
		}
		pending.set(null);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			captureSnapshot();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		captureSnapshot();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		captureSnapshot();
	}

	/**
	 * Builds a plain-data snapshot on the client thread (required for safe
	 * access to Client/ItemManager) and hands it off to the executor for
	 * serialization + disk write, so no blocking I/O happens on the client
	 * thread. The snapshot itself is cheap (a few small array iterations),
	 * so building it on every relevant event and debouncing only the actual
	 * file write is simpler than juggling ScheduledFuture cancellation.
	 */
	private void captureSnapshot()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null)
		{
			return;
		}

		ExportSnapshot snapshot = new ExportSnapshot();
		snapshot.username = player.getName();
		snapshot.writtenAt = Instant.now().toString();
		snapshot.stats = captureStats();
		snapshot.questPoints = client.getVarpValue(VarPlayer.QUEST_POINTS);
		snapshot.quests = captureQuests();
		snapshot.inventory = captureContainer(InventoryID.INV);
		snapshot.equipment = captureEquipment();

		// Bank and group storage are only non-null once the player has opened
		// them this session. Leave both null here rather than wiping out a
		// previous session's data — flushPending() carries the last known
		// contents forward from disk when null. We rely on the top-level
		// writtenAt/staleness check for freshness rather than a per-container
		// timestamp — an attempt at tracking bank/group-storage-specific
		// timestamps separately proved unreliable to populate correctly and
		// wasn't worth the added complexity for an informational note.
		snapshot.bank = captureContainer(InventoryID.BANK);

		// GIM group storage. Note: RuneLite's current (non-deprecated) API
		// names this constant INV_GROUP_TEMP, not "group storage" — same
		// numeric container ID (659) as the old deprecated GROUP_STORAGE
		// constant, so this should be the right container, but that's
		// inferred from the ID matching rather than confirmed live.
		snapshot.groupStorage = captureContainer(InventoryID.INV_GROUP_TEMP);

		pending.set(snapshot);
	}

	// Skill.OVERALL is deprecated but must still be referenced to exclude the
	// total-level pseudo-skill from the export — there's no non-deprecated way
	// to name it, so the suppression is intentional here, not a leftover.
	@SuppressWarnings("deprecation")
	private Map<String, ExportedStat> captureStats()
	{
		Map<String, ExportedStat> stats = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			ExportedStat stat = new ExportedStat();
			stat.level = client.getRealSkillLevel(skill);
			stat.xp = client.getSkillExperience(skill);
			stats.put(skill.getName(), stat);
		}
		return stats;
	}

	private Map<String, String> captureQuests()
	{
		Map<String, String> quests = new LinkedHashMap<>();
		for (Quest quest : Quest.values())
		{
			QuestState state = quest.getState(client);
			quests.put(quest.getName(), state.name());
		}
		return quests;
	}

	/**
	 * Returns null for containers that require having been opened this
	 * session (bank, group storage) when they haven't been populated yet —
	 * this is a real limitation of reading their contents, not a bug.
	 */
	private List<ExportedItem> captureContainer(int inventoryId)
	{
		ItemContainer container = client.getItemContainer(inventoryId);
		if (container == null)
		{
			boolean requiresOpening = inventoryId == InventoryID.BANK || inventoryId == InventoryID.INV_GROUP_TEMP;
			return requiresOpening ? null : Collections.emptyList();
		}

		List<ExportedItem> items = new ArrayList<>();
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ExportedItem exported = toExportedItem(item.getId(), item.getQuantity());
			if (exported != null)
			{
				items.add(exported);
			}
		}
		return items;
	}

	private Map<String, ExportedItem> captureEquipment()
	{
		Map<String, ExportedItem> equipment = new LinkedHashMap<>();
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			equipment.put(slot.name().toLowerCase(), null);
		}

		ItemContainer container = client.getItemContainer(InventoryID.WORN);
		if (container == null)
		{
			return equipment;
		}

		Item[] items = container.getItems();
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			int idx = slot.getSlotIdx();
			if (idx < items.length && items[idx].getId() > 0)
			{
				ExportedItem exported = toExportedItem(items[idx].getId(), items[idx].getQuantity());
				if (exported != null)
				{
					equipment.put(slot.name().toLowerCase(), exported);
				}
			}
		}
		return equipment;
	}

	/**
	 * Returns null for bank placeholders (a visual reminder of where an item
	 * goes, with no actual quantity owned) — these must be detected before
	 * any canonicalization, since blanket-canonicalizing (as this used to do)
	 * converts a placeholder into the real item's ID and makes it
	 * indistinguishable from actually owning it. Noted items are resolved to
	 * their unnoted equivalent so a noted and unnoted item report the same
	 * identity, without touching placeholders.
	 */
	private ExportedItem toExportedItem(int itemId, int quantity)
	{
		ItemComposition composition = itemManager.getItemComposition(itemId);

		// A placeholder's own composition reports a non-negative template id
		// pointing back at the real item it's standing in for. Placeholders
		// show up in the bank container with quantity 1 (not 0), which is why
		// the earlier quantity<=0 check in captureContainer never caught them —
		// this is the actual signal to filter on.
		if (composition.getPlaceholderTemplateId() != -1)
		{
			return null;
		}

		int canonicalId = itemManager.canonicalize(itemId);
		ItemComposition canonicalComposition = canonicalId == itemId ? composition : itemManager.getItemComposition(canonicalId);

		ExportedItem exported = new ExportedItem();
		exported.itemId = canonicalId;
		exported.name = canonicalComposition.getName();
		exported.quantity = quantity;
		return exported;
	}

	private void flushPending()
	{
		ExportSnapshot snapshot = pending.getAndSet(null);
		if (snapshot == null)
		{
			return;
		}

		try
		{
			Files.createDirectories(exportDir);
			String filename = sanitizeFilename(snapshot.username) + ".json";
			Path target = exportDir.resolve(filename);

			if ((snapshot.bank == null || snapshot.groupStorage == null) && Files.exists(target))
			{
				ExportSnapshot previous = readPreviousExport(target);
				if (previous != null)
				{
					if (snapshot.bank == null)
					{
						snapshot.bank = previous.bank;
					}
					if (snapshot.groupStorage == null)
					{
						snapshot.groupStorage = previous.groupStorage;
					}
				}
			}

			Path tmp = exportDir.resolve(filename + ".tmp");
			Files.write(tmp, prettyGson.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.debug("Failed to write OSRS MCP Bridge export", e);
		}
	}

	private ExportSnapshot readPreviousExport(Path target)
	{
		try
		{
			String existingJson = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
			return gson.fromJson(existingJson, ExportSnapshot.class);
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.debug("Failed to read previous export for carry-forward", e);
			return null;
		}
	}

	private static String sanitizeFilename(String name)
	{
		return name.replaceAll("[^a-zA-Z0-9 _-]", "_");
	}

	private static class ExportSnapshot
	{
		String username;
		String writtenAt;
		Map<String, ExportedStat> stats;
		int questPoints;
		Map<String, String> quests;
		List<ExportedItem> inventory;
		Map<String, ExportedItem> equipment;
		List<ExportedItem> bank;
		List<ExportedItem> groupStorage;
	}

	private static class ExportedStat
	{
		int level;
		int xp;
	}

	private static class ExportedItem
	{
		int itemId;
		String name;
		int quantity;
	}
}
