package com.ninuna.losttales.chat.server;

import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Vanilla's notice to operators is about the command the console just
 * recorded only when the same person ran it a moment ago.
 */
public final class ChatConsoleCommandHandlerTest {

    @After
    public void cleanUp() {
        ChatConsoleCommandHandler.clear();
    }

    @Test
    public void aNoticeMatchesTheCommandJustRecorded() {
        ChatConsoleCommandHandler.noteRecorded("Nils", 10000L);
        assertTrue(ChatConsoleCommandHandler.recordedJustNow("nils", 10000L));
        assertTrue(ChatConsoleCommandHandler.recordedJustNow("Nils", 11000L));
        assertFalse(ChatConsoleCommandHandler.recordedJustNow("Nils", 11001L));
        assertFalse(ChatConsoleCommandHandler.recordedJustNow("Sam", 10000L));
        assertFalse(ChatConsoleCommandHandler.recordedJustNow(null, 10000L));
    }

    @Test
    public void nothingMatchesBeforeACommandOrAfterClearing() {
        assertFalse(ChatConsoleCommandHandler.recordedJustNow("", 0L));
        assertFalse(ChatConsoleCommandHandler.recordedJustNow("Server", 500L));
        ChatConsoleCommandHandler.noteRecorded("Server", 10000L);
        ChatConsoleCommandHandler.clear();
        assertFalse(ChatConsoleCommandHandler.recordedJustNow("Server", 10000L));
    }

    /**
     * RCON's commands wait for the server thread in the order they came,
     * a bounded number at once, and go when it takes them (DATA-13).
     */
    @Test
    public void rconCommandsWaitForTheServerThreadInOrder() {
        assertTrue(ChatConsoleCommandHandler.waitForServerThread("/op Nils"));
        assertTrue(ChatConsoleCommandHandler.waitForServerThread("/save-all"));
        assertFalse("nothing to write", ChatConsoleCommandHandler.waitForServerThread(" "));
        assertEquals(Arrays.asList("/op Nils", "/save-all"),
                ChatConsoleCommandHandler.takeRconWaiting());
        assertEquals(Collections.emptyList(), ChatConsoleCommandHandler.takeRconWaiting());
        for (int index = 0; index < ChatConsoleCommandHandler.MAX_RCON_WAITING; index++) {
            assertTrue(ChatConsoleCommandHandler.waitForServerThread("/list"));
        }
        assertFalse("past the bound it is left out",
                ChatConsoleCommandHandler.waitForServerThread("/list"));
        ChatConsoleCommandHandler.clear();
        assertTrue("clearing makes room again",
                ChatConsoleCommandHandler.waitForServerThread("/list"));
        assertEquals(1, ChatConsoleCommandHandler.takeRconWaiting().size());
    }
}
