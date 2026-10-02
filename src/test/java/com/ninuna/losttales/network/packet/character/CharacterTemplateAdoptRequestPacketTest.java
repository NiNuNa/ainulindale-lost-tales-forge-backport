package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.server.CharacterTemplateAdoption;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The wire layout a world's one reading of the account template travels
 * in, and the payloads a server must refuse rather than act on.
 */
public final class CharacterTemplateAdoptRequestPacketTest {

    @Test
    public void anOfferedTemplateRoundTrips() {
        CharacterTemplateAdoption sent = new CharacterTemplateAdoption(
                true, "Beren", "lotr:human", "male", "human_male_1",
                "slim", "flat", "A ranger of the north.", 34, false, 3);
        CharacterTemplateAdoption decoded = roundTrip(sent).toAdoption();
        assertFalse(decoded.isMinecraftCapeVisible());
        assertEquals(3, decoded.getCosmeticCapeId());
        assertTrue(decoded.isOffered());
        assertEquals("Beren", decoded.getName());
        assertEquals("lotr:human", decoded.getRaceId());
        assertEquals("male", decoded.getGenderId());
        assertEquals("human_male_1", decoded.getSkinId());
        assertEquals("slim", decoded.getBodyTypeId());
        assertEquals("flat", decoded.getChestTypeId());
        assertEquals("A ranger of the north.", decoded.getHistory());
        assertEquals(34, decoded.getAge());
    }

    @Test
    public void aCapeIdOutOfTheCatalogueRangeIsMalformed() {
        CharacterTemplateAdoption sent = new CharacterTemplateAdoption(
                true, "Beren", "lotr:human", "male", "human_male_1",
                "slim", "flat", "", 34, true, 70000);
        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterTemplateAdoptRequestPacket(9, sent).toBytes(buffer);
            CharacterTemplateAdoptRequestPacket decoded =
                    new CharacterTemplateAdoptRequestPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void anAccountWithNoTemplateStillSpendsTheReading() {
        CharacterTemplateAdoptRequestPacket decoded =
                roundTrip(CharacterTemplateAdoption.none());
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.toAdoption().isOffered());
    }

    @Test
    public void aTruncatedPayloadIsMalformedRatherThanPartlyRead() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(1);
            buffer.writeBoolean(true);
            CharacterTemplateAdoptRequestPacket packet =
                    new CharacterTemplateAdoptRequestPacket();
            packet.fromBytes(buffer);
            assertTrue(packet.isMalformed());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void trailingBytesAreMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterTemplateAdoptRequestPacket(
                    2, CharacterTemplateAdoption.none()).toBytes(buffer);
            buffer.writeByte(0);
            CharacterTemplateAdoptRequestPacket packet =
                    new CharacterTemplateAdoptRequestPacket();
            packet.fromBytes(buffer);
            assertTrue(packet.isMalformed());
        } finally {
            buffer.release();
        }
    }

    private static CharacterTemplateAdoptRequestPacket roundTrip(
            CharacterTemplateAdoption adoption) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterTemplateAdoptRequestPacket(9, adoption).toBytes(buffer);
            CharacterTemplateAdoptRequestPacket decoded =
                    new CharacterTemplateAdoptRequestPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
