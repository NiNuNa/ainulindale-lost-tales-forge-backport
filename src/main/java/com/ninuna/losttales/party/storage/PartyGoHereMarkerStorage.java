package com.ninuna.losttales.party.storage;

import net.minecraft.server.MinecraftServer;
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
        WorldServer overworld = resolveOverworld(world);
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

    private static WorldServer resolveOverworld(World world) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            WorldServer overworld = server.worldServerForDimension(0);
            if (overworld != null) {
                return overworld;
            }
        }
        if (world instanceof WorldServer
                && world.provider.dimensionId == 0) {
            return (WorldServer) world;
        }
        throw new IllegalStateException(
                "Unable to resolve the server overworld for party marker storage");
    }
}
