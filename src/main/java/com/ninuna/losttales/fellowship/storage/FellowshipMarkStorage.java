package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves the fellowships' marks from dimension-zero MapStorage. */
public final class FellowshipMarkStorage {

    private FellowshipMarkStorage() {}

    public static FellowshipMarkWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Fellowship mark storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        FellowshipMarkWorldData data =
                (FellowshipMarkWorldData) storage.loadData(
                        FellowshipMarkWorldData.class,
                        FellowshipMarkWorldData.DATA_NAME);
        if (data == null) {
            data = new FellowshipMarkWorldData(
                    FellowshipMarkWorldData.DATA_NAME);
            storage.setData(FellowshipMarkWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
