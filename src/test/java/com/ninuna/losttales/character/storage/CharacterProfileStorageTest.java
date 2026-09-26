package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A character's profile is kept with its record: every part round-trips,
 * a record without one reads as saying nothing, and a part that is no
 * longer valid as stored is read as empty — a glance that no longer is
 * one, or one past the fifth, is left out.
 */
public final class CharacterProfileStorageTest {

    private static final UUID OWNER = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");
    private static final UUID CHARACTER = UUID.fromString(
            "91000000-0000-0000-0000-000000000019");

    private static CharacterProfile profile() {
        return CharacterProfile.EMPTY
                .withSection(CharacterProfile.Section.APPEARANCE, "Tall, grey.")
                .withSection(CharacterProfile.Section.PERSONALITY, "Patient.")
                .withSection(CharacterProfile.Section.HISTORY,
                        "A traveller from Bree.\n\nNow of the Shire.")
                .withFact(CharacterProfile.Fact.EYES, "Grey")
                .withFact(CharacterProfile.Fact.HOME, "Hobbiton")
                .withGlances(Arrays.asList(
                        new CharacterProfile.Glance("smiley", "Cheerful", ""),
                        new CharacterProfile.Glance("grinning", "Scarred",
                                "An old wound across the brow.")));
    }

    @Test
    public void aProfileRoundTripsThroughADetachedRecord() {
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(character(profile())),
                OWNER);
        assertNotNull(loaded);
        assertEquals(profile(), loaded.getProfile());
    }

    @Test
    public void aRecordWithoutAProfileSaysNothing() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(profile()));
        record.removeTag("Profile");
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                record, OWNER);
        assertNotNull(loaded);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION,
                loaded.getDataVersion());
        assertEquals(CharacterProfile.EMPTY, loaded.getProfile());
    }

    @Test
    public void aPartNoLongerValidIsReadAsEmpty() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(profile()));
        NBTTagCompound stored = record.getCompoundTag("Profile");
        StringBuilder tooLong = new StringBuilder();
        for (int index = 0; index <= CharacterProfile.MAX_SECTION_LENGTH;
             index++) {
            tooLong.append('a');
        }
        stored.setString("Personality", tooLong.toString());
        NBTTagList glances = stored.getTagList("Glances",
                Constants.NBT.TAG_COMPOUND);
        glances.getCompoundTagAt(0).setString("Emoji", "not_an_emoji");
        for (int index = 0; index < CharacterProfile.MAX_GLANCES; index++) {
            NBTTagCompound extra = new NBTTagCompound();
            extra.setString("Emoji", "smiley");
            extra.setString("Title", "Extra " + index);
            extra.setString("Line", "");
            glances.appendTag(extra);
        }

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                record, OWNER);
        assertNotNull(loaded);
        CharacterProfile read = loaded.getProfile();
        assertEquals("", read.section(CharacterProfile.Section.PERSONALITY));
        assertEquals("Tall, grey.",
                read.section(CharacterProfile.Section.APPEARANCE));
        assertEquals(CharacterProfile.MAX_GLANCES, read.glances().size());
        assertEquals("Scarred", read.glances().get(0).getTitle());
    }

    private static RoleplayCharacter character(CharacterProfile profile) {
        String skinId = CharacterSkinRegistry.getCompatibleSkins(
                CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE).get(0).getId();
        return RoleplayCharacter.builder(CHARACTER, OWNER)
                .name("Traveller").race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE).skin(skinId).age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .profile(profile)
                .build();
    }
}
