package com.ninuna.losttales.character.storage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * Resolves the single authoritative character store for the current server.
 */
public final class CharacterStorage {

    private CharacterStorage() {}

    public static CharacterWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException("Character storage is server-side only");
        }

        WorldServer overworld = resolveOverworld(world);
        MapStorage storage = overworld.mapStorage;
        CharacterWorldData data = (CharacterWorldData) storage.loadData(
                CharacterWorldData.class,
                CharacterWorldData.DATA_NAME
        );
        if (data == null) {
            data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
            storage.setData(CharacterWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    private static WorldServer resolveOverworld(World world) {
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

        throw new IllegalStateException("Unable to resolve the server overworld for character storage");
    }
}
