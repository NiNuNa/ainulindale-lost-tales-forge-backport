package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A message shown before the server has confirmed it: faint while it
 * waits, replaced when it arrives, and given up on if it never does.
 */
public final class ClientChatPendingEchoesTest {
    private static final UUID SENDER = UUID.randomUUID();

    @Before
    public void setUp() {
        ClientChatPendingEchoes.clear();
    }

    @After
    public void tearDown() {
        ClientChatPendingEchoes.clear();
    }

    /** Zero means "no name", so no message may ever be given it. */
    @Test
    public void namesAreNeverZero() {
        for (int index = 0; index < 8; index++) {
            assertTrue(ClientChatPendingEchoes.nextNonce() != 0L);
        }
    }

    @Test
    public void aPromisedLineIsPendingUntilItIsTaken() {
        long nonce = ClientChatPendingEchoes.nextNonce();
        ClientChatPendingEchoes.remember(nonce, 41, packet(), tab(), null,
                1000L);
        assertTrue(ClientChatPendingEchoes.isPending(41));

        ClientChatPendingEchoes.Pending taken =
                ClientChatPendingEchoes.take(nonce);
        assertNotNull(taken);
        assertEquals(41, taken.chatLineId);
        assertFalse(ClientChatPendingEchoes.isPending(41));
        // And only once: a second copy of the same message confirms
        // nothing, so it prints as the message it is.
        assertNull(ClientChatPendingEchoes.take(nonce));
    }

    /** A name nobody promised confirms nothing. */
    @Test
    public void anUnknownNameTakesNothing() {
        assertNull(ClientChatPendingEchoes.take(12345L));
        assertFalse(ClientChatPendingEchoes.isPending(7));
    }

    @Test
    public void onlyPromisesPastTheTimeoutAreGivenUpOn() {
        long old = ClientChatPendingEchoes.nextNonce();
        long recent = ClientChatPendingEchoes.nextNonce();
        ClientChatPendingEchoes.remember(old, 1, packet(), tab(), null, 0L);
        ClientChatPendingEchoes.remember(recent, 2, packet(), tab(), null,
                ClientChatPendingEchoes.TIMEOUT_MILLIS);

        List<ClientChatPendingEchoes.Pending> gone =
                ClientChatPendingEchoes.expired(
                        ClientChatPendingEchoes.TIMEOUT_MILLIS + 1L);
        assertEquals(1, gone.size());
        assertEquals(1, gone.get(0).chatLineId);
        // Handed back once and forgotten: the line has been marked.
        assertFalse(ClientChatPendingEchoes.isPending(1));
        assertTrue(ClientChatPendingEchoes.isPending(2));
        assertTrue(ClientChatPendingEchoes.expired(
                ClientChatPendingEchoes.TIMEOUT_MILLIS + 1L).isEmpty());
    }

    /** Nothing shown early outlives the connection it was said on. */
    @Test
    public void clearingForgetsEverything() {
        ClientChatPendingEchoes.remember(
                ClientChatPendingEchoes.nextNonce(), 3, packet(), tab(),
                null, 0L);
        assertEquals(1, ClientChatPendingEchoes.size());
        ClientChatPendingEchoes.clear();
        assertEquals(0, ClientChatPendingEchoes.size());
        assertFalse(ClientChatPendingEchoes.isPending(3));
    }

    private static ChatTab tab() {
        return ChatTab.of(ChatChannel.OOC);
    }

    private static LostTalesChatMessagePacket packet() {
        return new LostTalesChatMessagePacket(ChatChannel.OOC, SENDER,
                "Steve", "Steve", "", 0xFFFFFF, 0xFFFFFF, "hello", 1L, "");
    }
}
