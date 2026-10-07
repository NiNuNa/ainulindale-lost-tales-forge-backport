package com.ninuna.losttales.compat.lotr.structure;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves the structure bans from dimension-zero MapStorage. */
public final class LotrStructureBanStorage {

    private LotrStructureBanStorage() {}

    public static LotrStructureBanWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException("Structure bans are server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        LotrStructureBanWorldData data = (LotrStructureBanWorldData) storage.loadData(
                LotrStructureBanWorldData.class, LotrStructureBanWorldData.DATA_NAME);
        if (data == null) {
            data = new LotrStructureBanWorldData(LotrStructureBanWorldData.DATA_NAME);
            storage.setData(LotrStructureBanWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
