package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatComposerTest {

    @Before
    public void selectGlobal() {
        ClientChatChannelState.clear();
        ClientChatChannelState.select(ChatChannel.GLOBAL);
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
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), 42L, "Beren",
                "the road is clear", null);
        assertTrue(composer.isReplying());
        ChatReplyReference reference = composer.replyReference();
        assertEquals(42L, reference.getMessageId());
        assertEquals("Beren", reference.getAuthor());
        // Selecting another tab is moving away from the message.
        ClientChatChannelState.select(ChatChannel.OOC);
        assertFalse(composer.isReplying());
        assertFalse(composer.replyReference().exists());
        composer.onTabSelected(ChatTab.of(ChatChannel.OOC));
        ClientChatChannelState.select(ChatChannel.GLOBAL);
        assertFalse(composer.isReplying());
    }

    /**
     * A line this client named — an NPC's speech, a command's answer, a
     * system line — is anchored by its local id here, so a click on the
     * quote finds it; the outbox sends its words alone. Without an
     * author it is no reply, whatever the id.
     */
    @Test
    public void aLocalIdAnchorsAReplyForThisClientAlone() {
        ChatComposer composer = new ChatComposer();
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), -7L, "Bilbo", "x",
                null);
        assertTrue(composer.isReplying());
        ChatReplyReference reference = composer.replyReference();
        assertTrue(reference.isAnchored());
        assertEquals(-7L, reference.getMessageId());
        assertEquals("Bilbo", reference.getAuthor());
        assertEquals("x", reference.getExcerpt());
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), -8L, "", "x", null);
        assertFalse(composer.isReplying());
        assertEquals(ChatReplyReference.NONE, composer.replyReference());
    }

    /**
     * A line nobody named is answered by its words: no id, but an
     * author and an excerpt, which is a reply all the same. Without an
     * author there is nothing to quote and it is no reply.
     */
    @Test
    public void anUnnamedLineIsAnsweredByItsWords() {
        ChatComposer composer = new ChatComposer();
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), ChatMessageIds.NONE,
                "System", "Bilbo has just earned the achievement [Taking Inventory]",
                null);
        assertTrue(composer.isReplying());
        ChatReplyReference reference = composer.replyReference();
        assertTrue(reference.exists());
        assertFalse(reference.isAnchored());
        assertEquals(ChatMessageIds.NONE, reference.getMessageId());
        assertEquals("System", reference.getAuthor());
        assertEquals("Bilbo has just earned the achievement [Taking Inventory]",
                reference.getExcerpt());
        composer.cancelReply();
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), ChatMessageIds.NONE,
                "", "words with nobody behind them", null);
        assertFalse(composer.isReplying());
        assertEquals(ChatReplyReference.NONE, composer.replyReference());
    }

    /**
     * A quote wears the head of the line it answers, whatever kind of
     * line that is: a player's face and the colour their name was drawn
     * in, an NPC's portrait, or the mark standing for the Server — read
     * off the line's own head marker, never off a quote's.
     */
    @Test
    public void aQuoteWearsTheHeadOfTheLineItAnswers() {
        UUID self = UUID.randomUUID();
        ChatComposer composer = new ChatComposer();
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), -7L, "Aldric",
                "/warp home", head(ChatHeadMarker.encode(self, false,
                        UUID.randomUUID(), "skin", "Aldric", 0xFCECD1,
                        0x64B082)));
        ChatReplyReference reference = composer.replyReference();
        assertEquals(self, reference.getSenderId());
        assertFalse(reference.isAccountLine());
        assertFalse(reference.isNpcLine());
        assertEquals("skin", reference.getSkinId());
        assertEquals(0x64B082, reference.getAuthorColor());

        UUID npc = UUID.randomUUID();
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), -9L, "Barliman",
                "Welcome", head(ChatHeadMarker.encodeNpc(npc,
                        "lotr:textures/entity/bree.png", "Barliman", 0xFCECD1,
                        0xC3A79C)));
        reference = composer.replyReference();
        assertTrue(reference.isNpcLine());
        assertEquals(npc, reference.getSenderId());
        assertEquals("lotr:textures/entity/bree.png", reference.getSkinId());

        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), ChatMessageIds.NONE,
                "Server", "Unknown command", head(ChatHeadMarker.encode(
                        LostTalesChatMessagePacket.SERVER_SENDER_ID, true,
                        null, "", "Server", 0xFCECD1, 0x9C807E)));
        reference = composer.replyReference();
        assertEquals(LostTalesChatMessagePacket.SERVER_SENDER_ID,
                reference.getSenderId());
        assertTrue(reference.isAccountLine());
        composer.cancelReply();
        assertFalse(composer.replyReference().hasHead());
    }

    @Test
    public void aLinesHeadIsItsOwnNotItsQuotes() {
        UUID quoted = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        ChatComponentText line = new ChatComponentText("");
        line.appendSibling(ChatReplyMarker.applyHead(
                new ChatComponentText("  "), 0xFFFFFF, 10L, quoted, true,
                false, ""));
        line.appendSibling(slot(ChatHeadMarker.encode(sender, true, null,
                "", "Steve", 0xFCECD1, 0xFCECD1)));
        ChatHeadMarker.Data head = ChatHeadMarker.of(line);
        assertEquals(sender, head.senderId);
        assertNull(ChatHeadMarker.of(new ChatComponentText("no head")));
        assertNull(ChatHeadMarker.of(null));
    }

    /** A head slot carrying the marker value, as a line's own head is built. */
    private static ChatComponentText slot(String value) {
        ChatComponentText slot = new ChatComponentText("  ");
        slot.setChatStyle(new ChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, value)));
        return slot;
    }

    /** The head a slot carrying the marker value is read as. */
    private static ChatHeadMarker.Data head(String value) {
        return ChatHeadMarker.decode(slot(value));
    }

    @Test
    public void anEditPutsAReplyDownAndIsForgottenWithItsMessage() {
        ChatComposer composer = new ChatComposer();
        composer.startReply(ChatTab.of(ChatChannel.GLOBAL), 42L, "Beren", "x",
                null);
        composer.startEdit(ChatTab.of(ChatChannel.GLOBAL), 43L);
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
        composer.startEdit(ChatTab.of(ChatChannel.GLOBAL), 43L);
        composer.onTabSelected(ChatTab.of(ChatChannel.GLOBAL));
        assertTrue(composer.isEditing());
        composer.onTabSelected(ChatTab.of(ChatChannel.OOC));
        assertFalse(composer.isEditing());
        assertEquals(ChatMessageIds.NONE, composer.editingMessageId());
    }
}
