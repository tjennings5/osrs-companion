package com.osrsmcp.farmrun;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * Regular tree patches. Access quest and teleport priority listed per patch.
 * Gnome Stronghold requires THE_GRAND_TREE or TREE_GNOME_VILLAGE (either suffices).
 *
 * <p>stateVarbit values are best-guess FARMING_TRANSMIT_* assignments —
 * auto-advance is proximity-based so a wrong slot doesn't break the run.
 * Verify and correct slot letters after first in-game test.
 */
public enum TreePatch
{
    FARMING_GUILD(
        "Farming Guild",
        new WorldPoint(1233, 3742, 0),
        null, null,
        65,
        VarbitID.FARMING_TRANSMIT_A,
        0,
        Teleport.FARMING_CAPE, Teleport.SKILLS_NECKLACE
    ),
    LUMBRIDGE(
        "Lumbridge",
        new WorldPoint(3193, 3231, 0),
        null, null,
        1,
        VarbitID.FARMING_TRANSMIT_A,
        1,
        Teleport.LUMBRIDGE_SPELL, Teleport.LUMBRIDGE_TABLET, Teleport.POH_LUMBRIDGE
    ),
    VARROCK(
        "Varrock",
        new WorldPoint(3229, 3459, 0),
        null, null,
        1,
        VarbitID.FARMING_TRANSMIT_A,
        2,
        Teleport.VARROCK_SPELL, Teleport.VARROCK_TABLET, Teleport.POH_VARROCK
    ),
    FALADOR(
        "Falador",
        new WorldPoint(3004, 3372, 0),
        null, null,
        1,
        VarbitID.FARMING_TRANSMIT_A,
        3,
        Teleport.RING_OF_WEALTH,
        Teleport.FALADOR_SPELL, Teleport.FALADOR_TABLET, Teleport.POH_FALADOR
    ),
    TAVERLEY(
        "Taverley",
        new WorldPoint(2936, 3439, 0),
        null, null,
        1,
        VarbitID.FARMING_TRANSMIT_A,
        4,
        Teleport.RING_OF_DUELING, Teleport.GAMES_NECKLACE,
        Teleport.CAMELOT_SPELL, Teleport.CAMELOT_TABLET, Teleport.POH_CAMELOT
    ),
    GNOME_STRONGHOLD(
        "Gnome Stronghold",
        new WorldPoint(2461, 3414, 0),
        Quest.THE_GRAND_TREE, Quest.TREE_GNOME_VILLAGE,
        1,
        VarbitID.FARMING_TRANSMIT_A,
        5,
        Teleport.SLAYER_RING,
        Teleport.ARDOUGNE_CAPE, Teleport.POH_ARDOUGNE,
        Teleport.ARDOUGNE_SPELL, Teleport.ARDOUGNE_TABLET
    );

    private final String displayName;
    private final WorldPoint location;
    /** Primary quest gate — may be null. Either this OR accessQuest2 is sufficient. */
    private final Quest accessQuest;
    /** Secondary quest gate — if non-null, either quest qualifies for access. */
    private final Quest accessQuest2;
    /** Minimum Farming level required (1 = no practical requirement). */
    private final int minFarmingLevel;
    private final int stateVarbit;
    private final int routeIndex;
    private final List<Teleport> teleportPriority;

    TreePatch(String displayName, WorldPoint location,
        Quest accessQuest, Quest accessQuest2,
        int minFarmingLevel, int stateVarbit, int routeIndex, Teleport... teleports)
    {
        this.displayName = displayName;
        this.location = location;
        this.accessQuest = accessQuest;
        this.accessQuest2 = accessQuest2;
        this.minFarmingLevel = minFarmingLevel;
        this.stateVarbit = stateVarbit;
        this.routeIndex = routeIndex;
        this.teleportPriority = Arrays.asList(teleports);
    }

    public String getDisplayName()          { return displayName; }
    public WorldPoint getLocation()         { return location; }
    public Quest getAccessQuest()           { return accessQuest; }
    public Quest getAccessQuest2()          { return accessQuest2; }
    public int getMinFarmingLevel()         { return minFarmingLevel; }
    public int getStateVarbit()             { return stateVarbit; }
    public int getRouteIndex()              { return routeIndex; }
    public List<Teleport> getTeleportPriority() { return teleportPriority; }

    public boolean isAccessible(Client client)
    {
        if (minFarmingLevel > 1 && client.getRealSkillLevel(Skill.FARMING) < minFarmingLevel)
        {
            return false;
        }
        if (accessQuest != null)
        {
            boolean q1Done = accessQuest.getState(client) == net.runelite.api.QuestState.FINISHED;
            boolean q2Done = accessQuest2 != null
                && accessQuest2.getState(client) == net.runelite.api.QuestState.FINISHED;
            if (!q1Done && !q2Done)
            {
                return false;
            }
        }
        return true;
    }

    public int distanceTo(WorldPoint point)
    {
        return location.distanceTo2D(point);
    }

    public static TreePatch[] inRouteOrder()
    {
        TreePatch[] patches = values().clone();
        Arrays.sort(patches, (a, b) -> Integer.compare(a.routeIndex, b.routeIndex));
        return patches;
    }
}
