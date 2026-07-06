package com.ninuna.losttales.client.network;

import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class LostTalesQuickLootContainerSyncHandler implements IMessageHandler<LostTalesQuickLootContainerSyncPacket, IMessage> {
    @Override
    public IMessage onMessage(LostTalesQuickLootContainerSyncPacket message, MessageContext ctx) {
        LostTalesClientQuickLootCache.update(message.getX(), message.getY(), message.getZ(), message.getTitle(), message.isSealed(), message.getItems());
        return null;
    }
}
