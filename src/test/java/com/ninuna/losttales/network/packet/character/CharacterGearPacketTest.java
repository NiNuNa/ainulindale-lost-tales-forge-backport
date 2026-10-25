package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.sync.CharacterGear;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The stage's question about a character's gear and its answer (CHAR-42):
 * an id one way, an id and five stacks the other, and a payload of any
 * other shape discarded whole.
 */
public final class CharacterGearPacketTest {

    private static final UUID CHARACTER =
            UUID.fromString("6f1c2b1e-3d4a-4f5b-9c8d-7e6f5a4b3c2d");

    @Test
    public void aQuestionCarriesItsCharacter() {
        CharacterGearRequestPacket read = new CharacterGearRequestPacket();
        read.fromBytes(bytesOf(new CharacterGearRequestPacket(CHARACTER)));
        assertFalse(read.isMalformed());
        assertEquals(CHARACTER, read.getCharacterId());
    }

    @Test
    public void aQuestionWithoutACharacterIsNeverMade() {
        try {
            new CharacterGearRequestPacket(null);
            fail("a null id");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        try {
            new CharacterGearRequestPacket(new UUID(0L, 0L));
            fail("the nil id");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void aQuestionOfAnyOtherShapeIsMalformed() {
        ByteBuf nil = Unpooled.buffer();
        LostTalesPacketCodec.writeUuid(nil, new UUID(0L, 0L));
        assertRequestMalformed(nil);

        ByteBuf trailing = bytesOf(new CharacterGearRequestPacket(CHARACTER));
        trailing.writeByte(1);
        assertRequestMalformed(trailing);

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeLong(1L);
        assertRequestMalformed(truncated);
    }

    @Test
    public void anAnswerOfNothingReadsAsNothing() {
        CharacterGearPacket read = new CharacterGearPacket();
        ByteBuf buffer = bytesOf(CharacterGearPacket.of(CHARACTER, CharacterGear.NONE));
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertFalse(buffer.isReadable());
        assertEquals(CHARACTER, read.getCharacterId());
        assertTrue(read.getGear().isEmpty());
    }

    @Test
    public void anAnswerIsAnIdAndFiveEmptyStacksAtItsShortest() {
        ByteBuf buffer = bytesOf(CharacterGearPacket.of(CHARACTER, CharacterGear.NONE));
        // An empty stack is Forge's id of -1, two bytes.
        assertEquals(16 + CharacterGear.SLOTS * 2, buffer.readableBytes());
    }

    @Test
    public void anAnswerWithoutAnIdIsNeverMade() {
        try {
            CharacterGearPacket.of(null, CharacterGear.NONE);
            fail("a null id");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        try {
            CharacterGearPacket.of(CHARACTER, null);
            fail("no gear");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void aShortAnswerIsDiscardedWhole() {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesPacketCodec.writeUuid(buffer, CHARACTER);
        buffer.writeShort(-1);
        buffer.writeShort(-1);
        assertAnswerDiscarded(buffer);
    }

    @Test
    public void anAnswerWithBytesLeftOverIsDiscardedWhole() {
        ByteBuf buffer = bytesOf(CharacterGearPacket.of(CHARACTER, CharacterGear.NONE));
        buffer.writeByte(0);
        assertAnswerDiscarded(buffer);
    }

    @Test
    public void anAnswerPastItsBoundIsDiscardedWhole() {
        ByteBuf buffer = bytesOf(CharacterGearPacket.of(CHARACTER, CharacterGear.NONE));
        buffer.writeZero(CharacterGearPacket.MAX_PACKET_BYTES);
        assertAnswerDiscarded(buffer);
    }

    @Test
    public void anAnswerNamingAnItemThisGameLacksIsDiscardedWhole() {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesPacketCodec.writeUuid(buffer, CHARACTER);
        // No item is registered while the tests run: id 5 stands for one
        // this client does not know.
        buffer.writeShort(5);
        buffer.writeByte(1);
        buffer.writeShort(0);
        buffer.writeShort(-1);
        for (int slot = 1; slot < CharacterGear.SLOTS; slot++) {
            buffer.writeShort(-1);
        }
        assertAnswerDiscarded(buffer);
    }

    @Test
    public void theWholeAnswerStaysInsideOnePayload() {
        assertTrue(CharacterGearPacket.MAX_PACKET_BYTES < 32767);
    }

    @Test
    public void noStackStaysNoStack() {
        assertNull(CharacterGearPacket.bounded(null));
    }

    private static void assertRequestMalformed(ByteBuf buffer) {
        CharacterGearRequestPacket read = new CharacterGearRequestPacket();
        read.fromBytes(buffer);
        assertTrue(read.isMalformed());
        assertNull(read.getCharacterId());
        assertFalse(buffer.isReadable());
    }

    private static void assertAnswerDiscarded(ByteBuf buffer) {
        CharacterGearPacket read = new CharacterGearPacket();
        read.fromBytes(buffer);
        assertTrue(read.isMalformed());
        assertNull("nothing of a discarded answer is kept", read.getCharacterId());
        assertSame(CharacterGear.NONE, read.getGear());
        assertFalse(buffer.isReadable());
    }

    private static ByteBuf bytesOf(CharacterGearRequestPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer;
    }

    private static ByteBuf bytesOf(CharacterGearPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer;
    }
}
