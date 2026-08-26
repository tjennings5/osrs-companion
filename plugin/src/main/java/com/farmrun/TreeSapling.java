package com.farmrun;

import net.runelite.api.gameval.ItemID;

public enum TreeSapling
{
    //                                                                             removal coins (verify amounts in-game)
    OAK(  "Oak",   ItemID.PLANTPOT_OAK_SAPLING,         15, ItemID.TOMATO,          1,  "Tomato",            25),
    WILLOW("Willow", ItemID.PLANTPOT_WILLOW_SAPLING,     30, ItemID.BASKET_APPLE_5,  1,  "Basket of apples",  50),
    MAPLE( "Maple",  ItemID.PLANTPOT_MAPLE_SAPLING,      45, ItemID.BASKET_ORANGE_5, 1,  "Basket of oranges", 100),
    YEW(   "Yew",    ItemID.PLANTPOT_YEW_SAPLING,        60, ItemID.CACTUS_SPINE,   10,  "Cactus spine",      200),
    MAGIC( "Magic",  ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 75, ItemID.COCONUT,        25,  "Coconut",           500);

    private final String displayName;
    private final int saplingItemId;
    private final int farmingLevel;
    private final int paymentItemId;
    private final int paymentQty;
    private final String paymentName;
    /** Coins per patch to pay the farmer for instant tree removal (verify exact amounts in-game). */
    private final int removalCoins;

    TreeSapling(String displayName, int saplingItemId, int farmingLevel,
        int paymentItemId, int paymentQty, String paymentName, int removalCoins)
    {
        this.displayName = displayName;
        this.saplingItemId = saplingItemId;
        this.farmingLevel = farmingLevel;
        this.paymentItemId = paymentItemId;
        this.paymentQty = paymentQty;
        this.paymentName = paymentName;
        this.removalCoins = removalCoins;
    }

    public String getDisplayName()  { return displayName; }
    public int getSaplingItemId()   { return saplingItemId; }
    public int getFarmingLevel()    { return farmingLevel; }
    public int getPaymentItemId()   { return paymentItemId; }
    public int getPaymentQty()      { return paymentQty; }
    public String getPaymentName()  { return paymentName; }
    public int getRemovalCoins()    { return removalCoins; }
}
