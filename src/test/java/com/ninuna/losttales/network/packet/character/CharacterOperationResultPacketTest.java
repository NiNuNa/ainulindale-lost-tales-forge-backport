package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** An answer naming an error this build does not know is malformed, never read as another error. */
public final class CharacterOperationResultPacketTest {

    @Test
    public void aKnownErrorIsRead() {
        CharacterOperationResultPacket read = new CharacterOperationResultPacket();
        read.fromBytes(refusal("stale_roster"));
        assertFalse(read.isMalformed());
        assertEquals(CharacterErrorId.STALE_ROSTER, read.getErrorId());
    }

    @Test
    public void anUnknownErrorIsMalformed() {
        CharacterOperationResultPacket read = new CharacterOperationResultPacket();
        read.fromBytes(refusal("no_such_error"));
        assertTrue(read.isMalformed());
    }

    @Test
    public void anErrorIdIsReadExactly() {
        assertNull(CharacterErrorId.fromId("STALE_ROSTER"));
        assertNull(CharacterErrorId.fromId(" stale_roster"));
        assertNull(CharacterErrorId.fromId(null));
        assertEquals(CharacterErrorId.STALE_ROSTER, CharacterErrorId.fromId("stale_roster"));
    }

    private static ByteBuf refusal(String errorId) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(7);
        buffer.writeByte(CharacterOperationType.SELECT.getNetworkId());
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        LostTalesPacketCodec.writeUtf8String(buffer, errorId,
                CharacterPacketCodec.MAX_ERROR_ID_BYTES);
        buffer.writeLong(-1L);
        buffer.writeBoolean(false);
        return buffer;
    }
}
