package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Character data version 7 stores the arm width. Older records derive it
 * from the sex, unknown values are repaired the same way, and a stored
 * choice that differs from the sex's default survives a round trip.
 */
public final class CharacterBodyTypeMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");
    private static final UUID CHARACTER = UUID.fromString(
            "91000000-0000-0000-0000-000000000019");

    @Test
    public void versionSixRecordsTakeTheSexDefault() {
        NBTTagCompound legacy = CharacterNbtCodec.writeCharacterRecord(
                character(CharacterGenderRegistry.FEMALE, CharacterBodyTypeRegistry.WIDE));
        legacy.setInteger("DataVersion", 6);
        legacy.removeTag("BodyTypeId");

        RoleplayCharacter migrated = CharacterNbtCodec.readCharacterRecord(legacy, OWNER);

        assertNotNull(migrated);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION, migrated.getDataVersion());
        assertEquals(CharacterBodyTypeRegistry.SLIM, migrated.getBodyTypeId());
    }

    @Test
    public void unknownStoredBodyTypeIsRepairedToTheSexDefault() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(CharacterGenderRegistry.MALE, CharacterBodyTypeRegistry.SLIM));
        record.setString("BodyTypeId", "losttales:huge");

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(record, OWNER);

        assertNotNull(loaded);
        assertEquals(CharacterBodyTypeRegistry.WIDE, loaded.getBodyTypeId());
    }

    @Test
    public void chosenBodyTypeRoundTripsEvenAgainstTheSexDefault() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(CharacterGenderRegistry.MALE, CharacterBodyTypeRegistry.SLIM));
        assertTrue(record.hasKey("BodyTypeId"));

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(record, OWNER);

        assertNotNull(loaded);
        assertEquals(CharacterBodyTypeRegistry.SLIM, loaded.getBodyTypeId());
    }

    @Test
    public void accountSkinSurvivesTheRoundTrip() {
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(new RoleplayCharacter(
                        CHARACTER, OWNER, 0, "Traveller",
                        CharacterRaceRegistry.HUMAN, CharacterGenderRegistry.FEMALE,
                        CharacterSkinRegistry.ACCOUNT_SKIN_ID, 30, "lotr:bree",
                        RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL,
                        new CharacterProgression(), 1L,
                        RoleplayCharacter.CURRENT_DATA_VERSION,
                        RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                        RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                        "", false, "", CharacterBodyTypeRegistry.WIDE)),
                OWNER);
        assertNotNull(loaded);
        assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID, loaded.getSkinId());
        assertEquals(CharacterBodyTypeRegistry.WIDE, loaded.getBodyTypeId());
    }

    private static RoleplayCharacter character(String genderId, String bodyTypeId) {
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
                "", false, "", bodyTypeId);
    }
}
