package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatPresenceSyncPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Everyone's presence as the server holds it. A player's client states
 * the status it chose for each of its identities and whether anybody is
 * at its keyboard; the server decides which identities show at all —
 * the ones in use: the account, the character played and the character
 * spoken as — and tells everyone what each of them shows, as it changes,
 * and all of it to a player who joins. Every other identity reads as
 * Offline, and so does one whose choice is Invisible.
 *
 * <p>Nothing of a player shows until their client has stated its
 * presence, so a player who hides never shows for a moment as they join.
 * The identities in use are looked at again once a second, which is how
 * a change of character reaches everyone; a change of chat identity is
 * told at once. Session state, cleared at both ends of a run.</p>
 */
public final class ChatPresenceService {
    /** Ticks between two looks at which identities each player uses. */
    private static final int REFRESH_TICKS = 20;
    /** What each player chose, by identity; an identity never chosen is Online. */
    private static final Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> CHOSEN =
            new HashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
    /** Players whose client says nobody has been at the keyboard for a while. */
    private static final Set<UUID> IDLE = new HashSet<UUID>();
    /** What everyone was last told of each player who has stated a presence. */
    private static final Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> SHOWN =
            new LinkedHashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
    private static int ticks;

    /** Registered on the event bus for the once-a-second look. */
    public ChatPresenceService() {}

    /**
     * A player's client states its whole presence: the status chosen for
     * each identity, and whether it is idle. Everyone is told what that
     * changes.
     */
    public static synchronized void state(EntityPlayerMP player,
                                          Map<ChatPresenceIdentity, ChatPresence> choices,
                                          boolean idle) {
        if (player == null) {
            return;
        }
        UUID account = player.getUniqueID();
        CHOSEN.put(account, choices == null
                ? new HashMap<ChatPresenceIdentity, ChatPresence>()
                : new HashMap<ChatPresenceIdentity, ChatPresence>(choices));
        if (idle) {
            IDLE.add(account);
        } else {
            IDLE.remove(account);
        }
        refresh(player);
    }

    /**
     * Tells everyone what a player's identities show, when that is not
     * what they were last told: after a new statement, or when the
     * identities the player uses may have changed.
     */
    public static synchronized void refresh(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        UUID account = player.getUniqueID();
        Map<ChatPresenceIdentity, ChatPresence> chosen = CHOSEN.get(account);
        if (chosen == null) {
            return;
        }
        Map<ChatPresenceIdentity, ChatPresence> shown = shown(chosen,
                IDLE.contains(account), inUse(player));
        if (!shown.equals(SHOWN.get(account))) {
            SHOWN.put(account, shown);
            LostTalesNetworkHandler.CHANNEL.sendToAll(
                    new LostTalesChatPresenceSyncPacket(
                            Collections.singletonMap(account, shown)));
        }
    }

    /**
     * What a player's identities show everyone else: each identity in
     * use with its own choice — Online where none was made — Away over an
     * Online choice while the player is idle, and nothing at all for one
     * whose choice is Invisible. The identities not in use are left out,
     * which is what Offline is.
     */
    static Map<ChatPresenceIdentity, ChatPresence> shown(
            Map<ChatPresenceIdentity, ChatPresence> chosen, boolean idle,
            Collection<ChatPresenceIdentity> inUse) {
        Map<ChatPresenceIdentity, ChatPresence> shown =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        for (ChatPresenceIdentity identity : inUse) {
            ChatPresence choice = chosen == null ? null : chosen.get(identity);
            ChatPresence seen = choice == null ? ChatPresence.ONLINE
                    : choice.shownToOthers();
            if (seen == ChatPresence.OFFLINE) {
                continue;
            }
            shown.put(identity, idle && seen == ChatPresence.ONLINE
                    ? ChatPresence.AWAY : seen);
        }
        return shown;
    }

    /** The identities a player is using: the account, and the characters played and spoken as. */
    private static Set<ChatPresenceIdentity> inUse(EntityPlayerMP player) {
        Set<ChatPresenceIdentity> identities =
                new LinkedHashSet<ChatPresenceIdentity>();
        identities.add(ChatPresenceIdentity.ACCOUNT);
        RoleplayCharacter played = CharacterActiveResolver.get(player);
        if (played != null) {
            identities.add(ChatPresenceIdentity.character(
                    played.getCharacterId()));
        }
        RoleplayCharacter speaking = ChatIdentitySelection.character(player);
        if (speaking != null) {
            identities.add(ChatPresenceIdentity.character(
                    speaking.getCharacterId()));
        }
        return identities;
    }

    /** A player who left is offline everywhere, to everyone still here. */
    public static synchronized void forget(UUID account) {
        if (account == null) {
            return;
        }
        CHOSEN.remove(account);
        IDLE.remove(account);
        if (SHOWN.remove(account) != null) {
            LostTalesNetworkHandler.CHANNEL.sendToAll(
                    new LostTalesChatPresenceSyncPacket(Collections.singletonMap(
                            account,
                            Collections.<ChatPresenceIdentity, ChatPresence>emptyMap())));
        }
    }

    /** Everyone's presence to a player who has just joined. */
    public static synchronized void sendAll(EntityPlayerMP joiner) {
        if (joiner == null || SHOWN.isEmpty()) {
            return;
        }
        Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> batch =
                new LinkedHashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
        for (Map.Entry<UUID, Map<ChatPresenceIdentity, ChatPresence>> entry
                : SHOWN.entrySet()) {
            batch.put(entry.getKey(), entry.getValue());
            if (batch.size() == LostTalesChatPresenceSyncPacket.MAX_ACCOUNTS) {
                LostTalesNetworkHandler.CHANNEL.sendTo(
                        new LostTalesChatPresenceSyncPacket(batch), joiner);
                batch = new LinkedHashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
            }
        }
        if (!batch.isEmpty()) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    new LostTalesChatPresenceSyncPacket(batch), joiner);
        }
    }

    /** Once a second, what every player uses is looked at again. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < REFRESH_TICKS) {
            return;
        }
        ticks = 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (value instanceof EntityPlayerMP) {
                refresh((EntityPlayerMP)value);
            }
        }
    }

    public static synchronized void clear() {
        CHOSEN.clear();
        IDLE.clear();
        SHOWN.clear();
        ticks = 0;
    }
}
