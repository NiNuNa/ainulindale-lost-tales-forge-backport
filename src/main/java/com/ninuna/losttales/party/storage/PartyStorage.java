package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves the one authoritative party store from dimension-zero MapStorage. */
public final class PartyStorage {

    private PartyStorage() {}

    public static PartyWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException("Party storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        PartyWorldData data = (PartyWorldData) storage.loadData(
                PartyWorldData.class, PartyWorldData.DATA_NAME);
        if (data == null) {
            data = new PartyWorldData(PartyWorldData.DATA_NAME);
            storage.setData(PartyWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
