package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatPresenceSyncPacket;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Everyone's presence as the server holds it: what each player chose,
 * or what idle time chose for them, told to every client as it changes
 * and whole to a player who joins. Online is the resting state and is
 * held as no entry. A player who leaves is Online again to whoever
 * sees them next. Cleared at both ends of a run.
 */
public final class ChatPresenceService {
    private static final Map<UUID, ChatPresence> PRESENCE =
            new HashMap<UUID, ChatPresence>();

    private ChatPresenceService() {}

    /** An account's presence; Online for one that never said. */
    public static synchronized ChatPresence of(UUID account) {
        ChatPresence presence = account == null ? null : PRESENCE.get(account);
        return presence == null ? ChatPresence.ONLINE : presence;
    }

    /** Sets a player's presence and tells everyone online. */
    public static synchronized void set(EntityPlayerMP player, ChatPresence presence) {
        if (player == null || presence == null) {
            return;
        }
        if (presence == ChatPresence.ONLINE) {
            PRESENCE.remove(player.getUniqueID());
        } else {
            PRESENCE.put(player.getUniqueID(), presence);
        }
        LostTalesNetworkHandler.CHANNEL.sendToAll(new LostTalesChatPresenceSyncPacket(
                Collections.singletonMap(player.getUniqueID(), presence)));
    }

    /** A player who left is Online again to everyone still here. */
    public static synchronized void forget(UUID account) {
        if (account != null && PRESENCE.remove(account) != null) {
            LostTalesNetworkHandler.CHANNEL.sendToAll(new LostTalesChatPresenceSyncPacket(
                    Collections.singletonMap(account, ChatPresence.ONLINE)));
        }
    }

    /** Everyone's presence to a player who has just joined. */
    public static synchronized void sendAll(EntityPlayerMP joiner) {
        if (joiner == null || PRESENCE.isEmpty()) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(new LostTalesChatPresenceSyncPacket(
                new LinkedHashMap<UUID, ChatPresence>(PRESENCE)), joiner);
    }

    public static synchronized void clear() {
        PRESENCE.clear();
    }
}
