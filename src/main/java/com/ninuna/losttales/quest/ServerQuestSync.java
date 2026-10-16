package com.ninuna.losttales.quest;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesServerQuestSyncPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Sends the server's own quests ({@link ServerQuestFiles}) to a client
 * only once it needs them ({@link ServerQuestVisibility}): as a quest
 * enters its player's log, as a world quest run is shown to it, and as a
 * giver offers it. What each client was sent is kept until its player
 * leaves; after the files are read again, each client is sent afresh
 * what it holds and the server still has.
 */
public final class ServerQuestSync {
    /** The server quests each player's client was sent, by account. */
    private static final Map<UUID, Set<String>> SENT = new HashMap<UUID, Set<String>>();

    private ServerQuestSync() {}

    /** Sends the player those of {@code questIds} that are server quests their client was not sent yet. */
    public static void reveal(EntityPlayerMP player, Collection<String> questIds) {
        if (player == null || questIds == null || questIds.isEmpty()) {
            return;
        }
        List<LostTalesQuestDefinition> quests;
        synchronized (ServerQuestSync.class) {
            Set<String> sent = sentTo(player.getUniqueID());
            List<String> fresh = ServerQuestVisibility.newlyVisible(sent,
                    questIds, LostTalesQuestRegistry.serverQuestIds());
            if (fresh.isEmpty()) {
                return;
            }
            quests = definitions(fresh);
            sent.addAll(fresh);
        }
        send(player, LostTalesServerQuestSyncPacket.packetsFor(quests, false));
    }

    /** Sends the player every server quest their log names: running, ended and tracked. */
    public static void revealLog(EntityPlayerMP player,
                                 LostTalesQuestPlayerData data) {
        if (data != null) {
            reveal(player, data.getQuestIdsInLog());
        }
    }

    /**
     * After the server's files are read again: each player online is sent
     * afresh, in place of what their client held, what they were sent
     * that the server still has and what their log names now.
     */
    public static void resendAfterReload(MinecraftServer server) {
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        Set<String> serverQuests = LostTalesQuestRegistry.serverQuestIds();
        for (Object each : server.getConfigurationManager().playerEntityList) {
            if (!(each instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP)each;
            LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
            List<LostTalesQuestDefinition> quests;
            synchronized (ServerQuestSync.class) {
                Set<String> holds = ServerQuestVisibility.afterReload(
                        sentTo(player.getUniqueID()),
                        data == null ? Collections.<String>emptySet()
                                : data.getQuestIdsInLog(),
                        serverQuests);
                SENT.put(player.getUniqueID(), holds);
                quests = definitions(holds);
            }
            send(player, LostTalesServerQuestSyncPacket.packetsFor(quests, true));
        }
    }

    /** Forgets what a player's client was sent, as they join and as they leave. */
    public static synchronized void forget(UUID playerId) {
        SENT.remove(playerId);
    }

    /** Forgets every client as the server starts and stops. */
    public static synchronized void clear() {
        SENT.clear();
    }

    private static Set<String> sentTo(UUID playerId) {
        Set<String> sent = SENT.get(playerId);
        if (sent == null) {
            sent = new LinkedHashSet<String>();
            SENT.put(playerId, sent);
        }
        return sent;
    }

    private static List<LostTalesQuestDefinition> definitions(
            Collection<String> questIds) {
        List<LostTalesQuestDefinition> quests =
                new ArrayList<LostTalesQuestDefinition>();
        for (String questId : questIds) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getServerQuest(questId);
            if (quest != null) {
                quests.add(quest);
            }
        }
        return quests;
    }

    private static void send(EntityPlayerMP player,
                             List<LostTalesServerQuestSyncPacket> packets) {
        for (LostTalesServerQuestSyncPacket packet : packets) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, player);
        }
    }
}
