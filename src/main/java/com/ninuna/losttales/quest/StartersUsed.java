package com.ninuna.losttales.quest;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

/**
 * Whether a quest starter may still start its quest in a world, and the
 * record that it did ({@link StartersUsedWorldData}, QUEST-34): once only,
 * by anyone, as a missive is taken once. Server only.
 */
final class StartersUsed {
    /** What the world says of a starter. */
    enum Standing {
        /** Never used: it may start its quest. */
        UNUSED,
        /** Used already: a copy, which starts nothing. */
        USED,
        /** The world's record cannot be read, so no starter starts anything now. */
        NOT_NOW
    }

    private StartersUsed() {}

    /** Where the starter stands in this world: unused, used, or not to be judged now. */
    static Standing standing(World world, ItemStack starter) {
        StartersUsedWorldData data = of(world);
        if (data == null || !data.isUsable()) {
            if (data != null && data.firstRefusal()) {
                LostTalesLog.warning("The record of quest starters used in this world"
                        + " cannot be read; no starter can start a quest until it can");
            }
            return Standing.NOT_NOW;
        }
        return data.wasUsed(QuestStarters.nonceOf(starter)) ? Standing.USED : Standing.UNUSED;
    }

    /**
     * Records the starter as used; answers whether the world now remembers
     * it: not while the record is read-only or full.
     */
    static boolean record(World world, ItemStack starter) {
        StartersUsedWorldData data = of(world);
        if (data == null) {
            return false;
        }
        boolean recorded = data.use(QuestStarters.nonceOf(starter));
        if (!recorded && data.isUsable()
                && data.size() >= StartersUsedWorldData.MAX_ENTRIES
                && data.firstRefusal()) {
            LostTalesLog.warning("The record of quest starters used in this world holds"
                    + " %s already; no starter can start a quest until it has room",
                    Integer.valueOf(StartersUsedWorldData.MAX_ENTRIES));
        }
        return recorded;
    }

    /** The overworld's record, made on first use; null off the server. */
    private static StartersUsedWorldData of(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage;
        try {
            storage = LostTalesDimensionHelper.overworld(world).mapStorage;
        } catch (IllegalStateException noOverworld) {
            return null;
        }
        if (storage == null) {
            return null;
        }
        StartersUsedWorldData data = (StartersUsedWorldData) storage.loadData(
                StartersUsedWorldData.class, StartersUsedWorldData.DATA_NAME);
        if (data == null) {
            data = new StartersUsedWorldData(StartersUsedWorldData.DATA_NAME);
            storage.setData(StartersUsedWorldData.DATA_NAME, data);
        }
        return data;
    }
}
