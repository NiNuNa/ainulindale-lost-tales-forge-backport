package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves personal party markers from dimension-zero MapStorage. */
public final class PartyGoHereMarkerStorage {

    private PartyGoHereMarkerStorage() {}

    public static PartyGoHereMarkerWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Party marker storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        PartyGoHereMarkerWorldData data =
                (PartyGoHereMarkerWorldData) storage.loadData(
                        PartyGoHereMarkerWorldData.class,
                        PartyGoHereMarkerWorldData.DATA_NAME);
        if (data == null) {
            data = new PartyGoHereMarkerWorldData(
                    PartyGoHereMarkerWorldData.DATA_NAME);
            storage.setData(PartyGoHereMarkerWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
