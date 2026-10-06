package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.network.packet.ChatPacketFixtures;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A game shows the chat's own names in its player's language: the
 * channels on their tabs and lists, a faction's chat, the Server, the
 * Client and the Narrator on their lines and in the member lists. What a
 * line was signed with and what a search finds by code name stay as they
 * are.
 */
public final class ChatNamesInThisGameTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String UNALIGNED_KEY = "lotr.faction.UNALIGNED.name";
    private static final List<String> KEYS = Arrays.asList(
            ChatChannel.GLOBAL.getNameKey(), ChatChannel.WHISPER.getNameKey(),
            ChatChannel.FACTION.getNameKey(), ChatNames.FACTION_CHAT_KEY,
            UNALIGNED_KEY, ChatNames.SERVER_KEY, ChatNames.CLIENT_KEY,
            ChatNames.NARRATOR_KEY);

    @Before
    public void readInAnotherLanguage() {
        inject(ChatChannel.GLOBAL.getNameKey() + "=Allgemeiner Chat\n"
                + ChatChannel.WHISPER.getNameKey() + "=Flüstern\n"
                + ChatChannel.FACTION.getNameKey() + "=Fraktionschat\n"
                + ChatNames.FACTION_CHAT_KEY + "=%s-Chat\n"
                + UNALIGNED_KEY + "=Ungebunden\n"
                + ChatNames.SERVER_KEY + "=Serveur\n"
                + ChatNames.CLIENT_KEY + "=Klient\n"
                + ChatNames.NARRATOR_KEY + "=Erzähler\n");
    }

    /** The English lines of the keys read here, as the lang file has them. */
    @After
    public void readInEnglishAgain() throws IOException {
        StringBuilder english = new StringBuilder();
        InputStream in = ChatNamesInThisGameTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0 && KEYS.contains(line.substring(0, equals))) {
                    english.append(line).append('\n');
                }
            }
        } finally {
            in.close();
        }
        inject(english.toString());
        ClientChatChannelState.clear();
    }

    @Test
    public void channelsAreNamedInThisGamesLanguage() {
        assertEquals("Allgemeiner Chat",
                ClientChatChannelState.displayName(ChatChannel.GLOBAL));
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        assertEquals("Allgemeiner Chat", global.title());
        // A faction's chat names the faction in this game's language too;
        // with no character played that is Unaligned.
        assertEquals("Ungebunden-Chat",
                ClientChatChannelState.displayName(ChatChannel.FACTION));
        assertEquals("#Allgemeiner Chat",
                ChatChannelSuggestionBox.label(ChatChannel.GLOBAL));
        // The code name is the channel's own: it never changes.
        assertEquals("global", ChatChannel.GLOBAL.getId());
    }

    /** A search finds a conversation by its name here and by its code name, which reads the same everywhere. */
    @Test
    public void aSearchFindsAChannelByEitherName() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        assertTrue(global.answers("allgem"));
        assertTrue(global.answers("GLOB"));
        assertTrue(global.answers(""));
        assertFalse(global.answers("ooc"));
        ConversationPage whisper = ConversationPage.whisper("Steve", "Aldric", "");
        assertFalse("a whisper has no code name", whisper.answers("whisper"));
    }

    @Test
    public void theVoicesAreNamedInThisGamesLanguage() {
        LostTalesChatMessagePacket server = ChatPacketFixtures.line(
                ChatChannel.OOC, "Server", "Server", "Server started")
                .sender(LostTalesChatMessagePacket.SERVER_SENDER_ID).build();
        assertEquals("Server", server.getIdentityName());
        assertEquals("Serveur", LostTalesChatPresentation.shownName(server));
        LostTalesChatMessagePacket told = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, ChatNarrator.NAME, "Steve", "the gate falls")
                .skin(ChatNarrator.SKIN_ID).build();
        assertEquals("Erzähler", LostTalesChatPresentation.shownName(told));
        // A player whose account is called Server is shown by their account.
        LostTalesChatMessagePacket player = ChatPacketFixtures.line(
                ChatChannel.OOC, "Server", "Server", "hello").build();
        assertEquals("Server", LostTalesChatPresentation.shownName(player));
        assertEquals("Klient", ChatMemberList.clientMember().getName());
        assertEquals("Klient", ChatMemberList.nameOf(ChatMemberList.clientMember()));
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
