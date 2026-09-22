package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.chat.ChatNarrator;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatPresentationTest {

    @Before
    public void setUp() {
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    /**
     * A Narrator line wears the Narrator's mark for a head and tells its
     * words in italics; the words are still read for everything a
     * message carries.
     */
    @Test
    public void aNarratorLineWearsItsMarkAndItalicWords() {
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), ChatNarrator.NAME,
                "Steve", "", 0, ChatNarrator.color(), "the gate falls",
                123456789L, ChatNarrator.SKIN_ID);
        assertTrue(packet.isNarrator());
        IChatComponent message = LostTalesChatPresentation.build(packet);
        boolean marked = false;
        boolean italicWords = false;
        for (Object value : message) {
            IChatComponent part = (IChatComponent) value;
            ChatHeadMarker.Data head = ChatHeadMarker.decode(part);
            if (head != null) {
                marked = head.isNarrator()
                        && head.mark() == ChatHeadMarker.NARRATOR_MARK;
            }
            if ("the gate falls".equals(part.getUnformattedTextForChat())) {
                italicWords = part.getChatStyle().getItalic();
            }
        }
        assertTrue("the head slot holds the Narrator's mark", marked);
        assertTrue("the words are told in italics", italicWords);
    }

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
    }

    @Test
    public void identityUsesBracketsSpacingAndVanillaReplyAction() {
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
    }

    @Test
    public void titledNamesFollowLotrNpcNamingAndUntitledNamesAreBare() {
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
        assertEquals("Farmer", ChatEpithet.epithet("", "Farmer"));
        assertEquals("Gondor Farmer",
                ChatEpithet.epithet(" Gondor ", "Farmer "));
    }

    /**
     * A line the client printed for itself is adopted as the Client's:
     * the channel prefix every line carries, the Client for a sender,
     * and the words exactly as they were printed.
     */
    @Test
    public void aStrayLineIsAdoptedAsTheClients() {
        net.minecraft.util.ChatComponentText printed =
                new net.minecraft.util.ChatComponentText(
                        "Your game mode has been updated");
        ChatTab console = ChatTab.of(ChatChannel.CONSOLE);
        LostTalesChatMessagePacket packet = LostTalesChatPresentation
                .clientPacket(console, printed, 123456789L,
                        com.ninuna.losttales.chat.ChatReplyReference.NONE);
        assertEquals(LostTalesChatMessagePacket.CLIENT_SENDER_ID,
                packet.getSenderId());
        assertEquals("Your game mode has been updated", packet.getMessage());
        assertTrue(packet.isAccountLine());
        IChatComponent line = LostTalesChatPresentation.build(packet,
                console, new int[0], false, ChatBodyKind.ANSWER, printed);
        StringBuilder plainText = new StringBuilder();
        boolean anchor = false;
        boolean head = false;
        Integer prefixColor = null;
        for (Object value : line) {
            IChatComponent part = (IChatComponent)value;
            plainText.append(part.getUnformattedTextForChat());
            anchor |= ChatLayoutMarker.isAnchor(part);
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            head |= marker != null && marker.isSystemSender()
                    && marker.mark() == com.ninuna.losttales.chat.emoji.ChatEmoji.CONSOLE;
            if (ChatChannel.CONSOLE.getDisplayName().equals(
                    part.getUnformattedTextForChat())) {
                prefixColor = ChatPrefixMarker.decode(part);
            }
        }
        String rendered = plainText.toString();
        assertTrue(rendered, rendered.startsWith(
                ChatChannel.CONSOLE.getDisplayName() + ": "));
        assertTrue(rendered, rendered.endsWith("Your game mode has been updated"));
        assertTrue(anchor);
        assertTrue("the Client wears the console mark for a head", head);
        assertEquals(Integer.valueOf(ChatChannel.CONSOLE.getDisplayColor()),
                prefixColor);
    }

    /**
     * A line opens with its channel prefix and then its sender; it holds
     * no time of its own, since a window stands the time behind the
     * name as it lays the line out.
     */
    @Test
    public void theSenderFollowsTheChannelPrefix() {
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
        assertTrue(rendered, rendered.startsWith("Proximity: <"));
        assertFalse(rendered.contains(" | "));
        assertTrue(rendered.endsWith(
                "Arathorn> Halt."));
    }

    /**
     * The Faction prefix names the reader's faction, as the tab does — the
     * account reads as Unaligned — and wears the line's own faction colour.
     */
    @Test
    public void factionChannelUsesTheFactionSnapshotColor() {
        String prefix = ClientChatChannelState.displayName(ChatChannel.FACTION);
        assertNotEquals(ChatChannel.FACTION.getDisplayName(), prefix);
        IChatComponent message = LostTalesChatPresentation.build(
                new LostTalesChatMessagePacket(
                        ChatChannel.FACTION, UUID.randomUUID(),
                        "Amdir", "Player", "",
                        0x778899, 0x245A32, "Mae govannen.",
                        123456789L, "losttales:elf_high_male_0"));
        for (Object value : message) {
            IChatComponent part = (IChatComponent)value;
            if (prefix.equals(part.getUnformattedTextForChat())) {
                assertEquals(Integer.valueOf(0x245A32),
                        ChatPrefixMarker.decode(part));
                return;
            }
        }
        throw new AssertionError("Faction channel component missing");
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


    /**
     * The backdrop leans away from the very first pixel and reaches
     * nothing at the last, and it does so without a corner anywhere: it
     * held full strength for two thirds and then fell away in a straight
     * line, and the eye read the corner where the two met as an edge in
     * the band. What that costs is checked too — the curve spends the
     * same total opacity the old profile did, so a line's backdrop is no
     * lighter overall than it was.
     */
    @Test
    public void theBackdropFadeHasNoCornerToRead() {
        float[] weights = LostTalesChatOverlayRenderer.BACKDROP_FADE_WEIGHTS;
        assertTrue("The fade needs steps to be drawn in",
                weights.length > 8);
        assertEquals(1.0F, weights[0], 0.0F);
        assertEquals(0.0F, weights[weights.length - 1], 0.0F);
        float steepest = 0.0F;
        float area = 0.0F;
        for (int step = 0; step + 1 < weights.length; step++) {
            float drop = weights[step] - weights[step + 1];
            assertTrue("The fade never brightens again", drop >= 0.0F);
            steepest = Math.max(steepest, drop);
            area += (weights[step] + weights[step + 1]) / 2.0F;
        }
        area /= weights.length - 1;
        // The old profile: full for two thirds, then straight to
        // nothing. Same ink, spread instead of broken.
        assertEquals(2.0F / 3.0F + (1.0F / 3.0F) / 2.0F, area, 0.01F);
        // No step may drop more than a small share of the whole, which
        // is what a corner in the profile would show up as.
        assertTrue("The fade turns a corner somewhere: " + steepest,
                steepest < 0.2F);
    }


    /**
     * An ampersand code is not formatting: a player's own words carry
     * only the markup, so nothing typed can paint a line in a colour the
     * palette does not give it.
     */
    @Test
    public void ampersandCodesStayLiteral() {
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
        assertTrue(plainText.toString().endsWith("&6gold words"));
        assertFalse(plainText.toString().indexOf('\u00a7') >= 0);
        assertNotNull(marker);
        // Copying yields exactly what the sender typed.
        assertEquals("&6gold words", marker.copyText);
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
     * An account line wears no role beside its sender's name: the name
     * takes the primary role's colour, which the server put on the
     * packet, and the roles are read on the sender's card.
     */
    @Test
    public void accountLinesWearTheirRolesColourAndNoTag() {
        LostTalesChatMessagePacket tagged = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFCECD1, ChatAccountRole.TEAM.getColor(), "hello",
                123456789L, "", null, "", "",
                ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR,
                        ChatAccountRole.TEAM));
        // Without a loaded language a built-in's name reads as its key.
        String operatorTag = "[" + ChatRoleFixtures.OPERATOR.getDisplayName() + "]";
        String developerTag = "[" + ChatAccountRole.TEAM.getDisplayName() + "]";
        StringBuilder plain = new StringBuilder();
        Integer openingBracketRgb = null;
        for (Object value : LostTalesChatPresentation.build(tagged)) {
            IChatComponent part = (IChatComponent)value;
            String text = part.getUnformattedTextForChat();
            plain.append(text);
            assertEquals(null, ChatMentionMarker.decode(part));
            if ("<".equals(text)) {
                openingBracketRgb = replyOf(part);
            }
        }
        assertFalse(plain.toString().contains(operatorTag));
        assertFalse(plain.toString().contains(developerTag));
        // The bracket answers as the name does; the name's colour is
        // the packet's, which the server set to the primary role's.
        assertEquals(Integer.valueOf(1), openingBracketRgb);
        assertEquals(ChatAccountRole.TEAM.getColor(),
                markerOf(LostTalesChatPresentation.build(tagged)).nameColor);
    }

    /**
     * A direct message follows the same identity rules every other
     * channel does: an account line takes the primary role's colour
     * there too, with no tag, and a character-signed one takes the
     * character's colour and names the character alone.
     */
    @Test
    public void directMessagesFollowTheCommonIdentityRules() {
        int roles = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        LostTalesChatMessagePacket whisper =
                new LostTalesChatMessagePacket(
                        ChatChannel.WHISPER, UUID.randomUUID(), "Steve",
                        "Steve", "", 0xFCECD1,
                        ChatAccountRole.nameColor(roles), "hello",
                        123456789L, "", null, "", "Alex", roles, true);
        String operatorTag = "[" + ChatRoleFixtures.OPERATOR.getDisplayName() + "]";
        StringBuilder plain = new StringBuilder();
        for (Object value : LostTalesChatPresentation.build(whisper)) {
            plain.append(((IChatComponent)value).getUnformattedTextForChat());
        }
        assertFalse(plain.toString().contains(operatorTag));
        assertEquals(ChatRoleFixtures.OPERATOR.getColor(),
                markerOf(LostTalesChatPresentation.build(whisper))
                        .nameColor);

        // Signed with a character instead: no tag, the character's
        // own colour, and the character's name alone, no account
        // behind it.
        LostTalesChatMessagePacket asCharacter =
                new LostTalesChatMessagePacket(
                        ChatChannel.WHISPER, UUID.randomUUID(),
                        "Aldric", "Steve", "", 0xFCECD1, 0x55AA55,
                        "hello", 123456789L, "skin", null, "", "Alex",
                        0, false);
        StringBuilder character = new StringBuilder();
        for (Object value
                : LostTalesChatPresentation.build(asCharacter)) {
            character.append(((IChatComponent)value)
                    .getUnformattedTextForChat());
        }
        assertFalse(character.toString().contains(operatorTag));
        assertTrue(character.toString().contains("Aldric"));
        assertFalse(character.toString().contains("(Steve)"));
        assertEquals(0x55AA55,
                markerOf(LostTalesChatPresentation.build(asCharacter))
                        .nameColor);
    }

    /**
     * Markup styles the body and leaves the text: the markers are gone
     * from what is shown, and nothing else about the line changes.
     */
    @Test
    public void markupStylesTheBodyAndDropsItsMarkers() {
        IChatComponent message = LostTalesChatPresentation.build(
                new LostTalesChatMessagePacket(
                        ChatChannel.ALL, UUID.randomUUID(), "Aldric",
                        "Steve", "", 0x55AA55, 0x336633,
                        "a **bold** and `code` word", 123456789L, ""));
        StringBuilder body = new StringBuilder();
        boolean sawBold = false;
        boolean sawCode = false;
        boolean started = false;
        for (Object value : message) {
            IChatComponent part = (IChatComponent)value;
            String text = part.getUnformattedTextForChat();
            if ("> ".equals(text)) {
                started = true;
                continue;
            }
            if (!started) {
                continue;
            }
            body.append(text);
            if ("bold".equals(text)) {
                sawBold = Boolean.TRUE.equals(
                        part.getChatStyle().getBold());
            }
            if ("code".equals(text)) {
                // Inline code: in italics and the chat's aside tone.
                sawCode = Boolean.TRUE.equals(
                        part.getChatStyle().getItalic())
                        && Integer.valueOf(
                                LostTalesChatVisualStyle.asideRgb())
                                .equals(ChatColorMarker.decode(part));
            }
        }
        assertEquals("a bold and code word", body.toString());
        assertTrue("bold run is not bold", sawBold);
        assertTrue("code run is not marked", sawCode);
    }

    /**
     * A command echo is a line of the sender's like any other: the same
     * header, part for part, and a body row that opens behind the
     * chevron, as a message's does. The body is the command exactly as
     * typed, slash and all, shown as inline code since it was not said —
     * nothing in it is markup, an emoji or a mention — and its grouped
     * form opens the same way.
     */
    @Test
    public void commandEchoesShareTheHeaderAndKeepTheBodyVerbatim() {
        boolean originalEmojis = LostTalesConfig.enableChatEmojis;
        LostTalesConfig.enableChatEmojis = true;
        try {
            String command = "/losttales hud **bold** :smile: @Arathorn";
            LostTalesChatMessagePacket packet =
                    new LostTalesChatMessagePacket(
                            ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                            "RangerOfTheNorth", "", 0x55AA55, 0x336633,
                            command, 123456789L,
                            "losttales:human_ranger_male_2");
            IChatComponent said = LostTalesChatPresentation.build(packet,
                    ChatTab.of(ChatChannel.ALL), new int[0], false,
                    ChatBodyKind.MESSAGE);
            IChatComponent used = LostTalesChatPresentation.build(packet,
                    ChatTab.of(ChatChannel.ALL), new int[0], false,
                    ChatBodyKind.COMMAND);

            assertEquals(headerOf(said), headerOf(used));
            assertEquals("Global: <  Arathorn> ", headerOf(used));
            // Both bodies open behind the chevron.
            assertNull(labelOf(said));
            assertNull(labelOf(used));

            java.util.List<IChatComponent> body = bodyOf(used);
            // One run, slash and all, behind the chevron: the command as
            // inline code, in italics and exactly the aside tone.
            assertEquals(1, body.size());
            assertEquals(command, body.get(0).getUnformattedTextForChat());
            assertTrue(body.get(0).getChatStyle().getItalic());
            assertEquals(Integer.valueOf(LostTalesChatVisualStyle.asideRgb()),
                    ChatColorMarker.decode(body.get(0)));
            // The message form of the same words is read for markup.
            assertTrue(bodyOf(said).size() > 1);

            IChatComponent grouped = LostTalesChatPresentation.build(
                    packet, ChatTab.of(ChatChannel.ALL), new int[0], true,
                    ChatBodyKind.COMMAND);
            assertEquals("", headerOf(grouped));
            assertNull(labelOf(grouped));
            assertEquals(1, bodyOf(grouped).size());
            assertEquals(command,
                    bodyOf(grouped).get(0).getUnformattedTextForChat());
        } finally {
            LostTalesConfig.enableChatEmojis = originalEmojis;
        }
    }

    /**
     * A quoted command is still a command: the quote a reply opens with
     * shows it as inline code, as the line it quotes does, while quoted
     * words keep the chat's ivory.
     */
    @Test
    public void aQuotedCommandIsInlineCode() {
        LostTalesChatMessagePacket answer = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                "RangerOfTheNorth", "", 0x55AA55, 0x336633, "Done.",
                123456789L, "losttales:human_ranger_male_2");
        IChatComponent command = quoteWordsOf(answer.withReply(
                com.ninuna.losttales.chat.ChatReplyReference.unanchored(
                        "Player125", "/clear", 0xAA5555)));
        assertEquals("/clear", command.getUnformattedTextForChat());
        assertTrue(command.getChatStyle().getItalic());
        assertEquals(Integer.valueOf(LostTalesChatVisualStyle.asideRgb()),
                ChatReplyMarker.colorOf(command));

        IChatComponent words = quoteWordsOf(answer.withReply(
                com.ninuna.losttales.chat.ChatReplyReference.unanchored(
                        "Player125", "hello /there", 0xAA5555)));
        assertEquals("hello /there", words.getUnformattedTextForChat());
        assertFalse(words.getChatStyle().getItalic());
        assertEquals(Integer.valueOf(
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL)),
                ChatReplyMarker.colorOf(words));
    }

    /** The words of the quote the line built from {@code packet} opens with. */
    private static IChatComponent quoteWordsOf(
            LostTalesChatMessagePacket packet) {
        IChatComponent line = LostTalesChatPresentation.build(packet,
                ChatTab.of(ChatChannel.ALL), new int[0], false,
                ChatBodyKind.MESSAGE);
        IChatComponent words = null;
        for (Object value : line) {
            IChatComponent part = (IChatComponent)value;
            if (ChatLayoutMarker.isLineBreak(part)) {
                return words;
            }
            if (ChatReplyMarker.isMarker(part)) {
                words = part;
            }
        }
        return null;
    }

    /**
     * A console entry reads as a sentence: its words end with a full
     * stop, and words that already end one keep their own mark.
     */
    @Test
    public void consoleEntriesEndWithAFullStop() {
        assertEquals("Server started.",
                LostTalesChatPresentation.asSentence("Server started"));
        assertEquals("changed server settings: [a, b].",
                LostTalesChatPresentation.asSentence(
                        "changed server settings: [a, b]"));
        assertEquals("Server stopped.",
                LostTalesChatPresentation.asSentence("Server stopped. "));
        assertEquals("Really?",
                LostTalesChatPresentation.asSentence("Really?"));
        assertEquals("", LostTalesChatPresentation.asSentence(""));
        assertEquals("", LostTalesChatPresentation.asSentence(null));
    }

    /**
     * A reply to one of this player's lines pings them without naming
     * them, whoever replies — another player, a Discord member, the
     * Server answering their command, or they themselves — and neither
     * a reply to somebody else nor a line replying to nothing does.
     */
    @Test
    public void aReplyToThisPlayersLinePingsThem() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        com.ninuna.losttales.chat.ChatReplyReference toPlayer =
                com.ninuna.losttales.chat.ChatReplyReference.of(1234L,
                        "Arathorn", "hello").withHead(player, true, "");
        assertTrue(LostTalesChatPresentation.repliesTo(toPlayer, player));
        com.ninuna.losttales.chat.ChatReplyReference toOther =
                com.ninuna.losttales.chat.ChatReplyReference.of(1234L,
                        "Legolas", "hello").withHead(other, true, "");
        assertFalse(LostTalesChatPresentation.repliesTo(toOther, player));
        assertTrue(LostTalesChatPresentation.repliesTo(toOther, other));
        assertFalse(LostTalesChatPresentation.repliesTo(
                com.ninuna.losttales.chat.ChatReplyReference.NONE, player));
        // A quote told no head, of a line this client does not hold,
        // names nobody.
        assertFalse(LostTalesChatPresentation.repliesTo(
                com.ninuna.losttales.chat.ChatReplyReference.of(99L,
                        "Arathorn", "hello"), player));
    }

    /**
     * A console entry's actor is always a mention, of an account this
     * client cannot place too — one long offline — so an older entry
     * names its player as it did when it was new; this player's own name
     * pings them, and the Server names nobody.
     */
    @Test
    public void aConsoleEntrysActorIsAlwaysAMention() {
        boolean[] mentioned = new boolean[1];
        IChatComponent actor = LostTalesChatPresentation.actorMention(
                java.util.Collections.<String>emptyList(), "Player531",
                null, mentioned);
        assertEquals("@Player531", actor.getUnformattedTextForChat());
        ChatMentionMarker.Data marker = ChatMentionMarker.decode(actor);
        assertNotNull(marker);
        assertEquals("Player531", marker.account);
        assertFalse(mentioned[0]);
        IChatComponent own = LostTalesChatPresentation.actorMention(
                java.util.Arrays.asList("Player531"), "Player531", null,
                mentioned);
        assertEquals("@Player531", own.getUnformattedTextForChat());
        assertTrue(mentioned[0]);
        IChatComponent server = LostTalesChatPresentation.actorMention(
                java.util.Collections.<String>emptyList(), "Server", null,
                new boolean[1]);
        assertEquals("Server", server.getUnformattedTextForChat());
        assertNull(ChatMentionMarker.decode(server));
    }

    /**
     * An entry about a player long gone still names them as the server
     * recorded them: the mention carries their account's id, so it opens
     * their card and menu, and wears the one colour every mention of a
     * player wears, whatever roles they hold.
     */
    @Test
    public void aRecordedActorKeepsTheirCardInTheMentionColour() {
        UUID id = UUID.randomUUID();
        IChatComponent operator = LostTalesChatPresentation.actorMention(
                java.util.Collections.<String>emptyList(), "Player843",
                com.ninuna.losttales.chat.ChatNamedPlayer.account(id,
                        "Player843"), new boolean[1]);
        ChatMentionMarker.Data marker = ChatMentionMarker.decode(operator);
        assertNotNull(marker);
        assertNotNull(marker.recorded);
        assertEquals(id, marker.recorded.getPlayerId());
        assertEquals(ChatMentionColors.PLAYER_RGB, marker.color);
        assertEquals(LostTalesColors.rgb(LostTalesColors.HONEY),
                ChatMentionColors.PLAYER_RGB);
    }

    /**
     * A player is named as the channel presents people: the Server
     * Console and every out-of-character channel by the account, even
     * where the record says which character they were playing; an
     * in-character channel by that character, as the record says.
     */
    @Test
    public void aNameFollowsItsChannelsPresentation() {
        com.ninuna.losttales.chat.ChatNamedPlayer steveAsAragorn =
                new com.ninuna.losttales.chat.ChatNamedPlayer(UUID.randomUUID(),
                        "Steve", UUID.randomUUID(), "Aragorn", "");
        IChatComponent actor = LostTalesChatPresentation.actorMention(
                java.util.Collections.<String>emptyList(), "Steve",
                steveAsAragorn, new boolean[1]);
        assertEquals("@Steve", actor.getUnformattedTextForChat());
        java.util.List<com.ninuna.losttales.chat.ChatNamedPlayer> named =
                java.util.Collections.singletonList(steveAsAragorn);
        assertEquals("@Steve", LostTalesChatPresentation.asMentionName("Steve",
                ChatChannel.OOC, java.util.Collections.<String>emptyList(),
                new boolean[1], named).getUnformattedTextForChat());
        assertEquals("@Aragorn", LostTalesChatPresentation.asMentionName("Aragorn",
                ChatChannel.ALL, java.util.Collections.<String>emptyList(),
                new boolean[1], named).getUnformattedTextForChat());
        assertEquals("@Aragorn", LostTalesChatPresentation.asMentionName("Steve",
                ChatChannel.ALL, java.util.Collections.<String>emptyList(),
                new boolean[1], named).getUnformattedTextForChat());
    }

    /**
     * A mention keeps reading as one after its player has gone: a name
     * this client cannot place is placed by the players the server
     * recorded the message as naming, in the one mention colour, and a
     * name nobody recorded stays text.
     */
    @Test
    public void aRecordedMentionOutlivesItsPlayer() {
        boolean originalPings = LostTalesConfig.enableChatPings;
        LostTalesConfig.enableChatPings = true;
        try {
            UUID player = UUID.randomUUID();
            UUID character = UUID.randomUUID();
            LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                    ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                    "RangerOfTheNorth", "", 0x55AA55, 0x336633,
                    "hi @Aragorn and @Nobody", 123456789L,
                    "losttales:human_ranger_male_2").withNamedPlayers(
                            java.util.Collections.singletonList(
                                    new com.ninuna.losttales.chat
                                            .ChatNamedPlayer(player,
                                                    "Player531", character,
                                                    "Aragorn",
                                                    "human/male/3")));
            IChatComponent aragorn = null;
            IChatComponent nobody = null;
            for (IChatComponent part : bodyOf(LostTalesChatPresentation.build(
                    packet, ChatTab.of(ChatChannel.ALL), new int[0], false,
                    ChatBodyKind.MESSAGE))) {
                String text = part.getUnformattedTextForChat();
                if ("@Aragorn".equals(text)) {
                    aragorn = part;
                } else if (text.contains("@Nobody")) {
                    nobody = part;
                }
            }
            assertNotNull(aragorn);
            ChatMentionMarker.Data marker = ChatMentionMarker.decode(aragorn);
            assertNotNull(marker);
            assertEquals("Player531", marker.account);
            assertEquals(ChatMentionColors.PLAYER_RGB, marker.color);
            // Its card is the player as the line recorded them: the
            // character, its head and its name, named as a live card
            // names a player.
            LostTalesChatHoverCard.Target card =
                    LostTalesChatHoverCard.recordedTarget(null,
                            marker.recorded);
            assertEquals(player, card.playerId);
            assertEquals(character, card.characterId);
            assertEquals("human/male/3", card.skinId);
            assertEquals("Aragorn", card.identityName);
            assertEquals("Player531", card.accountName);
            assertEquals(LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                    card.nameColor);
            assertNotNull(nobody);
            assertNull(ChatMentionMarker.decode(nobody));
        } finally {
            LostTalesConfig.enableChatPings = originalPings;
        }
    }

    /** The line's text before its body break. */
    private static String headerOf(IChatComponent message) {
        StringBuilder header = new StringBuilder();
        for (Object value : message) {
            IChatComponent part = (IChatComponent)value;
            if (ChatLayoutMarker.isBodyBreak(part)) {
                break;
            }
            header.append(part.getUnformattedTextForChat());
        }
        return header.toString();
    }

    /** The label the line's body break carries, or null for the chevron. */
    private static String labelOf(IChatComponent message) {
        for (Object value : message) {
            IChatComponent part = (IChatComponent)value;
            if (ChatLayoutMarker.isBodyBreak(part)) {
                return ChatLayoutMarker.bodyLabel(part);
            }
        }
        return null;
    }

    /** The parts after the line's body break. */
    private static java.util.List<IChatComponent> bodyOf(
            IChatComponent message) {
        java.util.List<IChatComponent> body =
                new java.util.ArrayList<IChatComponent>();
        boolean started = false;
        for (Object value : message) {
            IChatComponent part = (IChatComponent)value;
            if (ChatLayoutMarker.isBodyBreak(part)) {
                started = true;
                continue;
            }
            if (started) {
                body.add(part);
            }
        }
        return body;
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
