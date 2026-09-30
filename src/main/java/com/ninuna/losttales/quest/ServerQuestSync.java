package com.ninuna.losttales.quest;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesServerQuestSyncPacket;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Sends the server's own quests ({@link ServerQuestFiles}) to players: to
 * each as they join, and to everyone after the files are read again. The
 * packets are built once for each read of the files, not for every join.
 */
public final class ServerQuestSync {
    private static List<LostTalesServerQuestSyncPacket> packets;
    /** The read of the files {@link #packets} were built from. */
    private static int packetsRevision = -1;

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

    private static synchronized List<LostTalesServerQuestSyncPacket> packets() {
        int revision = LostTalesQuestRegistry.serverQuestRevision();
        if (packets == null || packetsRevision != revision) {
            packets = LostTalesServerQuestSyncPacket.packetsFor(
                    LostTalesQuestRegistry.getServerQuests());
            packetsRevision = revision;
        }
        return packets;
    }

    /** Lets go of the packets as the server starts and stops. */
    public static synchronized void clear() {
        packets = null;
        packetsRevision = -1;
    }
}
