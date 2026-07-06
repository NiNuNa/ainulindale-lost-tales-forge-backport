package com.ninuna.losttales.client.network;

import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** Applies the server-authoritative mob aggro snapshot to the client cache. */
public class LostTalesMobAggroSyncHandler implements IMessageHandler<LostTalesMobAggroSyncPacket, IMessage> {
    @Override
    public IMessage onMessage(LostTalesMobAggroSyncPacket message, MessageContext ctx) {
        if (message != null) {
            LostTalesClientMobAggroCache.accept(message.getEntityIds());
        }
        return null;
    }
}
