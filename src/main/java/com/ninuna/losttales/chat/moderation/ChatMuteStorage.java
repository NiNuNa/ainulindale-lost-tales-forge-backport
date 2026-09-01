package com.ninuna.losttales.chat.moderation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves the chat mute list from dimension-zero MapStorage. */
public final class ChatMuteStorage {

    private ChatMuteStorage() {}

    public static ChatMuteWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Chat mute storage is server-side only");
        }
        WorldServer overworld = resolveOverworld(world);
        MapStorage storage = overworld.mapStorage;
        ChatMuteWorldData data = (ChatMuteWorldData) storage.loadData(
                ChatMuteWorldData.class, ChatMuteWorldData.DATA_NAME);
        if (data == null) {
            data = new ChatMuteWorldData(ChatMuteWorldData.DATA_NAME);
            storage.setData(ChatMuteWorldData.DATA_NAME, data);
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
                "Unable to resolve the server overworld for chat mute storage");
    }
}
