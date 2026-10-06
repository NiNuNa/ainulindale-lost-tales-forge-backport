package com.ninuna.losttales.chat;

import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * A {@code #} that opens a word asks for a channel; the word names one by
 * its code name alone, a faction's for a faction's chat; the completion
 * and every link write that code name.
 */
public final class ChatChannelSuggesterTest {

    @Before
    public void factions() {
        ChatCodeNames.installFactions(Arrays.asList(
                "lotr:gondor", "lotr:high_elf", "lotr:unaligned"));
    }

    @After
    public void cleanUp() {
        ChatChannel.resetToBuiltIn();
        ChatCodeNames.installFactions(Collections.<String>emptyList());
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
                ChatChannelSuggester.findQuery("/say #global", 12));
        assertNull("the cursor has to stand in the word",
                ChatChannelSuggester.findQuery("#global done", 12));
        assertNull(ChatChannelSuggester.findQuery(null, 0));
    }

    @Test
    public void matchesGoByCodeNameOrShownName() {
        List<ChatChannel> all = ChatChannel.presentationOrder();
        List<ChatChannel> named = new java.util.ArrayList<ChatChannel>(all);
        named.remove(ChatChannel.FELLOWSHIP);
        assertEquals("a fellowship is one conversation each, which the word alone names none of",
                named, ChatChannelSuggester.matches("", all, "lotr:gondor", 20, EnglishWords.INSTANCE));
        assertEquals(Arrays.asList(ChatChannel.GLOBAL),
                ChatChannelSuggester.matches("glo", all, "lotr:gondor", 20, EnglishWords.INSTANCE));
        assertEquals(Arrays.asList(ChatChannel.OOC),
                ChatChannelSuggester.matches("OO", all, "lotr:gondor", 20, EnglishWords.INSTANCE));
        assertEquals("the shown name finds a channel too",
                Arrays.asList(ChatChannel.SERVER_CONSOLE),
                ChatChannelSuggester.matches("serverl", all, "lotr:gondor", 20, EnglishWords.INSTANCE));
        assertEquals(Arrays.asList(ChatChannel.CLIENT_CONSOLE),
                ChatChannelSuggester.matches("cons", all, "lotr:gondor", 20, EnglishWords.INSTANCE));
        assertEquals("the Faction channel answers to its faction's name",
                Arrays.asList(ChatChannel.FACTION),
                ChatChannelSuggester.matches("gon", all, "lotr:gondor", 20, EnglishWords.INSTANCE));
        assertEquals(1, ChatChannelSuggester.matches("", all, "lotr:gondor", 1, EnglishWords.INSTANCE).size());
        assertEquals(0, ChatChannelSuggester.matches("zzz", all, "lotr:gondor", 20, EnglishWords.INSTANCE).size());
        assertEquals("without a faction the Faction channel has no name",
                0, ChatChannelSuggester.matches("fac", all, "", 20, EnglishWords.INSTANCE).size());
    }

    /** The shown name is the game's own: a channel is found by it there, and always by its code name. */
    @Test
    public void matchesGoByTheShownNameInTheGamesLanguage() {
        List<ChatChannel> all = ChatChannel.presentationOrder();
        LostTalesWords german = new LostTalesWords() {
            @Override
            public String format(String key, Object... arguments) {
                return ChatChannel.GLOBAL.getNameKey().equals(key) ? "Allgemeiner Chat"
                        : EnglishWords.INSTANCE.format(key, arguments);
            }
        };
        assertEquals(Arrays.asList(ChatChannel.GLOBAL),
                ChatChannelSuggester.matches("allgemeiner", all, "lotr:gondor", 20, german));
        assertEquals("the code name stays",
                Arrays.asList(ChatChannel.GLOBAL),
                ChatChannelSuggester.matches("glo", all, "lotr:gondor", 20, german));
    }

    @Test
    public void aMessageLinkIsTheCodeNameASlashAndTheServersId() {
        assertEquals("#global/1757522000000",
                ChatChannelSuggester.messageLink(ChatChannel.GLOBAL, "", 1757522000000L));
        assertEquals("#ooc/12",
                ChatChannelSuggester.messageLink(ChatChannel.OOC, "", 12L));
        assertEquals("#gondor/12",
                ChatChannelSuggester.messageLink(ChatChannel.FACTION, "lotr:gondor", 12L));
        // A whisper's message is linked like any other, though the word
        // alone names no whisper; a line the server never named cannot be
        // linked to.
        assertEquals("#whisper/12",
                ChatChannelSuggester.messageLink(ChatChannel.WHISPER, "", 12L));
        assertSame(ChatChannel.WHISPER,
                ChatChannelSuggester.linkAt("#whisper/12", 0).channel);
        assertEquals(12L, ChatChannelSuggester.linkAt("see #Whisper/12 now", 4).messageId);
        assertNull(ChatChannelSuggester.linkAt("#whisper", 0));
        assertSame(ChatChannel.FELLOWSHIP,
                ChatChannelSuggester.linkAt("#fellowship/12", 0).channel);
        assertNull(ChatChannelSuggester.linkAt("#fellowship", 0));
        assertNull(ChatChannelSuggester.messageLink(ChatChannel.GLOBAL, "", 0L));
        assertNull(ChatChannelSuggester.messageLink(ChatChannel.GLOBAL, "", -4L));
        assertNull(ChatChannelSuggester.messageLink(null, "", 12L));
        assertNull("a faction nobody knows has no link",
                ChatChannelSuggester.messageLink(ChatChannel.FACTION, "lotr:nowhere", 12L));
    }

    @Test
    public void theIdAfterAChannelWordIsASlashAndDigits() {
        String text = "see #global/1234 and #ooc/ and #fellowship/x #gondor/12345678901234567890";
        int global = ChatChannelSuggester.wordEnd(text, text.indexOf("global"));
        assertEquals(text.indexOf(" and"), ChatChannelSuggester.messageIdEnd(text, global));
        int ooc = ChatChannelSuggester.wordEnd(text, text.indexOf("ooc"));
        assertEquals("a slash with no digits is no id", ooc,
                ChatChannelSuggester.messageIdEnd(text, ooc));
        int fellowship = ChatChannelSuggester.wordEnd(text, text.indexOf("fellowship/"));
        assertEquals(fellowship, ChatChannelSuggester.messageIdEnd(text, fellowship));
        int gondor = ChatChannelSuggester.wordEnd(text, text.indexOf("gondor"));
        // Eighteen digits at most: what a long can hold.
        assertEquals(gondor + 1 + 18,
                ChatChannelSuggester.messageIdEnd(text, gondor));
        assertEquals(5, ChatChannelSuggester.messageIdEnd("#ooc", 5));
    }

    @Test
    public void aWordNamesAChannelByItsCodeNameAlone() {
        assertSame(ChatChannel.GLOBAL, ChatChannelSuggester.linkAt("#global", 0).channel);
        assertSame(ChatChannel.GLOBAL, ChatChannelSuggester.linkAt("#Global", 0).channel);
        assertSame(ChatChannel.OOC, ChatChannelSuggester.linkAt("#OOC", 0).channel);
        assertSame(ChatChannel.CLIENT_CONSOLE,
                ChatChannelSuggester.linkAt("#client_console", 0).channel);
        assertNull("the shown name is not a code name",
                ChatChannelSuggester.linkAt("#OutofCharacter", 0));
        assertNull("the old ids are gone", ChatChannelSuggester.linkAt("#all", 0));
        assertNull(ChatChannelSuggester.linkAt("#admin", 0));
        assertNull(ChatChannelSuggester.linkAt("#console", 0));
        assertNull("the Faction channel is named by its faction",
                ChatChannelSuggester.linkAt("#faction", 0));
        assertNull(ChatChannelSuggester.linkAt("#nowhere", 0));
        ChatChannelSuggester.Link gondor = ChatChannelSuggester.linkAt("#Gondor", 0);
        assertSame(ChatChannel.FACTION, gondor.channel);
        assertEquals("lotr:gondor", gondor.scope);
        assertEquals("", ChatChannelSuggester.linkAt("#ooc", 0).scope);
        assertEquals("#ooc", ChatChannelSuggester.token(ChatChannel.OOC, "lotr:gondor"));
        assertEquals("#high_elf",
                ChatChannelSuggester.token(ChatChannel.FACTION, "lotr:high_elf"));
        assertNull(ChatChannelSuggester.token(ChatChannel.FACTION, ""));
        assertEquals(7, ChatChannelSuggester.wordEnd("#global, said", 1));
        assertEquals(1, ChatChannelSuggester.wordEnd("# global", 1));
    }
}
