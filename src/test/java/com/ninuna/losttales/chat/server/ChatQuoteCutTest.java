package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Arrays;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Whether a quote of a kept line is cut (CHAT-86): what the Discord
 * bridge, which fades nothing, ends a cut quote's words with dots by.
 */
public final class ChatQuoteCutTest {
    private static final UUID ALICE = UUID.randomUUID();

    @Before
    @After
    public void clear() {
        ChatHistory.clear();
    }

    @Test
    public void onlyALineLongerThanItsQuoteIsCut() {
        StringBuilder long_ = new StringBuilder();
        while (long_.length() < ChatReplyReference.MAX_EXCERPT_CHARACTERS + 10) {
            long_.append("word ");
        }
        assertTrue(ChatHistory.quoteIsCut(record(long_.toString().trim())));
        assertFalse(ChatHistory.quoteIsCut(record("short words")));
        assertFalse("a line not kept", ChatHistory.quoteIsCut(ChatMessageIdAllocator.next()));
    }

    private static long record(String text) {
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, ALICE, "Aldric", null, new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, ALICE, "Aldric", "alice", "", 0, 0, text,
                1000000L, "", null, "", "", 0, false, id, ChatReplyReference.NONE, ""),
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        return id;
    }
}
