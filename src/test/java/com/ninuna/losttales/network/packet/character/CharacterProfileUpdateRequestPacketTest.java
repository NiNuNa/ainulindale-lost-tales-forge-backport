package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A profile request carries one character's profile and age; a profile
 * sent back carries the profile or word that it cannot be read. The wire
 * only bounds the words and leaves the rest for the server to judge, and
 * a payload it cannot read is flagged, never thrown.
 */
public final class CharacterProfileUpdateRequestPacketTest {

    private static final UUID CHARACTER = UUID.fromString(
            "c1000000-0000-0000-0000-00000000001c");

    private static CharacterProfile profile() {
        return CharacterProfile.EMPTY
                .withSection(CharacterProfile.Section.APPEARANCE, "Tall, grey.")
                .withSection(CharacterProfile.Section.HISTORY,
                        "A ranger of the North.\n\nBorn in Bree.")
                .withFact(CharacterProfile.Fact.HOME, "Bree")
                .withGlances(Arrays.asList(
                        new CharacterProfile.Glance("smiley", "Cheerful", ""),
                        new CharacterProfile.Glance("grinning", "Scarred",
                                "An old wound across the brow.")));
    }

    @Test
    public void aRequestSurvivesTheWire() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterProfileUpdateRequestPacket(7, 3L, CHARACTER, profile(), 87)
                .toBytes(buffer);
        CharacterProfileUpdateRequestPacket decoded =
                new CharacterProfileUpdateRequestPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(CHARACTER, decoded.getCharacterId());
        assertEquals(profile(), decoded.getProfile());
        assertEquals(87, decoded.getAge());
    }

    @Test
    public void aProfileSentBackSaysWhetherItCouldBeRead() {
        ByteBuf buffer = Unpooled.buffer();
        CharacterProfilePacket.of(CHARACTER, profile()).toBytes(buffer);
        CharacterProfilePacket decoded = new CharacterProfilePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isAvailable());
        assertEquals(profile(), decoded.getProfile());

        buffer = Unpooled.buffer();
        CharacterProfilePacket.unavailable(CHARACTER).toBytes(buffer);
        decoded = new CharacterProfilePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.isAvailable());
        assertEquals(CharacterProfile.EMPTY, decoded.getProfile());

        buffer = Unpooled.buffer();
        new CharacterProfileRequestPacket(CHARACTER).toBytes(buffer);
        CharacterProfileRequestPacket asked = new CharacterProfileRequestPacket();
        asked.fromBytes(buffer);
        assertFalse(asked.isMalformed());
        assertEquals(CHARACTER, asked.getCharacterId());
    }

    @Test
    public void aShortOrPaddedPayloadIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterProfileUpdateRequestPacket(7, 3L, CHARACTER, profile(), 30)
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
        nil.fromBytes(payload(3L, new UUID(0L, 0L), 0, 0));
        assertTrue(nil.isMalformed());

        CharacterProfileUpdateRequestPacket negative =
                new CharacterProfileUpdateRequestPacket();
        negative.fromBytes(payload(-1L, CHARACTER, 0, 0));
        assertTrue(negative.isMalformed());

        CharacterProfileRequestPacket asked = new CharacterProfileRequestPacket();
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeLong(0L);
        buffer.writeLong(0L);
        asked.fromBytes(buffer);
        assertTrue(asked.isMalformed());
    }

    @Test
    public void wordsPastTheWireBoundAreMalformedAndNeverBuilt() {
        CharacterProfileUpdateRequestPacket decoded =
                new CharacterProfileUpdateRequestPacket();
        decoded.fromBytes(payload(3L, CHARACTER,
                CharacterPacketCodec.MAX_SECTION_BYTES + 1, 0));
        assertTrue(decoded.isMalformed());

        CharacterProfileUpdateRequestPacket crowded =
                new CharacterProfileUpdateRequestPacket();
        crowded.fromBytes(payload(3L, CHARACTER, 0,
                CharacterProfile.MAX_GLANCES + 1));
        assertTrue("more glances than a profile holds", crowded.isMalformed());

        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= CharacterPacketCodec.MAX_SECTION_BYTES; i++) {
            text.append('a');
        }
        try {
            new CharacterProfileUpdateRequestPacket(1, 3L, CHARACTER,
                    CharacterProfile.EMPTY.withSection(
                            CharacterProfile.Section.HISTORY, text.toString()),
                    20);
            fail("words past the wire bound must not become a request");
        } catch (IllegalArgumentException expected) {
            // The sender reports a local failure instead of sending it.
        }
    }

    @Test
    public void anAgeTheServerRefusesStillReads() {
        // The refusal is the server's to give, with the reason attached.
        ByteBuf buffer = Unpooled.buffer();
        new CharacterProfileUpdateRequestPacket(1, 3L, CHARACTER,
                CharacterProfile.EMPTY, -5).toBytes(buffer);
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

    /**
     * A request written by hand: an Appearance of that many bytes, the
     * other texts and the facts empty, and that many glances claimed.
     */
    private static ByteBuf payload(long revision, UUID characterId,
                                   int appearanceBytes, int glances) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(1);
        buffer.writeLong(revision);
        buffer.writeLong(characterId.getMostSignificantBits());
        buffer.writeLong(characterId.getLeastSignificantBits());
        buffer.writeShort(appearanceBytes);
        for (int i = 0; i < appearanceBytes; i++) {
            buffer.writeByte('a');
        }
        int emptyStrings = CharacterProfile.Section.values().length - 1
                + CharacterProfile.Fact.values().length;
        for (int i = 0; i < emptyStrings; i++) {
            buffer.writeShort(0);
        }
        buffer.writeByte(glances);
        buffer.writeInt(20);
        return buffer;
    }
}
