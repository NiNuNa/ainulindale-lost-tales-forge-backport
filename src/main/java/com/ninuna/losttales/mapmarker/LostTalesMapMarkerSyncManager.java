package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** Builds per-player visibility-filtered authoritative marker snapshots. */
public final class LostTalesMapMarkerSyncManager {
    private LostTalesMapMarkerSyncManager() {}

    public static void sync(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(player.worldObj);
        ArrayList<LostTalesMapMarkerDefinition> visible =
                new ArrayList<LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerRecord record : data.getActiveRecords()) {
            if (LostTalesMapMarkerVisibilityPolicy.canView(
                    record, player)) {
                visible.add(record.toDefinition());
            }
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesMapMarkerSnapshotPacket(visible), player);
    }

    public static void syncAll() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        Collection players =
                server.getConfigurationManager().playerEntityList;
        if (players == null) {
            return;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayerMP) {
                sync((EntityPlayerMP)value);
            }
        }
    }
}
