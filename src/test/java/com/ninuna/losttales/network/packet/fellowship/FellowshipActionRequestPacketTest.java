package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A new fellowship and a rename carry a name, and only they do; a guide
 * made or unmade and a switch carry a value byte, and only they do. A
 * payload of any other shape is malformed.
 */
public final class FellowshipActionRequestPacketTest {

    private static final UUID IDENTITY =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID FELLOWSHIP =
            UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID MEMBER =
            UUID.fromString("50000000-0000-0000-0000-000000000005");

    @Test
    public void aRenameRoundTrips() {
        FellowshipActionRequestPacket decoded = roundTrip(request(
                FellowshipOperationType.RENAME, FELLOWSHIP, 3L, null,
                "The Grey Company", -1));
        assertFalse(decoded.isMalformed());
        assertEquals(FellowshipOperationType.RENAME, decoded.getOperationType());
        assertEquals("The Grey Company", decoded.getName());
    }

    @Test
    public void aNewFellowshipCarriesItsName() {
        FellowshipActionRequestPacket decoded = roundTrip(request(
                FellowshipOperationType.CREATE, null,
                FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION, null,
                "Rangers", -1));
        assertFalse(decoded.isMalformed());
        assertEquals("Rangers", decoded.getName());
    }

    @Test
    public void otherOperationsCarryNoName() {
        FellowshipActionRequestPacket decoded = roundTrip(request(
                FellowshipOperationType.LEAVE, FELLOWSHIP, 3L, null, null, -1));
        assertFalse(decoded.isMalformed());
        assertNull(decoded.getName());
    }

    @Test
    public void aGuideAndASwitchCarryTheirValue() {
        assertFalse(roundTrip(request(FellowshipOperationType.SET_GUIDE, FELLOWSHIP, 3L,
                MEMBER, null, 1)).isMalformed());
        assertFalse(roundTrip(request(FellowshipOperationType.SET_SWITCH, FELLOWSHIP, 3L,
                null, null, FellowshipOperationType.switchValue(
                        FellowshipSwitch.SHOWN_ON_MAP.getNetworkId(), false))).isMalformed());
    }

    @Test(expected = RuntimeException.class)
    public void aGuideIsYesOrNo() {
        request(FellowshipOperationType.SET_GUIDE, FELLOWSHIP, 3L, MEMBER, null, 2);
    }

    @Test(expected = RuntimeException.class)
    public void aSwitchMustBeOne() {
        request(FellowshipOperationType.SET_SWITCH, FELLOWSHIP, 3L, null, null,
                FellowshipOperationType.switchValue(9, true));
    }

    @Test(expected = RuntimeException.class)
    public void aValueOnAnotherOperationIsRefused() {
        request(FellowshipOperationType.LEAVE, FELLOWSHIP, 3L, null, null, 1);
    }

    @Test(expected = RuntimeException.class)
    public void aRenameNeedsTheRevision() {
        request(FellowshipOperationType.RENAME, null,
                FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION, null,
                "The Grey Company", -1);
    }

    @Test
    public void aNameOnAnotherOperationIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            request(FellowshipOperationType.LEAVE, FELLOWSHIP, 3L, null, null, -1)
                    .toBytes(buffer);
            LostTalesPacketCodec.writeUtf8String(buffer, "Stowaway", 64);
            FellowshipActionRequestPacket decoded = new FellowshipActionRequestPacket();
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
            request(FellowshipOperationType.RENAME, FELLOWSHIP, 3L, null, "x", -1)
                    .toBytes(buffer);
            // The same request with a name far past what a fellowship's name
            // can be in place of the one it carried.
            buffer.writerIndex(buffer.writerIndex() - 2);
            StringBuilder huge = new StringBuilder();
            for (int index = 0; index <= FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES; index++) {
                huge.append('x');
            }
            LostTalesPacketCodec.writeUtf8String(buffer, huge.toString(), 1024);
            FellowshipActionRequestPacket decoded = new FellowshipActionRequestPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
        } finally {
            buffer.release();
        }
    }

    private static FellowshipActionRequestPacket request(FellowshipOperationType type,
                                                         UUID fellowship, long revision,
                                                         UUID target, String name,
                                                         int value) {
        return new FellowshipActionRequestPacket(7, type, IDENTITY, fellowship, revision,
                target, null, false, 0, 0.0D, 0.0D, name, value);
    }

    private static FellowshipActionRequestPacket roundTrip(FellowshipActionRequestPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            FellowshipActionRequestPacket decoded = new FellowshipActionRequestPacket();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
