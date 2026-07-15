package com.ninuna.losttales.character.lore.transfer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves and synchronously flushes the lore-character transfer journal. */
public final class LoreCharacterTransferStorage {

    private LoreCharacterTransferStorage() {}

    public static LoreCharacterTransferWorldData get(World world) {
        WorldServer overworld = resolveOverworld(world);
        MapStorage storage = overworld.mapStorage;
        LoreCharacterTransferWorldData data =
                (LoreCharacterTransferWorldData)storage.loadData(
                        LoreCharacterTransferWorldData.class,
                        LoreCharacterTransferWorldData.DATA_NAME);
        if (data == null) {
            data = new LoreCharacterTransferWorldData(
                    LoreCharacterTransferWorldData.DATA_NAME);
            storage.setData(LoreCharacterTransferWorldData.DATA_NAME, data);
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
                    "Lore-character transfer storage is server-side only");
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            WorldServer overworld = server.worldServerForDimension(0);
            if (overworld != null) return overworld;
        }
        if (world instanceof WorldServer && world.provider.dimensionId == 0) {
            return (WorldServer)world;
        }
        throw new IllegalStateException("Unable to resolve server overworld");
    }
}
