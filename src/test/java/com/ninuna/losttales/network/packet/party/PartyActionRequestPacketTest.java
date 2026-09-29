package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.party.sync.PartyOperationType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A rename carries the party's revision and the name, and only a rename
 * carries a name. A payload of any other shape is malformed.
 */
public final class PartyActionRequestPacketTest {

    private static final UUID IDENTITY =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID PARTY =
            UUID.fromString("40000000-0000-0000-0000-000000000004");

    @Test
    public void aRenameRoundTrips() {
        PartyActionRequestPacket decoded = roundTrip(new PartyActionRequestPacket(
                7, PartyOperationType.RENAME, IDENTITY, PARTY, 3L,
                null, null, false, 0, 0.0D, 0.0D, "The Grey Company"));
        assertFalse(decoded.isMalformed());
        assertEquals(PartyOperationType.RENAME, decoded.getOperationType());
        assertEquals("The Grey Company", decoded.getName());
    }

    @Test
    public void anEmptyNameTakesTheNameAway() {
        PartyActionRequestPacket decoded = roundTrip(new PartyActionRequestPacket(
                7, PartyOperationType.RENAME, IDENTITY, PARTY, 3L,
                null, null, false, 0, 0.0D, 0.0D, ""));
        assertFalse(decoded.isMalformed());
        assertEquals("", decoded.getName());
    }

    @Test
    public void otherOperationsCarryNoName() {
        PartyActionRequestPacket decoded = roundTrip(new PartyActionRequestPacket(
                8, PartyOperationType.LEAVE, IDENTITY, PARTY, 3L,
                null, null, false, 0, 0.0D, 0.0D, null));
        assertFalse(decoded.isMalformed());
        assertNull(decoded.getName());
    }

    @Test(expected = RuntimeException.class)
    public void aRenameNeedsTheRevision() {
        new PartyActionRequestPacket(7, PartyOperationType.RENAME, IDENTITY,
                null, PartyActionRequestPacket.NO_PARTY_REVISION,
                null, null, false, 0, 0.0D, 0.0D, "The Grey Company");
    }

    @Test
    public void aNameOnAnotherOperationIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new PartyActionRequestPacket(8, PartyOperationType.LEAVE, IDENTITY,
                    PARTY, 3L, null, null, false, 0, 0.0D, 0.0D, null)
                    .toBytes(buffer);
            LostTalesPacketCodec.writeUtf8String(buffer, "Stowaway", 64);
            PartyActionRequestPacket decoded = new PartyActionRequestPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void aNameOverItsBoundIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new PartyActionRequestPacket(7, PartyOperationType.RENAME, IDENTITY,
                    PARTY, 3L, null, null, false, 0, 0.0D, 0.0D, "x")
                    .toBytes(buffer);
            // The same request with a name far past what a party's name
            // can be in place of the one it carried.
            buffer.writerIndex(buffer.writerIndex() - 2);
            StringBuilder huge = new StringBuilder();
            for (int index = 0; index <= PartyPacketCodec.MAX_PARTY_NAME_BYTES; index++) {
                huge.append('x');
            }
            LostTalesPacketCodec.writeUtf8String(buffer, huge.toString(), 1024);
            PartyActionRequestPacket decoded = new PartyActionRequestPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
        } finally {
            buffer.release();
        }
    }

    private static PartyActionRequestPacket roundTrip(PartyActionRequestPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            PartyActionRequestPacket decoded = new PartyActionRequestPacket();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
