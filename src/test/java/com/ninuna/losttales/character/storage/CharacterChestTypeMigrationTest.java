package com.ninuna.losttales.character.storage;

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
 * A record without a chest type derives it from the sex, an unknown
 * value is repaired the same way, and a stored choice that differs from
 * the sex's default survives a round trip.
 */
public final class CharacterChestTypeMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");
    private static final UUID CHARACTER = UUID.fromString(
            "91000000-0000-0000-0000-000000000019");

    @Test
    public void aRecordWithoutAChestTypeTakesTheSexDefault() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(CharacterGenderRegistry.FEMALE, CharacterChestTypeRegistry.NONE));
        record.removeTag("ChestTypeId");

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(record, OWNER);

        assertNotNull(loaded);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION, loaded.getDataVersion());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM, loaded.getChestTypeId());
        assertEquals(CharacterBodyTypeRegistry.SLIM, loaded.getBodyTypeId());
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
        return RoleplayCharacter.builder(CHARACTER, OWNER)
                .name("Traveller").race(CharacterRaceRegistry.HUMAN)
                .gender(genderId).skin(skinId).age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .bodyType(CharacterBodyTypeRegistry.defaultFor(genderId))
                .chestType(chestTypeId)
                .build();
    }
}
