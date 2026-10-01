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
 * The chat identity request and the server's answer to it: every field
 * survives the wire, and nothing short, overlong, trailing or out of
 * range ever applies.
 */
public final class LostTalesChatIdentityPacketTest {
    private static final UUID CHARACTER = new UUID(1L, 2L);
    private static final UUID FELLOWSHIP = new UUID(3L, 4L);
    private static final ChatFellowship GREY = new ChatFellowship(FELLOWSHIP, "Grey", 0x123456);
    private static final ChatFellowship RANGERS = new ChatFellowship(new UUID(5L, 6L), "Rangers", 0xABCDEF);

    @Test
    public void accountAndCharacterSelectionsRoundTripWithTheVoice() {
        for (UUID id : new UUID[] {null, CHARACTER}) {
            for (boolean narrating : new boolean[] {false, true}) {
                ByteBuf wire = Unpooled.buffer();
                new LostTalesChatIdentityPacket(id, narrating).toBytes(wire);
                LostTalesChatIdentityPacket decoded = new LostTalesChatIdentityPacket();
                decoded.fromBytes(wire);
                assertFalse(decoded.isMalformed());
                assertEquals(id, decoded.getCharacterId());
                assertEquals(narrating, decoded.isNarrating());
            }
        }
    }

    @Test
    public void everyTruncatedSelectionAndInvalidFlagIsRejected() {
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatIdentityPacket(CHARACTER, true).toBytes(wire);
        for (int length = 0; length < wire.readableBytes(); length++) {
            assertBadSelection(wire.copy(0, length));
        }
        assertBadSelection(Unpooled.buffer().writeByte(2));
        assertBadSelection(Unpooled.buffer().writeByte(0).writeByte(2));
        assertBadSelection(Unpooled.buffer().writeByte(0).writeByte(0).writeByte(0));
        assertBadSelection(wire.copy().writeByte(0));
    }

    @Test
    public void membershipRoundTripsForAccountCharacterAndAnyFellowships() {
        List<List<ChatFellowship>> lists = new ArrayList<List<ChatFellowship>>();
        lists.add(Collections.<ChatFellowship>emptyList());
        lists.add(Collections.singletonList(GREY));
        lists.add(Arrays.asList(GREY, RANGERS));
        for (UUID id : new UUID[] {null, CHARACTER}) {
            for (List<ChatFellowship> fellowships : lists) {
                for (boolean narrating : new boolean[] {false, true}) {
                    ByteBuf wire = Unpooled.buffer();
                    new LostTalesChatIdentitySyncPacket(id, fellowships, narrating)
                            .toBytes(wire);
                    LostTalesChatIdentitySyncPacket decoded =
                            new LostTalesChatIdentitySyncPacket();
                    decoded.fromBytes(wire);
                    assertFalse(decoded.isMalformed());
                    assertEquals(id, decoded.getCharacterId());
                    assertEquals(fellowships, decoded.getFellowships());
                    assertEquals(narrating, decoded.isNarrating());
                }
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
        new LostTalesChatIdentitySyncPacket(CHARACTER, Collections.singletonList(GREY), false)
                .toBytes(wire);
        // The fields before the name, then a length past the bound and
        // that many bytes: a well-formed frame the bound still refuses.
        ByteBuf claimed = wire.copy(0, 38);
        int length = overlong.length();
        claimed.writeByte((length & 0x7F) | 0x80).writeByte(length >>> 7);
        for (int index = 0; index < length; index++) {
            claimed.writeByte('a');
        }
        claimed.writeByte(0);
        assertBadMembership(claimed);
    }

    @Test
    public void partialInvalidAndTrailingMembershipDataNeverApply() {
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(CHARACTER, Collections.singletonList(GREY), true)
                .toBytes(wire);
        for (int length = 0; length < wire.readableBytes(); length++) {
            assertBadMembership(wire.copy(0, length));
        }
        assertBadMembership(wire.copy().setByte(0, 2));
        assertBadMembership(wire.copy().setByte(17, 9));
        assertBadMembership(wire.copy().setInt(34, -1));
        assertBadMembership(wire.copy().setInt(34, 0x1000000));
        assertBadMembership(wire.copy().setByte(39, ' '));
        assertBadMembership(wire.copy().setByte(wire.readableBytes() - 1, 2));
        assertBadMembership(wire.copy().writeByte(0));
        ByteBuf account = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(null, Collections.<ChatFellowship>emptyList(), false)
                .toBytes(account);
        assertBadMembership(account.writeByte(0));
    }

    private static void assertBadSelection(ByteBuf wire) {
        LostTalesChatIdentityPacket decoded = new LostTalesChatIdentityPacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertNull(decoded.getCharacterId());
        assertFalse(decoded.isNarrating());
        assertEquals(0, wire.readableBytes());
    }

    private static void assertBadMembership(ByteBuf wire) {
        LostTalesChatIdentitySyncPacket decoded = new LostTalesChatIdentitySyncPacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertNull(decoded.getCharacterId());
        assertTrue(decoded.getFellowships().isEmpty());
        assertFalse(decoded.isNarrating());
        assertEquals(0, wire.readableBytes());
    }
}
