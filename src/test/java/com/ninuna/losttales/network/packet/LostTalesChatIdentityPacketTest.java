package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatFellowship;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Whom the chat's copies read as and the server's answer to it: every
 * field survives the wire, and nothing short, overlong, repeated,
 * trailing or out of range ever applies.
 */
public final class LostTalesChatIdentityPacketTest {
    private static final UUID CHARACTER = new UUID(1L, 2L);
    private static final UUID FELLOWSHIP = new UUID(3L, 4L);
    private static final ChatFellowship GREY = new ChatFellowship(FELLOWSHIP, "Grey", 0x123456);
    private static final ChatFellowship RANGERS = new ChatFellowship(new UUID(5L, 6L), "Rangers", 0xABCDEF);

    @Test
    public void noneOneAndTheMostCharactersReadRoundTrip() {
        for (List<UUID> ids : readLists()) {
            ByteBuf wire = Unpooled.buffer();
            new LostTalesChatIdentityPacket(ids).toBytes(wire);
            LostTalesChatIdentityPacket decoded = new LostTalesChatIdentityPacket();
            decoded.fromBytes(wire);
            assertFalse(decoded.isMalformed());
            assertEquals(ids, decoded.getCharacterIds());
        }
    }

    @Test
    public void everyTruncatedRepeatedOverlongOrTrailingRequestIsRejected() {
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatIdentityPacket(Arrays.asList(CHARACTER, FELLOWSHIP)).toBytes(wire);
        for (int length = 0; length < wire.readableBytes(); length++) {
            assertBadRequest(wire.copy(0, length));
        }
        assertBadRequest(wire.copy().writeByte(0));
        ByteBuf repeated = Unpooled.buffer().writeByte(2);
        for (int index = 0; index < 2; index++) {
            repeated.writeLong(CHARACTER.getMostSignificantBits())
                    .writeLong(CHARACTER.getLeastSignificantBits());
        }
        assertBadRequest(repeated);
        ByteBuf tooMany = Unpooled.buffer().writeByte(LostTalesChatIdentityPacket.MAX_READ + 1);
        for (int index = 0; index <= LostTalesChatIdentityPacket.MAX_READ; index++) {
            tooMany.writeLong(index).writeLong(index);
        }
        assertBadRequest(tooMany);
        try {
            new LostTalesChatIdentityPacket(manyIds(LostTalesChatIdentityPacket.MAX_READ + 1));
            fail("more characters than the bound were accepted");
        } catch (IllegalArgumentException expected) {
            // The bound holds locally too.
        }
    }

    @Test
    public void theAnswerRoundTripsForAnyCharactersAndFellowships() {
        List<List<ChatFellowship>> lists = new ArrayList<List<ChatFellowship>>();
        lists.add(Collections.<ChatFellowship>emptyList());
        lists.add(Collections.singletonList(GREY));
        lists.add(Arrays.asList(GREY, RANGERS));
        for (List<UUID> ids : readLists()) {
            for (List<ChatFellowship> fellowships : lists) {
                ByteBuf wire = Unpooled.buffer();
                new LostTalesChatIdentitySyncPacket(ids, fellowships).toBytes(wire);
                LostTalesChatIdentitySyncPacket decoded =
                        new LostTalesChatIdentitySyncPacket();
                decoded.fromBytes(wire);
                assertFalse(decoded.isMalformed());
                assertEquals(ids, decoded.getCharacterIds());
                assertEquals(fellowships, decoded.getFellowships());
            }
        }
    }

    /** A fellowship's name past the bound is refused when built and when read. */
    @Test
    public void anOverlongFellowshipNameNeverApplies() {
        StringBuilder overlong = new StringBuilder();
        for (int index = 0; index <= LostTalesChatIdentitySyncPacket.MAX_FELLOWSHIP_NAME_BYTES;
                index++) {
            overlong.append('a');
        }
        try {
            new ChatFellowship(FELLOWSHIP, overlong.toString(), 0);
            fail("an overlong fellowship name was accepted");
        } catch (IllegalArgumentException expected) {
            // The bound holds locally too.
        }
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(Collections.singletonList(CHARACTER),
                Collections.singletonList(GREY)).toBytes(wire);
        // The fields before the name, then a length past the bound and
        // that many bytes: a well-formed frame the bound still refuses.
        ByteBuf claimed = wire.copy(0, 38);
        int length = overlong.length();
        claimed.writeByte((length & 0x7F) | 0x80).writeByte(length >>> 7);
        for (int index = 0; index < length; index++) {
            claimed.writeByte('a');
        }
        assertBadAnswer(claimed);
    }

    @Test
    public void partialInvalidAndTrailingAnswersNeverApply() {
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(Collections.singletonList(CHARACTER),
                Collections.singletonList(GREY)).toBytes(wire);
        for (int length = 0; length < wire.readableBytes(); length++) {
            assertBadAnswer(wire.copy(0, length));
        }
        assertBadAnswer(wire.copy().setByte(0, LostTalesChatIdentityPacket.MAX_READ + 1));
        assertBadAnswer(wire.copy().setByte(17, 9));
        assertBadAnswer(wire.copy().setInt(34, -1));
        assertBadAnswer(wire.copy().setInt(34, 0x1000000));
        assertBadAnswer(wire.copy().setByte(39, ' '));
        assertBadAnswer(wire.copy().writeByte(0));
        ByteBuf empty = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(Collections.<UUID>emptyList(),
                Collections.<ChatFellowship>emptyList()).toBytes(empty);
        assertBadAnswer(empty.writeByte(0));
    }

    private static List<List<UUID>> readLists() {
        List<List<UUID>> lists = new ArrayList<List<UUID>>();
        lists.add(Collections.<UUID>emptyList());
        lists.add(Collections.singletonList(CHARACTER));
        lists.add(manyIds(LostTalesChatIdentityPacket.MAX_READ));
        return lists;
    }

    private static List<UUID> manyIds(int count) {
        List<UUID> ids = new ArrayList<UUID>(count);
        for (int index = 0; index < count; index++) {
            ids.add(new UUID(100L, index));
        }
        return ids;
    }

    private static void assertBadRequest(ByteBuf wire) {
        LostTalesChatIdentityPacket decoded = new LostTalesChatIdentityPacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getCharacterIds().isEmpty());
        assertEquals(0, wire.readableBytes());
    }

    private static void assertBadAnswer(ByteBuf wire) {
        LostTalesChatIdentitySyncPacket decoded = new LostTalesChatIdentitySyncPacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getCharacterIds().isEmpty());
        assertTrue(decoded.getFellowships().isEmpty());
        assertEquals(0, wire.readableBytes());
    }
}
