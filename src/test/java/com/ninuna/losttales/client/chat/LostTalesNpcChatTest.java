package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.UUID;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class LostTalesNpcChatTest {

    @Test
    public void npcSpeechLineMirrorsThePlayerPresentation() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        boolean originalEmojis = LostTalesConfig.enableChatEmojis;
        LostTalesConfig.showChatTimestamps = true;
        LostTalesConfig.enableChatEmojis = true;
        try {
            UUID npcId = UUID.randomUUID();
            int factionColor = 0x8A9A5B;
            IChatComponent line = LostTalesChatPresentation.buildNpcSpeech(
                    ChatTab.whisper("Grey Wanderer"), npcId, "Grey Wanderer",
                    "lotr:mob/wanderer.png",
                    "Good day to you! :smile:", 123456789L, factionColor);

            StringBuilder plainText = new StringBuilder();
            ChatHeadMarker.Data marker = null;
            ChatEmoji emoji = null;
            for (Object value : line) {
                IChatComponent part = (IChatComponent)value;
                plainText.append(part.getUnformattedTextForChat());
                if (ChatHeadMarker.decode(part) != null) {
                    marker = ChatHeadMarker.decode(part);
                }
                if (ChatEmojiMarker.decode(part) != null) {
                    emoji = ChatEmojiMarker.decode(part);
                }
            }

            String rendered = plainText.toString();
            // NPC speech is a whisper from the NPC: its tab bears its name.
            assertTrue(rendered.startsWith("Grey Wanderer: ["));
            assertTrue(rendered.contains("] <"));
            assertTrue(rendered.contains("Grey Wanderer> Good day"));
            assertNotNull(marker);
            assertTrue(marker.npcIdentity);
            assertFalse(marker.accountIdentity);
            assertEquals(npcId, marker.senderId);
            assertEquals("lotr:mob/wanderer.png", marker.skinId);
            assertEquals("Good day to you! :smile:", marker.copyText);
            // The NPC speaks in its faction colour, as the hook resolved it.
            assertEquals(factionColor, marker.nameColor);
            // Body text after the un-clickable NPC name must stay ivory.
            assertEquals(LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                    marker.titleColor);
            assertSame(ChatEmoji.SMILE, emoji);
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
            LostTalesConfig.enableChatEmojis = originalEmojis;
        }
    }

    @Test
    public void playerHeadMarkersAreUnaffectedByTheNpcVariant() {
        UUID npcId = UUID.randomUUID();
        ChatHeadMarker.Data npc = decode(ChatHeadMarker.encodeNpc(
                npcId, "lotr:mob/wanderer.png", "Hello", 0xFCECD1,
                0xF7CF91));
        assertNotNull(npc);
        assertTrue(npc.npcIdentity);
        assertFalse(npc.accountIdentity);
    }

    @Test
    public void speechExtractionStripsTheNamePrefixAndFormatting() {
        assertEquals("Good day to you, Player734!",
                LostTalesNpcChatHook.extractSpeech(
                        "\u00a7e<Grey Wanderer>\u00a7f Good day to you, "
                                + "Player734!", "Grey Wanderer"));
        assertEquals("Unprefixed words",
                LostTalesNpcChatHook.extractSpeech(
                        "Unprefixed words", "Grey Wanderer"));
        assertEquals("", LostTalesNpcChatHook.extractSpeech(
                null, "Grey Wanderer"));
        assertEquals("<Someone Else> Hi",
                LostTalesNpcChatHook.extractSpeech(
                        "<Someone Else> Hi", "Grey Wanderer"));
    }

    private static ChatHeadMarker.Data decode(String encoded) {
        net.minecraft.util.ChatComponentText component =
                new net.minecraft.util.ChatComponentText("  ");
        component.setChatStyle(component.getChatStyle().setChatClickEvent(
                new net.minecraft.event.ClickEvent(
                        net.minecraft.event.ClickEvent.Action
                                .SUGGEST_COMMAND, encoded)));
        return ChatHeadMarker.decode(component);
    }
}
