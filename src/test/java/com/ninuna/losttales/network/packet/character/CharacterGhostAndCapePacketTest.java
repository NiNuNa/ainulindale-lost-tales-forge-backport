package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterAppearanceKind;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A player still making their first character travels on the wire as a
 * ghost: the appearance sync names its kind, with no cape. A cape request
 * always names one of the player's characters.
 */
public final class CharacterGhostAndCapePacketTest {

    private static final UUID OWNER = UUID.fromString(
            "b0000000-0000-0000-0000-00000000000b");

    @Test
    public void theAppearanceSyncCarriesAGhostAsItself() {
        CharacterAppearance ghost = CharacterAppearance.forAccount(OWNER, "Steve",
                CharacterBodyTypeRegistry.SLIM);
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(true, Collections.singletonList(ghost))
                .toBytes(buffer);
        CharacterAppearanceSyncPacket decoded = new CharacterAppearanceSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        CharacterAppearance read = decoded.getAppearances().get(0);
        assertEquals(CharacterAppearanceKind.ACCOUNT, read.getKind());
        assertTrue(read.isAccount());
        assertEquals("Steve", read.getAccountName());
        assertEquals(CharacterBodyTypeRegistry.SLIM, read.getBodyTypeId());
        assertFalse(read.isMinecraftCapeVisible());
        assertEquals(CharacterCapeCatalog.NONE_ID, read.getCosmeticCapeId());
    }

    @Test
    public void aRemovalKeepsItsKindAndAnUnknownKindIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(false, Collections.singletonList(
                CharacterAppearance.removed(OWNER))).toBytes(buffer);
        CharacterAppearanceSyncPacket decoded = new CharacterAppearanceSyncPacket();
        decoded.fromBytes(buffer.copy());
        assertFalse(decoded.isMalformed());
        assertEquals(CharacterAppearanceKind.NONE,
                decoded.getAppearances().get(0).getKind());

        // The kind is the last byte of the one entry.
        buffer.setByte(buffer.writerIndex() - 1, 200);
        CharacterAppearanceSyncPacket unknown = new CharacterAppearanceSyncPacket();
        unknown.fromBytes(buffer);
        assertTrue(unknown.isMalformed());
    }

    @Test
    public void aCapeRequestNamesItsCharacter() {
        UUID character = UUID.fromString("b1000000-0000-0000-0000-00000000001b");
        ByteBuf named = Unpooled.buffer();
        new CharacterCapeUpdateRequestPacket(6, 2L, character, false,
                CharacterCapeCatalog.PELARGIR).toBytes(named);
        CharacterCapeUpdateRequestPacket forCharacter = new CharacterCapeUpdateRequestPacket();
        forCharacter.fromBytes(named.copy());
        assertFalse(forCharacter.isMalformed());
        assertEquals(character, forCharacter.getCharacterId());
        assertEquals(CharacterCapeCatalog.PELARGIR, forCharacter.getCosmeticCapeId());

        // A request cut short is malformed.
        CharacterCapeUpdateRequestPacket cut = new CharacterCapeUpdateRequestPacket();
        cut.fromBytes(named.slice(0, named.readableBytes() - 1));
        assertTrue(cut.isMalformed());

        // The nil id names no character.
        ByteBuf nil = Unpooled.buffer();
        new CharacterCapeUpdateRequestPacket(7, 2L, new UUID(0L, 0L), true, 0)
                .toBytes(nil);
        CharacterCapeUpdateRequestPacket refused = new CharacterCapeUpdateRequestPacket();
        refused.fromBytes(nil);
        assertTrue(refused.isMalformed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aCapeRequestWithoutACharacterIsNotMade() {
        new CharacterCapeUpdateRequestPacket(5, 2L, null, true, 0);
    }
}
