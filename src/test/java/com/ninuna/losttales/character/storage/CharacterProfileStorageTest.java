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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A character's profile is kept with its record: every part round-trips,
 * each About text held to its own length. A record without a profile, or
 * with a part not as it is written (a text past its length, a glance that
 * is none, one past the fifth), is refused whole, never emptied.
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
    public void aRecordWithoutAProfileIsRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(profile()));
        record.removeTag("Profile");
        assertRefused(record, "missing_profile");
    }

    /** About text past its length is not emptied and saved over: the record is refused whole. */
    @Test
    public void anAboutTextPastItsLengthIsRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(profile()));
        record.getCompoundTag("Profile").setString("Personality", repeat(
                CharacterProfile.Section.PERSONALITY.getMaxLength() + 1));
        assertRefused(record, "invalid_profile");
    }

    @Test
    public void aGlanceThatIsNoneIsRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(profile()));
        record.getCompoundTag("Profile").getTagList("Glances",
                Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0)
                .setString("Emoji", "not_an_emoji");
        assertRefused(record, "invalid_profile");
    }

    @Test
    public void aGlancePastTheFifthIsRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(profile()));
        NBTTagList glances = record.getCompoundTag("Profile").getTagList(
                "Glances", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < CharacterProfile.MAX_GLANCES; index++) {
            NBTTagCompound extra = new NBTTagCompound();
            extra.setString("Emoji", "smiley");
            extra.setString("Title", "Extra " + index);
            extra.setString("Line", "");
            glances.appendTag(extra);
        }
        assertRefused(record, "invalid_profile");
    }

    @Test
    public void eachAboutTextKeepsItsOwnLength() {
        String history = repeat(CharacterProfile.Section.HISTORY.getMaxLength());
        CharacterProfile full = profile().withSection(
                CharacterProfile.Section.HISTORY, history);
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(character(full)), OWNER);
        assertNotNull(loaded);
        assertEquals("a History of 2,048 characters is kept whole", full,
                loaded.getProfile());

        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(profile()));
        record.getCompoundTag("Profile").setString("Appearance", repeat(
                CharacterProfile.Section.APPEARANCE.getMaxLength() + 1));
        assertRefused(record, "invalid_profile");
    }

    private static void assertRefused(NBTTagCompound record, String reason) {
        try {
            CharacterNbtCodec.readCharacterRecord(record, OWNER);
            fail("a record not as written is refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains(reason));
        }
    }

    private static String repeat(int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append('a');
        }
        return text.toString();
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
