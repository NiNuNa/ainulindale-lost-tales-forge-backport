package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void typistsAreListedInOrderUntilTheyStopOrExpire() {
        ChatTab party = ChatTab.of(ChatChannel.PARTY);
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        ClientChatTypingState.apply(party, "Aragorn", true, 0L);
        ClientChatTypingState.apply(party, "Gimli", true, 1L);
        ClientChatTypingState.apply(ooc, "Steve", true, 1L);
        assertEquals(Arrays.asList("Aragorn", "Gimli"),
                ClientChatTypingState.namesTyping(party, 2L));
        assertEquals(Collections.singletonList("Steve"),
                ClientChatTypingState.namesTyping(ooc, 2L));
        // A refresh keeps the order; a stop removes the one name.
        ClientChatTypingState.apply(party, "Aragorn", true, 3L);
        assertEquals(Arrays.asList("Aragorn", "Gimli"),
                ClientChatTypingState.namesTyping(party, 4L));
        ClientChatTypingState.apply(party, "Gimli", false, 4L);
        assertEquals(Collections.singletonList("Aragorn"),
                ClientChatTypingState.namesTyping(party, 5L));
        // Without a refresh a name expires on its own; a lost stop never
        // leaves a ghost.
        assertEquals(Collections.singletonList("Aragorn"),
                ClientChatTypingState.namesTyping(party,
                        3L + ClientChatTypingState.TTL_NANOS - 1L));
        assertTrue(ClientChatTypingState.namesTyping(party,
                3L + ClientChatTypingState.TTL_NANOS).isEmpty());
        // Whisper typists are filed under the partner's tab, by name,
        // case-insensitively like the tab itself.
        ClientChatTypingState.apply(ChatTab.whisper("bilbo"), "Bilbo",
                true, 0L);
        assertEquals(Collections.singletonList("Bilbo"),
                ClientChatTypingState.namesTyping(ChatTab.whisper("Bilbo"),
                        1L));
        ClientChatTypingState.clear();
        assertTrue(ClientChatTypingState.namesTyping(party, 1L).isEmpty());
    }

    @Test
    public void aTabKeepsABoundedNumberOfTypists() {
        ChatTab global = ChatTab.of(ChatChannel.ALL);
        for (int index = 0; index < ClientChatTypingState.MAX_NAMES_PER_TAB + 5;
             index++) {
            ClientChatTypingState.apply(global, "Player" + index, true,
                    index);
        }
        assertEquals(ClientChatTypingState.MAX_NAMES_PER_TAB,
                ClientChatTypingState.namesTyping(global, 100L).size());
        assertEquals("Player5",
                ClientChatTypingState.namesTyping(global, 100L).get(0));
    }
}
