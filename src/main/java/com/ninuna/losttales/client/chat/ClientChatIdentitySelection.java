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
 * Tells the server whom the chat's copies read as besides the character
 * played, so their factions' lines and the whispers sent to them arrive,
 * and holds what the server says of the fellowships of the character
 * played, the one it travels with first: each one's id, name and the
 * colour worn in it, which name and colour its conversation. Told again
 * every two seconds until the server's answer agrees.
 */
public final class ClientChatIdentitySelection {
    private static List<UUID> requested;
    private static List<UUID> confirmed;
    private static List<ChatFellowship> fellowships = Collections.emptyList();
    private static long requestedAt;

    private ClientChatIdentitySelection() {}

    public static void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null
                || ClientCharacterRosterCache.getSnapshot() == null) { return; }
        List<UUID> reading = ClientChatIdentities.readCharacters();
        if (reading.size() > LostTalesChatIdentityPacket.MAX_READ) {
            reading = reading.subList(0, LostTalesChatIdentityPacket.MAX_READ);
        }
        long now = System.nanoTime();
        boolean same = reading.equals(requested);
        if (same && (reading.equals(confirmed) || now - requestedAt < 2000000000L)) { return; }
        requested = reading;
        requestedAt = now;
        LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesChatIdentityPacket(reading));
    }

    public static void accept(LostTalesChatIdentitySyncPacket packet) {
        if (packet == null || packet.isMalformed()) { return; }
        confirmed = packet.getCharacterIds();
        fellowships = packet.getFellowships();
    }

    /** The fellowships of the character played, the one travelled with first, as the server confirmed them. */
    static List<ChatFellowship> fellowships() {
        return fellowships;
    }

    /** The fellowship a conversation's key names, of the character played; null for none. */
    static ChatFellowship fellowship(String key) {
        for (ChatFellowship fellowship : fellowships()) {
            if (ConversationPage.ownerKeyOf(fellowship.getId()).equals(key)) {
                return fellowship;
            }
        }
        return null;
    }

    /** The key of the fellowship travelled with; empty for none. */
    static String travellingKey() {
        List<ChatFellowship> all = fellowships();
        return all.isEmpty() ? "" : ConversationPage.ownerKeyOf(all.get(0).getId());
    }

    public static void clear() {
        requested = null;
        confirmed = null;
        fellowships = Collections.emptyList();
        requestedAt = 0L;
    }
}
