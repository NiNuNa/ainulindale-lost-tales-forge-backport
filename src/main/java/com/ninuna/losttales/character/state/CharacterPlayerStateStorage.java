package com.ninuna.losttales.character.state;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

import java.util.UUID;

/** Resolves one isolated character player-state file per Minecraft account. */
public final class CharacterPlayerStateStorage {

    private CharacterPlayerStateStorage() {}

    public static CharacterPlayerStateWorldData get(World world, UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        WorldServer overworld = resolveOverworld(world);
        MapStorage storage = overworld.mapStorage;
        String dataName = CharacterPlayerStateWorldData.dataName(ownerId);
        CharacterPlayerStateWorldData data =
                (CharacterPlayerStateWorldData) storage.loadData(
                        CharacterPlayerStateWorldData.class, dataName);
        if (data == null) {
            data = new CharacterPlayerStateWorldData(dataName);
            storage.setData(dataName, data);
            data.markDirty();
        }
        return data;
    }

    public static void flush(World world) {
        resolveOverworld(world).mapStorage.saveAllData();
    }

    private static WorldServer resolveOverworld(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Character player state storage is server-side only");
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
                "Unable to resolve server overworld for character player state storage");
    }
}
