package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatPresentationTest {

    /**
     * 1 when the component answers to a click as the sender's name does
     * — the same whisper, and so the same card on a hover — else null.
     */
    private static Integer replyOf(IChatComponent part) {
        ClickEvent click = part.getChatStyle() == null ? null
                : part.getChatStyle().getChatClickEvent();
        return click != null
                && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && click.getValue() != null
                && click.getValue().startsWith("/msg ")
                ? Integer.valueOf(1) : null;
    }

    /**
     * The brackets answer to the pointer as the name does, but the name
     * is still the one component the player card reads: it is the first
     * to answer after the head, the opening bracket coming before the
     * head and the closing one after the name. The card's own walk
     * depends on that order, so it is asserted here.
     */
    @Test
    public void theNameIsTheFirstPartToAnswerAfterTheHead() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = false;
        try {
            IChatComponent message = LostTalesChatPresentation.build(
                    new LostTalesChatMessagePacket(
                            ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                            "RangerOfTheNorth", "", 0x55AA55, 0x336633,
                            "hello", 123456789L, ""));
            boolean afterHead = false;
            String firstBefore = null;
            String firstAfter = null;
            for (Object value : message) {
                IChatComponent part = (IChatComponent)value;
                if (ChatHeadMarker.decode(part) != null) {
                    afterHead = true;
                    continue;
                }
                if (replyOf(part) == null) {
                    continue;
                }
                String text = part.getUnformattedTextForChat();
                if (!afterHead && firstBefore == null) {
                    firstBefore = text;
                } else if (afterHead && firstAfter == null) {
                    firstAfter = text;
                }
            }
            assertEquals("<", firstBefore);
            assertEquals("Arathorn", firstAfter);
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

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
                if ("Global".equals(part.getUnformattedTextForChat())) {
                    assertFalse(part.getChatStyle().getBold());
                }
                if ("Arathorn".equals(part.getUnformattedTextForChat())) {
                    reply = part.getChatStyle().getChatClickEvent();
                    identityColor = part.getChatStyle().getColor();
                } else if ("<".equals(
                        part.getUnformattedTextForChat())) {
                    openingBracketColor = part.getChatStyle().getColor();
                    openingBracketRgb = replyOf(part);
                } else if ("> ".equals(
                        part.getUnformattedTextForChat())) {
                    closingBracketColor = part.getChatStyle().getColor();
                    closingBracketRgb = replyOf(part);
                }
            }

            assertEquals("Global: <  Arathorn, the Ranger> The road is clear.",
                    plainText.toString());
            assertNotNull(reply);
            assertEquals(ClickEvent.Action.SUGGEST_COMMAND,
                    reply.getAction());
            assertEquals("/msg RangerOfTheNorth ", reply.getValue());
            assertEquals(identityColor, openingBracketColor);
            assertEquals(identityColor, closingBracketColor);
            // The brackets are part of the name: they carry the same
            // whisper, so a hover over either shows the sender's card
            // and a click opens the conversation. Their colour is the
            // name's, which the renderer reads from the head marker.
            assertEquals(Integer.valueOf(1), openingBracketRgb);
            assertEquals(Integer.valueOf(1), closingBracketRgb);
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
    public void titledNamesFollowLotrNpcNamingAndUntitledNamesAreBare() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = false;
        try {
            LostTalesChatMessagePacket titled =
                    new LostTalesChatMessagePacket(
                            ChatChannel.ALL, UUID.randomUUID(), "Aldric",
                            "Aldric123", "Farmer", 0x55AA55, 0x336633,
                            "Good harvest.", 123456789L, "", null, "Gondor");
            ChatTitleMarker.Data marker = null;
            StringBuilder plainText = new StringBuilder();
            for (Object value : LostTalesChatPresentation.build(titled)) {
                IChatComponent part = (IChatComponent)value;
                plainText.append(part.getUnformattedTextForChat());
                ChatTitleMarker.Data decoded = ChatTitleMarker.decode(part);
                if (decoded != null) {
                    marker = decoded;
                }
            }
            assertEquals("Global: <  Aldric, the Gondor Farmer> Good harvest.",
                    plainText.toString());
            assertNotNull(marker);
            assertEquals("Gondor Farmer", marker.epithet);
            assertEquals(0x55AA55, marker.color);

            LostTalesChatMessagePacket untitled =
                    new LostTalesChatMessagePacket(
                            ChatChannel.ALL, UUID.randomUUID(), "Aldric",
                            "Aldric123", "", 0x55AA55, 0x336633,
                            "Good harvest.", 123456789L, "", null, "Gondor");
            plainText.setLength(0);
            for (Object value : LostTalesChatPresentation.build(untitled)) {
                IChatComponent part = (IChatComponent)value;
                plainText.append(part.getUnformattedTextForChat());
                assertFalse(ChatTitleMarker.isMarker(part));
            }
            assertEquals("Global: <  Aldric> Good harvest.",
                    plainText.toString());
            assertEquals("Farmer", LostTalesChatPresentation.epithet("", "Farmer"));
            assertEquals("Gondor Farmer",
                    LostTalesChatPresentation.epithet(" Gondor ", "Farmer "));
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

    @Test
    public void systemLinesCarryTheChannelPrefixAndTimestamp() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = true;
        try {
            IChatComponent line = LostTalesChatPresentation.buildSystemLine(
                    new net.minecraft.util.ChatComponentText(
                            "Your game mode has been updated"),
                    ChatChannel.CONSOLE, 123456789L);
            StringBuilder plainText = new StringBuilder();
            boolean anchor = false;
            Integer prefixColor = null;
            for (Object value : line) {
                IChatComponent part = (IChatComponent)value;
                plainText.append(part.getUnformattedTextForChat());
                anchor |= ChatLayoutMarker.isAnchor(part);
                if ("Console".equals(part.getUnformattedTextForChat())) {
                    prefixColor = ChatPrefixMarker.decode(part);
                }
            }
            String rendered = plainText.toString();
            assertTrue(rendered.startsWith("Console: ["));
            assertTrue(rendered.endsWith("] Your game mode has been updated"));
            assertTrue(anchor);
            assertEquals(Integer.valueOf(ChatChannel.CONSOLE.getDisplayColor()),
                    prefixColor);
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
            assertTrue(rendered.contains("] <"));
            assertFalse(rendered.contains(" | "));
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
                            ChatPrefixMarker.decode(part));
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
    public void channelCatalogueColorsComeFromThePalette() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.FERN_GREEN),
                ChatChannel.ALL.getDisplayColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN),
                ChatChannel.PROXIMITY.getDisplayColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE),
                ChatChannel.OOC.getDisplayColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.MAUVE),
                ChatChannel.CONSOLE.getDisplayColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.STEEL_BLUE),
                ChatChannel.DISCORD.getDisplayColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.CRIMSON),
                ChatChannel.ADMIN.getDisplayColor());
        // The catalogue colour is only the fallback: presentation shows
        // the member's own party colour and the sender's faction colour.
        assertEquals(LostTalesColors.rgb(LostTalesColors.SEAFOAM),
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
        int pinged = LostTalesChatHistoryHooks.MAX_CAPACITY + 50;
        for (int index = 0; index < pinged; index++) {
            LostTalesChatPresentation.markPinged(1000 + index);
        }
        // The oldest entries are evicted once the history's largest
        // capacity is hit.
        assertFalse(LostTalesChatPresentation.isPingedLine(7));
        assertTrue(LostTalesChatPresentation.isPingedLine(1000 + pinged - 1));
        LostTalesChatPresentation.clear();
        assertFalse(LostTalesChatPresentation.isPingedLine(1000 + pinged - 1));
    }

    /**
     * An account line tags every role its sender holds, in precedence
     * order and each in its own colour, ahead of the name; a line without
     * roles carries no tag.
     */
    @Test
    public void accountLinesTagEveryRoleInPrecedenceOrder() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = false;
        try {
            LostTalesChatMessagePacket tagged = new LostTalesChatMessagePacket(
                    ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                    0xFCECD1, ChatAccountRole.DEVELOPER.getColor(), "hello",
                    123456789L, "", null, "", "",
                    ChatAccountRole.maskOf(ChatAccountRole.OPERATOR,
                            ChatAccountRole.DEVELOPER));
            // Without a loaded language a tag reads as its key.
            String operatorTag = StatCollector.translateToLocal(
                    ChatAccountRole.OPERATOR.getTagKey()) + " ";
            String developerTag = StatCollector.translateToLocal(
                    ChatAccountRole.DEVELOPER.getTagKey()) + " ";
            StringBuilder plain = new StringBuilder();
            Integer operatorRgb = null;
            Integer developerRgb = null;
            Integer openingBracketRgb = null;
            for (Object value : LostTalesChatPresentation.build(tagged)) {
                IChatComponent part = (IChatComponent)value;
                String text = part.getUnformattedTextForChat();
                plain.append(text);
                if (operatorTag.equals(text)) {
                    operatorRgb = ChatColorMarker.decode(part);
                } else if (developerTag.equals(text)) {
                    developerRgb = ChatColorMarker.decode(part);
                } else if ("<".equals(text)) {
                    openingBracketRgb = replyOf(part);
                }
            }
            // Developer first, Operator second, both ahead of the bracket.
            int developer = plain.indexOf(developerTag);
            int operator = plain.indexOf(operatorTag);
            assertTrue(developer >= 0 && developer < operator);
            assertTrue(operator < plain.indexOf("<"));
            assertEquals(Integer.valueOf(
                    LostTalesColors.rgb(LostTalesColors.MULBERRY)),
                    developerRgb);
            assertEquals(Integer.valueOf(
                    LostTalesColors.rgb(LostTalesColors.CRIMSON)),
                    operatorRgb);
            // The bracket answers as the name does; the name's colour is
            // the packet's, which the server set to the primary role's.
            assertEquals(Integer.valueOf(1), openingBracketRgb);
            assertEquals(ChatAccountRole.DEVELOPER.getColor(),
                    markerOf(LostTalesChatPresentation.build(tagged)).nameColor);

            LostTalesChatMessagePacket plainPacket =
                    new LostTalesChatMessagePacket(
                            ChatChannel.OOC, UUID.randomUUID(), "Steve",
                            "Steve", "", 0xFCECD1, 0xFCECD1, "hello",
                            123456789L, "");
            StringBuilder untagged = new StringBuilder();
            for (Object value : LostTalesChatPresentation.build(plainPacket)) {
                untagged.append(
                        ((IChatComponent)value).getUnformattedTextForChat());
            }
            assertFalse(untagged.toString().contains(operatorTag));
            assertFalse(untagged.toString().contains(developerTag));
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
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
