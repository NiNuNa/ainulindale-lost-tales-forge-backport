package com.ninuna.losttales.chat;

import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The chat's own names come from the lang file, in the language of the
 * words asked: every built-in channel, a faction's chat and the three
 * voices. A channel a server defined reads as its config names it, and
 * the name a line is signed with stands for anyone but the voices.
 */
public final class ChatNamesTest {
    /** Words in another language: a key's line is the key with a German mark. */
    private static final LostTalesWords GERMAN = new LostTalesWords() {
        @Override
        public String format(String key, Object... arguments) {
            if (ChatNames.FACTION_CHAT_KEY.equals(key)) {
                return arguments[0] + "-Chat";
            }
            return "de:" + key;
        }
    };

    @Test
    public void everyBuiltInChannelIsNamedByItsLangLine() {
        for (ChatChannel channel : ChatChannel.values()) {
            if (ChatChannel.isBuiltIn(channel)) {
                assertEquals("chat.losttales.channel." + channel.getId(),
                        channel.getNameKey());
                assertEquals("de:" + channel.getNameKey(),
                        ChatNames.channel(GERMAN, channel));
                // In English the lang file says what the logs say.
                assertEquals(channel.getDisplayName(),
                        ChatNames.channel(EnglishWords.INSTANCE, channel));
            }
        }
        assertEquals("Whisper", ChatNames.channel(EnglishWords.INSTANCE, ChatChannel.WHISPER));
        assertEquals("", ChatNames.channel(GERMAN, null));
    }

    @Test
    public void aServersOwnChannelReadsAsItsConfigNamesIt() {
        ChatChannelDescriptor trade = new ChatChannelDescriptor("trade", "Trade",
                ChatPresentationMode.OUT_OF_CHARACTER, ChatRecipientRule.EVERYONE,
                ChatChannelAccess.NONE, 0xC9A227, false, ChatChannelScope.NONE);
        assertEquals("", trade.getNameKey());
        ChatChannel.installDefined(java.util.Collections.singletonList(trade), null);
        try {
            assertEquals("Trade", ChatNames.channel(GERMAN, ChatChannel.fromId("trade")));
        } finally {
            ChatChannel.resetToBuiltIn();
        }
    }

    /** A lang file without the line leaves a channel its English name. */
    @Test
    public void aChannelTheLangFileDoesNotNameKeepsItsEnglishName() {
        LostTalesWords none = new LostTalesWords() {
            @Override
            public String format(String key, Object... arguments) {
                return key;
            }
        };
        assertEquals("OOC Chat", ChatNames.channel(none, ChatChannel.OOC));
    }

    @Test
    public void aFactionsChatIsNamedByTheFactionInTheSameWords() {
        assertEquals("Gondor Chat", ChatNames.factionChat(EnglishWords.INSTANCE, "Gondor"));
        assertEquals("Gondor-Chat", ChatNames.factionChat(GERMAN, "Gondor"));
    }

    @Test
    public void theVoicesAreNamedByTheirWordsWhateverTheLineWasSignedWith() {
        assertEquals("Server", ChatNames.server(EnglishWords.INSTANCE));
        assertEquals("Client", ChatNames.client(EnglishWords.INSTANCE));
        assertEquals(ChatNarrator.NAME, ChatNames.narrator(EnglishWords.INSTANCE));
        assertEquals("de:" + ChatNames.SERVER_KEY, ChatNames.sender(GERMAN,
                LostTalesChatMessagePacket.SERVER_SENDER_ID, "", "Server"));
        assertEquals("de:" + ChatNames.CLIENT_KEY, ChatNames.sender(GERMAN,
                LostTalesChatMessagePacket.CLIENT_SENDER_ID, "", "Client"));
        // Everyone else's copy is signed by the Narrator's id; the
        // narrator's own copy by their own id and the Narrator's mark.
        assertEquals("de:" + ChatNames.NARRATOR_KEY, ChatNames.sender(GERMAN,
                ChatNarrator.SENDER_ID, "", ChatNarrator.NAME));
        assertEquals("de:" + ChatNames.NARRATOR_KEY, ChatNames.sender(GERMAN,
                UUID.randomUUID(), ChatNarrator.SKIN_ID, ChatNarrator.NAME));
        // A player whose account is called Server, or Narrator, is themselves.
        UUID player = UUID.randomUUID();
        assertEquals("Server", ChatNames.sender(GERMAN, player, "", "Server"));
        assertEquals("Narrator", ChatNames.sender(GERMAN, player, "", "Narrator"));
        assertEquals("Aldric", ChatNames.sender(GERMAN, null, null, "Aldric"));
        assertEquals("", ChatNames.sender(GERMAN, player, "", null));
    }
}
