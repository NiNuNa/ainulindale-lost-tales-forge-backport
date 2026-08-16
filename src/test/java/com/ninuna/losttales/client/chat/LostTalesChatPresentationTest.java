package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class LostTalesChatPresentationTest {

    @Test
    public void identityUsesBracketsSpacingAndVanillaReplyAction() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = false;
        try {
            LostTalesChatMessagePacket packet =
                    new LostTalesChatMessagePacket(
                            ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                            "RangerOfTheNorth", "Ranger",
                            0x55AA55, 0x336633,
                            "The road is clear.", 123456789L,
                            "losttales:human_ranger_male_2");
            IChatComponent message = LostTalesChatPresentation.build(packet);

            StringBuilder plainText = new StringBuilder();
            ClickEvent reply = null;
            ChatHeadMarker.Data headMarker = null;
            for (Object value : message) {
                IChatComponent part = (IChatComponent)value;
                plainText.append(part.getUnformattedTextForChat());
                ChatHeadMarker.Data decoded = ChatHeadMarker.decode(part);
                if (decoded != null) {
                    headMarker = decoded;
                }
                if ("All".equals(part.getUnformattedTextForChat())) {
                    assertFalse(part.getChatStyle().getBold());
                }
                if ("Arathorn".equals(part.getUnformattedTextForChat())) {
                    reply = part.getChatStyle().getChatClickEvent();
                }
            }

            assertEquals("All: <  [Ranger] Arathorn> The road is clear.",
                    plainText.toString());
            assertNotNull(reply);
            assertEquals(ClickEvent.Action.SUGGEST_COMMAND,
                    reply.getAction());
            assertEquals("/msg RangerOfTheNorth ", reply.getValue());
            assertNotNull(headMarker);
            // The invisible two-space marker is bold only to reserve ten
            // pixels for the nine-pixel portrait and a compact final gap.
            IChatComponent markerComponent = null;
            for (Object value : message) {
                IChatComponent part = (IChatComponent)value;
                if (ChatHeadMarker.decode(part) != null) {
                    markerComponent = part;
                    break;
                }
            }
            assertNotNull(markerComponent);
            assertEquals(Boolean.TRUE,
                    markerComponent.getChatStyle().getBold());
            assertEquals("The road is clear.", headMarker.copyText);
            assertEquals("losttales:human_ranger_male_2",
                    headMarker.skinId);
            assertEquals(0x55AA55, headMarker.titleColor);
            assertEquals(0x336633, headMarker.nameColor);
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

    @Test
    public void messageKeepsSkinSnapshotAfterLaterCharacterChange() {
        UUID sender = UUID.randomUUID();
        LostTalesChatMessagePacket first = new LostTalesChatMessagePacket(
                ChatChannel.ALL, sender, "Borin", "Player", "",
                0xAABBCC, 0xAABBCC, "First", 1000L,
                "losttales:dwarf_erebor_male_0");
        LostTalesChatMessagePacket second = new LostTalesChatMessagePacket(
                ChatChannel.ALL, sender, "Amdir", "Player", "",
                0x112233, 0x112233, "Second", 2000L,
                "losttales:elf_high_male_0");

        ChatHeadMarker.Data firstMarker = markerOf(
                LostTalesChatPresentation.build(first));
        ChatHeadMarker.Data secondMarker = markerOf(
                LostTalesChatPresentation.build(second));

        assertNotNull(firstMarker);
        assertNotNull(secondMarker);
        assertEquals("losttales:dwarf_erebor_male_0",
                firstMarker.skinId);
        assertEquals("losttales:elf_high_male_0",
                secondMarker.skinId);
        assertEquals("First", firstMarker.copyText);
        assertEquals("Second", secondMarker.copyText);
    }

    @Test
    public void shadowFormattingKeepsBoldAdvanceButDropsColors() {
        assertEquals("\u00a7l\u00a7o",
                LostTalesChatVisualStyle.styleCodesOnly(
                        "\u00a7f\u00a7l\u00a7a\u00a7o\u00a7r"));
    }

    private static ChatHeadMarker.Data markerOf(IChatComponent message) {
        for (Object value : message) {
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(
                    (IChatComponent)value);
            if (marker != null) {
                return marker;
            }
        }
        return null;
    }
}
