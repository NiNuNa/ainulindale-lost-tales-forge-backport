package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A profile request carries one character's description and age. The wire
 * only bounds the text and leaves the rest for the server to judge, and a
 * payload it cannot read is flagged, never thrown.
 */
public final class CharacterProfileUpdateRequestPacketTest {

    private static final UUID CHARACTER = UUID.fromString(
            "c1000000-0000-0000-0000-00000000001c");

    @Test
    public void aRequestSurvivesTheWire() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterProfileUpdateRequestPacket(7, 3L, CHARACTER,
                "A ranger of the North.", 87).toBytes(buffer);
        CharacterProfileUpdateRequestPacket decoded =
                new CharacterProfileUpdateRequestPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(CHARACTER, decoded.getCharacterId());
        assertEquals("A ranger of the North.", decoded.getDescription());
        assertEquals(87, decoded.getAge());
    }

    @Test
    public void aShortOrPaddedPayloadIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterProfileUpdateRequestPacket(7, 3L, CHARACTER, "Short.", 30)
                .toBytes(buffer);

        CharacterProfileUpdateRequestPacket truncated =
                new CharacterProfileUpdateRequestPacket();
        truncated.fromBytes(buffer.slice(0, buffer.readableBytes() - 1));
        assertTrue(truncated.isMalformed());

        ByteBuf padded = buffer.copy();
        padded.writeByte(0);
        CharacterProfileUpdateRequestPacket extra =
                new CharacterProfileUpdateRequestPacket();
        extra.fromBytes(padded);
        assertTrue(extra.isMalformed());
    }

    @Test
    public void aNilCharacterOrANegativeRevisionIsMalformed() {
        CharacterProfileUpdateRequestPacket nil =
                new CharacterProfileUpdateRequestPacket();
        nil.fromBytes(payload(3L, new UUID(0L, 0L), 0));
        assertTrue(nil.isMalformed());

        CharacterProfileUpdateRequestPacket negative =
                new CharacterProfileUpdateRequestPacket();
        negative.fromBytes(payload(-1L, CHARACTER, 0));
        assertTrue(negative.isMalformed());
    }

    @Test
    public void textPastTheWireBoundIsMalformedAndNeverBuilt() {
        CharacterProfileUpdateRequestPacket decoded =
                new CharacterProfileUpdateRequestPacket();
        decoded.fromBytes(payload(3L, CHARACTER,
                CharacterPacketCodec.MAX_DESCRIPTION_BYTES + 1));
        assertTrue(decoded.isMalformed());

        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= CharacterPacketCodec.MAX_DESCRIPTION_BYTES; i++) {
            text.append('a');
        }
        try {
            new CharacterProfileUpdateRequestPacket(1, 3L, CHARACTER,
                    text.toString(), 20);
            fail("a description past the wire bound must not become a request");
        } catch (IllegalArgumentException expected) {
            // The sender reports a local failure instead of sending it.
        }
    }

    @Test
    public void anAgeTheServerRefusesStillReads() {
        // The refusal is the server's to give, with the reason attached.
        ByteBuf buffer = Unpooled.buffer();
        new CharacterProfileUpdateRequestPacket(1, 3L, CHARACTER, "", -5)
                .toBytes(buffer);
        CharacterProfileUpdateRequestPacket decoded =
                new CharacterProfileUpdateRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(-5, decoded.getAge());
    }

    @Test
    public void aRefusalNamesTheOperationAndItsReason() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterOperationResultPacket(9, CharacterOperationType.PROFILE_UPDATE,
                CharacterErrorId.LORE_CHARACTER_CANNOT_EDIT, 4L).toBytes(buffer);
        CharacterOperationResultPacket decoded = new CharacterOperationResultPacket();
        decoded.fromBytes(buffer);

        assertEquals(CharacterOperationType.PROFILE_UPDATE, decoded.getOperationType());
        assertEquals(CharacterErrorId.LORE_CHARACTER_CANNOT_EDIT, decoded.getErrorId());
    }

    /** A request written by hand, with a description of that many bytes. */
    private static ByteBuf payload(long revision, UUID characterId,
                                   int descriptionBytes) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(1);
        buffer.writeLong(revision);
        buffer.writeLong(characterId.getMostSignificantBits());
        buffer.writeLong(characterId.getLeastSignificantBits());
        buffer.writeShort(descriptionBytes);
        for (int i = 0; i < descriptionBytes; i++) {
            buffer.writeByte('a');
        }
        buffer.writeInt(20);
        return buffer;
    }
}
