package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatTranslatedWords;
import com.ninuna.losttales.util.EnglishWords;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The names the bridge writes come from the lang file: on Discord and in
 * the logs in the server's words, in a line a player is sent as words
 * their game translates. In English they read as the chat names them.
 */
public final class DiscordBridgeWordsTest {

    @Test
    public void everyBuiltInChannelIsNamedAsTheChatNamesIt() {
        int named = 0;
        for (ChatChannel channel : ChatChannel.values()) {
            if (ChatChannel.isBuiltIn(channel)) {
                assertEquals(channel.getId(), channel.getDisplayName(),
                        ChatNames.channel(EnglishWords.INSTANCE, channel));
                named++;
            }
        }
        assertTrue(named >= 9);
        assertEquals("OOC Chat",
                LostTalesDiscordBridge.gameChannelName(EnglishWords.INSTANCE, "ooc"));
        assertEquals("Global Chat",
                LostTalesDiscordBridge.gameChannelName(EnglishWords.INSTANCE, "global"));
        assertEquals("nowhere",
                LostTalesDiscordBridge.gameChannelName(EnglishWords.INSTANCE, "nowhere"));
    }

    @Test
    public void aFactionsChatIsNamedByTheFaction() {
        assertEquals("Gondor Chat", ChatNames.factionChat(EnglishWords.INSTANCE, "Gondor"));
    }

    /** In English the voices read as the names their lines are signed with. */
    @Test
    public void theNarratorIsNamedAsTheChatNamesIt() {
        assertEquals(ChatNarrator.NAME, ChatNames.narrator(EnglishWords.INSTANCE));
        assertEquals(com.ninuna.losttales.chat.server.LostTalesServerBroadcastHook.SERVER_NAME,
                ChatNames.server(EnglishWords.INSTANCE));
    }

    @Test
    public void aDiscordChannelIsDescribedFromWhatIsKnownOfIt() {
        assertEquals("#general of The Shire", LostTalesDiscordBridge.describeDiscordChannel(
                EnglishWords.INSTANCE, "general", "The Shire", "1"));
        assertEquals("#general", LostTalesDiscordBridge.describeDiscordChannel(
                EnglishWords.INSTANCE, "general", "", "1"));
        assertEquals("channel 123", LostTalesDiscordBridge.describeDiscordChannel(
                EnglishWords.INSTANCE, "", "", "123"));
        assertEquals("channel 123 of The Shire", LostTalesDiscordBridge.describeDiscordChannel(
                EnglishWords.INSTANCE, "", "The Shire", "123"));
    }

    /** In a player's line the words are translations, the names plain. */
    @Test
    public void aPlayersLineNamesThemAsWordsToTranslate() {
        IChatComponent known = LostTalesDiscordBridge.discordChannelComponent(
                "general", "The Shire", "1");
        ChatComponentTranslation of = (ChatComponentTranslation)known;
        assertEquals(LostTalesDiscordBridge.CHANNEL_OF_KEY, of.getKey());
        assertEquals("#general", ((IChatComponent)of.getFormatArgs()[0])
                .getUnformattedTextForChat());
        assertEquals("The Shire", of.getFormatArgs()[1]);
        IChatComponent unknown = LostTalesDiscordBridge.discordChannelComponent("", "", "123");
        assertEquals(LostTalesDiscordBridge.CHANNEL_ID_KEY,
                ((ChatComponentTranslation)unknown).getKey());

        IChatComponent ooc = LostTalesDiscordBridge.gameChannelComponent("ooc");
        assertEquals("chat.losttales.channel.ooc", ((ChatComponentTranslation)ooc).getKey());
        assertTrue(LostTalesDiscordBridge.gameChannelComponent("nowhere")
                instanceof ChatComponentText);
        // A channel's name is no line of words of its own.
        assertFalse(ChatTranslatedWords.isWords(ChatChannel.OOC.getNameKey()));
    }
}
