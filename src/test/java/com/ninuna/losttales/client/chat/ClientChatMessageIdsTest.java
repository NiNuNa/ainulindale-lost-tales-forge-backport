package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Crossing between the two names a message has: the server's id, and
 * the chat line this client happens to draw it on.
 */
public final class ClientChatMessageIdsTest {

    @Before
    public void setUp() {
        ClientChatMessageIds.clear();
    }

    @After
    public void tearDown() {
        ClientChatMessageIds.clear();
    }

    @Test
    public void aRememberedMessageIsFoundFromEitherSide() {
        ClientChatMessageIds.remember(7, 1234L);
        assertEquals(1234L, ClientChatMessageIds.messageIdOf(7));
        assertEquals(Integer.valueOf(7),
                ClientChatMessageIds.chatLineIdOf(1234L));
    }

    /** A line nobody can name is not recorded, and names nothing. */
    @Test
    public void unnamedLinesAreNotRecorded() {
        ClientChatMessageIds.remember(7, ChatMessageIds.NONE);
        assertEquals(ChatMessageIds.NONE,
                ClientChatMessageIds.messageIdOf(7));
        assertEquals(0, ClientChatMessageIds.size());
        assertNull(ClientChatMessageIds.chatLineIdOf(ChatMessageIds.NONE));
        assertNull(ClientChatMessageIds.chatLineIdOf(99L));
    }

    /**
     * The client's own lines are named apart from the server's, so one
     * can never be mistaken for the other.
     */
    @Test
    public void localIdsAreNegativeAndDistinct() {
        long first = ClientChatMessageIds.nextLocal();
        long second = ClientChatMessageIds.nextLocal();
        assertTrue(first < ChatMessageIds.NONE);
        assertTrue(second < first);
    }

    /**
     * A line drawn again under a new id takes its message with it, and
     * the message stops pointing at the line it left.
     */
    @Test
    public void aReplacedLineHandsItsMessageOn() {
        ClientChatMessageIds.remember(7, 1234L);
        ClientChatMessageIds.remember(8, 1234L);
        assertEquals(Integer.valueOf(8),
                ClientChatMessageIds.chatLineIdOf(1234L));

        // And a line renamed to another message drops the old name.
        ClientChatMessageIds.remember(8, 5678L);
        assertEquals(5678L, ClientChatMessageIds.messageIdOf(8));
        assertNull(ClientChatMessageIds.chatLineIdOf(1234L));
    }

    /**
     * Bounded with the history: the oldest names fall out, and neither
     * direction is left holding them.
     */
    @Test
    public void oldestNamesFallOutWithTheHistory() {
        int capacity = ClientChatChannelViews.maxTrackedLines();
        for (int index = 0; index < capacity + 50; index++) {
            ClientChatMessageIds.remember(index, 1000L + index);
        }
        assertEquals(capacity, ClientChatMessageIds.size());
        assertEquals(ChatMessageIds.NONE,
                ClientChatMessageIds.messageIdOf(0));
        assertNull(ClientChatMessageIds.chatLineIdOf(1000L));
        int newest = capacity + 49;
        assertEquals(1000L + newest,
                ClientChatMessageIds.messageIdOf(newest));
        assertEquals(Integer.valueOf(newest),
                ClientChatMessageIds.chatLineIdOf(1000L + newest));

        ClientChatMessageIds.clear();
        assertEquals(0, ClientChatMessageIds.size());
    }
}
