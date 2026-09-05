package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatComposerTest {

    @Before
    public void selectGlobal() {
        ClientChatChannelState.clear();
        ClientChatChannelState.select(ChatChannel.ALL);
    }

    @After
    public void reset() {
        ClientChatChannelState.clear();
    }

    @Test
    public void aReplyIsComposedInItsOwnTabOnly() {
        ChatComposer composer = new ChatComposer();
        assertFalse(composer.isReplying());
        assertFalse(composer.replyReference().exists());
        composer.startReply(ChatTab.of(ChatChannel.ALL), 42L, "Beren",
                "the road is clear");
        assertTrue(composer.isReplying());
        ChatReplyReference reference = composer.replyReference();
        assertEquals(42L, reference.getMessageId());
        assertEquals("Beren", reference.getAuthor());
        // Selecting another tab is moving away from the message.
        ClientChatChannelState.select(ChatChannel.OOC);
        assertFalse(composer.isReplying());
        assertFalse(composer.replyReference().exists());
        composer.onTabSelected(ChatTab.of(ChatChannel.OOC));
        ClientChatChannelState.select(ChatChannel.ALL);
        assertFalse(composer.isReplying());
    }

    @Test
    public void aLocalIdNeverCountsAsAReply() {
        ChatComposer composer = new ChatComposer();
        composer.startReply(ChatTab.of(ChatChannel.ALL), -7L, "Bilbo", "x");
        assertFalse(composer.isReplying());
        assertEquals(ChatReplyReference.NONE, composer.replyReference());
    }

    @Test
    public void anEditPutsAReplyDownAndIsForgottenWithItsMessage() {
        ChatComposer composer = new ChatComposer();
        composer.startReply(ChatTab.of(ChatChannel.ALL), 42L, "Beren", "x");
        composer.startEdit(ChatTab.of(ChatChannel.ALL), 43L);
        assertTrue(composer.isEditing());
        assertFalse(composer.isReplying());
        assertEquals(43L, composer.editingMessageId());
        composer.forgetEditOf(99L);
        assertTrue(composer.isEditing());
        composer.forgetEditOf(43L);
        assertFalse(composer.isEditing());
        assertEquals(ChatMessageIds.NONE, composer.editingMessageId());
    }

    @Test
    public void composingBelongsToTheTabItStartedIn() {
        ChatComposer composer = new ChatComposer();
        composer.startEdit(ChatTab.of(ChatChannel.ALL), 43L);
        composer.onTabSelected(ChatTab.of(ChatChannel.ALL));
        assertTrue(composer.isEditing());
        composer.onTabSelected(ChatTab.of(ChatChannel.OOC));
        assertFalse(composer.isEditing());
        assertEquals(ChatMessageIds.NONE, composer.editingMessageId());
    }
}
