package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class CharacterAppearanceSyncPacketTest {

    @Test
    public void roundTripsTheCardDetails() {
        UUID playerId = UUID.randomUUID();
        CharacterAppearance appearance = new CharacterAppearance(
                playerId, "Steve123", "Aragorn", "losttales:human",
                "losttales:male", "losttales:human_ranger_male_2",
                true, 0, "lotr:gondor", 7, 87,
                "", "");
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(
                false, Collections.singletonList(appearance))
                .toBytes(buffer);

        CharacterAppearanceSyncPacket decoded =
                new CharacterAppearanceSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(1, decoded.getAppearances().size());
        CharacterAppearance read = decoded.getAppearances().get(0);
        assertEquals(playerId, read.getPlayerId());
        assertEquals("Steve123", read.getAccountName());
        assertEquals("Aragorn", read.getCharacterName());
        assertEquals("lotr:gondor", read.getStartingFactionId());
        assertEquals(7, read.getRoleplayLevel());
        assertEquals(87, read.getAge());
        assertTrue(read.isPresent());
        // Named by name alone, the character has no id on either side.
        assertNull(read.getCharacterId());
    }

    /** The character's stable id travels with its appearance and never with the account's. */
    @Test
    public void roundTripsTheCharacterId() {
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        CharacterAppearance appearance = new CharacterAppearance(
                playerId, "Steve123", "Aragorn", "losttales:human",
                "losttales:male", "losttales:human_ranger_male_2",
                true, 0, "lotr:gondor", 7, 87, "", "").withCharacterId(characterId);
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(false, Collections.singletonList(appearance))
                .toBytes(buffer);
        CharacterAppearanceSyncPacket decoded = new CharacterAppearanceSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(characterId, decoded.getAppearances().get(0).getCharacterId());

        CharacterAppearance account = CharacterAppearance.forAccount(playerId, "Steve123",
                "wide", true, 0).withCharacterId(characterId);
        assertNull(account.getCharacterId());
    }

    @Test
    public void removalsCarryNoDetailsAndStayDecodable() {
        UUID playerId = UUID.randomUUID();
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(false, Collections.singletonList(
                CharacterAppearance.removed(playerId))).toBytes(buffer);

        CharacterAppearanceSyncPacket decoded =
                new CharacterAppearanceSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        CharacterAppearance read = decoded.getAppearances().get(0);
        assertFalse(read.isPresent());
        assertEquals("", read.getStartingFactionId());
        assertEquals(0, read.getRoleplayLevel());
        assertEquals(0, read.getAge());
    }

    @Test
    public void truncatedDetailsAreFlaggedMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(false, Collections.singletonList(
                new CharacterAppearance(UUID.randomUUID(), "Steve", "Amdir",
                        "losttales:elf", "losttales:male",
                        "losttales:elf_high_male_0", true, 0,
                        "lotr:high_elf", 3, 2000, "", "")))
                .toBytes(buffer);
        ByteBuf truncated = buffer.slice(0, buffer.readableBytes() - 6);

        CharacterAppearanceSyncPacket decoded =
                new CharacterAppearanceSyncPacket();
        decoded.fromBytes(truncated);

        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getAppearances().isEmpty());
    }

    @Test
    public void aLevelOrAgeBelowZeroIsNotKnown() {
        CharacterAppearance appearance = new CharacterAppearance(
                UUID.randomUUID(), "Steve", "Amdir", "losttales:elf",
                "losttales:male", "losttales:elf_high_male_0", true, 0,
                "lotr:high_elf", -4, -1, "", "");
        assertEquals(0, appearance.getRoleplayLevel());
        assertEquals(0, appearance.getAge());
    }
}
