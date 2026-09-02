package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Character data version 8 stores the chest type. Older records derive it
 * from the sex, unknown values are repaired the same way, and a stored
 * choice that differs from the sex's default survives a round trip.
 */
public final class CharacterChestTypeMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");
    private static final UUID CHARACTER = UUID.fromString(
            "91000000-0000-0000-0000-000000000019");

    @Test
    public void versionSevenRecordsTakeTheSexDefault() {
        NBTTagCompound legacy = CharacterNbtCodec.writeCharacterRecord(
                character(CharacterGenderRegistry.FEMALE, CharacterChestTypeRegistry.NONE));
        legacy.setInteger("DataVersion", 7);
        legacy.removeTag("ChestTypeId");

        RoleplayCharacter migrated = CharacterNbtCodec.readCharacterRecord(legacy, OWNER);

        assertNotNull(migrated);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION, migrated.getDataVersion());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM, migrated.getChestTypeId());
        assertEquals(CharacterBodyTypeRegistry.SLIM, migrated.getBodyTypeId());
    }

    @Test
    public void unknownStoredChestTypeIsRepairedToTheSexDefault() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(CharacterGenderRegistry.MALE, CharacterChestTypeRegistry.FULL_LARGE));
        record.setString("ChestTypeId", "losttales:huge");

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(record, OWNER);

        assertNotNull(loaded);
        assertEquals(CharacterChestTypeRegistry.NONE, loaded.getChestTypeId());
    }

    @Test
    public void chosenChestTypeRoundTripsEvenAgainstTheSexDefault() {
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(character(
                        CharacterGenderRegistry.MALE, CharacterChestTypeRegistry.CLASSIC)),
                OWNER);
        assertNotNull(loaded);
        assertEquals(CharacterChestTypeRegistry.CLASSIC, loaded.getChestTypeId());

        RoleplayCharacter none = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(character(
                        CharacterGenderRegistry.FEMALE, CharacterChestTypeRegistry.NONE)),
                OWNER);
        assertNotNull(none);
        assertEquals(CharacterChestTypeRegistry.NONE, none.getChestTypeId());
    }

    private static RoleplayCharacter character(String genderId, String chestTypeId) {
        String skinId = CharacterSkinRegistry.getDefaultSkinId(
                CharacterRaceRegistry.HUMAN, genderId, CHARACTER);
        return new RoleplayCharacter(
                CHARACTER, OWNER, 0, "Traveller",
                CharacterRaceRegistry.HUMAN, genderId,
                skinId, 30, "lotr:bree",
                RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL,
                new CharacterProgression(), 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                "", false, "", CharacterBodyTypeRegistry.defaultFor(genderId), chestTypeId);
    }
}
