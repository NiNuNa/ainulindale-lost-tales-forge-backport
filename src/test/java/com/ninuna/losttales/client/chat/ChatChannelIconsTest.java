package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Every tab wears an emote; conversations with players and NPCs differ. */
public final class ChatChannelIconsTest {

    @Test
    public void everyChannelAndConversationHasAnIcon() {
        for (ChatChannel channel : ChatChannel.values()) {
            assertNotNull(channel + " has no icon",
                    ChatChannelIcons.iconOf(channel));
            assertEquals(ChatChannelIcons.iconOf(channel),
                    ChatChannelIcons.iconOf(ChatTab.of(channel)));
        }
        assertEquals(ChatEmoji.SHY,
                ChatChannelIcons.iconOf(ChatTab.whisper("Bilbo")));
        assertEquals(ChatEmoji.GRIN, ChatChannelIcons.iconOf(ChatTab.npc("Bilbo")));
        assertNull(ChatChannelIcons.iconOf((ChatTab)null));
        assertNull(ChatChannelIcons.iconOf((ChatChannel)null));
    }
}
