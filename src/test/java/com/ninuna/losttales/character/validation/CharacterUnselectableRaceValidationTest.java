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

/**
 * The server refuses a race nobody may choose, and the client never
 * offering it is not what makes that true: a crafted request, or a
 * template written before the race was withdrawn, has to be refused by
 * the server on its own.
 *
 * <p>What it refuses is <em>taking</em> the race. A character who already
 * is one keeps it, which is what stops an ordinary change to their
 * description turning them into somebody else.</p>
 */
public final class CharacterUnselectableRaceValidationTest {

    private static final String FACTION = "lotr:test_troll";
    private static final String WAYPOINT = "lotr:test_troll_camp";
    private static final UUID OWNER =
            UUID.fromString("71000000-0000-0000-0000-000000000017");

    @Test
    public void aHalfTrollIsRefused() {
        CharacterCreationValidationResult result = CharacterValidator
                .validateCreation(new CharacterRoster(OWNER), request(),
                        new TestFactionResolver());
        assertEquals(CharacterErrorId.INVALID_RACE, result.getErrorId());
    }

    @Test
    public void aRaceStillOfferedIsAccepted() {
        CharacterAppearanceValidationResult result =
                CharacterValidator.validateAppearance(
                        new CharacterRoster(OWNER), null,
                        "Adventurer", CharacterRaceRegistry.ORC,
                        CharacterGenderRegistry.NON_BINARY,
                        skinFor(CharacterRaceRegistry.ORC),
                        CharacterBodyTypeRegistry.WIDE,
                        CharacterChestTypeRegistry.CLASSIC, "", 25);
        assertTrue("an orc is still a race anyone may be", result.isValid());
    }

    @Test
    public void aCharacterThatAlreadyIsOneMayKeepIt() {
        CharacterAppearanceValidationResult result =
                CharacterValidator.validateAppearance(
                        new CharacterRoster(OWNER), null,
                        CharacterRaceRegistry.HALF_TROLL,
                        "Bogdal", CharacterRaceRegistry.HALF_TROLL,
                        CharacterGenderRegistry.NON_BINARY,
                        skinFor(CharacterRaceRegistry.HALF_TROLL),
                        CharacterBodyTypeRegistry.WIDE,
                        CharacterChestTypeRegistry.CLASSIC, "", 25);
        assertTrue("keeping the race it already is stays allowed",
                result.isValid());
    }

    @Test
    public void aCharacterOfAnotherRaceMayNotTakeIt() {
        CharacterAppearanceValidationResult result =
                CharacterValidator.validateAppearance(
                        new CharacterRoster(OWNER), null,
                        CharacterRaceRegistry.ORC,
                        "Bogdal", CharacterRaceRegistry.HALF_TROLL,
                        CharacterGenderRegistry.NON_BINARY,
                        skinFor(CharacterRaceRegistry.HALF_TROLL),
                        CharacterBodyTypeRegistry.WIDE,
                        CharacterChestTypeRegistry.CLASSIC, "", 25);
        assertEquals(CharacterErrorId.INVALID_RACE, result.getErrorId());
    }

    /** That race's own default skin, whatever the registry names it. */
    private static String skinFor(String raceId) {
        return CharacterSkinRegistry.getDefaultSkinId(raceId,
                CharacterGenderRegistry.NON_BINARY, OWNER);
    }

    private static CharacterCreationRequest request() {
        return new CharacterCreationRequest(
                0L, 0, "Bogdal", CharacterRaceRegistry.HALF_TROLL,
                CharacterGenderRegistry.NON_BINARY,
                skinFor(CharacterRaceRegistry.HALF_TROLL), 25, FACTION, WAYPOINT,
                false, "", CharacterBodyTypeRegistry.WIDE);
    }

    private static final class TestFactionResolver
            implements CharacterFactionResolver {

        private final CharacterFactionDefinition trolls =
                new CharacterFactionDefinition(
                        FACTION, true,
                        EnumSet.of(CharacterFactionCategory.TROLL));

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
            return FACTION.equals(factionId) ? this.trolls : null;
        }

        @Override
        public String resolveStartingWaypointId(String factionId,
                                                String waypointId,
                                                boolean allowAnyRegion) {
            return FACTION.equals(factionId) && WAYPOINT.equals(waypointId)
                    ? waypointId : null;
        }
    }
}
