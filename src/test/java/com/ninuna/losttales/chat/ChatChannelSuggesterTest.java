package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * A {@code #} that opens a word asks for a channel; the word names one
 * by its id or its shown name; the completion writes the id.
 */
public final class ChatChannelSuggesterTest {

    @After
    public void cleanUp() {
        ChatChannel.resetToBuiltIn();
    }

    @Test
    public void theQueryIsTheWordBehindAHashThatOpensAWord() {
        ChatChannelSuggester.Query query = ChatChannelSuggester.findQuery("see #gl", 7);
        assertEquals(4, query.hashIndex);
        assertEquals("gl", query.prefix);
        assertEquals("", ChatChannelSuggester.findQuery("#", 1).prefix);
        assertNull("a hash inside a word is not a channel",
                ChatChannelSuggester.findQuery("item#3", 6));
        assertNull("a command completes nothing",
                ChatChannelSuggester.findQuery("/say #all", 9));
        assertNull("the cursor has to stand in the word",
                ChatChannelSuggester.findQuery("#all done", 9));
        assertNull(ChatChannelSuggester.findQuery(null, 0));
    }

    @Test
    public void matchesGoByIdOrShownName() {
        List<ChatChannel> all = ChatChannel.presentationOrder();
        assertEquals(all, ChatChannelSuggester.matches("", all, 20));
        List<ChatChannel> global = ChatChannelSuggester.matches("glo", all, 20);
        assertEquals(Arrays.asList(ChatChannel.ALL), global);
        assertEquals(Arrays.asList(ChatChannel.OOC),
                ChatChannelSuggester.matches("OOC&", all, 20));
        assertEquals(Arrays.asList(ChatChannel.ALL),
                ChatChannelSuggester.matches("al", all, 20));
        assertEquals(1, ChatChannelSuggester.matches("", all, 1).size());
        assertEquals(0, ChatChannelSuggester.matches("zzz", all, 20).size());
    }

    @Test
    public void aWordResolvesByIdOrShownNameAndTheTokenIsTheId() {
        assertSame(ChatChannel.ALL, ChatChannelSuggester.resolve("all"));
        assertSame(ChatChannel.ALL, ChatChannelSuggester.resolve("Global"));
        assertSame(ChatChannel.OOC, ChatChannelSuggester.resolve("ooc&discord"));
        assertSame(ChatChannel.OOC, ChatChannelSuggester.resolve("discord"));
        assertSame(ChatChannel.CONSOLE, ChatChannelSuggester.resolve("CONSOLE"));
        assertNull(ChatChannelSuggester.resolve("whisper"));
        assertNull(ChatChannelSuggester.resolve("nowhere"));
        assertNull(ChatChannelSuggester.resolve(""));
        assertEquals("#ooc", ChatChannelSuggester.token(ChatChannel.OOC));
        assertEquals(4, ChatChannelSuggester.wordEnd("#all, said", 1));
        assertEquals(1, ChatChannelSuggester.wordEnd("# all", 1));
    }
}
