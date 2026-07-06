package com.ninuna.losttales.client.network;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class LostTalesQuestSyncHandler implements IMessageHandler<LostTalesQuestSyncPacket, IMessage> {
    @Override
    public IMessage onMessage(LostTalesQuestSyncPacket message, MessageContext ctx) {
        LostTalesClientQuestNotificationStore.notifyForIncomingSync(message.getActiveQuests(), message.getCompletedQuestIds());
        LostTalesClientMapMarkerStore.setDynamicMarkers(message.getDynamicMapMarkers());
        LostTalesClientQuestProgressStore.update(message.getActiveQuests(), message.getCompletedQuestIds(), message.getPinnedQuestId(), message.getDiscoveredMarkerIds(), message.getPinnedMapMarkerId());
        return null;
    }
}
