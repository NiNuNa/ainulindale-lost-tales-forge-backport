package com.ninuna.losttales.character.storage;

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
 * A record without an arm width derives it from the sex, an unknown
 * value is repaired the same way, and a stored choice that differs from
 * the sex's default survives a round trip.
 */
public final class CharacterBodyTypeMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");
    private static final UUID CHARACTER = UUID.fromString(
            "91000000-0000-0000-0000-000000000019");

    @Test
    public void aRecordWithoutABodyTypeTakesTheSexDefault() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(CharacterGenderRegistry.FEMALE, CharacterBodyTypeRegistry.WIDE));
        record.removeTag("BodyTypeId");

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(record, OWNER);

        assertNotNull(loaded);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION, loaded.getDataVersion());
        assertEquals(CharacterBodyTypeRegistry.SLIM, loaded.getBodyTypeId());
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
                CharacterNbtCodec.writeCharacterRecord(
                        RoleplayCharacter.builder(CHARACTER, OWNER)
                                .name("Traveller")
                                .race(CharacterRaceRegistry.HUMAN)
                                .gender(CharacterGenderRegistry.FEMALE)
                                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                                .age(30).startingFaction("lotr:bree")
                                .createdAt(1L)
                                .bodyType(CharacterBodyTypeRegistry.WIDE)
                                .build()),
                OWNER);
        assertNotNull(loaded);
        assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID, loaded.getSkinId());
        assertEquals(CharacterBodyTypeRegistry.WIDE, loaded.getBodyTypeId());
    }

    private static RoleplayCharacter character(String genderId, String bodyTypeId) {
        String skinId = CharacterSkinRegistry.getDefaultSkinId(
                CharacterRaceRegistry.HUMAN, genderId, CHARACTER);
        return RoleplayCharacter.builder(CHARACTER, OWNER)
                .name("Traveller").race(CharacterRaceRegistry.HUMAN)
                .gender(genderId).skin(skinId).age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .bodyType(bodyTypeId)
                .build();
    }
}
