package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.UUID;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A reply's quote opens with the chat's speech bubble: a run of the
 * quote that answers the quote's click, names the message it quotes,
 * declares the bubble's width, and is neither a head nor words.
 */
public final class ChatReplyMarkerTest {

    @Test
    public void theBubbleIsARunOfTheQuoteWithADeclaredWidth() {
        ChatComponentText bubble = ChatReplyMarker.applyIcon(
                new ChatComponentText(""), 0xB8A79A, 4242L);
        assertTrue(ChatReplyMarker.isMarker(bubble));
        assertTrue(ChatReplyMarker.isIconSlot(bubble));
        assertEquals(4242L, ChatReplyMarker.messageIdOf(bubble));
        assertEquals(Integer.valueOf(0xB8A79A), ChatReplyMarker.colorOf(bubble));
        assertNull("the bubble is not the quoted sender's head",
                ChatReplyMarker.headOf(bubble));
        assertEquals(LostTalesUiSheet.SPEECH_BUBBLE.getWidth() + 3,
                ChatReplyMarker.ICON_SLOT_WIDTH);
        assertEquals(ChatReplyMarker.ICON_SLOT_WIDTH,
                ChatInlineIcons.declaredWidth(bubble));
    }

    @Test
    public void wordsAndHeadsAreNotTheBubble() {
        ChatComponentText words = ChatReplyMarker.apply(
                new ChatComponentText("meet me at the gate"), 0xFFFFFF, 7L);
        assertFalse(ChatReplyMarker.isIconSlot(words));
        assertEquals(7L, ChatReplyMarker.messageIdOf(words));
        ChatComponentText head = ChatReplyMarker.applyHead(
                new ChatComponentText("  "), 0xFFFFFF, 7L, UUID.randomUUID(),
                false, false, "skin-7");
        assertFalse(ChatReplyMarker.isIconSlot(head));
        assertNotNull(ChatReplyMarker.headOf(head));
        assertEquals(7L, ChatReplyMarker.messageIdOf(head));
    }
}
