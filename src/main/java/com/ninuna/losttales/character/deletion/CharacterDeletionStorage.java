package com.ninuna.losttales.character.deletion;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves and synchronously flushes the deletion-recovery journal. */
public final class CharacterDeletionStorage {

    private CharacterDeletionStorage() {}

    public static CharacterDeletionWorldData get(World world) {
        WorldServer overworld = resolveOverworld(world);
        MapStorage storage = overworld.mapStorage;
        CharacterDeletionWorldData data =
                (CharacterDeletionWorldData) storage.loadData(
                        CharacterDeletionWorldData.class,
                        CharacterDeletionWorldData.DATA_NAME);
        if (data == null) {
            data = new CharacterDeletionWorldData(
                    CharacterDeletionWorldData.DATA_NAME);
            storage.setData(CharacterDeletionWorldData.DATA_NAME, data);
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
                    "Character deletion storage is server-side only");
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
                "Unable to resolve server overworld for deletion storage");
    }
}
