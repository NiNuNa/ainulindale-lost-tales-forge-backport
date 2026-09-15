package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelIconSpec;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Every tab wears an emoji; conversations with players and NPCs differ.
 * A server may choose a channel's emoji itself, and its choice goes with
 * the server.
 */
public final class ChatChannelIconsTest {

    @After
    public void tearDown() {
        ChatChannelIcons.forgetChannelIcons();
    }

    @Test
    public void aChosenEmojiReplacesTheCodesOwn() {
        assertEquals(ChatEmoji.EXPRESSIONLESS,
                ChatChannelIcons.iconOf(ChatChannel.ADMIN));
        Map<String, ChatChannelIconSpec> chosen =
                new HashMap<String, ChatChannelIconSpec>();
        chosen.put("ADMIN", ChatChannelIconSpec.parse("emoji:joy"));
        ChatChannelIcons.install(chosen);

        assertEquals(ChatEmoji.JOY, ChatChannelIcons.iconOf(ChatChannel.ADMIN));
        assertEquals(ChatEmoji.JOY,
                ChatChannelIcons.iconOf(ChatTab.of(ChatChannel.ADMIN)));
        // Every other channel keeps its own.
        assertEquals(ChatEmoji.JOY, ChatChannelIcons.iconOf(ChatChannel.PARTY));
        assertEquals(ChatEmoji.SLIGHT_SMILE,
                ChatChannelIcons.iconOf(ChatChannel.ALL));

        ChatChannelIcons.forgetChannelIcons();
        assertEquals(ChatEmoji.EXPRESSIONLESS,
                ChatChannelIcons.iconOf(ChatChannel.ADMIN));
    }

    /** A choice this client cannot draw leaves the code's own emoji standing. */
    @Test
    public void aChoiceThisClientCannotDrawKeepsTheCodesOwn() {
        Map<String, ChatChannelIconSpec> chosen =
                new HashMap<String, ChatChannelIconSpec>();
        chosen.put(ChatChannel.ADMIN.getId(),
                ChatChannelIconSpec.parse("emoji:no_such_face"));
        ChatChannelIcons.install(chosen);
        assertEquals(ChatEmoji.EXPRESSIONLESS,
                ChatChannelIcons.iconOf(ChatChannel.ADMIN));

        ChatChannelIcons.install(null);
        assertEquals(ChatEmoji.EXPRESSIONLESS,
                ChatChannelIcons.iconOf(ChatChannel.ADMIN));
        ChatChannelIcons.install(Collections.<String, ChatChannelIconSpec>emptyMap());
        assertNull(ChatChannelIcons.itemIconOf(ChatChannel.ADMIN));
        assertNull(ChatChannelIcons.itemIconOf(null));
    }

    @Test
    public void everyChannelAndConversationHasAnIcon() {
        for (ChatChannel channel : ChatChannel.values()) {
            assertNotNull(channel + " has no icon",
                    ChatChannelIcons.iconOf(channel));
            if (channel == ChatChannel.WHISPER) {
                // A whisper has no plain tab: each names a partner.
                assertNull(ChatTab.of(channel));
                continue;
            }
            assertEquals(ChatChannelIcons.iconOf(channel),
                    ChatChannelIcons.iconOf(ChatTab.of(channel)));
        }
        assertEquals(ChatEmoji.BLUSH,
                ChatChannelIcons.iconOf(ChatTab.whisper("Bilbo")));
        assertEquals(ChatEmoji.GRINNING,
                ChatChannelIcons.iconOf(ChatTab.npc("Bilbo")));
        assertNull(ChatChannelIcons.iconOf((ChatTab)null));
        assertNull(ChatChannelIcons.iconOf((ChatChannel)null));
    }
}
