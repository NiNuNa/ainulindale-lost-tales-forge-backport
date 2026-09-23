package com.ninuna.losttales.chat;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A server line carries its id and the players it names in marks the
 * client reads back whole, and refuses anything that does not hold
 * together.
 */
public final class ChatBroadcastMarkersTest {

    @Test
    public void aNamedPlayerReadsBackWhole() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        ChatNamedPlayer named = new ChatNamedPlayer(account, "Nils", character,
                "Aldric | of Bree", "human/male/2");
        ChatNamedPlayer read = ChatBroadcastMarkers.decodeNamed(
                ChatBroadcastMarkers.namedValue(named));
        assertEquals(named, read);
        ChatNamedPlayer plain = ChatNamedPlayer.account(account, "Nils");
        assertEquals(plain, ChatBroadcastMarkers.decodeNamed(
                ChatBroadcastMarkers.namedValue(plain)));
    }

    @Test
    public void anythingElseIsNobody() {
        assertNull(ChatBroadcastMarkers.namedValue(null));
        assertNull(ChatBroadcastMarkers.namedValue(
                new ChatNamedPlayer(null, "Nils", null, "", "")));
        assertNull(ChatBroadcastMarkers.decodeNamed("losttales-chat-msgid:12"));
        assertNull(ChatBroadcastMarkers.decodeNamed(
                ChatBroadcastMarkers.NAMED_PREFIX + "not-an-id|Nils|||Nils"));
        assertNull(ChatBroadcastMarkers.decodeNamed(
                ChatBroadcastMarkers.NAMED_PREFIX + UUID.randomUUID() + "||||x"));
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < ChatNamedPlayer.MAX_ACCOUNT_BYTES + 1; index++) {
            long_.append('a');
        }
        assertNull(ChatBroadcastMarkers.decodeNamed(
                ChatBroadcastMarkers.NAMED_PREFIX + UUID.randomUUID() + "|"
                        + long_ + "|||x"));
        // The id mark and the named marks never read as each other.
        assertEquals(ChatMessageIds.NONE, ChatBroadcastMarkers.decode(
                ChatBroadcastMarkers.namedValue(ChatNamedPlayer.account(
                        UUID.randomUUID(), "Nils"))));
    }
}
