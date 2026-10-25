package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatIdentityPacket;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.chat.ChatFellowship;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Tells the server whom the chat's copies read as besides the character
 * played, so their factions' lines and the whispers sent to them arrive,
 * and holds what the server says of the fellowships of the character
 * played, the one it travels with first: each one's id, name and the
 * colour worn in it, which name and colour its conversation. Told again
 * every two seconds until the server's answer agrees. It holds too which
 * fellowships any character of the account is in: a fellowship's
 * conversation stays in its window, hidden, while one is, and leaves the
 * windows once none is (WIN-18).
 */
public final class ClientChatIdentitySelection {
    private static List<UUID> requested;
    private static List<UUID> confirmed;
    private static List<ChatFellowship> fellowships = Collections.emptyList();
    /** Whether the server could say which fellowships the account's characters are in. */
    private static boolean accountKnown;
    /** The conversation keys of the fellowships any character of the account is in. */
    private static Set<String> accountFellowships = Collections.emptySet();
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

    /**
     * The server's answer: who the copies read as and the fellowships.
     * A fellowship's conversation no character of the account is in any
     * more leaves every window.
     */
    public static void accept(LostTalesChatIdentitySyncPacket packet) {
        if (packet == null || packet.isMalformed()) { return; }
        confirmed = packet.getCharacterIds();
        fellowships = packet.getFellowships();
        accountKnown = packet.isAccountKnown();
        Set<String> keys = new HashSet<String>();
        for (UUID id : packet.getAccountFellowshipIds()) {
            keys.add(ConversationPage.ownerKeyOf(id));
        }
        accountFellowships = Collections.unmodifiableSet(keys);
        ChatLayout.dropFellowshipsLeft();
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

    /**
     * Whether the conversation of the fellowship {@code key} names stays
     * in the windows: while any character of the account is in it, the
     * one played or another, and while the server has not said.
     */
    static boolean keepsFellowship(String key) {
        return keeps(key, fellowship(key) != null, accountKnown,
                accountFellowships);
    }

    /**
     * The rule {@link #keepsFellowship} follows: a fellowship's
     * conversation stays while the character played is in it
     * ({@code played}), while the server has not said which fellowships
     * the account's characters are in ({@code known} false), and while one
     * of them is ({@code account} holds its key).
     */
    static boolean keeps(String key, boolean played, boolean known,
                         Collection<String> account) {
        return played || !known || account != null && account.contains(key);
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
        accountKnown = false;
        accountFellowships = Collections.emptySet();
        requestedAt = 0L;
    }
}
