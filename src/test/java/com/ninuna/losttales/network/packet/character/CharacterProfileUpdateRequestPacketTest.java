package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidator;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A profile request carries one character's profile and age; a profile
 * sent back carries the profile or word that it cannot be read. The wire
 * only bounds the words, each About text by its own length, and leaves
 * the rest for the server to judge, and a payload it cannot read is
 * flagged, never thrown. The largest profile the wire takes fits in one
 * custom payload.
 */
public final class CharacterProfileUpdateRequestPacketTest {

    /** The most a 1.7.10 custom payload sent by a client holds, the channel's discriminator byte among it. */
    private static final int CUSTOM_PAYLOAD_LIMIT = 32767;

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
        nil.fromBytes(payload(3L, new UUID(0L, 0L),
                CharacterProfile.Section.APPEARANCE, 0, 0));
        assertTrue(nil.isMalformed());

        CharacterProfileUpdateRequestPacket negative =
                new CharacterProfileUpdateRequestPacket();
        negative.fromBytes(payload(-1L, CHARACTER,
                CharacterProfile.Section.APPEARANCE, 0, 0));
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
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            int bound = CharacterPacketCodec.maxSectionBytes(section);
            CharacterProfileUpdateRequestPacket whole =
                    new CharacterProfileUpdateRequestPacket();
            whole.fromBytes(payload(3L, CHARACTER, section, bound, 0));
            assertFalse(section.getId() + " at its bound", whole.isMalformed());

            CharacterProfileUpdateRequestPacket decoded =
                    new CharacterProfileUpdateRequestPacket();
            decoded.fromBytes(payload(3L, CHARACTER, section, bound + 1, 0));
            assertTrue(section.getId() + " past its bound",
                    decoded.isMalformed());

            try {
                new CharacterProfileUpdateRequestPacket(1, 3L, CHARACTER,
                        CharacterProfile.EMPTY.withSection(section,
                                repeat('a', bound + 1)), 20);
                fail("words past the wire bound must not become a request");
            } catch (IllegalArgumentException expected) {
                // The sender reports a local failure instead of sending it.
            }
        }

        CharacterProfileUpdateRequestPacket crowded =
                new CharacterProfileUpdateRequestPacket();
        crowded.fromBytes(payload(3L, CHARACTER,
                CharacterProfile.Section.APPEARANCE, 0,
                CharacterProfile.MAX_GLANCES + 1));
        assertTrue("more glances than a profile holds", crowded.isMalformed());
    }

    @Test
    public void eachAboutTextHasItsOwnWireBound() {
        assertEquals(CharacterProfile.Section.HISTORY.getMaxLength() * 4,
                CharacterPacketCodec.maxSectionBytes(
                        CharacterProfile.Section.HISTORY));
        assertEquals(CharacterProfile.Section.APPEARANCE.getMaxLength() * 4,
                CharacterPacketCodec.maxSectionBytes(
                        CharacterProfile.Section.APPEARANCE));
        // A History of its full length in four-byte characters goes out
        // and comes back whole.
        CharacterProfile history = CharacterProfile.EMPTY.withSection(
                CharacterProfile.Section.HISTORY, repeat("\uD83D\uDE00",
                        CharacterProfile.Section.HISTORY.getMaxLength()));
        ByteBuf buffer = Unpooled.buffer();
        CharacterProfilePacket.of(CHARACTER, history).toBytes(buffer);
        CharacterProfilePacket decoded = new CharacterProfilePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(history, decoded.getProfile());
    }

    /**
     * The largest profile the wire takes, every text at its byte bound and
     * five glances, fits in one custom payload going either way.
     */
    @Test
    public void theLargestProfileFitsInOnePayload() {
        CharacterProfile largest = CharacterProfile.EMPTY;
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            largest = largest.withSection(section, repeat('a',
                    CharacterPacketCodec.maxSectionBytes(section)));
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            largest = largest.withFact(fact,
                    repeat('a', CharacterPacketCodec.MAX_FACT_BYTES));
        }
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>();
        for (int index = 0; index < CharacterProfile.MAX_GLANCES; index++) {
            glances.add(new CharacterProfile.Glance(
                    repeat('a', CharacterPacketCodec.MAX_GLANCE_EMOJI_BYTES),
                    repeat('a', CharacterPacketCodec.MAX_GLANCE_TITLE_BYTES),
                    repeat('a', CharacterPacketCodec.MAX_GLANCE_LINE_BYTES)));
        }
        largest = largest.withGlances(glances);

        ByteBuf sent = Unpooled.buffer();
        CharacterProfilePacket.of(CHARACTER, largest).toBytes(sent);
        assertTrue("a profile sent back: " + sent.readableBytes(),
                sent.readableBytes() + 1 <= CUSTOM_PAYLOAD_LIMIT);

        ByteBuf asked = Unpooled.buffer();
        new CharacterProfileUpdateRequestPacket(1, 3L, CHARACTER, largest,
                CharacterValidator.MAX_AGE).toBytes(asked);
        assertTrue("a profile edited: " + asked.readableBytes(),
                asked.readableBytes() + 1 <= CUSTOM_PAYLOAD_LIMIT);
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
                CharacterErrorId.LORE_CHARACTER_CANNOT_EDIT).toBytes(buffer);
        CharacterOperationResultPacket decoded = new CharacterOperationResultPacket();
        decoded.fromBytes(buffer);

        assertEquals(CharacterOperationType.PROFILE_UPDATE, decoded.getOperationType());
        assertEquals(CharacterErrorId.LORE_CHARACTER_CANNOT_EDIT, decoded.getErrorId());
    }

    /**
     * A request written by hand: {@code filled} of that many bytes, the
     * other texts and the facts empty, and that many glances claimed.
     */
    private static ByteBuf payload(long revision, UUID characterId,
                                   CharacterProfile.Section filled,
                                   int filledBytes, int glances) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(1);
        buffer.writeLong(revision);
        buffer.writeLong(characterId.getMostSignificantBits());
        buffer.writeLong(characterId.getLeastSignificantBits());
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            int bytes = section == filled ? filledBytes : 0;
            ByteBufUtils.writeVarInt(buffer, bytes, 2);
            for (int i = 0; i < bytes; i++) {
                buffer.writeByte('a');
            }
        }
        for (int i = 0; i < CharacterProfile.Fact.values().length; i++) {
            ByteBufUtils.writeVarInt(buffer, 0, 2);
        }
        buffer.writeByte(glances);
        buffer.writeInt(20);
        return buffer;
    }

    private static String repeat(char unit, int count) {
        return repeat(String.valueOf(unit), count);
    }

    private static String repeat(String unit, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(unit);
        }
        return text.toString();
    }
}
