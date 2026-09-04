package com.ninuna.losttales.network.packet.character;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharacterSelectRequestPacketTest {

    private static final UUID OWNER =
            UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID CHARACTER =
            UUID.fromString("a1000000-0000-0000-0000-00000000001a");

    @Test
    public void aCharacterSelectionRoundTrips() {
        CharacterSelectRequestPacket decoded = roundTrip(
                new CharacterSelectRequestPacket(3, 7L, CHARACTER));
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.isSelectAccount());
        assertEquals(CHARACTER, decoded.getCharacterId());
    }

    @Test
    public void anAccountSelectionCarriesTheFlag() {
        CharacterSelectRequestPacket decoded = roundTrip(
                CharacterSelectRequestPacket.forAccount(3, 7L, OWNER));
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isSelectAccount());
        assertEquals(OWNER, decoded.getCharacterId());
    }

    @Test
    public void aRequestWithoutTheTrailingFlagIsACharacterSelection() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(3);
            buffer.writeLong(7L);
            buffer.writeLong(CHARACTER.getMostSignificantBits());
            buffer.writeLong(CHARACTER.getLeastSignificantBits());
            CharacterSelectRequestPacket decoded = new CharacterSelectRequestPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            assertFalse(decoded.isSelectAccount());
            assertEquals(CHARACTER, decoded.getCharacterId());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void trailingBytesBeyondTheFlagAreMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterSelectRequestPacket(3, 7L, CHARACTER).toBytes(buffer);
            buffer.writeByte(1);
            CharacterSelectRequestPacket decoded = new CharacterSelectRequestPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
        } finally {
            buffer.release();
        }
    }

    private static CharacterSelectRequestPacket roundTrip(
            CharacterSelectRequestPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            CharacterSelectRequestPacket decoded = new CharacterSelectRequestPacket();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
