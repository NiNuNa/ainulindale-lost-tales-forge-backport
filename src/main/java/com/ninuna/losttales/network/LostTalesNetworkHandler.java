package com.ninuna.losttales.network;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.network.packet.LostTalesQuickLootDropItemPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class LostTalesNetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(LostTalesMetaData.MOD_ID);

    private LostTalesNetworkHandler() {}

    public static void registerCommonPackets() {
        CHANNEL.registerMessage(LostTalesQuickLootRequestPacket.Handler.class, LostTalesQuickLootRequestPacket.class, 0, Side.SERVER);
        CHANNEL.registerMessage(LostTalesQuickLootDropItemPacket.Handler.class, LostTalesQuickLootDropItemPacket.class, 1, Side.SERVER);
        CHANNEL.registerMessage(LostTalesQuestActionPacket.Handler.class, LostTalesQuestActionPacket.class, 4, Side.SERVER);
    }
}
