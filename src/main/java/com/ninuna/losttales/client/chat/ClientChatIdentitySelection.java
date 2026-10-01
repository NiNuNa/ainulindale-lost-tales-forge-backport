package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatIdentityPacket;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.chat.ChatFellowship;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Synchronizes the shared chat selection with the server and holds what
 * the server says of the fellowships of the character played, the one
 * it travels with first: each one's id, name and the colour worn in it,
 * which name and colour its conversation. An answer for another identity
 * than the one selected now is a late one and is ignored.
 */
public final class ClientChatIdentitySelection {
    private static String requestedKey;
    private static boolean requestedNarrating;
    private static String confirmedKey;
    private static List<ChatFellowship> fellowships = Collections.emptyList();
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
        fellowships = packet.getFellowships();
        ClientChatIdentities.confirmNarrating(packet.isNarrating());
    }

    /** The fellowships of the character played, the one travelled with first, as the server confirmed them. */
    static List<ChatFellowship> fellowships() {
        return ClientChatIdentities.viewIdentityKey().equals(confirmedKey)
                ? fellowships : Collections.<ChatFellowship>emptyList();
    }

    /** The fellowship a conversation's key names, of the character played; null for none. */
    static ChatFellowship fellowship(String key) {
        for (ChatFellowship fellowship : fellowships()) {
            if (ChatTab.ownerKeyOf(fellowship.getId()).equals(key)) {
                return fellowship;
            }
        }
        return null;
    }

    /** The key of the fellowship travelled with; empty for none. */
    static String travellingKey() {
        List<ChatFellowship> all = fellowships();
        return all.isEmpty() ? "" : ChatTab.ownerKeyOf(all.get(0).getId());
    }

    public static void clear() {
        requestedKey = null;
        requestedNarrating = false;
        confirmedKey = null;
        fellowships = Collections.emptyList();
        requestedAt = 0L;
    }
}
