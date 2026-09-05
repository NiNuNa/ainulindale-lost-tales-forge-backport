package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterAppearanceKind;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The account travels on the wire as an identity of its own: the appearance
 * sync names its kind, the private roster carries its cape, and a cape
 * request can name it with an appended flag an older client never sends.
 */
public final class CharacterAccountCapePacketTest {

    private static final UUID OWNER = UUID.fromString(
            "b0000000-0000-0000-0000-00000000000b");

    @Test
    public void theAppearanceSyncCarriesTheAccountAsItself() {
        CharacterAppearance account = CharacterAppearance.forAccount(OWNER, "Steve",
                CharacterBodyTypeRegistry.SLIM, false, CharacterCapeCatalog.GONDOR);
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(true, Collections.singletonList(account))
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
        assertEquals(CharacterCapeCatalog.GONDOR, read.getCosmeticCapeId());
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
    public void thePrivateRosterCarriesTheAccountCapeAndDefaultsWithoutIt() {
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                OWNER, CharacterRoster.INITIAL_UNLOCKED_SLOTS, null, 3L,
                CharacterRoster.CURRENT_DATA_VERSION,
                Collections.<CharacterSummary>emptyList(), false,
                CharacterCapeCatalog.RANGER);
        ByteBuf buffer = Unpooled.buffer();
        new CharacterRosterSyncPacket(1, snapshot).toBytes(buffer);

        CharacterRosterSyncPacket decoded = new CharacterRosterSyncPacket();
        decoded.fromBytes(buffer.copy());
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.getSnapshot().isAccountMinecraftCapeVisible());
        assertEquals(CharacterCapeCatalog.RANGER,
                decoded.getSnapshot().getAccountCosmeticCapeId());

        CharacterRosterSyncPacket older = new CharacterRosterSyncPacket();
        older.fromBytes(buffer.slice(0, buffer.readableBytes() - 3));
        assertFalse(older.isMalformed());
        assertEquals(RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                older.getSnapshot().isAccountMinecraftCapeVisible());
        assertEquals(RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                older.getSnapshot().getAccountCosmeticCapeId());
    }

    @Test
    public void aCapeRequestNamesTheAccountWithTheAppendedFlag() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterCapeUpdateRequestPacket(5, 2L, null, true,
                CharacterCapeCatalog.PELARGIR).toBytes(buffer);
        CharacterCapeUpdateRequestPacket decoded = new CharacterCapeUpdateRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertNull(decoded.getCharacterId());
        assertEquals(CharacterCapeCatalog.PELARGIR, decoded.getCosmeticCapeId());

        UUID character = UUID.fromString("b1000000-0000-0000-0000-00000000001b");
        ByteBuf named = Unpooled.buffer();
        new CharacterCapeUpdateRequestPacket(6, 2L, character, false, 0).toBytes(named);
        CharacterCapeUpdateRequestPacket forCharacter = new CharacterCapeUpdateRequestPacket();
        forCharacter.fromBytes(named.copy());
        assertFalse(forCharacter.isMalformed());
        assertEquals(character, forCharacter.getCharacterId());

        // An older client sends no flag: the id it named still stands.
        CharacterCapeUpdateRequestPacket older = new CharacterCapeUpdateRequestPacket();
        older.fromBytes(named.slice(0, named.readableBytes() - 1));
        assertFalse(older.isMalformed());
        assertEquals(character, older.getCharacterId());

        // The nil id without the flag names nothing.
        ByteBuf nil = Unpooled.buffer();
        new CharacterCapeUpdateRequestPacket(7, 2L, null, true, 0).toBytes(nil);
        CharacterCapeUpdateRequestPacket refused = new CharacterCapeUpdateRequestPacket();
        refused.fromBytes(nil.slice(0, nil.readableBytes() - 1));
        assertTrue(refused.isMalformed());
    }
}
