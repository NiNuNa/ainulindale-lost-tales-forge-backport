package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMembersRequestPacket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * What the server last said of who is in each conversation, for the
 * member lists of the windows showing it, and when to ask again. A
 * conversation is the one a tab shows ({@link ChatTab#viewed}), known by
 * its id: a channel's, a faction's or a party's as the chat is read, a
 * whisper held as one identity, an NPC's.
 *
 * <p>A window whose list stands asks for its front tab's conversation
 * while it has no answer, once the answer is older than
 * {@link #REFRESH_MILLIS}, and at once when the identity the chat is read
 * as has changed since it asked, but never more often than
 * {@link #MIN_ASK_MILLIS}. Each ask carries the fingerprint of the answer
 * held, so the server only says so when nothing has changed; a whisper's
 * ask names its other party and the identity it is held as. Until a new
 * answer comes, the old one stays on screen. Session state, cleared on
 * disconnect.</p>
 */
public final class ClientChatMembers {
    /** How long an answer is kept before the list asks again. */
    static final long REFRESH_MILLIS = 4000L;
    /** The least time between two asks for one conversation. */
    private static final long MIN_ASK_MILLIS = 1000L;

    /** One conversation's members as last told, and how many absent ones the answer left out. */
    static final class Answer {
        final List<LostTalesChatMembersPacket.Member> members;
        final int unlisted;
        final long fingerprint;

        Answer(List<LostTalesChatMembersPacket.Member> members, int unlisted,
               long fingerprint) {
            this.members = members;
            this.unlisted = unlisted;
            this.fingerprint = fingerprint;
        }
    }

    private static final Map<String, Answer> BY_CONVERSATION =
            new HashMap<String, Answer>();
    private static final Map<String, Long> ASKED_AT = new HashMap<String, Long>();
    private static final Map<String, Long> ANSWERED_AT = new HashMap<String, Long>();
    /** The identity the chat was read as when each conversation was last asked for. */
    private static final Map<String, String> ASKED_AS = new HashMap<String, String>();

    private ClientChatMembers() {}

    /**
     * The server's answer: the conversation's members, in the order it
     * stands them, or the word that the answer held still stands.
     */
    public static synchronized void accept(LostTalesChatMembersPacket packet) {
        if (packet == null || packet.getChannel() == null) {
            return;
        }
        String key = packet.getConversationKey();
        if (packet.isUnchanged()) {
            Answer held = BY_CONVERSATION.get(key);
            if (held != null && held.fingerprint == packet.getFingerprint()) {
                ANSWERED_AT.put(key, Long.valueOf(System.currentTimeMillis()));
            }
            return;
        }
        BY_CONVERSATION.put(key, new Answer(packet.getMembers(),
                packet.getUnlisted(), packet.getFingerprint()));
        ANSWERED_AT.put(key, Long.valueOf(System.currentTimeMillis()));
        // A member shown as the account wears the account's own skin,
        // which is asked for once, as a line's account head's is.
        Minecraft minecraft = Minecraft.getMinecraft();
        for (LostTalesChatMembersPacket.Member member : packet.getMembers()) {
            if (member.getCharacterId() == null) {
                LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                        minecraft, member.getPlayerId(), member.getAccount());
            }
        }
    }

    /** The answer last told for the conversation the tab shows, or null while none has been. */
    static synchronized Answer of(ChatTab tab) {
        return tab == null ? null : BY_CONVERSATION.get(keyOf(tab));
    }

    /**
     * Asks for the members of the conversation the tab shows when its
     * list is due an answer; nothing when one was asked for too recently.
     */
    static void requestIfDue(ChatTab tab) {
        ChatTab viewed = ChatTab.viewed(tab);
        if (viewed == null || viewed.getChannel() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String asAs = ClientChatIdentities.viewIdentityKey();
        String key = viewed.id();
        long held;
        synchronized (ClientChatMembers.class) {
            if (!isDue(now, ASKED_AT.get(key), ANSWERED_AT.get(key),
                    asAs.equals(ASKED_AS.get(key)))) {
                return;
            }
            ASKED_AT.put(key, Long.valueOf(now));
            ASKED_AS.put(key, asAs);
            Answer answer = BY_CONVERSATION.get(key);
            held = answer == null ? 0L : answer.fingerprint;
        }
        boolean player = viewed.isWhisper() && !viewed.isNpc();
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatMembersRequestPacket(viewed.getChannel(), key,
                        player ? viewed.getPartner() : "",
                        player ? viewed.getPartnerIdentity() : "",
                        player ? ClientChatChannelState.partnerCharacterIdOf(viewed)
                                : null,
                        viewed.isWhisper() ? heldCharacterOf(viewed, asAs) : null,
                        held));
    }

    /**
     * The player's own character a whisper is held as: the one the
     * conversation names, else — for an NPC's, which names none — the one
     * the chat is read as; null for the account.
     */
    private static UUID heldCharacterOf(ChatTab conversation, String readAs) {
        String owner = conversation.getOwnerKey().length() > 0
                ? conversation.getOwnerKey() : readAs;
        if (owner == null || owner.length() == 0) {
            return null;
        }
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    /** The key a tab's conversation is known by. */
    private static String keyOf(ChatTab tab) {
        ChatTab viewed = ChatTab.viewed(tab);
        return viewed == null ? "" : viewed.id();
    }

    /**
     * Whether a conversation asked for at {@code askedAt} and last
     * answered at {@code answeredAt} is to be asked for again at
     * {@code now}; either may be null for never. {@code sameIdentity}
     * says whether the chat is still read as the identity it was asked as.
     */
    static boolean isDue(long now, Long askedAt, Long answeredAt,
                         boolean sameIdentity) {
        if (askedAt != null && now - askedAt.longValue() < MIN_ASK_MILLIS) {
            return false;
        }
        if (askedAt == null || answeredAt == null || !sameIdentity) {
            return true;
        }
        return now - answeredAt.longValue() >= REFRESH_MILLIS;
    }

    public static synchronized void clear() {
        BY_CONVERSATION.clear();
        ASKED_AT.clear();
        ANSWERED_AT.clear();
        ASKED_AS.clear();
    }
}
