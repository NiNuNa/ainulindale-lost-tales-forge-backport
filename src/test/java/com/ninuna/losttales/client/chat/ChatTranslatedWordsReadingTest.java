package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatUpdatePacket;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * How a game reads a line sent as words to translate: a Discord member's
 * marks in this game's language, a Server line's words the same way, and
 * the server's own words for anything that is not translated words alone.
 * A Discord member's line never keeps its component once read.
 */
public final class ChatTranslatedWordsReadingTest {
    private static final String STICKER_KEY = "chat.losttales.words.discord_sticker";
    private static final String STICKER_BODY = "{\"text\":\"\",\"extra\":[{\"text\":\"look \"},"
            + "{\"translate\":\"" + STICKER_KEY + "\",\"with\":[\"Wave\"]}]}";
    private static final String SERVER_WORDS = "look *[Sticker: Wave]*";
    private static final UUID SAM =
            LostTalesChatMessagePacket.discordSenderId("123456789012345678");
    private static final List<ChatNamedPlayer> BEREN = Collections.singletonList(
            new ChatNamedPlayer(UUID.randomUUID(), "bob", null, "Beren", ""));

    @Before
    public void readInAnotherLanguage() {
        inject(STICKER_KEY + "=*[Aufkleber: %s]*\n"
                + "chat.losttales.words.mark_removed=%s removed the mark %s.\n");
    }

    @After
    public void readInEnglishAgain() {
        inject(STICKER_KEY + "=*[Sticker: %s]*\n");
    }

    @Test
    public void aDiscordMembersMarksReadInThisGamesLanguage() {
        LostTalesChatMessagePacket read = LostTalesChatPresentation.asTranslatedWords(
                discordLine(STICKER_BODY));
        assertEquals("look *[Aufkleber: Wave]*", read.getMessage());
        assertEquals("only plain words go on", "", read.getBodyJson());
        assertEquals(SAM, read.getSenderId());
        assertEquals("Beren", read.getNamedPlayers().get(0).getIdentityName());
    }

    @Test
    public void anythingButTranslatedWordsLeavesTheServersWords() {
        String[] notWords = {
            // A translation that is no words key.
            "{\"translate\":\"chat.type.text\",\"with\":[\"a\",\"b\"]}",
            // A style, a link or a hover anywhere.
            "{\"text\":\"\",\"extra\":[{\"text\":\"look \",\"color\":\"red\"},"
                    + "{\"translate\":\"" + STICKER_KEY + "\",\"with\":[\"Wave\"]}]}",
            "{\"text\":\"\",\"extra\":[{\"translate\":\"" + STICKER_KEY + "\","
                    + "\"with\":[\"Wave\"],\"clickEvent\":{\"action\":\"open_url\","
                    + "\"value\":\"http://example.com\"}}]}",
            "{\"text\":\"\",\"extra\":[{\"translate\":\"" + STICKER_KEY + "\","
                    + "\"with\":[{\"text\":\"Wave\",\"hoverEvent\":{\"action\":"
                    + "\"show_text\",\"value\":\"x\"}}]}]}",
            // Plain text with no translation in it.
            "{\"text\":\"look\"}",
            // Not a component at all.
            "{not json",
        };
        for (String body : notWords) {
            LostTalesChatMessagePacket read = LostTalesChatPresentation.asTranslatedWords(
                    discordLine(body));
            assertEquals(body, SERVER_WORDS, read.getMessage());
            assertEquals(body, "", read.getBodyJson());
            assertEquals(body, 1, read.getNamedPlayers().size());
        }
    }

    @Test
    public void wordsThatWouldNotMakeALineLeaveTheServersWords() {
        StringBuilder endless = new StringBuilder();
        for (int index = 0; index < 1100; index++) {
            endless.append('x');
        }
        inject(STICKER_KEY + "=" + endless + "%s\n");
        LostTalesChatMessagePacket read = LostTalesChatPresentation.asTranslatedWords(
                discordLine(STICKER_BODY));
        assertEquals(SERVER_WORDS, read.getMessage());
        assertEquals("", read.getBodyJson());
    }

    /** A Server line's component is read as words only when it is translated words. */
    @Test
    public void aServerLineKeepsAComponentThatIsNotWords() {
        LostTalesChatMessagePacket plain = serverLine("Steve fell", "{\"text\":\"Steve fell\"}");
        assertEquals("{\"text\":\"Steve fell\"}",
                LostTalesChatPresentation.asTranslatedWords(plain).getBodyJson());
        LostTalesChatMessagePacket mark = serverLine("Aldric removed the mark Weathertop.",
                "{\"translate\":\"chat.losttales.words.mark_removed\","
                        + "\"with\":[\"Aldric\",\"Weathertop\"]}");
        LostTalesChatMessagePacket read = LostTalesChatPresentation.asTranslatedWords(mark);
        assertEquals("Aldric removed the mark Weathertop.", read.getMessage());
        assertEquals("", read.getBodyJson());
    }

    /** A line nobody may give a component is left as it came. */
    @Test
    public void aLineWithoutAComponentIsLeftAsItIs() {
        LostTalesChatMessagePacket line = discordLine("");
        assertEquals(line, LostTalesChatPresentation.asTranslatedWords(line));
    }

    /** An edit is read as the line was: this game's words, or the server's. */
    @Test
    public void anEditIsReadAsTheLineWas() {
        LostTalesChatMessagePacket held = LostTalesChatPresentation.asTranslatedWords(
                discordLine(""));
        LostTalesChatMessagePacket edited = LostTalesChatPresentation.edited(held,
                LostTalesChatUpdatePacket.edited(held.getMessageId(), SERVER_WORDS,
                        STICKER_BODY, BEREN));
        assertEquals("look *[Aufkleber: Wave]*", edited.getMessage());
        assertEquals("", edited.getBodyJson());
        LostTalesChatMessagePacket plain = LostTalesChatPresentation.edited(edited,
                LostTalesChatUpdatePacket.edited(held.getMessageId(), "just words", "",
                        BEREN));
        assertEquals("just words", plain.getMessage());
        assertEquals("", plain.getBodyJson());
        assertEquals("Beren", plain.getNamedPlayers().get(0).getIdentityName());
    }

    private static LostTalesChatMessagePacket discordLine(String body) {
        return new LostTalesChatMessagePacket(ChatChannel.GLOBAL, SAM, "Sam", "Sam",
                "The Shire", 0xFFFFFF, 0xFFFFFF, SERVER_WORDS, 1L, "", null, "", "", 0,
                true, ChatMessageIdAllocator.next(), ChatReplyReference.NONE)
                .withServerBody(body, BEREN);
    }

    private static LostTalesChatMessagePacket serverLine(String words, String body) {
        return new LostTalesChatMessagePacket(ChatChannel.GLOBAL,
                LostTalesChatMessagePacket.SERVER_SENDER_ID, "Server", "Server", "",
                0xFFFFFF, 0xFFFFFF, words, 1L, "", null, "", "", 0, true,
                ChatMessageIdAllocator.next(), ChatReplyReference.NONE)
                .withServerBody(body, null);
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(
                lines.getBytes(Charset.forName("UTF-8"))));
    }
}
