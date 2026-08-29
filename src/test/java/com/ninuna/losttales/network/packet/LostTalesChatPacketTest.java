package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatMessageIds;
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
    public void channelCatalogueHasStableSemantics() {
        assertEquals(ChatIdentityType.CHARACTER,
                ChatChannel.ALL.getIdentityType());
        assertEquals(ChatRecipientRule.PROXIMITY,
                ChatChannel.PROXIMITY.getRecipientRule());
        assertEquals(ChatRecipientRule.PARTY,
                ChatChannel.PARTY.getRecipientRule());
        assertEquals(ChatRecipientRule.FACTION,
                ChatChannel.FACTION.getRecipientRule());
        assertEquals(ChatIdentityType.ACCOUNT,
                ChatChannel.OOC.getIdentityType());
        assertEquals(ChatChannel.PROXIMITY,
                ChatChannel.fromId("Proximity"));
        assertNull(ChatChannel.fromId("trade"));
        assertEquals("Global", ChatChannel.ALL.getDisplayName());
        assertEquals("all", ChatChannel.ALL.getId());
    }

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
    public void sendRequestCarriesTheAskedForAppearance() {
        assertEquals(LostTalesChatSendPacket.APPEARANCE_DEFAULT,
                new LostTalesChatSendPacket(ChatChannel.ALL, "hello")
                        .getAppearanceKind());
        java.util.UUID characterId = java.util.UUID.randomUUID();
        LostTalesChatSendPacket original = new LostTalesChatSendPacket(
                ChatChannel.OOC, "hello", null, "",
                LostTalesChatSendPacket.APPEARANCE_CHARACTER, characterId);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesChatSendPacket.APPEARANCE_CHARACTER,
                decoded.getAppearanceKind());
        assertEquals(characterId, decoded.getAppearanceCharacterId());

        LostTalesChatSendPacket account = new LostTalesChatSendPacket(
                ChatChannel.ALL, "hello", null, "",
                LostTalesChatSendPacket.APPEARANCE_ACCOUNT, null);
        ByteBuf accountBuffer = Unpooled.buffer();
        account.toBytes(accountBuffer);
        LostTalesChatSendPacket decodedAccount =
                new LostTalesChatSendPacket();
        decodedAccount.fromBytes(accountBuffer);
        assertFalse(decodedAccount.isMalformed());
        assertEquals(LostTalesChatSendPacket.APPEARANCE_ACCOUNT,
                decodedAccount.getAppearanceKind());
        assertNull(decodedAccount.getAppearanceCharacterId());

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
                LostTalesChatSendPacket.APPEARANCE_DEFAULT, null)
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
    public void messageValidationRejectsFormattingAndControlText() {
        assertTrue(ChatMessageValidator.isValid("Mae govannen!"));
        assertFalse(ChatMessageValidator.isValid(" padded "));
        assertFalse(ChatMessageValidator.isValid("colored §cmessage"));
        assertFalse(ChatMessageValidator.isValid("line\nbreak"));

        StringBuilder oversized = new StringBuilder();
        for (int index = 0;
             index <= ChatMessageValidator.MAX_CHARACTERS; index++) {
            oversized.append('x');
        }
        assertFalse(ChatMessageValidator.isValid(oversized.toString()));
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
                "", LostTalesChatSendPacket.APPEARANCE_CHARACTER,
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

    /** Markers are cheap, so a message may carry the full count of them. */
    @Test
    public void aFullCountOfMarkersFitsTheBudget() {
        List<ChatShowcase> markers = new ArrayList<ChatShowcase>();
        for (int index = 1; index < ChatShareTokenParser.MAX_TOKENS;
                index += 2) {
            markers.add(ChatShowcase.marker(index, "losttales:m" + index,
                    "Place " + index, "star", "gold", 0, index, -index));
        }
        assertTrue(ChatShowcase.serializedBytes(markers)
                <= ChatShowcase.MAX_TOTAL_BYTES);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.ALL, UUID.randomUUID(), "Aldric", "Steve", "",
                0xFFFFFF, 0xFFFFFF,
                tokensFor(ChatShareTokenParser.MAX_TOKENS), 1L, "", markers);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(markers.size(), decoded.getShowcases().size());
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

    /** A request may only name an id a server could have handed out. */
    @Test
    public void aRequestCannotNameALocalId() {
        try {
            new LostTalesChatSendPacket(ChatChannel.ALL, "hello", null, "",
                    LostTalesChatSendPacket.APPEARANCE_DEFAULT, null, -7L);
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
                LostTalesChatSendPacket.APPEARANCE_DEFAULT, null, target);
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
}
