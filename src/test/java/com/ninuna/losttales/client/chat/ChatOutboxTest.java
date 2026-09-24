package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When the field's text counts as typing worth telling the server. */
public final class ChatOutboxTest {
    private boolean originalSend;

    @Before
    public void setUp() {
        this.originalSend = LostTalesConfig.sendChatTypingStatus;
        LostTalesConfig.sendChatTypingStatus = true;
        ClientChatChannelState.clear();
        ClientChatChannelState.select(ChatChannel.OOC);
    }

    @After
    public void tearDown() {
        LostTalesConfig.sendChatTypingStatus = this.originalSend;
        ClientChatChannelState.clear();
    }

    @Test
    public void aMessageBeingWrittenIsTyping() {
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        assertTrue(ChatOutbox.isTyping("hello", ooc, 0L));
        assertTrue(ChatOutbox.isTyping("hello", ooc,
                ChatOutbox.TYPING_IDLE_NANOS - 1L));
    }

    @Test
    public void idleTextCommandsAndSilentTabsAreNot() {
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        assertFalse(ChatOutbox.isTyping("hello", ooc,
                ChatOutbox.TYPING_IDLE_NANOS));
        assertFalse(ChatOutbox.isTyping("   ", ooc, 0L));
        assertFalse(ChatOutbox.isTyping("", ooc, 0L));
        assertFalse(ChatOutbox.isTyping("/losttales hud", ooc, 0L));
        assertFalse(ChatOutbox.isTyping("/msg Bilbo hi", ooc, 0L));
        // Nobody is on the other end of an NPC conversation.
        assertFalse(ChatOutbox.isTyping("hello", ChatTab.npc("Bilbo"), 0L));
        // A tab the player may not send into says nothing either.
        assertFalse(ChatOutbox.isTyping("hello", ChatTab.of(ChatChannel.OPERATOR),
                0L));
        LostTalesConfig.sendChatTypingStatus = false;
        assertFalse(ChatOutbox.isTyping("hello", ooc, 0L));
    }
}
