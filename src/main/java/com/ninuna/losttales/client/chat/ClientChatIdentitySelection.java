package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatIdentityPacket;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.chat.ChatChannel;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Synchronizes the shared chat selection with the server and holds what
 * the server says of the selected identity's party: its id, the colour
 * the identity wears in it, and its leader's name, which names the
 * Party tab. An answer for another identity than the one selected now
 * is a late one and is ignored.
 */
public final class ClientChatIdentitySelection {
    private static String requestedKey;
    private static boolean requestedNarrating;
    private static String confirmedKey;
    private static String partyKey = "";
    private static int partyColor;
    private static String partyLeader = "";
    private static long requestedAt;

    private ClientChatIdentitySelection() {}

    public static void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null
                || ClientCharacterRosterCache.getSnapshot() == null) { return; }
        String key = ClientChatIdentities.viewIdentityKey();
        boolean narrating = ClientChatIdentities.isNarrating();
        long now = System.nanoTime();
        boolean same = key.equals(requestedKey) && narrating == requestedNarrating;
        if (same && (key.equals(confirmedKey) || now - requestedAt < 2000000000L)) { return; }
        if (!key.equals(requestedKey)) {
            ClientChatTypingState.clear();
            ChatSpeechBubbles.clear();
        }
        requestedKey = key;
        requestedNarrating = narrating;
        requestedAt = now;
        LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesChatIdentityPacket(
                key.length() == 0 ? null : UUID.fromString(key), narrating));
    }

    public static void accept(LostTalesChatIdentitySyncPacket packet) {
        if (packet == null || packet.isMalformed()) { return; }
        String key = ChatTab.ownerKeyOf(packet.getCharacterId());
        if (!key.equals(ClientChatIdentities.viewIdentityKey())) { return; }
        confirmedKey = key;
        partyKey = ChatTab.ownerKeyOf(packet.getPartyId());
        partyColor = packet.getPartyColor();
        partyLeader = packet.getPartyLeader();
        ClientChatIdentities.confirmNarrating(packet.isNarrating());
    }

    /** The selected identity's party, as the server confirmed it; empty for none. */
    static String partyKey() {
        return ClientChatIdentities.viewIdentityKey().equals(confirmedKey) ? partyKey : "";
    }

    static int partyColor() {
        return partyKey().length() == 0 ? ChatChannel.PARTY.getDisplayColor() : partyColor;
    }

    /** The leader's character name of that party; empty without one. */
    static String partyLeader() {
        return partyKey().length() == 0 ? "" : partyLeader;
    }

    public static void clear() {
        requestedKey = null;
        requestedNarrating = false;
        confirmedKey = null;
        partyKey = "";
        partyColor = 0;
        partyLeader = "";
        requestedAt = 0L;
    }
}
