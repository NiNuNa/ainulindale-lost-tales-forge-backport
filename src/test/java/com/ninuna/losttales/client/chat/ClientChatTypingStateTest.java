package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClientChatTypingStateTest {

    @Before
    public void reset() {
        ClientChatTypingState.clear();
    }

    @After
    public void cleanUp() {
        ClientChatTypingState.clear();
    }

    /**
     * Only typing heard nearby shows over a head (CHAT-58), found by the
     * typist's account, never by a name: a fellowship's, OOC's or an NPC's
     * typing does not, and neither does a namesake's.
     */
    @Test
    public void onlyTypingHeardNearbyShowsOverTheHead() {
        UUID aragorn = new UUID(1L, 1L);
        UUID steve = new UUID(1L, 2L);
        UUID namesake = new UUID(1L, 3L);
        ClientChatTypingState.apply(ConversationPage.of(ChatChannel.PROXIMITY),
                "Aragorn", aragorn, true, 0L);
        ClientChatTypingState.apply(ConversationPage.of(ChatChannel.FELLOWSHIP),
                "Gimli", steve, true, 0L);
        ClientChatTypingState.apply(ConversationPage.of(ChatChannel.OOC), "Steve",
                steve, true, 0L);
        ClientChatTypingState.apply(ConversationPage.npc("Bard"), "Bard", true, 0L);
        long soon = ClientChatTypingState.TTL_NANOS - 1L;
        assertTrue(ClientChatTypingState.isTypingNearby(aragorn, soon));
        assertTrue(ClientChatTypingState.anyTypingNearby(soon));
        assertFalse("a fellowship's words are not said aloud",
                ClientChatTypingState.isTypingNearby(steve, soon));
        assertFalse("a namesake shows nothing",
                ClientChatTypingState.isTypingNearby(namesake, soon));
        assertFalse(ClientChatTypingState.isTypingNearby(aragorn,
                ClientChatTypingState.TTL_NANOS));
        assertFalse(ClientChatTypingState.isTypingNearby(null, soon));
        ClientChatTypingState.apply(ConversationPage.of(ChatChannel.PROXIMITY),
                "Aragorn", aragorn, false, 1L);
        assertFalse(ClientChatTypingState.anyTypingNearby(soon));
    }

    @Test
    public void typistsAreListedInOrderUntilTheyStopOrExpire() {
        ConversationPage fellowship = ConversationPage.of(ChatChannel.FELLOWSHIP);
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        ClientChatTypingState.apply(fellowship, "Aragorn", true, 0L);
        ClientChatTypingState.apply(fellowship, "Gimli", true, 1L);
        ClientChatTypingState.apply(ooc, "Steve", true, 1L);
        assertEquals(Arrays.asList("Aragorn", "Gimli"),
                ClientChatTypingState.namesTyping(fellowship, 2L));
        assertEquals(Collections.singletonList("Steve"),
                ClientChatTypingState.namesTyping(ooc, 2L));
        // A refresh keeps the order; a stop removes the one name.
        ClientChatTypingState.apply(fellowship, "Aragorn", true, 3L);
        assertEquals(Arrays.asList("Aragorn", "Gimli"),
                ClientChatTypingState.namesTyping(fellowship, 4L));
        ClientChatTypingState.apply(fellowship, "Gimli", false, 4L);
        assertEquals(Collections.singletonList("Aragorn"),
                ClientChatTypingState.namesTyping(fellowship, 5L));
        // Without a refresh a name expires on its own; a lost stop never
        // leaves a ghost.
        assertEquals(Collections.singletonList("Aragorn"),
                ClientChatTypingState.namesTyping(fellowship,
                        3L + ClientChatTypingState.TTL_NANOS - 1L));
        assertTrue(ClientChatTypingState.namesTyping(fellowship,
                3L + ClientChatTypingState.TTL_NANOS).isEmpty());
        // Whisper typists are filed under the partner's page, by name,
        // case-insensitively like the page itself.
        ClientChatTypingState.apply(ConversationPage.whisper("bilbo", ""), "Bilbo",
                true, 0L);
        assertEquals(Collections.singletonList("Bilbo"),
                ClientChatTypingState.namesTyping(ConversationPage.whisper("Bilbo", ""),
                        1L));
        ClientChatTypingState.clear();
        assertTrue(ClientChatTypingState.namesTyping(fellowship, 1L).isEmpty());
    }

    @Test
    public void aPageKeepsABoundedNumberOfTypists() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        for (int index = 0; index < ClientChatTypingState.MAX_NAMES_PER_PAGE + 5;
             index++) {
            ClientChatTypingState.apply(global, "Player" + index, true,
                    index);
        }
        assertEquals(ClientChatTypingState.MAX_NAMES_PER_PAGE,
                ClientChatTypingState.namesTyping(global, 100L).size());
        assertEquals("Player5",
                ClientChatTypingState.namesTyping(global, 100L).get(0));
    }
}
