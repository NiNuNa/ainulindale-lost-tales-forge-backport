package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
    private static final UUID PARTY = new UUID(3L, 4L);

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
    public void membershipRoundTripsForAccountCharacterAndNoParty() {
        for (UUID id : new UUID[] {null, CHARACTER}) {
            for (UUID party : new UUID[] {null, PARTY}) {
                for (boolean narrating : new boolean[] {false, true}) {
                    String leader = party == null ? "" : "Aldric";
                    ByteBuf wire = Unpooled.buffer();
                    new LostTalesChatIdentitySyncPacket(id, party, 0xABCDEF, leader,
                            narrating).toBytes(wire);
                    LostTalesChatIdentitySyncPacket decoded =
                            new LostTalesChatIdentitySyncPacket();
                    decoded.fromBytes(wire);
                    assertFalse(decoded.isMalformed());
                    assertEquals(id, decoded.getCharacterId());
                    assertEquals(party, decoded.getPartyId());
                    assertEquals(0xABCDEF, decoded.getPartyColor());
                    assertEquals(leader, decoded.getPartyLeader());
                    assertEquals(narrating, decoded.isNarrating());
                }
            }
        }
    }

    /** A leader's name past the limit is refused when built and when read. */
    @Test
    public void anOverlongLeaderNameNeverApplies() {
        StringBuilder overlong = new StringBuilder();
        for (int index = 0; index <= LostTalesChatIdentitySyncPacket.MAX_PARTY_LEADER_BYTES;
                index++) {
            overlong.append('a');
        }
        try {
            new LostTalesChatIdentitySyncPacket(CHARACTER, PARTY, 0, overlong.toString(),
                    false);
            fail("an overlong leader name was accepted");
        } catch (IllegalArgumentException expected) {
            // The bound holds locally too.
        }
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(CHARACTER, PARTY, 0, "Aldric", false)
                .toBytes(wire);
        // The fields before the name, then a length past the limit and
        // that many bytes: a well-formed frame the bound still refuses.
        ByteBuf claimed = wire.copy(0, 38).writeByte(overlong.length());
        for (int index = 0; index < overlong.length(); index++) {
            claimed.writeByte('a');
        }
        claimed.writeByte(0);
        assertBadMembership(claimed);
    }

    @Test
    public void partialInvalidAndTrailingMembershipDataNeverApply() {
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(CHARACTER, PARTY, 0x123456, "Aldric", true)
                .toBytes(wire);
        for (int length = 0; length < wire.readableBytes(); length++) {
            assertBadMembership(wire.copy(0, length));
        }
        assertBadMembership(wire.copy().setByte(0, 2));
        assertBadMembership(wire.copy().setByte(17, 2));
        assertBadMembership(wire.copy().setInt(34, -1));
        assertBadMembership(wire.copy().setInt(34, 0x1000000));
        assertBadMembership(wire.copy().setByte(wire.readableBytes() - 1, 2));
        assertBadMembership(wire.copy().writeByte(0));
        ByteBuf account = Unpooled.buffer();
        new LostTalesChatIdentitySyncPacket(null, null, 0, "", false).toBytes(account);
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
        assertNull(decoded.getPartyId());
        assertFalse(decoded.isNarrating());
        assertEquals(0, wire.readableBytes());
    }
}
