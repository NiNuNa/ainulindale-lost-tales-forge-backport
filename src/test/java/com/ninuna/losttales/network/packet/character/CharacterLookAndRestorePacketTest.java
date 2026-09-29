package com.ninuna.losttales.network.packet.character;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The look update (70) and the restore request (71) round-trip, and a
 * payload of any other shape is malformed rather than read in part.
 */
public final class CharacterLookAndRestorePacketTest {

    private static final UUID CHARACTER =
            UUID.fromString("d8000000-0000-0000-0000-00000000008d");

    @Test
    public void aLookUpdateRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterLookUpdateRequestPacket(4, 9L, CHARACTER,
                "losttales:human_bree_male_3", "losttales:slim",
                "losttales:classic").toBytes(buffer);
        CharacterLookUpdateRequestPacket decoded =
                new CharacterLookUpdateRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(CHARACTER, decoded.getCharacterId());
        assertEquals("losttales:human_bree_male_3", decoded.getSkinId());
        assertEquals("losttales:slim", decoded.getBodyTypeId());
        assertEquals("losttales:classic", decoded.getChestTypeId());
    }

    @Test
    public void aLookUpdateCutShortOrLongerIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterLookUpdateRequestPacket(4, 9L, CHARACTER, "a", "b", "c")
                .toBytes(buffer);
        CharacterLookUpdateRequestPacket shorter =
                new CharacterLookUpdateRequestPacket();
        shorter.fromBytes(buffer.slice(0, buffer.readableBytes() - 1));
        assertTrue(shorter.isMalformed());
        assertEquals("", shorter.getSkinId());

        buffer.writeByte(0);
        CharacterLookUpdateRequestPacket longer =
                new CharacterLookUpdateRequestPacket();
        longer.fromBytes(buffer);
        assertTrue(longer.isMalformed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aLookUpdateForNobodyIsNeverBuilt() {
        new CharacterLookUpdateRequestPacket(4, 9L, new UUID(0L, 0L),
                "a", "b", "c");
    }

    @Test
    public void aRestoreRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterRestoreRequestPacket(5, 3L, CHARACTER).toBytes(buffer);
        CharacterRestoreRequestPacket decoded =
                new CharacterRestoreRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(CHARACTER, decoded.getCharacterId());
    }

    @Test
    public void aRestoreWithANegativeRevisionOrTrailingBytesIsMalformed() {
        ByteBuf negative = Unpooled.buffer();
        negative.writeInt(5);
        negative.writeLong(-1L);
        negative.writeLong(CHARACTER.getMostSignificantBits());
        negative.writeLong(CHARACTER.getLeastSignificantBits());
        CharacterRestoreRequestPacket decoded =
                new CharacterRestoreRequestPacket();
        decoded.fromBytes(negative);
        assertTrue(decoded.isMalformed());

        ByteBuf trailing = Unpooled.buffer();
        new CharacterRestoreRequestPacket(5, 3L, CHARACTER).toBytes(trailing);
        trailing.writeByte(1);
        CharacterRestoreRequestPacket longer =
                new CharacterRestoreRequestPacket();
        longer.fromBytes(trailing);
        assertTrue(longer.isMalformed());
    }
}
