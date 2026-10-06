package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    /** One of a reader's identities that reacted. */
    private static final UUID READER = new UUID(77L, 77L);

    /** An optional id at the payload's tail: a presence flag and a UUID, written whole either way. */
    private static final int IDENTITY_ID_TAIL_BYTES = 1 + 16;

    @Test
    public void sendRequestRoundTripsAndRejectsTrailingData() {
        java.util.UUID fellowship = java.util.UUID.randomUUID();
        LostTalesChatSendPacket original = ChatPacketFixtures.send(ChatChannel.FELLOWSHIP,
                "Meet at the western gate.").to(fellowship.toString()).build();
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded =
                new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.FELLOWSHIP, decoded.getChannel());
        assertEquals("Meet at the western gate.", decoded.getMessage());
        assertEquals(fellowship, decoded.getFellowshipId());
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
                ChatPacketFixtures.send(ChatChannel.GLOBAL, "hello").build()
                        .getIdentityKind());
        java.util.UUID characterId = java.util.UUID.randomUUID();
        LostTalesChatSendPacket original = ChatPacketFixtures.send(ChatChannel.OOC, "hello")
                .as(LostTalesChatSendPacket.IDENTITY_CHARACTER, characterId).build();
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesChatSendPacket.IDENTITY_CHARACTER,
                decoded.getIdentityKind());
        assertEquals(characterId, decoded.getIdentityCharacterId());

        LostTalesChatSendPacket account = ChatPacketFixtures.send(ChatChannel.GLOBAL, "hello")
                .as(LostTalesChatSendPacket.IDENTITY_ACCOUNT, null).build();
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
        ChatPacketFixtures.send(ChatChannel.GLOBAL, "hello")
                .as(LostTalesChatSendPacket.IDENTITY_DEFAULT, null).build()
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
        LostTalesChatSendPacket original = ChatPacketFixtures.send(
                ChatChannel.GLOBAL, "see [i:Sword] [m:Bree] [q:Road Work]")
                .sharing(Arrays.asList(ChatShareReference.item(4),
                        ChatShareReference.marker("losttales:bree"),
                        ChatShareReference.quest("losttales:road_work")))
                .build();
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
        assertEquals(ChatShareKind.QUEST,
                decoded.getReferences().get(2).getKind());
        assertEquals("losttales:road_work",
                decoded.getReferences().get(2).getQuestReference());

        boolean rejectedSlot = false;
        try {
            ChatShareReference.item(40);
        } catch (IllegalArgumentException expected) {
            rejectedSlot = true;
        }
        assertTrue(rejectedSlot);

        // A hand-built payload with an out-of-range slot is discarded.
        ByteBuf forged = Unpooled.buffer();
        ChatPacketFixtures.send(ChatChannel.GLOBAL, "[i:Sword]").build()
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
        LostTalesChatMessagePacket original = ChatPacketFixtures.line(
                ChatChannel.OOC, "Steve", "Steve",
                "look [i:Sword] near [m:Bree] [q:Road Work]").at(5L)
                .showcases(Arrays.asList(ChatShowcase.item(0, data),
                        ChatShowcase.marker(1, "losttales:bree",
                                "Bree", "town", "orange", 100,
                                512.5D, -384.0D),
                        ChatShowcase.quest(2, "losttales:road_work",
                                new ChatQuestCard(ChatQuestCard.Source.SERVER,
                                        "Road Work", "regional",
                                        Collections.singletonList(
                                                new ChatQuestCard.Objective(
                                                        "kill_orcs", "kill",
                                                        Collections.singletonMap(
                                                                "group", "hostile"),
                                                        4, 1, false,
                                                        "Defeat 4 orcs")),
                                        Collections.singletonMap(
                                                "experience", "20")),
                                true)))
                .build();
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(3, decoded.getShowcases().size());
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
        ChatShowcase quest = decoded.getShowcases().get(2);
        assertEquals(ChatShareKind.QUEST, quest.getKind());
        assertEquals("losttales:road_work", quest.getQuestReference());
        assertEquals(ChatQuestCard.Source.SERVER,
                quest.getQuestCard().getSource());
        assertEquals("Road Work", quest.getQuestCard().getTitle());
        assertEquals("regional", quest.getQuestCard().getCategory());
        ChatQuestCard.Objective objective =
                quest.getQuestCard().getObjectives().get(0);
        assertEquals("Defeat 4 orcs", objective.getText());
        assertEquals("hostile", objective.getTargets().get("group"));
        assertEquals(4, objective.getCount());
        assertEquals(1, objective.getProgress());
        assertEquals("20", quest.getQuestCard().getRewards().get("experience"));
        assertTrue(quest.isQuestJoinable());

        LostTalesQuestShareJoinPacket join =
                new LostTalesQuestShareJoinPacket(7L, 2);
        ByteBuf joinBytes = Unpooled.buffer();
        join.toBytes(joinBytes);
        LostTalesQuestShareJoinPacket decodedJoin =
                new LostTalesQuestShareJoinPacket();
        decodedJoin.fromBytes(joinBytes);
        ByteBuf encodedAgain = Unpooled.buffer();
        decodedJoin.toBytes(encodedAgain);
        assertEquals(9, encodedAgain.readableBytes());

        // A showcase whose kind does not match its token is refused.
        boolean rejectedKind = false;
        try {
            ChatPacketFixtures.line(ChatChannel.OOC, "Steve", "Steve", "only [m:Bree]").at(5L)
                    .showcases(Arrays.asList(ChatShowcase.item(0, data))).build();
        } catch (IllegalArgumentException expected) {
            rejectedKind = true;
        }
        assertTrue(rejectedKind);

        boolean rejectedIndex = false;
        try {
            ChatPacketFixtures.line(ChatChannel.OOC, "Steve", "Steve", "only [i:Sword]").at(5L)
                    .showcases(Arrays.asList(ChatShowcase.item(1, data))).build();
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
        LostTalesChatSendPacket send = ChatPacketFixtures.send(ChatChannel.WHISPER, "psst")
                .to(" Steve ").build();
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
            ChatPacketFixtures.send(ChatChannel.WHISPER, "psst").to("").build();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals("", ChatPacketFixtures.send(ChatChannel.OOC, "hi").build()
                .getTarget());

        LostTalesChatMessagePacket message = ChatPacketFixtures.line(
                ChatChannel.WHISPER, "Alex", "Alex", "psst").at(5L).partner("Steve").build();
        buffer = Unpooled.buffer();
        message.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("Steve", decoded.getPartner());
        assertEquals("", ChatPacketFixtures.line(ChatChannel.OOC, "Alex", "Alex", "hi").at(5L)
                .build().getPartner());
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

        LostTalesChatSendPacket packet = ChatPacketFixtures.send(ChatChannel.OOC, withToken)
                .sharing(Arrays.asList(ChatShareReference.marker("losttales:x"))).build();
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
                    ChatPacketFixtures.line(ChatChannel.GLOBAL, "Aldric", "Steve", message)
                            .showcases(showcases).build();
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
        LostTalesChatSendPacket packet = ChatPacketFixtures.send(
                ChatChannel.OOC, message.toString()).sharing(references)
                .as(LostTalesChatSendPacket.IDENTITY_CHARACTER, UUID.randomUUID()).build();
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
        LostTalesChatMessagePacket packet = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Aldric", "Steve", message).showcases(withinBudget).build();
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
            ChatPacketFixtures.line(ChatChannel.GLOBAL, "Aldric", "Steve", message)
                    .showcases(overBudget).build();
            fail("a line over the showcase budget was accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }
    }


    /**
     * The message id survives the wire, and a whisper's two copies carry
     * the same one: it is one message, and anything naming it later has
     * to name it the same to both people.
     */
    @Test
    public void theMessageIdRoundTripsAndIsSharedByBothWhisperCopies() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket original = ChatPacketFixtures.line(
                ChatChannel.WHISPER, "Steve", "Steve", "hello").partner("Alex").accountLine(true)
                .messageId(id).build();
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
                decoded.getShowcases(), decoded.getFactionId(), "Steve",
                decoded.getRoles(), decoded.isAccountLine(),
                decoded.getMessageId(), decoded.getReply());
        assertEquals(id, other.getMessageId());
    }

    /** A line the server never named carries no id, and still travels. */
    @Test
    public void anUnnamedLineTravelsWithoutAnId() {
        LostTalesChatMessagePacket original = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Aldric", "Steve", "hello").build();
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
        LostTalesChatMessagePacket original = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Aldric", "Steve", "hello").accountLine(false).messageId(-7L)
                .build();
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
                ChatChannel.GLOBAL, UUID.randomUUID(), "Beren", "Steve", "",
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
                ChatChannel.GLOBAL, UUID.randomUUID(), "Beren", "Steve", "",
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

    /**
     * A forward's quote travels with its link to where the message was
     * said, its author and head, and none of its words, which are the
     * line's own; every copy a client makes of the line keeps it.
     */
    @Test
    public void aForwardCarriesItsLinkOverTheWire() {
        long original = ChatMessageIdAllocator.next();
        UUID quoted = UUID.randomUUID();
        ChatReplyReference forward = ChatReplyReference.forward(original,
                "Aldric", 0x4A90D9, "#ooc/" + original)
                .withHead(quoted, false, "skin-7");
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, UUID.randomUUID(), "Beren", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "meet me at the gate", 1L, "", null, "",
                "", 0, false, ChatMessageIdAllocator.next(), forward);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getReply().isForward());
        assertEquals("#ooc/" + original, decoded.getReply().getForwardedFrom());
        assertEquals(original, decoded.getReply().getMessageId());
        assertEquals("Aldric", decoded.getReply().getAuthor());
        assertEquals("", decoded.getReply().getExcerpt());
        assertEquals(quoted, decoded.getReply().getSenderId());
        assertTrue(decoded.withMessage("changed").getReply().isForward());
        assertFalse("a forward names a message",
                ChatReplyReference.forward(ChatMessageIds.NONE, "Aldric",
                        0, "#ooc/1").exists());
        assertFalse("a reply is no forward", ChatReplyReference.of(original,
                "Aldric", "hi").isForward());
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
                ChatChannel.GLOBAL, UUID.randomUUID(), "Beren", "Steve", "",
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
        UUID steve = UUID.randomUUID();
        UUID aldric = UUID.randomUUID();
        List<ChatNamedPlayer> named = Arrays.asList(
                new ChatNamedPlayer(steve, "Steve", aldric, "Aldric",
                        "human/male/2"),
                new ChatNamedPlayer(UUID.randomUUID(), "Alex", null, "", ""));
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, LostTalesChatMessagePacket.SERVER_SENDER_ID,
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
        assertEquals(new ChatNamedPlayer(steve, "Steve", aldric, "Aldric",
                "human/male/2"), decoded.getNamedPlayers().get(0));
        assertEquals("a nameless identity is the account",
                "Alex", decoded.getNamedPlayers().get(1).getIdentityName());
        assertEquals(json, decoded.withNameColor(0x123456).getBodyJson());
        assertEquals(2, decoded.withScope("").getNamedPlayers().size());

        // A player's line keeps no component, whatever it is handed.
        LostTalesChatMessagePacket player = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Beren", "Steve", "hello").build().withServerBody(json, named);
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

    /**
     * Every part of a line is there, the last ones included: a payload
     * that ends before the quote block, the quote's head, the reactions
     * or the tab is malformed. The chat history keeps lines as these
     * bytes, so a kept line of such a shape is quarantined.
     */
    @Test
    public void aLineEndingBeforeItsLastPartsIsMalformed() {
        LostTalesChatMessagePacket packet = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Beren", "Steve", "hello").build();
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        // The quote block is nothing for a line that quotes nothing. An
        // empty string is one byte of length; the tail is a head (17 and
        // a flag), a skin, a component, a count of named players, the
        // reactions and the tab a command's answer is filed under.
        int tail = 17 + 1 + 1 + 1 + 4 + 4 + 1;
        int quoteBlock = 1 + 1 + 4;
        int[] shortBy = {tail + quoteBlock, tail, 4 + 1, 1};
        for (int cut : shortBy) {
            LostTalesChatMessagePacket decoded =
                    new LostTalesChatMessagePacket();
            decoded.fromBytes(buffer.slice(0, buffer.readableBytes() - cut));
            assertTrue(cut + " bytes short", decoded.isMalformed());
            assertTrue(decoded.getNamedPlayers().isEmpty());
        }
    }

    /** The reactions ride on the line a reader is handed, and survive its copies. */
    @Test
    public void aLineCarriesItsReactionsAsItsReaderSeesThem() {
        ChatReactionSummary reactions = new ChatReactionSummary(Arrays.asList(
                new ChatReactionSummary.Reaction("smile", 3, Collections.singletonList(READER),
                        Arrays.asList("Aldric", "Beren")),
                new ChatReactionSummary.Reaction("joy", 1, Collections.<UUID>emptyList(),
                        Arrays.asList("Nils"))));
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, UUID.randomUUID(), "Beren", "Steve", "",
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
        assertTrue(smile.isMine(READER));
        assertEquals(Arrays.asList("Aldric", "Beren"), smile.names);
        assertEquals(1, decoded.withMessage("edited").getReactions()
                .find("joy").count);
    }

    @Test
    public void aReactionRequestNamesAKnownEmojiAndAServerMessage() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatReactPacket request = new LostTalesChatReactPacket(id,
                "smile", true, LostTalesChatSendPacket.IDENTITY_CHARACTER, READER);
        ByteBuf buffer = Unpooled.buffer();
        request.toBytes(buffer);
        LostTalesChatReactPacket decoded = new LostTalesChatReactPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(id, decoded.getMessageId());
        assertEquals("smile", decoded.getEmoji());
        assertTrue(decoded.isAdd());
        // Who the reaction is made as: the identity its copy speaks as.
        assertEquals(LostTalesChatSendPacket.IDENTITY_CHARACTER, decoded.getIdentityKind());
        assertEquals(READER, decoded.getIdentityCharacterId());

        ByteBuf forged = Unpooled.buffer();
        forged.writeLong(id);
        LostTalesPacketCodec.writeUtf8String(forged, "not_an_emoji", 64);
        forged.writeBoolean(true);
        forged.writeByte(LostTalesChatSendPacket.IDENTITY_DEFAULT);
        LostTalesChatReactPacket refused = new LostTalesChatReactPacket();
        refused.fromBytes(forged);
        assertTrue(refused.isMalformed());

        // A character named without an id, or an id without a character, is no identity.
        ByteBuf unnamed = Unpooled.buffer();
        unnamed.writeLong(id);
        LostTalesPacketCodec.writeUtf8String(unnamed, "smile", 64);
        unnamed.writeBoolean(true);
        unnamed.writeByte(9);
        LostTalesChatReactPacket wrongKind = new LostTalesChatReactPacket();
        wrongKind.fromBytes(unnamed);
        assertTrue(wrongKind.isMalformed());
    }

    @Test
    public void aReactionSyncRoundTripsAndRefusesAnEmojiTwice() {
        long id = ChatMessageIdAllocator.next();
        ChatReactionSummary reactions = new ChatReactionSummary(Arrays.asList(
                new ChatReactionSummary.Reaction("smile", 2, Collections.<UUID>emptyList(),
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
                new ChatReactionSummary.Reaction(parrot, 2, Collections.singletonList(READER),
                        Arrays.asList("Nils", "Aldric")),
                new ChatReactionSummary.Reaction(family, 1, Collections.<UUID>emptyList(),
                        Arrays.asList("Nils"))));
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatReactionSyncPacket(id, reactions).toBytes(buffer);
        LostTalesChatReactionSyncPacket decoded =
                new LostTalesChatReactionSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getReactions().find(parrot).count);
        assertTrue(decoded.getReactions().find(parrot).isMine(READER));
        assertEquals(Arrays.asList("Nils"),
                decoded.getReactions().find(family).names);

        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, UUID.randomUUID(), "Beren", "Steve", "",
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
                "partyparrot:556", true, LostTalesChatSendPacket.IDENTITY_DEFAULT, null);
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
            forged.writeByte(LostTalesChatSendPacket.IDENTITY_DEFAULT);
            LostTalesChatReactPacket refused = new LostTalesChatReactPacket();
            refused.fromBytes(forged);
            assertTrue(forgery, refused.isMalformed());
        }
    }

    /** An ordinary line replies to nothing and pays nothing for it. */
    @Test
    public void anOrdinaryLineCarriesNoQuote() {
        LostTalesChatMessagePacket packet = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Beren", "Steve", "hello").build();
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
        new LostTalesChatMessagePacket(ChatChannel.GLOBAL, UUID.randomUUID(),
                "Beren", "Steve", "", 0xFFFFFF, 0xFFFFFF, "hello", 1L, "",
                null, "", "", 0, false, ChatMessageIds.NONE,
                ChatReplyReference.of(ChatMessageIdAllocator.next(),
                        "Aldric", "hi")).toBytes(quoted);
        assertTrue(quoted.readableBytes() > size);
    }

    /**
     * A line no server holds a record of is quoted as a message no longer
     * kept: the request says only that it answers one, never an author or
     * words, and may not name an id besides; the line carries the quote
     * with no author, no words and no head, whatever its sender's screen
     * showed.
     */
    @Test
    public void aLineNoRecordHoldsIsQuotedAsNoLongerKept() {
        LostTalesChatSendPacket request = ChatPacketFixtures.send(ChatChannel.GLOBAL, "well done")
                .quotingUnkept().build();
        ByteBuf buffer = Unpooled.buffer();
        request.toBytes(buffer);
        LostTalesChatSendPacket decodedRequest = new LostTalesChatSendPacket();
        decodedRequest.fromBytes(buffer);
        assertFalse(decodedRequest.isMalformed());
        assertEquals(ChatMessageIds.NONE, decodedRequest.getReplyToMessageId());
        assertTrue(decodedRequest.quotesUnkept());
        // A request without a quote reads back without one.
        ByteBuf plain = Unpooled.buffer();
        ChatPacketFixtures.send(ChatChannel.GLOBAL, "hello").build().toBytes(plain);
        LostTalesChatSendPacket decodedPlain = new LostTalesChatSendPacket();
        decodedPlain.fromBytes(plain);
        assertFalse(decodedPlain.isMalformed());
        assertFalse(decodedPlain.quotesUnkept());
        try {
            ChatPacketFixtures.send(ChatChannel.GLOBAL, "hello")
                    .replyingTo(ChatMessageIdAllocator.next()).quotingUnkept().build();
            fail("an unkept quote and an id were both accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }

        assertTrue(ChatReplyReference.UNKEPT.exists());
        assertTrue(ChatReplyReference.UNKEPT.isUnkept());
        assertFalse(ChatReplyReference.UNKEPT.isAnchored());
        assertEquals("", ChatReplyReference.UNKEPT.getAuthor());
        assertEquals(ChatReplyReference.NONE,
                ChatReplyReference.unanchored("  ", "x", 0));
        // What a client's own screen quotes by words travels as unkept.
        ChatReplyReference seen = ChatReplyReference.unanchored("System",
                "Bilbo has just earned the achievement [Taking Inventory]",
                0x4A90D9).withHead(UUID.randomUUID(), true, "");
        for (ChatReplyReference reply : new ChatReplyReference[] {
                seen, ChatReplyReference.UNKEPT}) {
            LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                    ChatChannel.GLOBAL, UUID.randomUUID(), "Beren", "Steve", "",
                    0xFFFFFF, 0xFFFFFF, "well done", 1L, "", null, "", "", 0,
                    false, ChatMessageIdAllocator.next(), reply);
            ByteBuf line = Unpooled.buffer();
            packet.toBytes(line);
            LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
            decoded.fromBytes(line);
            assertFalse(decoded.isMalformed());
            assertTrue(decoded.getReply().isUnkept());
            assertFalse(decoded.getReply().isAnchored());
            assertEquals(ChatMessageIds.NONE, decoded.getReply().getMessageId());
            assertEquals("", decoded.getReply().getAuthor());
            assertEquals("", decoded.getReply().getExcerpt());
            assertFalse(decoded.getReply().hasHead());
        }
    }

    /** A request may only name an id a server could have handed out. */
    @Test
    public void aRequestCannotNameALocalId() {
        try {
            ChatPacketFixtures.send(ChatChannel.GLOBAL, "hello").replyingTo(-7L).build();
            fail("a client-local id was accepted as a reply target");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }
    }

    /** The reply request round-trips like every other field. */
    @Test
    public void aReplyRequestRoundTrips() {
        long target = ChatMessageIdAllocator.next();
        LostTalesChatSendPacket original = ChatPacketFixtures.send(ChatChannel.GLOBAL, "on my way")
                .replyingTo(target).build();
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(target, decoded.getReplyToMessageId());

        LostTalesChatSendPacket plain = ChatPacketFixtures.send(ChatChannel.GLOBAL, "hello").build();
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
                ChatChannel.GLOBAL, UUID.randomUUID(), "Aragorn", "Steve", "",
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
                - 3 * IDENTITY_ID_TAIL_BYTES, true);
        LostTalesChatMessagePacket forged = new LostTalesChatMessagePacket();
        forged.fromBytes(buffer);
        assertTrue(forged.isMalformed());
    }

    /**
     * A whisper says which character of each fellowship it is held as and
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
        // A payload cut short inside a conversation id, or ending before
        // them, is malformed.
        buffer = Unpooled.buffer();
        whisper.toBytes(buffer);
        LostTalesChatMessagePacket cut = new LostTalesChatMessagePacket();
        cut.fromBytes(buffer.slice(0,
                buffer.readableBytes() - scopeTailBytes("") - 5));
        assertTrue(cut.isMalformed());
        LostTalesChatMessagePacket shortened = new LostTalesChatMessagePacket();
        shortened.fromBytes(buffer.slice(0, buffer.readableBytes() - scopeTailBytes("")
                - 2 * IDENTITY_ID_TAIL_BYTES));
        assertTrue(shortened.isMalformed());
        assertNull(shortened.getOwnCharacterId());
        assertNull(shortened.getPartnerCharacterId());
        // A plain line never carries them, whatever it is built with.
        LostTalesChatMessagePacket plain = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Aragorn", "Steve", "hello").skin("skin").accountLine(false)
                .character(own).build().withConversation(own, partner);
        assertNull(plain.getOwnCharacterId());
        assertNull(plain.getPartnerCharacterId());
        buffer = Unpooled.buffer();
        plain.toBytes(buffer);
        buffer.setBoolean(buffer.writerIndex() - scopeTailBytes("")
                - IDENTITY_ID_TAIL_BYTES, true);
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
                ChatChannel.GLOBAL, UUID.randomUUID(), "Aldric", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "skin", null, "", "", 0,
                false, ChatMessageIds.NONE, null, "", 0L, UUID.randomUUID())
                .withScope("lotr:gondor");
        assertEquals("", global.getScopeValue());
        buffer = Unpooled.buffer();
        faction.toBytes(buffer);
        // The same line with an empty scope where its own was, and what
        // follows the scope as it was: scopeTailBytes("") less the empty
        // scope's one length byte.
        int behindScope = scopeTailBytes("") - 1;
        ByteBuf forged = Unpooled.buffer();
        forged.writeBytes(buffer.slice(0, buffer.readableBytes()
                - scopeTailBytes("lotr:gondor")));
        LostTalesPacketCodec.writeUtf8String(forged, "", 128);
        forged.writeBytes(buffer.slice(buffer.readableBytes() - behindScope,
                behindScope));
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
        LostTalesChatSendPacket send = ChatPacketFixtures.send(ChatChannel.WHISPER, "psst")
                .to("Steve").toCharacter("Aldric", target).echoNonce(3L).build();
        assertEquals(target, send.getTargetCharacterId());
        ByteBuf buffer = Unpooled.buffer();
        send.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(target, decoded.getTargetCharacterId());
        assertEquals("Aldric", decoded.getTargetIdentity());
        assertEquals(3L, decoded.getEchoNonce());
        // Cut short inside the id, or without it (a presence flag and a
        // UUID), the request is malformed.
        buffer = Unpooled.buffer();
        send.toBytes(buffer);
        LostTalesChatSendPacket cut = new LostTalesChatSendPacket();
        cut.fromBytes(buffer.slice(0, buffer.readableBytes() - 3));
        assertTrue(cut.isMalformed());
        LostTalesChatSendPacket shortened = new LostTalesChatSendPacket();
        shortened.fromBytes(buffer.slice(0, buffer.readableBytes() - (1 + 16)));
        assertTrue(shortened.isMalformed());
        assertNull(shortened.getTargetCharacterId());
        assertNull(ChatPacketFixtures.send(ChatChannel.OOC, "hi").build()
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
        // Behind the scope, for a line quoting nothing: whether it quotes
        // a line no longer kept, then the quote's head and whether it
        // quotes an action, a forward's link, a server line's component
        // and its named players, every one of them empty.
        probe.writeBoolean(false);
        probe.writeBoolean(false);
        probe.writeLong(0L);
        probe.writeLong(0L);
        probe.writeBoolean(false);
        LostTalesPacketCodec.writeUtf8String(probe, "", 128);
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
