package com.ninuna.losttales.progression;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.ProgressionCatalogPacket;
import com.ninuna.losttales.network.packet.ProgressionStatePacket;
import com.ninuna.losttales.progression.deed.DeedDefinition;
import com.ninuna.losttales.progression.deed.DeedEvents;
import com.ninuna.losttales.progression.deed.DeedSummary;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * What a client is sent of the progression: the catalog as it joins and
 * after a reload, and its played character's growth whenever it changed
 * (once a tick at most) or the character was switched.
 */
public final class ProgressionSync {
    private ProgressionSync() {}

    public static void sendCatalog(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        List<DeedSummary> deeds = new ArrayList<DeedSummary>();
        for (DeedDefinition deed : ProgressionCatalog.deeds()) {
            deeds.add(DeedSummary.of(deed));
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new ProgressionCatalogPacket(ProgressionCatalog.skills(), deeds), player);
    }

    /** After a reload: every player gets the catalog again, and their growth with it. */
    public static void sendCatalogToAll(MinecraftServer server) {
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) entry;
                sendCatalog(player);
                ProgressionPlayerData data = ProgressionPlayerData.get(player);
                if (data != null) {
                    data.markDirty();
                }
                DeedEvents.checkSoon(player.getUniqueID());
            }
        }
    }

    public static void sendState(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null) {
            return;
        }
        ProgressionSnapshot snapshot = ProgressionSnapshot.of(data.state(),
                ProgressionSettings.curve(), ProgressionSettings.characterRule(),
                ProgressionSettings.pointsPerLevel(), ProgressionCatalog.skillsById());
        LostTalesNetworkHandler.CHANNEL.sendTo(new ProgressionStatePacket(snapshot), player);
    }
}
