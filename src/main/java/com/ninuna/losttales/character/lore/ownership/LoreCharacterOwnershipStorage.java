package com.ninuna.losttales.character.lore.ownership;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves and synchronously flushes the world-level ownership store. */
public final class LoreCharacterOwnershipStorage {

    private LoreCharacterOwnershipStorage() {}

    public static LoreCharacterOwnershipWorldData get(World world) {
        WorldServer overworld = resolveOverworld(world);
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
        resolveOverworld(world).mapStorage.saveAllData();
    }

    private static WorldServer resolveOverworld(World world) {
        if (world == null || world.isRemote) {
            throw new IllegalArgumentException(
                    "Lore-character ownership storage is server-side only");
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            WorldServer overworld = server.worldServerForDimension(0);
            if (overworld != null) {
                return overworld;
            }
        }
        if (world instanceof WorldServer && world.provider.dimensionId == 0) {
            return (WorldServer) world;
        }
        throw new IllegalStateException(
                "Unable to resolve server overworld for lore-character ownership storage");
    }
}
