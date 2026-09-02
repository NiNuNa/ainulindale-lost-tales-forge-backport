package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** The arm width rides at the end of both character sync layouts. */
public final class CharacterBodyTypePacketTest {

    @Test
    public void appearancePacketRoundTripsTheBodyType() {
        UUID playerId = UUID.randomUUID();
        CharacterAppearance appearance = new CharacterAppearance(
                playerId, "Steve123", "Aragorn", "losttales:human",
                "losttales:male", CharacterSkinRegistry.ACCOUNT_SKIN_ID,
                true, 0, "lotr:gondor", 7, 87, "", CharacterBodyTypeRegistry.SLIM,
                CharacterChestTypeRegistry.FULL_MEDIUM);
        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterAppearanceSyncPacket(
                    false, Collections.singletonList(appearance)).toBytes(buffer);
            CharacterAppearanceSyncPacket decoded = new CharacterAppearanceSyncPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            CharacterAppearance read = decoded.getAppearances().get(0);
            assertEquals(CharacterBodyTypeRegistry.SLIM, read.getBodyTypeId());
            assertEquals(CharacterChestTypeRegistry.FULL_MEDIUM, read.getChestTypeId());
            assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID, read.getSkinId());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void rosterPacketRoundTripsTheBodyTypeAndRepairsUnknownValues() {
        UUID ownerId = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
        UUID characterId = UUID.fromString("a1000000-0000-0000-0000-00000000001a");
        CharacterSummary summary = new CharacterSummary(
                characterId, 0, "Ranger", "losttales:human",
                "losttales:female", "losttales:human_bree_female_0",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                32, "lotr:bree", 1, 0L, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                "", CharacterBodyTypeRegistry.WIDE, CharacterChestTypeRegistry.NONE);
        assertEquals(CharacterBodyTypeRegistry.WIDE, summary.getBodyTypeId());
        assertEquals(CharacterChestTypeRegistry.NONE, summary.getChestTypeId());
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                ownerId, CharacterRoster.INITIAL_UNLOCKED_SLOTS,
                characterId, 1L, CharacterRoster.CURRENT_DATA_VERSION,
                Collections.singletonList(summary));

        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterRosterSyncPacket(7, snapshot).toBytes(buffer);
            CharacterRosterSyncPacket decoded = new CharacterRosterSyncPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            assertEquals(CharacterBodyTypeRegistry.WIDE,
                    decoded.getSnapshot().getActiveCharacter().getBodyTypeId());
            assertEquals(CharacterChestTypeRegistry.NONE,
                    decoded.getSnapshot().getActiveCharacter().getChestTypeId());
        } finally {
            buffer.release();
        }

        CharacterSummary unknown = new CharacterSummary(
                characterId, 0, "Ranger", "losttales:human",
                "losttales:female", "losttales:human_bree_female_0",
                true, 0, 32, "lotr:bree", 1, 0L, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION, "", "losttales:huge");
        assertEquals(CharacterBodyTypeRegistry.SLIM, unknown.getBodyTypeId());
    }
}
