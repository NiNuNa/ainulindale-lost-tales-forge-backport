package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves and synchronously flushes the lore-character transfer journal. */
public final class LoreCharacterTransferStorage {

    private LoreCharacterTransferStorage() {}

    public static LoreCharacterTransferWorldData get(World world) {
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        LoreCharacterTransferWorldData data =
                (LoreCharacterTransferWorldData)storage.loadData(
                        LoreCharacterTransferWorldData.class,
                        LoreCharacterTransferWorldData.DATA_NAME);
        if (data == null) {
            data = new LoreCharacterTransferWorldData(
                    LoreCharacterTransferWorldData.DATA_NAME);
            storage.setData(LoreCharacterTransferWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    public static void flush(World world) {
        LostTalesDimensionHelper.flushOverworldStorage(world);
    }
}
