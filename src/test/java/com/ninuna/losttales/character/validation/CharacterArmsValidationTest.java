package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.registry.CharacterArmsRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import org.junit.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The server accepts any registered arms and fills in the body type default. */
public final class CharacterArmsValidationTest {

    private static final String FACTION = "lotr:test_bree";
    private static final String WAYPOINT = "lotr:test_bree_gate";

    @Test
    public void slimMaleWithAccountSkinIsValid() {
        CharacterCreationValidationResult result = validate(request(
                CharacterBodyTypeRegistry.MASCULINE, CharacterSkinRegistry.ACCOUNT_SKIN_ID,
                CharacterArmsRegistry.SLENDER));
        assertTrue(result.isValid());
        assertEquals(CharacterArmsRegistry.SLENDER, result.getCreation().getArmsId());
        assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID, result.getCreation().getSkinId());
    }

    @Test
    public void wideFemaleWithLotrSkinIsValid() {
        CharacterCreationValidationResult result = validate(request(
                CharacterBodyTypeRegistry.FEMININE, "losttales:human_bree_feminine_0",
                CharacterArmsRegistry.BROAD));
        assertTrue(result.isValid());
        assertEquals(CharacterArmsRegistry.BROAD, result.getCreation().getArmsId());
    }

    @Test
    public void blankArmsTakeTheBodyTypesDefault() {
        CharacterCreationValidationResult female = validate(request(
                CharacterBodyTypeRegistry.FEMININE, "losttales:human_bree_feminine_0", ""));
        assertTrue(female.isValid());
        assertEquals(CharacterArmsRegistry.SLENDER, female.getCreation().getArmsId());
        CharacterCreationValidationResult male = validate(request(
                CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0", null));
        assertTrue(male.isValid());
        assertEquals(CharacterArmsRegistry.BROAD, male.getCreation().getArmsId());
    }

    @Test
    public void chestTypeIsValidatedAndDefaultsFromTheBodyType() {
        CharacterCreationValidationResult chosen = validate(request(
                CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0",
                CharacterArmsRegistry.BROAD, CharacterChestTypeRegistry.FULL_LARGE));
        assertTrue(chosen.isValid());
        assertEquals(CharacterChestTypeRegistry.FULL_LARGE, chosen.getCreation().getChestTypeId());

        CharacterCreationValidationResult defaulted = validate(request(
                CharacterBodyTypeRegistry.FEMININE, "losttales:human_bree_feminine_0", ""));
        assertTrue(defaulted.isValid());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM,
                defaulted.getCreation().getChestTypeId());

        CharacterCreationValidationResult rejected = validate(request(
                CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0",
                CharacterArmsRegistry.BROAD, "losttales:huge"));
        assertEquals(CharacterErrorId.INVALID_CHEST_TYPE, rejected.getErrorId());
    }

    @Test
    public void unknownArmsAreRejected() {
        CharacterCreationValidationResult result = validate(request(
                CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0",
                "losttales:huge"));
        assertEquals(CharacterErrorId.INVALID_ARMS, result.getErrorId());
    }

    private static CharacterCreationValidationResult validate(
            CharacterCreationRequest request) {
        return CharacterValidator.validateCreation(
                new CharacterRoster(UUID.fromString(
                        "70000000-0000-0000-0000-000000000007")),
                request,
                new TestFactionResolver());
    }

    /** An empty chest type lets validation pick the default for the body type. */
    private static CharacterCreationRequest request(String bodyTypeId, String skinId,
                                                     String armsId) {
        return request(bodyTypeId, skinId, armsId, "");
    }

    private static CharacterCreationRequest request(String bodyTypeId, String skinId,
                                                     String armsId,
                                                     String chestTypeId) {
        return new CharacterCreationRequest(
                0L, 0, "Adventurer", CharacterRaceRegistry.HUMAN, bodyTypeId,
                skinId, 25, FACTION, WAYPOINT, false, "", armsId,
                chestTypeId, true, "");
    }

    private static final class TestFactionResolver implements CharacterFactionResolver {

        private final CharacterFactionDefinition bree = new CharacterFactionDefinition(
                FACTION, true, EnumSet.of(CharacterFactionCategory.HUMAN));

        @Override
        public boolean isAvailable() {
            return true;
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
