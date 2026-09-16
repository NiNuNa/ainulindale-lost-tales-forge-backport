package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatPacketTest {


    @Test
    public void sendRequestRoundTripsAndRejectsTrailingData() {
        LostTalesChatSendPacket original =
                new LostTalesChatSendPacket(
                        ChatChannel.PARTY, "Meet at the western gate.");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded =
                new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.PARTY, decoded.getChannel());
        assertEquals("Meet at the western gate.", decoded.getMessage());
        assertTrue(decoded.getReferences().isEmpty());

        ByteBuf trailing = Unpooled.buffer();
        original.toBytes(trailing);
        trailing.writeByte(1);
        LostTalesChatSendPacket rejected =
                new LostTalesChatSendPacket();
        rejected.fromBytes(trailing);
        assertTrue(rejected.isMalformed());
    }

    @Test
    public void sendRequestCarriesTheAskedForIdentity() {
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT,
                new LostTalesChatSendPacket(ChatChannel.ALL, "hello")
                        .getIdentityKind());
        java.util.UUID characterId = java.util.UUID.randomUUID();
        LostTalesChatSendPacket original = new LostTalesChatSendPacket(
                ChatChannel.OOC, "hello", null, "",
                LostTalesChatSendPacket.IDENTITY_CHARACTER, characterId);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesChatSendPacket.IDENTITY_CHARACTER,
                decoded.getIdentityKind());
        assertEquals(characterId, decoded.getIdentityCharacterId());

        LostTalesChatSendPacket account = new LostTalesChatSendPacket(
                ChatChannel.ALL, "hello", null, "",
                LostTalesChatSendPacket.IDENTITY_ACCOUNT, null);
        ByteBuf accountBuffer = Unpooled.buffer();
        account.toBytes(accountBuffer);
        LostTalesChatSendPacket decodedAccount =
                new LostTalesChatSendPacket();
        decodedAccount.fromBytes(accountBuffer);
        assertFalse(decodedAccount.isMalformed());
        assertEquals(LostTalesChatSendPacket.IDENTITY_ACCOUNT,
                decodedAccount.getIdentityKind());
        assertNull(decodedAccount.getIdentityCharacterId());

        // A kind the catalogue does not know is malformed on arrival.
        // Where the kind is written is found rather than counted to:
        // the same request encoded under two kinds, neither of which
        // names a character, differs in exactly one byte, and that byte
        // is the kind. Counting back from the end instead would have to
        // be recounted every time the layout grows a field.
        ByteBuf badKind = Unpooled.buffer();
        account.toBytes(badKind);
        ByteBuf defaultKind = Unpooled.buffer();
        new LostTalesChatSendPacket(ChatChannel.ALL, "hello", null, "",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null)
                .toBytes(defaultKind);
        badKind.setByte(onlyDifference(badKind, defaultKind), 9);
        LostTalesChatSendPacket rejected = new LostTalesChatSendPacket();
        rejected.fromBytes(badKind);
        assertTrue(rejected.isMalformed());
    }

    /**
     * The one index at which two encodings differ. Fails the test
     * rather than guessing if they differ anywhere else, since then the
     * byte found would not be the one meant.
     */
    private static int onlyDifference(ByteBuf first, ByteBuf second) {
        assertEquals("the two encodings should be the same length",
                first.writerIndex(), second.writerIndex());
        int found = -1;
        for (int index = 0; index < first.writerIndex(); index++) {
            if (first.getByte(index) != second.getByte(index)) {
                assertEquals("exactly one byte should differ", -1, found);
                found = index;
            }
        }
        assertTrue("no byte differed", found >= 0);
        return found;
    }

    @Test
    public void sendRequestCarriesBoundedShareReferences() {
        LostTalesChatSendPacket original = new LostTalesChatSendPacket(
                ChatChannel.ALL, "see [i:Sword] [m:Bree] [i:Bow]",
                Arrays.asList(ChatShareReference.item(4),
                        ChatShareReference.marker("losttales:bree"),
                        ChatShareReference.unresolved(ChatShareKind.ITEM)));
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(3, decoded.getReferences().size());
        assertEquals(4, decoded.getReferences().get(0).getSlot());
        assertEquals("losttales:bree",
                decoded.getReferences().get(1).getMarkerId());
        assertEquals(ChatShareKind.MARKER,
                decoded.getReferences().get(1).getKind());
        assertFalse(decoded.getReferences().get(2).isResolved());

        boolean rejectedSlot = false;
        try {
            ChatShareReference.item(40);
        } catch (IllegalArgumentException expected) {
            rejectedSlot = true;
        }
        assertTrue(rejectedSlot);

        // A hand-built payload with an out-of-range slot is discarded.
        ByteBuf forged = Unpooled.buffer();
        new LostTalesChatSendPacket(ChatChannel.ALL, "[i:Sword]")
                .toBytes(forged);
        forged.writerIndex(forged.writerIndex() - 1);
        forged.writeByte(1);
        forged.writeByte('i');
        forged.writeByte(77);
        LostTalesChatSendPacket rejected = new LostTalesChatSendPacket();
        rejected.fromBytes(forged);
        assertTrue(rejected.isMalformed());
        assertTrue(rejected.getReferences().isEmpty());
    }

    @Test
    public void presentationCarriesValidatedShowcasesAndRejectsBadOnes() {
        byte[] data = new byte[] {31, -117, 8, 0, 1, 2, 3, 4};
        LostTalesChatMessagePacket original =
                new LostTalesChatMessagePacket(
                        ChatChannel.OOC, UUID.randomUUID(), "Steve",
                        "Steve", "", 0xFFFFFF, 0xFFFFFF,
                        "look [i:Sword] near [m:Bree]", 5L, "",
                        Arrays.asList(ChatShowcase.item(0, data),
                                ChatShowcase.marker(1, "losttales:bree",
                                        "Bree", "town", "orange", 100,
                                        512.5D, -384.0D)));
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getShowcases().size());
        assertEquals(ChatShareKind.ITEM,
                decoded.getShowcases().get(0).getKind());
        assertTrue(Arrays.equals(data,
                decoded.getShowcases().get(0).getStackData()));
        ChatShowcase marker = decoded.getShowcases().get(1);
        assertEquals(ChatShareKind.MARKER, marker.getKind());
        assertEquals("losttales:bree", marker.getMarkerId());
        assertEquals("Bree", marker.getMarkerName());
        assertEquals("town", marker.getMarkerIcon());
        assertEquals(100, marker.getMarkerDimension());
        assertEquals(512.5D, marker.getMarkerX(), 0.0D);
        assertEquals(-384.0D, marker.getMarkerZ(), 0.0D);

        // A showcase whose kind does not match its token is refused.
        boolean rejectedKind = false;
        try {
            new LostTalesChatMessagePacket(
                    ChatChannel.OOC, UUID.randomUUID(), "Steve",
                    "Steve", "", 0xFFFFFF, 0xFFFFFF,
                    "only [m:Bree]", 5L, "",
                    Arrays.asList(ChatShowcase.item(0, data)));
        } catch (IllegalArgumentException expected) {
            rejectedKind = true;
        }
        assertTrue(rejectedKind);

        boolean rejectedIndex = false;
        try {
            new LostTalesChatMessagePacket(
                    ChatChannel.OOC, UUID.randomUUID(), "Steve",
                    "Steve", "", 0xFFFFFF, 0xFFFFFF,
                    "only [i:Sword]", 5L, "",
                    Arrays.asList(ChatShowcase.item(1, data)));
        } catch (IllegalArgumentException expected) {
            rejectedIndex = true;
        }
        assertTrue(rejectedIndex);

        boolean rejectedSize = false;
        try {
            ChatShowcase.item(0, new byte[ChatShowcase.MAX_STACK_BYTES + 1]);
        } catch (IllegalArgumentException expected) {
            rejectedSize = true;
        }
        assertTrue(rejectedSize);

        boolean rejectedCoordinate = false;
        try {
            ChatShowcase.marker(0, "id", "Name", "", "", 0,
                    Double.NaN, 0.0D);
        } catch (IllegalArgumentException expected) {
            rejectedCoordinate = true;
        }
        assertTrue(rejectedCoordinate);
    }

    @Test
    public void whisperPacketsCarryTheirTargetAndPartner() {
        LostTalesChatSendPacket send = new LostTalesChatSendPacket(
                ChatChannel.WHISPER, "psst", null, " Steve ");
        ByteBuf buffer = Unpooled.buffer();
        send.toBytes(buffer);
        LostTalesChatSendPacket decodedSend = new LostTalesChatSendPacket();
        decodedSend.fromBytes(buffer);
        assertFalse(decodedSend.isMalformed());
        assertEquals("Steve", decodedSend.getTarget());
        assertEquals(ChatChannel.WHISPER, decodedSend.getChannel());
        // A whisper without a target is refused; other channels need none.
        boolean rejected = false;
        try {
            new LostTalesChatSendPacket(ChatChannel.WHISPER, "psst", null, "");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals("", new LostTalesChatSendPacket(ChatChannel.OOC, "hi")
                .getTarget());

        LostTalesChatMessagePacket message = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, UUID.randomUUID(), "Alex", "Alex", "",
                0xFFFFFF, 0xFFFFFF, "psst", 5L, "", null, "", "Steve");
        buffer = Unpooled.buffer();
        message.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("Steve", decoded.getPartner());
        assertEquals("", new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Alex", "Alex", "",
                0xFFFFFF, 0xFFFFFF, "hi", 5L, "").getPartner());
    }


    @Test
    public void shareTokensCountAsOneVisibleCharacter() {
        StringBuilder filler = new StringBuilder();
        for (int index = 0;
             index < ChatMessageValidator.MAX_CHARACTERS - 1; index++) {
            filler.append('x');
        }
        String withToken = filler + "[m:Northgate Test City]";
        assertEquals(ChatMessageValidator.MAX_CHARACTERS,
                ChatMessageValidator.visibleLength(withToken));
        assertTrue(ChatMessageValidator.isValid(withToken));
        assertFalse(ChatMessageValidator.isValid(withToken + "y"));
        assertTrue(withToken.length() > ChatMessageValidator.MAX_CHARACTERS);
        assertEquals(5, ChatMessageValidator.visibleLength("ab[i:Sword]cd"));

        LostTalesChatSendPacket packet = new LostTalesChatSendPacket(
                ChatChannel.OOC, withToken,
                Arrays.asList(ChatShareReference.marker("losttales:x")));
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(withToken, decoded.getMessage());
    }

    /**
     * The hard limit on a client-to-server custom payload in 1.7.10:
     * {@code C17PacketCustomPayload} refuses anything from this size up,
     * so the largest request a client can legally build has to stay
     * under it with room to spare for the channel framing FML adds.
     */
    private static final int CLIENT_BOUND_PAYLOAD_LIMIT = 32767;

    /** One share token per index, alternating kinds, all distinct. */
    private static String tokensFor(int count) {
        StringBuilder message = new StringBuilder();
        for (int index = 0; index < count; index++) {
            message.append(index % 2 == 0 ? "[i:item" : "[m:place")
                    .append(index).append("] ");
        }
        return message.toString().trim();
    }

    /**
     * A message carries as many insertions as it has tokens for, of
     * either kind, each keyed to its own token; nothing about the count
     * is fixed but the defensive ceiling.
     */
    @Test
    public void messagesCarryAVariableNumberOfInsertions() {
        byte[] data = { 1, 2, 3 };
        for (int count : new int[] { 0, 1, 5,
                ChatShareTokenParser.MAX_TOKENS }) {
            String message = count == 0 ? "nothing shared"
                    : tokensFor(count);
            List<ChatShowcase> showcases = new ArrayList<ChatShowcase>();
            for (int index = 0; index < count; index++) {
                showcases.add(index % 2 == 0
                        ? ChatShowcase.item(index, data)
                        : ChatShowcase.marker(index, "losttales:m" + index,
                                "Place " + index, "star", "gold", 0,
                                index, -index));
            }
            LostTalesChatMessagePacket original =
                    new LostTalesChatMessagePacket(ChatChannel.ALL,
                            UUID.randomUUID(), "Aldric", "Steve", "",
                            0xFFFFFF, 0xFFFFFF, message, 1L, "",
                            showcases);
            ByteBuf buffer = Unpooled.buffer();
            original.toBytes(buffer);
            LostTalesChatMessagePacket decoded =
                    new LostTalesChatMessagePacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            assertEquals(count, decoded.getShowcases().size());
            for (int index = 0; index < count; index++) {
                assertEquals(index,
                        decoded.getShowcases().get(index).getTokenIndex());
                assertEquals(index % 2 == 0 ? ChatShareKind.ITEM
                                : ChatShareKind.MARKER,
                        decoded.getShowcases().get(index).getKind());
            }
        }
    }

    /**
     * The largest request a client may legally build fits the custom
     * payload it travels in, so the insertion ceiling can never be
     * raised past what the wire carries without this failing.
     */
    @Test
    public void theLargestRequestFitsTheClientBoundPayload() {
        StringBuilder markerId = new StringBuilder();
        while (markerId.length()
                < ChatShareReference.MAX_MARKER_ID_BYTES) {
            markerId.append('m');
        }
        List<ChatShareReference> references =
                new ArrayList<ChatShareReference>();
        for (int index = 0; index < ChatShareTokenParser.MAX_TOKENS;
                index++) {
            references.add(ChatShareReference.marker(markerId.toString()));
        }
        StringBuilder message =
                new StringBuilder(tokensFor(
                        ChatShareTokenParser.MAX_TOKENS));
        while (ChatMessageValidator.isValid(message.toString() + "x")) {
            message.append('x');
        }
        LostTalesChatSendPacket packet = new LostTalesChatSendPacket(
                ChatChannel.OOC, message.toString(), references,
                "", LostTalesChatSendPacket.IDENTITY_CHARACTER,
                UUID.randomUUID());
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        assertTrue("request of " + buffer.readableBytes()
                        + " bytes exceeds the payload limit",
                buffer.readableBytes() < CLIENT_BOUND_PAYLOAD_LIMIT);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatShareTokenParser.MAX_TOKENS,
                decoded.getReferences().size());
    }

    /**
     * What the delivered line costs is bounded by bytes, not by count:
     * a line of markers is cheap however many it carries, and a line of
     * full stacks stops being attached once the set reaches the budget.
     * The line goes to every recipient, so this is the bound that
     * matters.
     */
    @Test
    public void theDeliveredLineIsBoundedByItsShowcaseBudget() {
        byte[] stack = new byte[ChatShowcase.MAX_STACK_BYTES];
        Arrays.fill(stack, (byte)7);
        List<ChatShowcase> withinBudget = new ArrayList<ChatShowcase>();
        int index = 0;
        while (index < ChatShareTokenParser.MAX_TOKENS
                && ChatShowcase.serializedBytes(withinBudget)
                        + ChatShowcase.item(index, stack).serializedBytes()
                                <= ChatShowcase.MAX_TOTAL_BYTES) {
            withinBudget.add(ChatShowcase.item(index, stack));
            index += 2;
        }
        assertTrue(withinBudget.size() > 1);
        String message = tokensFor(ChatShareTokenParser.MAX_TOKENS);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Aldric", "Steve", "",
                0xFFFFFF, 0xFFFFFF, message, 1L, "", withinBudget);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        assertTrue("delivered line of " + buffer.readableBytes()
                        + " bytes is larger than budgeted",
                buffer.readableBytes() < 32 * 1024);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(withinBudget.size(), decoded.getShowcases().size());

        // One stack more than the budget holds is refused outright.
        List<ChatShowcase> overBudget =
                new ArrayList<ChatShowcase>(withinBudget);
        overBudget.add(ChatShowcase.item(index, stack));
        try {
            new LostTalesChatMessagePacket(ChatChannel.ALL,
                    UUID.randomUUID(), "Aldric", "Steve", "", 0xFFFFFF,
                    0xFFFFFF, message, 1L, "", overBudget);
            fail("a line over the showcase budget was accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }
    }


    /**
     * The message id survives the wire, and a whisper's two copies carry
     * the same one: it is one message, and anything naming it later has
     * to name it the same to both parties.
     */
    @Test
    public void theMessageIdRoundTripsAndIsSharedByBothWhisperCopies() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket original = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "Alex", 0,
                true, id);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(id, decoded.getMessageId());

        // The copy the other party is sent differs only in the partner.
        LostTalesChatMessagePacket other = new LostTalesChatMessagePacket(
                decoded.getChannel(), decoded.getSenderId(),
                decoded.getIdentityName(), decoded.getAccountName(),
                decoded.getTitle(), decoded.getTitleColor(),
                decoded.getNameColor(), decoded.getMessage(),
                decoded.getTimestampMillis(), decoded.getSkinId(),
                decoded.getShowcases(), decoded.getFactionName(), "Steve",
                decoded.getRoles(), decoded.isAccountLine(),
                decoded.getMessageId());
        assertEquals(id, other.getMessageId());
    }

    /** A line the server never named carries no id, and still travels. */
    @Test
    public void anUnnamedLineTravelsWithoutAnId() {
        LostTalesChatMessagePacket original = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Aldric", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatMessageIds.NONE, decoded.getMessageId());
    }

    /**
     * Negative ids are the receiving client's own, for lines it wrote
     * itself; one arriving over the wire is malformed, not a message.
     */
    @Test
    public void aNegativeIdOffTheWireIsMalformed() {
        LostTalesChatMessagePacket original = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Aldric", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", 0, false,
                -7L);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals(0, buffer.readableBytes());
    }

    /** The quote travels with the reply, so every recipient sees one. */
    @Test
    public void aReplyCarriesItsQuoteOverTheWire() {
        long original = ChatMessageIdAllocator.next();
        ChatReplyReference reply = ChatReplyReference.of(original, "Aldric",
                "meet me at the gate");
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "on my way", 1L, "", null, "", "", 0,
                false, ChatMessageIdAllocator.next(), reply);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getReply().exists());
        assertEquals(original, decoded.getReply().getMessageId());
        assertEquals("Aldric", decoded.getReply().getAuthor());
        assertEquals("meet me at the gate",
                decoded.getReply().getExcerpt());
        assertEquals("a quote told no colour keeps saying so",
                ChatReplyReference.NO_COLOR,
                decoded.getReply().getAuthorColor());
    }

    /**
     * The quoted author's own name colour travels too. A client holds
     * name colours for accounts it has been told about; an in-character
     * author is a character, which is not on that list, so the quote
     * would otherwise be the one place that name is drawn grey.
     */
    @Test
    public void aQuoteCarriesTheAuthorsNameColour() {
        long original = ChatMessageIdAllocator.next();
        ChatReplyReference reply = ChatReplyReference.of(original, "Aldric",
                "meet me at the gate", 0x4A90D9);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "on my way", 1L, "", null, "", "", 0,
                false, ChatMessageIdAllocator.next(), reply);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(0x4A90D9, decoded.getReply().getAuthorColor());
        assertEquals("Aldric", decoded.getReply().getAuthor());
    }

    /** The quoted sender's head travels with the quote, so it is drawn whether or not the reader holds the line. */
    @Test
    public void aQuoteCarriesTheQuotedSendersHead() {
        long original = ChatMessageIdAllocator.next();
        UUID quoted = UUID.randomUUID();
        ChatReplyReference reply = ChatReplyReference.of(original, "Aldric",
                "meet me at the gate", 0x4A90D9)
                .withHead(quoted, false, "skin-7");
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "on my way", 1L, "", null, "", "", 0,
                false, ChatMessageIdAllocator.next(), reply);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getReply().hasHead());
        assertEquals(quoted, decoded.getReply().getSenderId());
        assertFalse(decoded.getReply().isAccountLine());
        assertEquals("skin-7", decoded.getReply().getSkinId());
        assertEquals(0x4A90D9, decoded.getReply().getAuthorColor());
        // The head survives every copy a client makes of the line.
        assertTrue(decoded.withMessage("changed").getReply().hasHead());
        // A quote of nothing wears no head, whatever it is handed.
        assertFalse(ChatReplyReference.NONE.withHead(quoted, true, "")
                .hasHead());
    }

    /**
     * A server line carries its own component and the players it
     * names, so a replay shows it as the live line was shown; a
     * player's line may carry neither.
     */
    @Test
    public void aServerLineCarriesItsComponentAndNamedPlayers() {
        String json = "{\"translate\":\"chat.type.achievement\","
                + "\"with\":[\"Steve\",{\"translate\":\"achievement.openInventory\","
                + "\"color\":\"green\"}]}";
        List<ChatNamedPlayer> named = Arrays.asList(
                new ChatNamedPlayer("Steve", "Aldric", 0x4A90D9),
                new ChatNamedPlayer("Alex", "", 0xFFFFFF));
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                "Server", "Server", "", 0xFFFFFF, 0xFFFFFF,
                "Steve has just earned the achievement [Taking Inventory]",
                1L, "", null, "", "", 0, true, ChatMessageIdAllocator.next(),
                ChatReplyReference.NONE).withServerBody(json, named);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(json, decoded.getBodyJson());
        assertEquals(2, decoded.getNamedPlayers().size());
        assertEquals(new ChatNamedPlayer("Steve", "Aldric", 0x4A90D9),
                decoded.getNamedPlayers().get(0));
        assertEquals("a nameless identity is the account",
                "Alex", decoded.getNamedPlayers().get(1).getIdentityName());
        assertEquals(json, decoded.withNameColor(0x123456).getBodyJson());
        assertEquals(2, decoded.withScope("").getNamedPlayers().size());

        // A player's line keeps no component, whatever it is handed.
        LostTalesChatMessagePacket player = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "").withServerBody(json, named);
        assertEquals("", player.getBodyJson());
        assertEquals(2, player.getNamedPlayers().size());
        ByteBuf playerBuffer = Unpooled.buffer();
        player.toBytes(playerBuffer);
        LostTalesChatMessagePacket playerDecoded =
                new LostTalesChatMessagePacket();
        playerDecoded.fromBytes(playerBuffer);
        assertFalse(playerDecoded.isMalformed());
        assertEquals("", playerDecoded.getBodyJson());

        // A component too large to carry is left behind, not cut.
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= LostTalesChatMessagePacket.MAX_BODY_BYTES) {
            huge.append("{\"text\":\"x\"},");
        }
        assertEquals("", packet.withServerBody(huge.toString(), null)
                .getBodyJson());
    }

    /** A payload written before the tail decodes as a line with nothing appended. */
    @Test
    public void anOlderLayoutWithoutTheTailStillDecodes() {
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "");
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        // The older layout ended where the quote block starts: nothing
        // for a line that quotes nothing. An empty string is one byte
        // of length; the tail is a head (17 and a flag), a skin, a
        // component and a count of named players.
        // The tab a command's answer is filed under is one more byte
        // of length, appended last.
        int tail = 17 + 1 + 1 + 1 + 4 + 4 + 1;
        int quoteBlock = 1 + 1 + 4;
        ByteBuf older = Unpooled.buffer();
        older.writeBytes(buffer, buffer.readableBytes() - tail - quoteBlock);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(older);
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.getReply().exists());
        assertEquals("", decoded.getBodyJson());
        assertTrue(decoded.getNamedPlayers().isEmpty());
    }

    /** The reactions ride on the line a reader is handed, and survive its copies. */
    @Test
    public void aLineCarriesItsReactionsAsItsReaderSeesThem() {
        ChatReactionSummary reactions = new ChatReactionSummary(Arrays.asList(
                new ChatReactionSummary.Reaction("smile", 3, true,
                        Arrays.asList("Aldric", "Beren")),
                new ChatReactionSummary.Reaction("joy", 1, false,
                        Arrays.asList("Nils"))));
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", 0, false,
                ChatMessageIdAllocator.next(), ChatReplyReference.NONE)
                .withReactions(reactions);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getReactions().getReactions().size());
        ChatReactionSummary.Reaction smile = decoded.getReactions().find("smile");
        assertEquals(3, smile.count);
        assertTrue(smile.mine);
        assertEquals(Arrays.asList("Aldric", "Beren"), smile.names);
        assertEquals(1, decoded.withMessage("edited").getReactions()
                .find("joy").count);
    }

    @Test
    public void aReactionRequestNamesAKnownEmojiAndAServerMessage() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatReactPacket request = new LostTalesChatReactPacket(id,
                "smile", true);
        ByteBuf buffer = Unpooled.buffer();
        request.toBytes(buffer);
        LostTalesChatReactPacket decoded = new LostTalesChatReactPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(id, decoded.getMessageId());
        assertEquals("smile", decoded.getEmoji());
        assertTrue(decoded.isAdd());

        ByteBuf forged = Unpooled.buffer();
        forged.writeLong(id);
        LostTalesPacketCodec.writeUtf8String(forged, "not_an_emoji", 64);
        forged.writeBoolean(true);
        LostTalesChatReactPacket refused = new LostTalesChatReactPacket();
        refused.fromBytes(forged);
        assertTrue(refused.isMalformed());
    }

    @Test
    public void aReactionSyncRoundTripsAndRefusesAnEmojiTwice() {
        long id = ChatMessageIdAllocator.next();
        ChatReactionSummary reactions = new ChatReactionSummary(Arrays.asList(
                new ChatReactionSummary.Reaction("smile", 2, false,
                        Arrays.asList("Aldric"))));
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatReactionSyncPacket(id, reactions).toBytes(buffer);
        LostTalesChatReactionSyncPacket decoded =
                new LostTalesChatReactionSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(id, decoded.getMessageId());
        assertEquals(2, decoded.getReactions().find("smile").count);

        ByteBuf twice = Unpooled.buffer();
        twice.writeLong(id);
        twice.writeInt(2);
        for (int index = 0; index < 2; index++) {
            LostTalesPacketCodec.writeUtf8String(twice, "smile", 64);
            twice.writeInt(1);
            twice.writeBoolean(false);
            twice.writeInt(0);
        }
        LostTalesChatReactionSyncPacket refused =
                new LostTalesChatReactionSyncPacket();
        refused.fromBytes(twice);
        assertTrue(refused.isMalformed());
        assertTrue(refused.getReactions().isEmpty());
    }

    /** An emoji the registry lacks rides the reaction wire by its foreign key. */
    @Test
    public void aForeignEmojiRidesTheReactionWireAsItsKey() {
        long id = ChatMessageIdAllocator.next();
        String parrot = "Party_Parrot:123456789012345678";
        String family = "👨‍👩‍👧";
        ChatReactionSummary reactions = new ChatReactionSummary(Arrays.asList(
                new ChatReactionSummary.Reaction(parrot, 2, true,
                        Arrays.asList("Nils", "Aldric")),
                new ChatReactionSummary.Reaction(family, 1, false,
                        Arrays.asList("Nils"))));
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatReactionSyncPacket(id, reactions).toBytes(buffer);
        LostTalesChatReactionSyncPacket decoded =
                new LostTalesChatReactionSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getReactions().find(parrot).count);
        assertTrue(decoded.getReactions().find(parrot).mine);
        assertEquals(Arrays.asList("Nils"),
                decoded.getReactions().find(family).names);

        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", 0, false,
                ChatMessageIdAllocator.next(), ChatReplyReference.NONE)
                .withReactions(reactions);
        ByteBuf lineBuffer = Unpooled.buffer();
        line.toBytes(lineBuffer);
        LostTalesChatMessagePacket decodedLine = new LostTalesChatMessagePacket();
        decodedLine.fromBytes(lineBuffer);
        assertFalse(decodedLine.isMalformed());
        assertNotNull(decodedLine.getReactions().find(family));

        ByteBuf forged = Unpooled.buffer();
        forged.writeLong(id);
        forged.writeInt(1);
        LostTalesPacketCodec.writeUtf8String(forged, "partyparrot", 64);
        forged.writeInt(1);
        forged.writeBoolean(false);
        forged.writeInt(0);
        LostTalesChatReactionSyncPacket refused =
                new LostTalesChatReactionSyncPacket();
        refused.fromBytes(forged);
        assertTrue("a bare custom name is no key", refused.isMalformed());
    }

    @Test
    public void aReactionRequestMayNameAForeignKeyAndNothingElse() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatReactPacket request = new LostTalesChatReactPacket(id,
                "partyparrot:556", true);
        ByteBuf buffer = Unpooled.buffer();
        request.toBytes(buffer);
        LostTalesChatReactPacket decoded = new LostTalesChatReactPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("partyparrot:556", decoded.getEmoji());

        String[] forgeries = {"partyparrot:0556", "😄",
                "partyparrot", "🦄 "};
        for (String forgery : forgeries) {
            ByteBuf forged = Unpooled.buffer();
            forged.writeLong(id);
            LostTalesPacketCodec.writeUtf8String(forged, forgery, 64);
            forged.writeBoolean(true);
            LostTalesChatReactPacket refused = new LostTalesChatReactPacket();
            refused.fromBytes(forged);
            assertTrue(forgery, refused.isMalformed());
        }
    }

    /** An ordinary line replies to nothing and pays nothing for it. */
    @Test
    public void anOrdinaryLineCarriesNoQuote() {
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "");
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        int size = buffer.readableBytes();
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.getReply().exists());
        assertEquals(ChatMessageIds.NONE,
                decoded.getReply().getMessageId());
        assertEquals("", decoded.getReply().getAuthor());

        // A quote costs its own bytes only when there is one.
        ByteBuf quoted = Unpooled.buffer();
        new LostTalesChatMessagePacket(ChatChannel.ALL, UUID.randomUUID(),
                "Beren", "Steve", "", 0xFFFFFF, 0xFFFFFF, "hello", 1L, "",
                null, "", "", 0, false, ChatMessageIds.NONE,
                ChatReplyReference.of(ChatMessageIdAllocator.next(),
                        "Aldric", "hi")).toBytes(quoted);
        assertTrue(quoted.readableBytes() > size);
    }

    /**
     * A line nobody named is quoted by its author and words: the quote
     * crosses both wires whole, names no message, and a request may not
     * carry both a quote and an id, nor words without an author.
     */
    @Test
    public void anUnnamedLineIsQuotedByItsWordsOverTheWire() {
        LostTalesChatSendPacket request = new LostTalesChatSendPacket(
                ChatChannel.ALL, "well done", null, "",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIds.NONE, "", 0L, null, "System",
                "Bilbo has just earned the achievement [Taking Inventory]");
        ByteBuf buffer = Unpooled.buffer();
        request.toBytes(buffer);
        LostTalesChatSendPacket decodedRequest = new LostTalesChatSendPacket();
        decodedRequest.fromBytes(buffer);
        assertFalse(decodedRequest.isMalformed());
        assertEquals(ChatMessageIds.NONE, decodedRequest.getReplyToMessageId());
        assertEquals("System", decodedRequest.getQuoteAuthor());
        assertEquals("Bilbo has just earned the achievement [Taking Inventory]",
                decodedRequest.getQuoteExcerpt());
        // A request without a quote reads back without one.
        ByteBuf plain = Unpooled.buffer();
        new LostTalesChatSendPacket(ChatChannel.ALL, "hello").toBytes(plain);
        LostTalesChatSendPacket decodedPlain = new LostTalesChatSendPacket();
        decodedPlain.fromBytes(plain);
        assertFalse(decodedPlain.isMalformed());
        assertEquals("", decodedPlain.getQuoteAuthor());
        assertEquals("", decodedPlain.getQuoteExcerpt());
        try {
            new LostTalesChatSendPacket(ChatChannel.ALL, "hello", null, "",
                    LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                    ChatMessageIdAllocator.next(), "", 0L, null, "System",
                    "x");
            fail("a quote and an id were both accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }
        try {
            new LostTalesChatSendPacket(ChatChannel.ALL, "hello", null, "",
                    LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                    ChatMessageIds.NONE, "", 0L, null, "", "x");
            fail("words without an author were accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }

        ChatReplyReference reply = ChatReplyReference.unanchored("System",
                "Bilbo has just earned the achievement [Taking Inventory]",
                0x4A90D9);
        assertTrue(reply.exists());
        assertFalse(reply.isAnchored());
        assertEquals(ChatReplyReference.NONE,
                ChatReplyReference.unanchored("  ", "x", 0));
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "well done", 1L, "", null, "", "", 0,
                false, ChatMessageIdAllocator.next(), reply);
        ByteBuf line = Unpooled.buffer();
        packet.toBytes(line);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(line);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getReply().exists());
        assertFalse(decoded.getReply().isAnchored());
        assertEquals(ChatMessageIds.NONE, decoded.getReply().getMessageId());
        assertEquals("System", decoded.getReply().getAuthor());
        assertEquals("Bilbo has just earned the achievement [Taking Inventory]",
                decoded.getReply().getExcerpt());
        assertEquals(0x4A90D9, decoded.getReply().getAuthorColor());
    }

    /** A request may only name an id a server could have handed out. */
    @Test
    public void aRequestCannotNameALocalId() {
        try {
            new LostTalesChatSendPacket(ChatChannel.ALL, "hello", null, "",
                    LostTalesChatSendPacket.IDENTITY_DEFAULT, null, -7L);
            fail("a client-local id was accepted as a reply target");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }
    }

    /** The reply request round-trips like every other field. */
    @Test
    public void aReplyRequestRoundTrips() {
        long target = ChatMessageIdAllocator.next();
        LostTalesChatSendPacket original = new LostTalesChatSendPacket(
                ChatChannel.ALL, "on my way", null, "",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null, target);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(target, decoded.getReplyToMessageId());

        LostTalesChatSendPacket plain = new LostTalesChatSendPacket(
                ChatChannel.ALL, "hello");
        ByteBuf plainBuffer = Unpooled.buffer();
        plain.toBytes(plainBuffer);
        LostTalesChatSendPacket plainDecoded = new LostTalesChatSendPacket();
        plainDecoded.fromBytes(plainBuffer);
        assertFalse(plainDecoded.isMalformed());
        assertEquals(ChatMessageIds.NONE,
                plainDecoded.getReplyToMessageId());
    }

    /** The worn character's id rides the line; an account line never names one. */
    @Test
    public void theWornCharactersIdRoundTrips() {
        UUID character = UUID.randomUUID();
        LostTalesChatMessagePacket worn = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Aragorn", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "skin", null, "", "", 0,
                false, ChatMessageIds.NONE, null, "", 0L, character);
        ByteBuf buffer = Unpooled.buffer();
        worn.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(character, decoded.getIdentityCharacterId());
        assertEquals(character, decoded.withMessage("edited").getIdentityCharacterId());
        assertEquals(character,
                decoded.withPartner("Beren", "Beren").getIdentityCharacterId());

        LostTalesChatMessagePacket account = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", 0,
                true, ChatMessageIds.NONE, null, "", 0L, character);
        assertNull(account.getIdentityCharacterId());
        buffer = Unpooled.buffer();
        account.toBytes(buffer);
        // A tail claiming a character on an account line is refused.
        buffer.setBoolean(buffer.writerIndex() - scopeTailBytes("")
                - 3 * LostTalesChatMessagePacket.IDENTITY_ID_TAIL_BYTES, true);
        LostTalesChatMessagePacket forged = new LostTalesChatMessagePacket();
        forged.fromBytes(buffer);
        assertTrue(forged.isMalformed());
    }

    /**
     * A whisper says which character of each party it is held as and
     * with, on the wire and through every rebuild; a plain line carries
     * neither, and one claiming them is refused.
     */
    @Test
    public void whispersCarryTheirConversationIds() {
        UUID own = UUID.randomUUID();
        UUID partner = UUID.randomUUID();
        LostTalesChatMessagePacket whisper = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, UUID.randomUUID(), "Aragorn", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "psst", 1L, "skin", null, "", "Alex", 0,
                false, ChatMessageIds.NONE, null, "Beren", 7L, own)
                .withConversation(own, partner);
        assertEquals(own, whisper.getOwnCharacterId());
        assertEquals(partner, whisper.getPartnerCharacterId());
        ByteBuf buffer = Unpooled.buffer();
        whisper.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(own, decoded.getOwnCharacterId());
        assertEquals(partner, decoded.getPartnerCharacterId());
        assertEquals(own, decoded.getIdentityCharacterId());
        assertEquals(partner, decoded.withMessage("edited").getPartnerCharacterId());
        assertEquals(own, decoded.withoutEcho().getOwnCharacterId());
        // The partner's copy is held the other way round.
        LostTalesChatMessagePacket theirs = decoded.withPartner("Steve", "Aragorn")
                .withConversation(partner, own);
        assertEquals(partner, theirs.getOwnCharacterId());
        assertEquals(own, theirs.getPartnerCharacterId());
        assertEquals("Aragorn", theirs.getPartnerIdentity());
        // A payload cut short inside a conversation tail is malformed;
        // one from an older server, without the tails, reads as accounts.
        buffer = Unpooled.buffer();
        whisper.toBytes(buffer);
        LostTalesChatMessagePacket cut = new LostTalesChatMessagePacket();
        cut.fromBytes(buffer.slice(0,
                buffer.readableBytes() - scopeTailBytes("") - 5));
        assertTrue(cut.isMalformed());
        LostTalesChatMessagePacket older = new LostTalesChatMessagePacket();
        older.fromBytes(buffer.slice(0, buffer.readableBytes() - scopeTailBytes("")
                - 2 * LostTalesChatMessagePacket.IDENTITY_ID_TAIL_BYTES));
        assertFalse(older.isMalformed());
        assertNull(older.getOwnCharacterId());
        assertNull(older.getPartnerCharacterId());
        // A plain line never carries them, whatever it is built with.
        LostTalesChatMessagePacket plain = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Aragorn", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "skin", null, "", "", 0,
                false, ChatMessageIds.NONE, null, "", 0L, own, own, partner);
        assertNull(plain.getOwnCharacterId());
        assertNull(plain.getPartnerCharacterId());
        buffer = Unpooled.buffer();
        plain.toBytes(buffer);
        buffer.setBoolean(buffer.writerIndex() - scopeTailBytes("")
                - LostTalesChatMessagePacket.IDENTITY_ID_TAIL_BYTES, true);
        LostTalesChatMessagePacket forged = new LostTalesChatMessagePacket();
        forged.fromBytes(buffer);
        assertTrue(forged.isMalformed());
    }

    /**
     * A scoped channel says which of its conversations a line is in, so
     * an account with characters in two factions never sees one under
     * the other. A channel that is only ever one conversation carries
     * none, and a payload claiming one is refused.
     */
    @Test
    public void aScopedLineSaysWhichConversationItIsIn() {
        LostTalesChatMessagePacket faction = new LostTalesChatMessagePacket(
                ChatChannel.FACTION, UUID.randomUUID(), "Aldric", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "for Gondor", 1L, "skin", null, "Gondor", "", 0,
                false, ChatMessageIds.NONE, null, "", 0L, UUID.randomUUID())
                .withScope("lotr:gondor");
        assertEquals("lotr:gondor", faction.getScopeValue());
        ByteBuf buffer = Unpooled.buffer();
        faction.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("lotr:gondor", decoded.getScopeValue());
        // It survives every rebuild the client and the server do.
        assertEquals("lotr:gondor", decoded.withMessage("edited").getScopeValue());
        assertEquals("lotr:gondor", decoded.withoutEcho().getScopeValue());

        // A channel that is one conversation carries none, whatever it
        // is built with, and a payload claiming one is refused.
        LostTalesChatMessagePacket global = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Aldric", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "skin", null, "", "", 0,
                false, ChatMessageIds.NONE, null, "", 0L, UUID.randomUUID())
                .withScope("lotr:gondor");
        assertEquals("", global.getScopeValue());
        buffer = Unpooled.buffer();
        faction.toBytes(buffer);
        ByteBuf forged = Unpooled.buffer();
        forged.writeBytes(buffer.slice(0, buffer.readableBytes()
                - scopeTailBytes("lotr:gondor")));
        LostTalesPacketCodec.writeUtf8String(forged, "", 128);
        LostTalesChatMessagePacket unscoped = new LostTalesChatMessagePacket();
        unscoped.fromBytes(forged);
        assertFalse("a faction line may name no conversation at all",
                unscoped.isMalformed());
        assertEquals("", unscoped.getScopeValue());
    }

    /** A whisper addresses the partner's character by id when the client knows it. */
    @Test
    public void whisperRequestsAddressTheCharacterById() {
        UUID target = UUID.randomUUID();
        LostTalesChatSendPacket send = new LostTalesChatSendPacket(
                ChatChannel.WHISPER, "psst", null, "Steve",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIds.NONE, "Aldric", 3L, target);
        assertEquals(target, send.getTargetCharacterId());
        ByteBuf buffer = Unpooled.buffer();
        send.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(target, decoded.getTargetCharacterId());
        assertEquals("Aldric", decoded.getTargetIdentity());
        assertEquals(3L, decoded.getEchoNonce());
        // Cut short inside the tail it is malformed; without the tail,
        // as an older client sends it, the target is named alone.
        buffer = Unpooled.buffer();
        send.toBytes(buffer);
        LostTalesChatSendPacket cut = new LostTalesChatSendPacket();
        cut.fromBytes(buffer.slice(0, buffer.readableBytes() - 3));
        assertTrue(cut.isMalformed());
        LostTalesChatSendPacket older = new LostTalesChatSendPacket();
        older.fromBytes(buffer.slice(0, buffer.readableBytes()
                - LostTalesChatSendPacket.TARGET_ID_TAIL_BYTES));
        assertFalse(older.isMalformed());
        assertNull(older.getTargetCharacterId());
        assertEquals("Aldric", older.getTargetIdentity());
        assertNull(new LostTalesChatSendPacket(ChatChannel.OOC, "hi")
                .getTargetCharacterId());
    }

    /**
     * How many bytes the conversation a line belongs to takes at the end
     * of the payload. Measured rather than assumed, so a test that walks
     * back from the end of a packet keeps saying what it means when
     * another field is appended after this one.
     */
    private static int scopeTailBytes(String scopeValue) {
        ByteBuf probe = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(probe, scopeValue, 128);
        // Behind the scope, for a line quoting nothing: the empty quote
        // of a line nobody named (author, words, colour), then the
        // quote's head, a server line's component and its named
        // players, every one of them empty.
        LostTalesPacketCodec.writeUtf8String(probe, "", 256);
        LostTalesPacketCodec.writeUtf8String(probe, "", 297);
        probe.writeInt(0);
        probe.writeBoolean(false);
        probe.writeLong(0L);
        probe.writeLong(0L);
        probe.writeBoolean(false);
        LostTalesPacketCodec.writeUtf8String(probe, "", 128);
        LostTalesPacketCodec.writeUtf8String(probe, "", 8192);
        probe.writeInt(0);
        // And the reactions, none, and the tab a command's answer is
        // filed under, none.
        probe.writeInt(0);
        LostTalesPacketCodec.writeUtf8String(probe, "", 384);
        return probe.readableBytes();
    }
}
