package com.ninuna.losttales.chat.server;

import org.junit.After;
import org.junit.Test;

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
}
