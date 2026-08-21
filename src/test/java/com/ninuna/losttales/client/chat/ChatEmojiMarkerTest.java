package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ChatEmojiMarkerTest {

    @Test
    public void markerRoundTripsAndReservesTenBoldPixels() {
        ChatComponentText marker = ChatEmojiMarker.create(ChatEmoji.JOY);
        assertSame(ChatEmoji.JOY, ChatEmojiMarker.decode(marker));
        assertTrue(ChatEmojiMarker.isMarker(marker));
        assertEquals("  ", marker.getUnformattedTextForChat());
        assertEquals(Boolean.TRUE, marker.getChatStyle().getBold());
    }

    @Test
    public void decodeRejectsForeignClickEventsAndOtherMarkers() {
        ChatComponentText reply = new ChatComponentText("Arathorn");
        reply.setChatStyle(reply.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/msg Arathorn ")));
        assertNull(ChatEmojiMarker.decode(reply));
        assertNull(ChatEmojiMarker.decode(
                new ChatComponentText("plain")));
        assertNull(ChatEmojiMarker.decode(null));

        ChatComponentText unknown = new ChatComponentText("  ");
        unknown.setChatStyle(unknown.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "losttales-chat-emoji:does_not_exist")));
        assertNull(ChatEmojiMarker.decode(unknown));
    }

    @Test
    public void splitPlaceholderHalvesDoNotClaimAFullSlot() {
        assertTrue(ChatEmojiMarker.reservesFullSlot("  "));
        // Wrapped copies carry the formatting codes baked into the text.
        assertTrue(ChatEmojiMarker.reservesFullSlot(
                "\u00a7f\u00a7l  "));
        assertFalse(ChatEmojiMarker.reservesFullSlot("\u00a7f\u00a7l "));
        assertFalse(ChatEmojiMarker.reservesFullSlot(" "));
        assertFalse(ChatEmojiMarker.reservesFullSlot(""));
        assertFalse(ChatEmojiMarker.reservesFullSlot(null));
    }

    @Test
    public void presentationReplacesShortcodesWithMarkersInOrder() {
        boolean originalEmojis = LostTalesConfig.enableChatEmojis;
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.enableChatEmojis = true;
        LostTalesConfig.showChatTimestamps = false;
        try {
            IChatComponent message = LostTalesChatPresentation.build(
                    packet("Hi :smile: there :joy:"));
            List<ChatEmoji> markers = new ArrayList<ChatEmoji>();
            ChatHeadMarker.Data head = null;
            for (Object value : message) {
                IChatComponent part = (IChatComponent)value;
                ChatEmoji emoji = ChatEmojiMarker.decode(part);
                if (emoji != null) {
                    markers.add(emoji);
                }
                if (ChatHeadMarker.decode(part) != null) {
                    head = ChatHeadMarker.decode(part);
                }
            }
            assertEquals(2, markers.size());
            assertSame(ChatEmoji.SMILE, markers.get(0));
            assertSame(ChatEmoji.JOY, markers.get(1));
            assertNotNull(head);
            // Copying a line must yield the canonical shortcode text.
            assertEquals("Hi :smile: there :joy:", head.copyText);
        } finally {
            LostTalesConfig.enableChatEmojis = originalEmojis;
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

    @Test
    public void disabledConfigKeepsShortcodesAsPlainText() {
        boolean originalEmojis = LostTalesConfig.enableChatEmojis;
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.enableChatEmojis = false;
        LostTalesConfig.showChatTimestamps = false;
        try {
            IChatComponent message = LostTalesChatPresentation.build(
                    packet("Hi :smile:"));
            StringBuilder plainText = new StringBuilder();
            for (Object value : message) {
                IChatComponent part = (IChatComponent)value;
                assertNull(ChatEmojiMarker.decode(part));
                plainText.append(part.getUnformattedTextForChat());
            }
            assertTrue(plainText.toString().endsWith("Hi :smile:"));
        } finally {
            LostTalesConfig.enableChatEmojis = originalEmojis;
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

    private static LostTalesChatMessagePacket packet(String message) {
        return new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                "RangerOfTheNorth", "Ranger", 0x55AA55, 0x336633,
                message, 123456789L, "losttales:human_ranger_male_2");
    }
}
