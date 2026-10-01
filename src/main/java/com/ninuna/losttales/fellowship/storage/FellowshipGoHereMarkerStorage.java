package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves personal fellowship markers from dimension-zero MapStorage. */
public final class FellowshipGoHereMarkerStorage {

    private FellowshipGoHereMarkerStorage() {}

    public static FellowshipGoHereMarkerWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Fellowship marker storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        FellowshipGoHereMarkerWorldData data =
                (FellowshipGoHereMarkerWorldData) storage.loadData(
                        FellowshipGoHereMarkerWorldData.class,
                        FellowshipGoHereMarkerWorldData.DATA_NAME);
        if (data == null) {
            data = new FellowshipGoHereMarkerWorldData(
                    FellowshipGoHereMarkerWorldData.DATA_NAME);
            storage.setData(FellowshipGoHereMarkerWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
