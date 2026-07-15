package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import org.junit.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharacterUnconventionalCreationValidationTest {

    private static final String ORC_FACTION = "lotr:test_orcs";
    private static final String ORC_WAYPOINT = "lotr:test_mordor";
    private static final String SHIRE_WAYPOINT = "lotr:test_shire";

    @Test
    public void conventionalModeRejectsHumanOrcPairing() {
        CharacterCreationValidationResult result = validate(
                request(humanSkin(), ORC_WAYPOINT, false));

        assertFalse(result.isValid());
        assertEquals(CharacterErrorId.INCOMPATIBLE_RACE_FACTION,
                result.getErrorId());
    }

    @Test
    public void unconventionalModeAllowsCrossFactionAndCrossRegion() {
        CharacterCreationValidationResult result = validate(
                request(humanSkin(), SHIRE_WAYPOINT, true));

        assertTrue(result.isValid());
        assertEquals(SHIRE_WAYPOINT,
                result.getCreation().getStartingWaypointId());
        assertTrue(result.getCreation().hasUnconventionalSettings());
    }

    @Test
    public void unconventionalModeDoesNotBypassSkinModelCompatibility() {
        String hobbitSkin = CharacterSkinRegistry.getCompatibleSkins(
                CharacterRaceRegistry.HOBBIT,
                CharacterGenderRegistry.MALE).get(0).getId();

        CharacterCreationValidationResult result = validate(
                request(hobbitSkin, SHIRE_WAYPOINT, true));

        assertFalse(result.isValid());
        assertEquals(CharacterErrorId.INVALID_SKIN, result.getErrorId());
    }

    @Test
    public void descriptionIsNormalizedAndCarriedByValidatedCreation() {
        CharacterCreationValidationResult result = validate(
                request(humanSkin(), SHIRE_WAYPOINT, true,
                        "  A ranger\tfrom\nBree.  "));

        assertTrue(result.isValid());
        assertEquals("A ranger from Bree.",
                result.getCreation().getDescription());
    }

    @Test
    public void descriptionFormattingCodesAreRejected() {
        CharacterCreationValidationResult result = validate(
                request(humanSkin(), SHIRE_WAYPOINT, true,
                        "A §cformatted biography"));

        assertFalse(result.isValid());
        assertEquals(CharacterErrorId.INVALID_DESCRIPTION,
                result.getErrorId());
    }

    @Test
    public void overlongDescriptionIsRejected() {
        StringBuilder description = new StringBuilder();
        for (int index = 0;
             index <= CharacterValidator.MAX_DESCRIPTION_LENGTH; index++) {
            description.append('a');
        }
        CharacterCreationValidationResult result = validate(
                request(humanSkin(), SHIRE_WAYPOINT, true,
                        description.toString()));

        assertFalse(result.isValid());
        assertEquals(CharacterErrorId.INVALID_DESCRIPTION,
                result.getErrorId());
    }

    private static CharacterCreationValidationResult validate(
            CharacterCreationRequest request) {
        return CharacterValidator.validateCreation(
                new CharacterRoster(UUID.fromString(
                        "70000000-0000-0000-0000-000000000007")),
                request,
                new TestFactionResolver());
    }

    private static CharacterCreationRequest request(String skinId,
                                                     String waypointId,
                                                     boolean unconventional) {
        return request(skinId, waypointId, unconventional, "");
    }

    private static CharacterCreationRequest request(String skinId,
                                                     String waypointId,
                                                     boolean unconventional,
                                                     String description) {
        return new CharacterCreationRequest(
                0L,
                0,
                "Adventurer",
                CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE,
                skinId,
                25,
                ORC_FACTION,
                waypointId,
                unconventional,
                description);
    }

    private static String humanSkin() {
        CharacterSkinDefinition skin = CharacterSkinRegistry.getCompatibleSkins(
                CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE).get(0);
        return skin.getId();
    }

    private static final class TestFactionResolver
            implements CharacterFactionResolver {

        private final CharacterFactionDefinition orcs =
                new CharacterFactionDefinition(
                        ORC_FACTION, true,
                        EnumSet.of(CharacterFactionCategory.ORC));

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
            return ORC_FACTION.equals(factionId) ? this.orcs : null;
        }

        @Override
        public String resolveStartingWaypointId(String factionId,
                                                String waypointId,
                                                boolean allowAnyRegion) {
            if (!ORC_FACTION.equals(factionId)) {
                return null;
            }
            if (ORC_WAYPOINT.equals(waypointId)) {
                return waypointId;
            }
            return allowAnyRegion && SHIRE_WAYPOINT.equals(waypointId)
                    ? waypointId : null;
        }
    }
}
