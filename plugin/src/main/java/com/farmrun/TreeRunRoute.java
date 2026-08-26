package com.farmrun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;

class TreeRunRoute
{
    private final List<TreePatchStop> stops;
    private final List<BankItem> bankChecklist;

    TreeRunRoute(Client client, ItemContainer inventory, ItemContainer bank, FarmRunConfig config)
    {
        stops = buildStops(client, inventory, bank, config);
        bankChecklist = buildChecklist(config, stops, inventory, bank);
    }

    List<TreePatchStop> getStops()      { return stops; }
    List<BankItem> getBankChecklist()   { return bankChecklist; }
    int size()                          { return stops.size(); }

    private static List<TreePatchStop> buildStops(Client client, ItemContainer inventory,
        ItemContainer bank, FarmRunConfig config)
    {
        List<TreePatchStop> result = new ArrayList<>();
        for (TreePatch patch : TreePatch.inRouteOrder())
        {
            if (!isAccessible(patch, client))
            {
                continue;
            }
            Teleport teleport = chooseTeleport(patch, inventory, bank, client, config);
            result.add(new TreePatchStop(patch, teleport));
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean isAccessible(TreePatch patch, Client client)
    {
        return patch.isAccessible(client);
    }

    private static Teleport chooseTeleport(TreePatch patch, ItemContainer inventory,
        ItemContainer bank, Client client, FarmRunConfig config)
    {
        Teleport bestGuess = null;
        for (Teleport teleport : patch.getTeleportPriority())
        {
            if (teleport.isPohRoute())
            {
                if (RunRoute.isPohTeleportAvailable(teleport, inventory, bank, client, config))
                {
                    return teleport;
                }
                if (bestGuess == null
                    && RunRoute.isPohFeatureConfirmed(teleport, client, config))
                {
                    bestGuess = teleport;
                }
            }
            else if (teleport.isAvailable(inventory, bank, client))
            {
                return teleport;
            }
            else if (bank == null && bestGuess == null)
            {
                bestGuess = teleport;
            }
        }
        return bestGuess;
    }

    private static List<BankItem> buildChecklist(FarmRunConfig config, List<TreePatchStop> stops,
        ItemContainer inventory, ItemContainer bank)
    {
        if (stops.isEmpty())
        {
            return Collections.emptyList();
        }

        int patchCount = stops.size();
        List<BankItem> items = new ArrayList<>();
        TreeSapling sapling = config.treeSapling();

        // --- Axe (best available) ---
        int[] axes = {ItemID.CRYSTAL_AXE, ItemID.INFERNAL_AXE, ItemID.DRAGON_AXE, ItemID.RUNE_AXE};
        for (int axeId : axes)
        {
            if (!RunRoute.hasItemId(inventory, axeId)
                && (bank == null || RunRoute.hasItemId(bank, axeId)))
            {
                items.add(new BankItem(axeName(axeId), 1));
                break;
            }
            if (RunRoute.hasItemId(inventory, axeId))
            {
                break; // already carrying it
            }
        }

        // --- Sapling ---
        if (!RunRoute.hasItemId(inventory, sapling.getSaplingItemId()))
        {
            items.add(new BankItem(sapling.getDisplayName() + " sapling", patchCount));
        }

        // --- Spade ---
        if (!RunRoute.hasItemId(inventory, ItemID.SPADE)
            && (bank == null || RunRoute.hasItemId(bank, ItemID.SPADE)))
        {
            items.add(new BankItem("Spade", 1));
        }

        // --- Farmer protection payment ---
        if (config.treePayWatcher())
        {
            items.add(new BankItem(sapling.getPaymentName(), sapling.getPaymentQty() * patchCount));
        }

        // --- Farmer removal payment ---
        if (config.treePayRemoval())
        {
            items.add(new BankItem("Coins (tree removal)", sapling.getRemovalCoins() * patchCount));
        }

        // --- Graceful ---
        for (int slot = 0; slot < RunRoute.GRACEFUL_SLOT_IDS.length; slot++)
        {
            boolean inInventory = RunRoute.hasAnyItemId(inventory, RunRoute.GRACEFUL_SLOT_IDS[slot]);
            boolean inBank = RunRoute.hasAnyItemId(bank, RunRoute.GRACEFUL_SLOT_IDS[slot]);
            if (!inInventory && (bank == null || inBank))
            {
                items.add(new BankItem(RunRoute.GRACEFUL_SLOT_NAMES[slot], 1));
            }
        }

        // --- Teleport items and runes (deduplicated) ---
        Set<String> addedTeleports = new LinkedHashSet<>();
        boolean hasSpell = false;
        for (TreePatchStop stop : stops)
        {
            Teleport tp = stop.getTeleport();
            if (tp == null) continue;
            if (tp.isSpellBased())
            {
                hasSpell = true;
            }
            else if (addedTeleports.add(tp.getDisplayName()))
            {
                items.add(new BankItem(tp.getDisplayName(), 1));
            }
        }
        if (hasSpell)
        {
            items.add(new BankItem("Rune pouch (with spell runes)", 1));
        }

        return Collections.unmodifiableList(items);
    }

    private static String axeName(int itemId)
    {
        if (itemId == ItemID.CRYSTAL_AXE)  return "Crystal axe";
        if (itemId == ItemID.INFERNAL_AXE) return "Infernal axe";
        if (itemId == ItemID.DRAGON_AXE)   return "Dragon axe";
        return "Rune axe";
    }
}
