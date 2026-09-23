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
                ChatChannelSuggester.matches("OO", all, 20));
        assertEquals(Arrays.asList(ChatChannel.ALL),
                ChatChannelSuggester.matches("al", all, 20));
        assertEquals(1, ChatChannelSuggester.matches("", all, 1).size());
        assertEquals(0, ChatChannelSuggester.matches("zzz", all, 20).size());
    }

    @Test
    public void aMessageLinkIsTheShownNameASlashAndTheServersId() {
        assertEquals("#Global/1757522000000",
                ChatChannelSuggester.messageLink(ChatChannel.ALL, 1757522000000L));
        assertEquals("#OutofCharacter/12",
                ChatChannelSuggester.messageLink(ChatChannel.OOC, 12L));
        // What it spells resolves back to the channel it names.
        assertSame(ChatChannel.OOC, ChatChannelSuggester.resolve("OutofCharacter"));
        assertSame(ChatChannel.OOC, ChatChannelSuggester.resolve("OOC"));
        // A whisper's message is linked like any other, though the word
        // alone names no whisper; a line the server never named cannot be
        // linked to.
        assertEquals("#Whisper/12",
                ChatChannelSuggester.messageLink(ChatChannel.WHISPER, 12L));
        assertSame(ChatChannel.WHISPER,
                ChatChannelSuggester.linkAt("#Whisper/12", 0).channel);
        assertEquals(12L, ChatChannelSuggester.linkAt("see #Whisper/12 now", 4).messageId);
        assertNull(ChatChannelSuggester.linkAt("#Whisper", 0));
        assertNull(ChatChannelSuggester.resolve("Whisper"));
        assertNull(ChatChannelSuggester.messageLink(ChatChannel.ALL, 0L));
        assertNull(ChatChannelSuggester.messageLink(ChatChannel.ALL, -4L));
        assertNull(ChatChannelSuggester.messageLink(null, 12L));
    }

    @Test
    public void theIdAfterAChannelWordIsASlashAndDigits() {
        String text = "see #Global/1234 and #ooc/ and #all/x #faction/12345678901234567890";
        int global = ChatChannelSuggester.wordEnd(text, text.indexOf("Global"));
        assertEquals(text.indexOf(" and"), ChatChannelSuggester.messageIdEnd(text, global));
        int ooc = ChatChannelSuggester.wordEnd(text, text.indexOf("ooc"));
        assertEquals("a slash with no digits is no id", ooc,
                ChatChannelSuggester.messageIdEnd(text, ooc));
        int all = ChatChannelSuggester.wordEnd(text, text.indexOf("all/"));
        assertEquals(all, ChatChannelSuggester.messageIdEnd(text, all));
        int faction = ChatChannelSuggester.wordEnd(text, text.indexOf("faction"));
        // Eighteen digits at most: what a long can hold.
        assertEquals(faction + 1 + 18,
                ChatChannelSuggester.messageIdEnd(text, faction));
        assertEquals(5, ChatChannelSuggester.messageIdEnd("#all", 5));
    }

    @Test
    public void aWordResolvesByIdOrShownNameAndTheTokenIsTheId() {
        assertSame(ChatChannel.ALL, ChatChannelSuggester.resolve("all"));
        assertSame(ChatChannel.ALL, ChatChannelSuggester.resolve("Global"));
        assertSame(ChatChannel.OOC, ChatChannelSuggester.resolve("OOC"));
        assertNull(ChatChannelSuggester.resolve("discord"));
        assertSame(ChatChannel.CONSOLE, ChatChannelSuggester.resolve("CONSOLE"));
        assertNull(ChatChannelSuggester.resolve("whisper"));
        assertNull(ChatChannelSuggester.resolve("nowhere"));
        assertNull(ChatChannelSuggester.resolve(""));
        assertEquals("#ooc", ChatChannelSuggester.token(ChatChannel.OOC));
        assertEquals(4, ChatChannelSuggester.wordEnd("#all, said", 1));
        assertEquals(1, ChatChannelSuggester.wordEnd("# all", 1));
    }
}
