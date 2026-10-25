package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterArmsRegistry;
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

/** The arms rides at the end of both character sync layouts. */
public final class CharacterArmsPacketTest {

    @Test
    public void appearancePacketRoundTripsTheArms() {
        UUID playerId = UUID.randomUUID();
        CharacterAppearance appearance = new CharacterAppearance(
                playerId, "Steve123", "Aragorn", "losttales:human",
                "losttales:masculine", CharacterSkinRegistry.ACCOUNT_SKIN_ID,
                true, "", "lotr:gondor", 87, CharacterArmsRegistry.SLENDER,
                CharacterChestTypeRegistry.FULL_MEDIUM);
        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterAppearanceSyncPacket(
                    false, Collections.singletonList(appearance)).toBytes(buffer);
            CharacterAppearanceSyncPacket decoded = new CharacterAppearanceSyncPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            CharacterAppearance read = decoded.getAppearances().get(0);
            assertEquals(CharacterArmsRegistry.SLENDER, read.getArmsId());
            assertEquals(CharacterChestTypeRegistry.FULL_MEDIUM, read.getChestTypeId());
            assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID, read.getSkinId());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void rosterPacketRoundTripsTheArmsAndRepairsUnknownValues() {
        UUID ownerId = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
        UUID characterId = UUID.fromString("a1000000-0000-0000-0000-00000000001a");
        CharacterSummary summary = new CharacterSummary(
                characterId, 0, "Ranger", "losttales:human",
                "losttales:feminine", "losttales:human_bree_feminine_0",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                32, "lotr:bree",
                CharacterArmsRegistry.BROAD, CharacterChestTypeRegistry.NONE);
        assertEquals(CharacterArmsRegistry.BROAD, summary.getArmsId());
        assertEquals(CharacterChestTypeRegistry.NONE, summary.getChestTypeId());
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                ownerId, CharacterRoster.INITIAL_UNLOCKED_SLOTS,
                characterId, 1L,
                Collections.singletonList(summary),
                Collections.<com.ninuna.losttales.character.sync.DeletedCharacterSummary>emptyList());

        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterRosterSyncPacket(7, snapshot).toBytes(buffer);
            CharacterRosterSyncPacket decoded = new CharacterRosterSyncPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            assertEquals(CharacterArmsRegistry.BROAD,
                    decoded.getSnapshot().getActiveCharacter().getArmsId());
            assertEquals(CharacterChestTypeRegistry.NONE,
                    decoded.getSnapshot().getActiveCharacter().getChestTypeId());
        } finally {
            buffer.release();
        }

        CharacterSummary unknown = new CharacterSummary(
                characterId, 0, "Ranger", "losttales:human",
                "losttales:feminine", "losttales:human_bree_feminine_0",
                true, "", 32, "lotr:bree", "losttales:huge", "");
        assertEquals(CharacterArmsRegistry.SLENDER, unknown.getArmsId());
    }
}
