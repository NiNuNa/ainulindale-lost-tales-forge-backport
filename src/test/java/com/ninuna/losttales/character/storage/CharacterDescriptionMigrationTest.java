package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class CharacterDescriptionMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");
    private static final UUID CHARACTER = UUID.fromString(
            "91000000-0000-0000-0000-000000000019");

    @Test
    public void versionFiveCharacterMigratesToEmptyDescription() {
        NBTTagCompound legacy = CharacterNbtCodec.writeCharacterRecord(
                character("Temporary description"));
        legacy.setInteger("DataVersion", 5);
        legacy.removeTag("Description");

        RoleplayCharacter migrated = CharacterNbtCodec.readCharacterRecord(
                legacy, OWNER);

        assertNotNull(migrated);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION,
                migrated.getDataVersion());
        assertEquals("", migrated.getDescription());
    }

    @Test
    public void descriptionRoundTripsThroughDetachedRecord() {
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(
                        character("A patient traveller from Bree.")),
                OWNER);

        assertNotNull(loaded);
        assertEquals("A patient traveller from Bree.",
                loaded.getDescription());
    }

    private static RoleplayCharacter character(String description) {
        String skinId = CharacterSkinRegistry.getCompatibleSkins(
                CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE).get(0).getId();
        return RoleplayCharacter.builder(CHARACTER, OWNER)
                .name("Traveller").race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE).skin(skinId).age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .description(description)
                .build();
    }
}
