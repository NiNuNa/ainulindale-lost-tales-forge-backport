package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackedMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The tracking packet carries the members' positions and nothing else; the marks ride in the state. */
public final class FellowshipTrackingSyncPacketTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CHARACTER = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    public void aStateWithoutAFellowshipRoundTrips() {
        FellowshipTrackingSyncPacket decoded = roundTrip(
                FellowshipTrackingSnapshot.noFellowship(OWNER, 7L, CHARACTER));

        assertFalse(decoded.isMalformed());
        assertFalse(decoded.getSnapshot().hasFellowship());
        assertEquals(CHARACTER, decoded.getSnapshot().getActiveIdentityId());
        assertTrue(decoded.getSnapshot().getTrackedMembers().isEmpty());
    }

    @Test
    public void aMembersPositionRoundTrips() {
        FellowshipTrackedMemberSnapshot member = new FellowshipTrackedMemberSnapshot(
                new UUID(3L, 3L), "Beleg", FellowshipColor.BLUE, 100,
                -22000.25D, 71.0D, -7278.5D);
        FellowshipTrackingSnapshot snapshot = new FellowshipTrackingSnapshot(OWNER, 8L,
                CHARACTER, new UUID(4L, 4L), 2L, Collections.singletonList(member));

        FellowshipTrackingSyncPacket decoded = roundTrip(snapshot);

        assertFalse(decoded.isMalformed());
        assertEquals(Collections.singletonList(member),
                decoded.getSnapshot().getTrackedMembers());
    }

    /** A payload with anything after the positions is malformed. */
    @Test
    public void bytesPastThePositionsAreMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new FellowshipTrackingSyncPacket(FellowshipTrackingSnapshot.noFellowship(
                    OWNER, 7L, CHARACTER)).toBytes(buffer);
            buffer.writeByte(0);
            FellowshipTrackingSyncPacket decoded = new FellowshipTrackingSyncPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
        } finally {
            buffer.release();
        }
    }

    private static FellowshipTrackingSyncPacket roundTrip(FellowshipTrackingSnapshot snapshot) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new FellowshipTrackingSyncPacket(snapshot).toBytes(buffer);
            FellowshipTrackingSyncPacket decoded = new FellowshipTrackingSyncPacket();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
