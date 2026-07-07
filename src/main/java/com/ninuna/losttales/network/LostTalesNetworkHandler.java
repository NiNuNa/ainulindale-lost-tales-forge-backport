package com.ninuna.losttales.network;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootDropItemPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootRequestPacket;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class LostTalesNetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(LostTalesMetaData.MOD_ID);

    private LostTalesNetworkHandler() {}

    public static void registerCommonPackets() {
        // Client -> server requests. These handlers are common/server-safe and validate
        // every request on the logical server before touching world state.
        CHANNEL.registerMessage(LostTalesQuickLootRequestPacket.Handler.class, LostTalesQuickLootRequestPacket.class, 0, Side.SERVER);
        CHANNEL.registerMessage(LostTalesQuickLootDropItemPacket.Handler.class, LostTalesQuickLootDropItemPacket.class, 1, Side.SERVER);
        CHANNEL.registerMessage(LostTalesQuestActionPacket.Handler.class, LostTalesQuestActionPacket.class, 4, Side.SERVER);

        // Server -> client snapshots. These are registered from the common proxy so a
        // dedicated server also knows the packet discriminators when it sends them.
        // The handlers route through the sided proxy instead of importing client-only
        // cache/renderer classes here, keeping dedicated-server class loading safe.
        CHANNEL.registerMessage(LostTalesQuickLootContainerSyncPacket.Handler.class, LostTalesQuickLootContainerSyncPacket.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesQuestSyncPacket.Handler.class, LostTalesQuestSyncPacket.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesMobAggroSyncPacket.Handler.class, LostTalesMobAggroSyncPacket.class, 5, Side.CLIENT);
    }
}
