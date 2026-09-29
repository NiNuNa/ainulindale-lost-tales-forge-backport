package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves the one world quest store from dimension zero's MapStorage. */
public final class WorldQuestStorage {

    private WorldQuestStorage() {}

    public static WorldQuestWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "World quest storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        WorldQuestWorldData data = (WorldQuestWorldData)storage.loadData(
                WorldQuestWorldData.class, WorldQuestWorldData.DATA_NAME);
        if (data == null) {
            data = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
            storage.setData(WorldQuestWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
