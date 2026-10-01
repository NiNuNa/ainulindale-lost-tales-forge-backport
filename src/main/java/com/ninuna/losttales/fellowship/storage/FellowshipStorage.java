package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves the one authoritative fellowship store from dimension-zero MapStorage. */
public final class FellowshipStorage {

    private FellowshipStorage() {}

    public static FellowshipWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException("Fellowship storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        FellowshipWorldData data = (FellowshipWorldData) storage.loadData(
                FellowshipWorldData.class, FellowshipWorldData.DATA_NAME);
        if (data == null) {
            data = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
            storage.setData(FellowshipWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
