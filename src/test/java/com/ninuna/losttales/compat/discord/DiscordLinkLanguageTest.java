package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.faction.FactionDemonyms;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Each link writes in its own language, else the server's default
 * (DISC-30): the entry carries it, the bridge words a post's name, its
 * marks and its quote in it, and LOTR's words stay as they come.
 */
public final class DiscordLinkLanguageTest {

    private static final String HOOK_A = "https://discord.com/api/webhooks/1/abc";
    private static final String HOOK_B = "https://discord.com/api/webhooks/2/def";

    /** German for the words these tests need, a key it lacks reading as itself. */
    private static final LostTalesWords GERMAN = new LostTalesWords() {
        @Override
        public String format(String key, Object... arguments) {
            if ("chat.losttales.title.suffix".equals(key)) {
                return ", der " + arguments[0];
            }
            if ("chat.losttales.title.epithet".equals(key)) {
                return arguments[0] + "-" + arguments[1];
            }
            if ("chat.losttales.title.discord".equals(key)) {
                return ", aus " + arguments[0];
            }
            if ("chat.losttales.narrator".equals(key)) {
                return "Erzähler";
            }
            if ("losttales.faction.demonym.LOTHLORIEN".equals(key)) {
                return "Galadhrim (de)";
            }
            if (ChatReplyReference.UNKEPT_KEY.equals(key)) {
                return "Nicht mehr da";
            }
            if (DiscordMessageSanitizer.STICKER.equals(key)) {
                return "*[Sticker: " + arguments[0] + "] (de)*";
            }
            return key;
        }
    };

    private static final class Collected implements DiscordChannelLinks.Warnings {
        final List<String> lines = new ArrayList<String>();

        @Override
        public void warn(String message) {
            this.lines.add(message);
        }

        @Override
        public void refuse(String message) {
            this.lines.add(message);
        }
    }

    @After
    public void restoreTheDefault() {
        LostTalesConfig.discordLanguage = "en_US";
    }

