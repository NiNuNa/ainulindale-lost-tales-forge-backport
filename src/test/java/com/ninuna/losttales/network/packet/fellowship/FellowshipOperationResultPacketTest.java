package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** An answer naming an error this build does not know is malformed, never read as another error. */
public final class FellowshipOperationResultPacketTest {

    @Test
    public void aKnownErrorIsRead() {
        FellowshipOperationResultPacket read = new FellowshipOperationResultPacket();
        read.fromBytes(refusal(FellowshipErrorId.NOT_LEADER.getId()));
        assertFalse(read.isMalformed());
    }

    @Test
    public void anUnknownErrorIsMalformed() {
        FellowshipOperationResultPacket read = new FellowshipOperationResultPacket();
        read.fromBytes(refusal("no_such_error"));
        assertTrue(read.isMalformed());
    }

    @Test
    public void anErrorIdIsReadExactly() {
        String id = FellowshipErrorId.NOT_LEADER.getId();
        assertNull(FellowshipErrorId.fromId(id.toUpperCase()));
        assertNull(FellowshipErrorId.fromId(" " + id));
        assertNull(FellowshipErrorId.fromId(null));
        assertEquals(FellowshipErrorId.NOT_LEADER, FellowshipErrorId.fromId(id));
    }

    private static ByteBuf refusal(String errorId) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(7);
        buffer.writeByte(FellowshipOperationType.LEAVE.getNetworkId());
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        LostTalesPacketCodec.writeUtf8String(buffer, errorId,
                FellowshipPacketCodec.MAX_ERROR_ID_BYTES);
        buffer.writeLong(-1L);
        buffer.writeBoolean(false);
        return buffer;
    }
}
