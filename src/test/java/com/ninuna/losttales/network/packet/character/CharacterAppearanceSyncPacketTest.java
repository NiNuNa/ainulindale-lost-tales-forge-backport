package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharacterAppearanceSyncPacketTest {

    @Test
    public void roundTripsTheCardDetails() {
        UUID playerId = UUID.randomUUID();
        CharacterAppearance appearance = new CharacterAppearance(
                playerId, "Steve123", "Aragorn", "losttales:human",
                "losttales:male", "losttales:human_ranger_male_2",
                true, 0, "lotr:gondor", 7, 87,
                "Heir of Isildur, for now a Ranger of the North.");
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
        assertEquals("Heir of Isildur, for now a Ranger of the North.",
                read.getDescription());
        assertTrue(read.isPresent());
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
        assertEquals("", read.getDescription());
    }

    @Test
    public void truncatedDetailsAreFlaggedMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterAppearanceSyncPacket(false, Collections.singletonList(
                new CharacterAppearance(UUID.randomUUID(), "Steve", "Amdir",
                        "losttales:elf", "losttales:male",
                        "losttales:elf_high_male_0", true, 0,
                        "lotr:high_elf", 3, 2000, "Of Lindon.")))
                .toBytes(buffer);
        ByteBuf truncated = buffer.slice(0, buffer.readableBytes() - 6);

        CharacterAppearanceSyncPacket decoded =
                new CharacterAppearanceSyncPacket();
        decoded.fromBytes(truncated);

        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getAppearances().isEmpty());
    }

    @Test
    public void descriptionsAreBoundedAtTheCreationLimit() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            text.append('a');
        }
        CharacterAppearance appearance = new CharacterAppearance(
                UUID.randomUUID(), "Steve", "Amdir", "losttales:elf",
                "losttales:male", "losttales:elf_high_male_0", true, 0,
                "lotr:high_elf", -4, -1, text.toString());
        assertEquals(CharacterAppearance.MAX_DESCRIPTION_LENGTH,
                appearance.getDescription().length());
        assertEquals(0, appearance.getRoleplayLevel());
        assertEquals(0, appearance.getAge());
    }
}
