package com.ninuna.losttales.character.lore.ownership;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves and synchronously flushes the world-level ownership store. */
public final class LoreCharacterOwnershipStorage {

    private LoreCharacterOwnershipStorage() {}

    public static LoreCharacterOwnershipWorldData get(World world) {
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        LoreCharacterOwnershipWorldData data =
                (LoreCharacterOwnershipWorldData) storage.loadData(
                        LoreCharacterOwnershipWorldData.class,
                        LoreCharacterOwnershipWorldData.DATA_NAME);
        if (data == null) {
            data = new LoreCharacterOwnershipWorldData(
                    LoreCharacterOwnershipWorldData.DATA_NAME);
            storage.setData(LoreCharacterOwnershipWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    public static void flush(World world) {
        LostTalesDimensionHelper.flushOverworldStorage(world);
    }
}
