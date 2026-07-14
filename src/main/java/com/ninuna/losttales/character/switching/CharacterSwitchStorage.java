package com.ninuna.losttales.character.switching;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves and synchronously flushes the dedicated switch-state store. */
public final class CharacterSwitchStorage {

    private CharacterSwitchStorage() {}

    public static CharacterSwitchWorldData get(World world) {
        WorldServer overworld = resolveOverworld(world);
        MapStorage storage = overworld.mapStorage;
        CharacterSwitchWorldData data = (CharacterSwitchWorldData) storage.loadData(
                CharacterSwitchWorldData.class,
                CharacterSwitchWorldData.DATA_NAME);
        if (data == null) {
            data = new CharacterSwitchWorldData(CharacterSwitchWorldData.DATA_NAME);
            storage.setData(CharacterSwitchWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    /**
     * Switches are rare and security-sensitive, so phase boundaries force all
     * dirty overworld map data to disk instead of waiting for the next autosave.
     */
    public static void flush(World world) {
        resolveOverworld(world).mapStorage.saveAllData();
    }

    private static WorldServer resolveOverworld(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException("Character switch storage is server-side only");
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
        throw new IllegalStateException("Unable to resolve server overworld for character switch storage");
    }
}