    /** An entry may name the language its link writes in; without one it follows the default. */
    @Test
    public void anEntryNamesItsLanguageOrFollowsTheDefault() {
        Collected log = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A + ";language=en_us",
                "global=GAME_TO_DISCORD;channel=6;webhook=" + HOOK_B}, true, log);
        assertTrue(log.lines.toString(), log.lines.isEmpty());
        assertEquals("spelled the game's way", "en_US", channelLinks.byId("ooc").getLanguage());
        assertEquals("", channelLinks.byId("global").getLanguage());
        assertEquals("de_DE", channelLinks.byId("global").languageOr("de_DE"));
    }

    /** A language the mod ships no file for leaves the link in the default, with a warning. */
    @Test
    public void aLanguageTheModLacksIsTheDefault() {
        Collected log = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A + ";language=xx_XX",
                "global=GAME_TO_DISCORD;channel=6;webhook=" + HOOK_B + ";language=../x"},
                true, log);
        assertEquals("", channelLinks.byId("ooc").getLanguage());
        assertEquals("", channelLinks.byId("global").getLanguage());
        assertEquals(2, log.lines.size());
        assertTrue(log.lines.get(0), log.lines.get(0).contains("server's default"));
        assertTrue("the value is never repeated", !log.lines.get(1).contains("../x"));
    }

    /** A fourth part of any other kind, or a fifth, is not the form the link commands write. */
    @Test
    public void anyOtherFourthPartIsRefused() {
        Collected log = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A + ";colour=red",
                "ooc=BIDIRECTIONAL;channel=6;webhook=" + HOOK_B
                        + ";language=en_US;language=en_US"}, true, log);
        assertTrue(channelLinks.all().isEmpty());
        assertEquals(2, log.lines.size());
        assertTrue(log.lines.get(0), log.lines.get(0).contains("[;language=<code>]"));
    }

    /** A topic and an answer find their link by its Discord channel, a post by its webhook. */
    @Test
    public void aLinkIsFoundByItsChannelAndItsWebhook() {
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A + ";language=en_US",
                "global=GAME_TO_DISCORD;channel=6;webhook=" + HOOK_B}, true, null);
        assertSame(channelLinks.byId("ooc"), channelLinks.linkOfChannel("5"));
        assertSame(channelLinks.byId("global"), channelLinks.linkOfChannel("6"));
        assertNull(channelLinks.linkOfChannel("7"));
        assertNull(channelLinks.linkOfChannel(""));
        assertSame(channelLinks.byId("ooc"), channelLinks.posterOf(HOOK_A));
        assertSame(channelLinks.byId("global"), channelLinks.posterOf(HOOK_B));
        assertNull(channelLinks.posterOf("https://discord.com/api/webhooks/3/x"));
        assertNull(channelLinks.posterOf(null));
    }

    /** A link's words: its own language, else the server's default. */
    @Test
    public void aLinkReadsItsOwnLanguageElseTheDefault() {
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "global=GAME_TO_DISCORD;channel=6;webhook=" + HOOK_B}, true, null);
        assertSame(LostTalesLangFile.ENGLISH_WORDS,
                LostTalesDiscordBridge.wordsOf(channelLinks.byId("global")));
        assertSame(LostTalesLangFile.ENGLISH_WORDS, LostTalesDiscordBridge.wordsOf(null));
        LostTalesConfig.discordLanguage = "xx_XX";
        assertSame("a language the mod lacks reads in English",
                LostTalesLangFile.ENGLISH_WORDS, LostTalesDiscordBridge.defaultWords());
    }

    /** A post's name: the mod's words in the link's language, LOTR's as they come. */
    @Test
    public void aPostsNameIsWordedInEachLinksLanguage() {
        DiscordPostName titled = DiscordPostName.titled("Aldric", "lotr:lothlorien",
                "Lothlórien", "Farmer");
        assertEquals("Aldric, der Galadhrim (de)-Farmer", titled.in(GERMAN));
        assertEquals("a faction without a people's name keeps its own",
                "Aldric, the Gondor Farmer", DiscordPostName.titled("Aldric",
                        "lotr:gondor", "Gondor", "Farmer").in(LostTalesLangFile.ENGLISH_WORDS));
        assertEquals("Aldric", DiscordPostName.titled("Aldric", "", "", "").in(GERMAN));
        assertEquals("Steve", DiscordPostName.plain("Steve").in(GERMAN));
        assertEquals("Erzähler", DiscordPostName.NARRATOR.in(GERMAN));
        assertEquals(ChatNarrator.NAME, DiscordPostName.NARRATOR.in(EnglishWords.INSTANCE));
        assertEquals("Mira, aus The Shire",
                DiscordPostName.discordMember("Mira", "The Shire").in(GERMAN));
        assertEquals("Mira", DiscordPostName.discordMember("Mira", "").in(GERMAN));
        assertEquals("Mira, of The Shire", ChatEpithet.discordName(
                EnglishWords.INSTANCE, "Mira", "The Shire"));
        assertEquals("Galadhrim (de)", FactionDemonyms.of(GERMAN, "lotr:lothlorien",
                "Lothlórien"));
        assertEquals("Gondor", FactionDemonyms.of(GERMAN, "lotr:gondor", "Gondor"));
    }

    /** A quote of a line no longer kept reads in the link's words, in an author's place. */
    @Test
    public void anUnkeptQuoteIsWordedPerLink() {
        assertEquals("Nicht mehr da",
                LostTalesDiscordBridge.quoteAuthor(ChatReplyReference.UNKEPT, GERMAN));
        assertEquals(EnglishWords.INSTANCE.format(ChatReplyReference.UNKEPT_KEY),
                LostTalesDiscordBridge.quoteAuthor(ChatReplyReference.UNKEPT,
                        EnglishWords.INSTANCE));
        assertEquals("Aldric", LostTalesDiscordBridge.quoteAuthor(
                ChatReplyReference.of(1234L, "Aldric", "Well met."), GERMAN));
        assertEquals("", LostTalesDiscordBridge.quoteAuthor(null, GERMAN));
    }

    /**
     * A member's line keeps its marks apart: the history's text has them in
     * English, each linked channel it goes on to in its own language, and
     * the body read back gives the same pieces.
     */
    @Test
    public void aMembersMarksAreWordedPerLink() {
        DiscordInboundLine line = DiscordMessageSanitizer.inboundWithAttachments(
                LostTalesLangFile.ENGLISH_WORDS, "hello",
                "", Arrays.asList("Wave"), new ArrayList<String>(), null);
        assertEquals("hello *[Sticker: Wave]*", line.getText());
        assertEquals("hello *[Sticker: Wave] (de)*", line.textIn(GERMAN));
        DiscordInboundLine back = DiscordInboundBody.lineOf(DiscordInboundBody.jsonOf(line));
        assertTrue(back != null && back.hasMarks());
        assertEquals(line.getText(), back.getText());
        assertEquals(line.textIn(GERMAN), back.textIn(GERMAN));
        assertNull(DiscordInboundBody.lineOf(""));
        assertNull(DiscordInboundBody.lineOf("not json at all {"));
        assertNull(DiscordInboundBody.lineOf("{\"text\":\"words of a root\"}"));
    }
}
