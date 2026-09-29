package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Change Look (R1 a) holds a new skin, arm width and chest to the
 * creator's checks for the character's own race and sex, and never
 * touches who the character is.
 */
public final class CharacterLookValidationTest {

    private static final UUID OWNER =
            UUID.fromString("a2000000-0000-0000-0000-00000000002a");

    @Test
    public void aSkinTheRaceAndSexMayWearIsTaken() {
        CharacterAppearanceValidationResult result =
                CharacterValidator.validateLook(human(),
                        "losttales:human_bree_male_3",
                        CharacterBodyTypeRegistry.SLIM,
                        CharacterChestTypeRegistry.CLASSIC);
        assertTrue(result.isValid());
        assertEquals("losttales:human_bree_male_3",
                result.getAppearance().getSkinId());
        assertEquals(CharacterBodyTypeRegistry.SLIM,
                result.getAppearance().getBodyTypeId());
        assertEquals(CharacterChestTypeRegistry.CLASSIC,
                result.getAppearance().getChestTypeId());
    }

    @Test
    public void whoTheCharacterIsStaysAsItIs() {
        ValidatedCharacterAppearance look = CharacterValidator.validateLook(
                human(), "losttales:human_bree_male_3", "", "")
                .getAppearance();
        assertEquals("Aldric", look.getName());
        assertEquals(CharacterRaceRegistry.HUMAN, look.getRaceId());
        assertEquals(CharacterGenderRegistry.MALE, look.getGenderId());
        assertEquals("From Bree.", look.getHistory());
        assertEquals(41, look.getAge());
    }

    @Test
    public void anEmptyArmWidthOrChestTakesTheSexsOwn() {
        ValidatedCharacterAppearance look = CharacterValidator.validateLook(
                human(), "losttales:human_bree_male_3", "", null)
                .getAppearance();
        assertEquals(CharacterBodyTypeRegistry.defaultFor(
                CharacterGenderRegistry.MALE), look.getBodyTypeId());
        assertEquals(CharacterChestTypeRegistry.defaultFor(
                CharacterGenderRegistry.MALE), look.getChestTypeId());
    }

    @Test
    public void aSkinOfAnotherSexOrRaceIsRefused() {
        assertEquals(CharacterErrorId.INVALID_SKIN,
                CharacterValidator.validateLook(human(),
                        "losttales:human_bree_female_0", "", "").getErrorId());
        assertEquals(CharacterErrorId.INVALID_SKIN,
                CharacterValidator.validateLook(human(),
                        "losttales:elf_high_male_0", "", "").getErrorId());
        assertEquals(CharacterErrorId.INVALID_SKIN,
                CharacterValidator.validateLook(human(), "", "", "")
                        .getErrorId());
    }

    @Test
    public void anUnknownArmWidthOrChestIsRefused() {
        assertEquals(CharacterErrorId.INVALID_BODY_TYPE,
                CharacterValidator.validateLook(human(),
                        "losttales:human_bree_male_3", "losttales:huge", "")
                        .getErrorId());
        assertEquals(CharacterErrorId.INVALID_CHEST_TYPE,
                CharacterValidator.validateLook(human(),
                        "losttales:human_bree_male_3", "",
                        "losttales:enormous").getErrorId());
    }

    /** The account's own skin is offered where the race allows it, as in the creator. */
    @Test
    public void theAccountSkinFollowsTheRace() {
        assertTrue(CharacterValidator.validateLook(human(),
                CharacterSkinRegistry.ACCOUNT_SKIN_ID, "", "").isValid());
        RoleplayCharacter troll = RoleplayCharacter.builder(UUID.randomUUID(),
                OWNER).slot(1).name("Grug")
                .race(CharacterRaceRegistry.HALF_TROLL)
                .gender(CharacterGenderRegistry.NON_BINARY)
                .skin("losttales:half_troll_0").age(30)
                .startingFaction("lotr:half_troll").createdAt(1L).build();
        assertFalse(CharacterValidator.validateLook(troll,
                CharacterSkinRegistry.ACCOUNT_SKIN_ID, "", "").isValid());
    }

    @Test
    public void noCharacterIsNoLook() {
        assertEquals(CharacterErrorId.CHARACTER_NOT_FOUND,
                CharacterValidator.validateLook(null,
                        "losttales:human_bree_male_3", "", "").getErrorId());
    }

    private static RoleplayCharacter human() {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(0).name("Aldric").race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_bree_male_0").age(41)
                .startingFaction("lotr:bree")
                .profile(CharacterProfile.EMPTY.withSection(
                        CharacterProfile.Section.HISTORY, "From Bree."))
                .createdAt(1L)
                .build();
    }
}
