package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import org.junit.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The server accepts any registered arm width and fills in the sex default. */
public final class CharacterBodyTypeValidationTest {

    private static final String FACTION = "lotr:test_bree";
    private static final String WAYPOINT = "lotr:test_bree_gate";

    @Test
    public void slimMaleWithAccountSkinIsValid() {
        CharacterCreationValidationResult result = validate(request(
                CharacterGenderRegistry.MALE, CharacterSkinRegistry.ACCOUNT_SKIN_ID,
                CharacterBodyTypeRegistry.SLIM));
        assertTrue(result.isValid());
        assertEquals(CharacterBodyTypeRegistry.SLIM, result.getCreation().getBodyTypeId());
        assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID, result.getCreation().getSkinId());
    }

    @Test
    public void wideFemaleWithLotrSkinIsValid() {
        CharacterCreationValidationResult result = validate(request(
                CharacterGenderRegistry.FEMALE, "losttales:human_bree_female_0",
                CharacterBodyTypeRegistry.WIDE));
        assertTrue(result.isValid());
        assertEquals(CharacterBodyTypeRegistry.WIDE, result.getCreation().getBodyTypeId());
    }

    @Test
    public void blankBodyTypeTakesTheSexDefault() {
        CharacterCreationValidationResult female = validate(request(
                CharacterGenderRegistry.FEMALE, "losttales:human_bree_female_0", ""));
        assertTrue(female.isValid());
        assertEquals(CharacterBodyTypeRegistry.SLIM, female.getCreation().getBodyTypeId());
        CharacterCreationValidationResult male = validate(request(
                CharacterGenderRegistry.MALE, "losttales:human_bree_male_0", null));
        assertTrue(male.isValid());
        assertEquals(CharacterBodyTypeRegistry.WIDE, male.getCreation().getBodyTypeId());
    }

    @Test
    public void chestTypeIsValidatedAndDefaultsFromTheSex() {
        CharacterCreationValidationResult chosen = validate(new CharacterCreationRequest(
                0L, 0, "Adventurer", CharacterRaceRegistry.HUMAN, CharacterGenderRegistry.MALE,
                "losttales:human_bree_male_0", 25, FACTION, WAYPOINT, false, "",
                CharacterBodyTypeRegistry.WIDE, CharacterChestTypeRegistry.FULL_LARGE));
        assertTrue(chosen.isValid());
        assertEquals(CharacterChestTypeRegistry.FULL_LARGE, chosen.getCreation().getChestTypeId());

        CharacterCreationValidationResult defaulted = validate(request(
                CharacterGenderRegistry.FEMALE, "losttales:human_bree_female_0", ""));
        assertTrue(defaulted.isValid());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM,
                defaulted.getCreation().getChestTypeId());

        CharacterCreationValidationResult rejected = validate(new CharacterCreationRequest(
                0L, 0, "Adventurer", CharacterRaceRegistry.HUMAN, CharacterGenderRegistry.MALE,
                "losttales:human_bree_male_0", 25, FACTION, WAYPOINT, false, "",
                CharacterBodyTypeRegistry.WIDE, "losttales:huge"));
        assertEquals(CharacterErrorId.INVALID_CHEST_TYPE, rejected.getErrorId());
    }

    @Test
    public void unknownBodyTypeIsRejected() {
        CharacterCreationValidationResult result = validate(request(
                CharacterGenderRegistry.MALE, "losttales:human_bree_male_0",
                "losttales:huge"));
        assertEquals(CharacterErrorId.INVALID_BODY_TYPE, result.getErrorId());
    }

    private static CharacterCreationValidationResult validate(
            CharacterCreationRequest request) {
        return CharacterValidator.validateCreation(
                new CharacterRoster(UUID.fromString(
                        "70000000-0000-0000-0000-000000000007")),
                request,
                new TestFactionResolver());
    }

    private static CharacterCreationRequest request(String genderId, String skinId,
                                                     String bodyTypeId) {
        return new CharacterCreationRequest(
                0L, 0, "Adventurer", CharacterRaceRegistry.HUMAN, genderId,
                skinId, 25, FACTION, WAYPOINT, false, "", bodyTypeId);
    }

    private static final class TestFactionResolver implements CharacterFactionResolver {

        private final CharacterFactionDefinition bree = new CharacterFactionDefinition(
                FACTION, true, EnumSet.of(CharacterFactionCategory.HUMAN));

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getUnavailableReason() {
            return "";
        }

        @Override
        public CharacterFactionDefinition resolve(String factionId) {
            return FACTION.equals(factionId) ? this.bree : null;
        }

        @Override
        public String resolveStartingWaypointId(String factionId, String waypointId,
                                                boolean allowAnyRegion) {
            return FACTION.equals(factionId) && WAYPOINT.equals(waypointId)
                    ? waypointId : null;
        }
    }
}
