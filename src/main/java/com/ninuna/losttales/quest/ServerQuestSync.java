package com.ninuna.losttales.quest;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesServerQuestSyncPacket;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Sends the server's own quests ({@link ServerQuestFiles}) to players: to
 * each as they join, and to everyone after the files are read again.
 */
public final class ServerQuestSync {

    private ServerQuestSync() {}

    public static void sendTo(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        for (LostTalesServerQuestSyncPacket packet : packets()) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, player);
        }
    }

    public static void sendToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<LostTalesServerQuestSyncPacket> packets = packets();
        for (Object each : server.getConfigurationManager().playerEntityList) {
            if (!(each instanceof EntityPlayerMP)) {
                continue;
            }
            for (LostTalesServerQuestSyncPacket packet : packets) {
                LostTalesNetworkHandler.CHANNEL.sendTo(packet,
                        (EntityPlayerMP)each);
            }
        }
    }

    private static List<LostTalesServerQuestSyncPacket> packets() {
        return LostTalesServerQuestSyncPacket.packetsFor(
                LostTalesQuestRegistry.getServerQuests());
    }
}
