package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
            EnumChatFormatting identityColor = null;
            EnumChatFormatting openingBracketColor = null;
            EnumChatFormatting closingBracketColor = null;
            Integer openingBracketRgb = null;
            Integer closingBracketRgb = null;
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
                    identityColor = part.getChatStyle().getColor();
                } else if ("<".equals(
                        part.getUnformattedTextForChat())) {
                    openingBracketColor = part.getChatStyle().getColor();
                    openingBracketRgb = ChatColorMarker.decode(part);
                } else if ("> ".equals(
                        part.getUnformattedTextForChat())) {
                    closingBracketColor = part.getChatStyle().getColor();
                    closingBracketRgb = ChatColorMarker.decode(part);
                }
            }

            assertEquals("All: <  Ranger Arathorn> The road is clear.",
                    plainText.toString());
            assertNotNull(reply);
            assertEquals(ClickEvent.Action.SUGGEST_COMMAND,
                    reply.getAction());
            assertEquals("/msg RangerOfTheNorth ", reply.getValue());
            assertEquals(identityColor, openingBracketColor);
            assertEquals(identityColor, closingBracketColor);
            assertEquals(Integer.valueOf(0x336633), openingBracketRgb);
            assertEquals(Integer.valueOf(0x336633), closingBracketRgb);
            assertNotNull(headMarker);
            // The invisible two-space marker is bold only to reserve ten
            // pixels for the raised portrait and a compact final gap.
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
    public void timestampIsFollowedByChannelSeparator() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = true;
        try {
            LostTalesChatMessagePacket packet =
                    new LostTalesChatMessagePacket(
                            ChatChannel.PROXIMITY, UUID.randomUUID(),
                            "Arathorn", "RangerOfTheNorth", "",
                            0x55AA55, 0x336633, "Halt.",
                            123456789L,
                            "losttales:human_ranger_male_2");
            StringBuilder plainText = new StringBuilder();
            for (Object value : LostTalesChatPresentation.build(packet)) {
                plainText.append(((IChatComponent)value)
                        .getUnformattedTextForChat());
            }

            String rendered = plainText.toString();
            assertTrue(rendered.startsWith("Proximity: ["));
            assertTrue(rendered.contains("] | <"));
            assertTrue(rendered.endsWith(
                    "Arathorn> Halt."));
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

    @Test
    public void factionChannelUsesTheFactionSnapshotColor() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = false;
        try {
            IChatComponent message = LostTalesChatPresentation.build(
                    new LostTalesChatMessagePacket(
                            ChatChannel.FACTION, UUID.randomUUID(),
                            "Amdir", "Player", "",
                            0x778899, 0x245A32, "Mae govannen.",
                            123456789L, "losttales:elf_high_male_0"));
            for (Object value : message) {
                IChatComponent part = (IChatComponent)value;
                if ("Faction".equals(
                        part.getUnformattedTextForChat())) {
                    assertEquals(Integer.valueOf(0x245A32),
                            ChatColorMarker.decode(part));
                    return;
                }
            }
            throw new AssertionError("Faction channel component missing");
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
        assertEquals("\u00a7lFarmer\u00a7o of Bree",
                LostTalesChatVisualStyle.removeColorCodes(
                        "\u00a7f\u00a7lFarmer\u00a7r\u00a7o of Bree\u00a7z"));
    }

    @Test
    public void chatBackdropUsesPlumBlackAndFadesAfterTwoThirds() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.PLUM_BLACK),
                LostTalesChatOverlayRenderer.CHAT_BACKDROP_RGB);
        assertEquals(0x2D1E2F,
                LostTalesChatOverlayRenderer.CHAT_BACKDROP_RGB);
        assertEquals(67,
                LostTalesChatOverlayRenderer.backdropFadeStart(100));
        assertEquals(66,
                LostTalesChatOverlayRenderer.backdropFadeStart(99));
        assertEquals(200,
                LostTalesChatOverlayRenderer.backdropFadeStart(300));
    }

    @Test
    public void allAndPartyUseTheSharedGreenAndBluePalette() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.GREEN),
                ChatChannel.ALL.getDisplayColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.BLUE),
                ChatChannel.PARTY.getDisplayColor());
    }

    @Test
    public void ampersandCodesRenderInTheMessageBodyOnly() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = false;
        try {
            LostTalesChatMessagePacket packet =
                    new LostTalesChatMessagePacket(
                            ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                            "RangerOfTheNorth", "", 0x55AA55, 0x336633,
                            "&6gold words", 123456789L,
                            "losttales:human_ranger_male_2");
            IChatComponent message = LostTalesChatPresentation.build(packet);
            StringBuilder plainText = new StringBuilder();
            ChatHeadMarker.Data marker = null;
            for (Object value : message) {
                IChatComponent part = (IChatComponent)value;
                plainText.append(part.getUnformattedTextForChat());
                if (ChatHeadMarker.decode(part) != null) {
                    marker = ChatHeadMarker.decode(part);
                }
            }
            assertTrue(plainText.toString().endsWith(
                    "\u00a76gold words"));
            assertNotNull(marker);
            // Copying yields exactly what the sender typed.
            assertEquals("&6gold words", marker.copyText);
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

    @Test
    public void pingedLinesAreTrackedBoundedAndCleared() {
        LostTalesChatPresentation.clear();
        LostTalesChatPresentation.markPinged(7);
        assertTrue(LostTalesChatPresentation.isPingedLine(7));
        assertFalse(LostTalesChatPresentation.isPingedLine(8));
        for (int index = 0; index < 150; index++) {
            LostTalesChatPresentation.markPinged(1000 + index);
        }
        // The oldest entries are evicted once the history cap is hit.
        assertFalse(LostTalesChatPresentation.isPingedLine(7));
        assertTrue(LostTalesChatPresentation.isPingedLine(1149));
        LostTalesChatPresentation.clear();
        assertFalse(LostTalesChatPresentation.isPingedLine(1149));
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
