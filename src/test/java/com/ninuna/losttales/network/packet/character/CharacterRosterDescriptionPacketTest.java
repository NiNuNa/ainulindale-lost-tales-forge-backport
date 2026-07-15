package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class CharacterRosterDescriptionPacketTest {

    @Test
    public void privateRosterPacketRoundTripsDescription() {
        UUID ownerId = UUID.fromString(
                "a0000000-0000-0000-0000-00000000000a");
        UUID characterId = UUID.fromString(
                "a1000000-0000-0000-0000-00000000001a");
        CharacterSummary summary = new CharacterSummary(
                characterId, 0, "Ranger", "losttales:human",
                "losttales:male", "losttales:human_bree_male_0",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                32, "lotr:bree", 1, 0L, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                "A watchful traveller from the North.");
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                ownerId, CharacterRoster.INITIAL_UNLOCKED_SLOTS,
                characterId, 1L, CharacterRoster.CURRENT_DATA_VERSION,
                Collections.singletonList(summary));

        ByteBuf buffer = Unpooled.buffer();
        try {
            new CharacterRosterSyncPacket(7, snapshot).toBytes(buffer);
            CharacterRosterSyncPacket decoded =
                    new CharacterRosterSyncPacket();
            decoded.fromBytes(buffer);

            assertFalse(decoded.isMalformed());
            assertNotNull(decoded.getSnapshot());
            assertEquals("A watchful traveller from the North.",
                    decoded.getSnapshot().getActiveCharacter()
                            .getDescription());
        } finally {
            buffer.release();
        }
    }
}
