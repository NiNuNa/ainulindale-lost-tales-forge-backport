package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.character.physics.CharacterRaceDimensions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for race size, camera, and eye-origin metadata. */
public final class CharacterRaceDimensionsRegistryTest {

    private static final float EPSILON = 0.0001F;

    @Test
    public void everyRaceEyeHeightTracksItsPhysicalHeight() {
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            assertEquals(race.getId(), race.getHeight() * 0.85F,
                    race.getStandingEyeHeight(), EPSILON);
            assertEquals(race.getId(), 0.08F,
                    race.getStandingEyeHeight() - race.getSneakingEyeHeight(),
                    EPSILON);
            assertTrue(race.getId(), race.getStandingEyeHeight() < race.getHeight());
            assertTrue(race.getId(), race.getSneakingEyeHeight()
                    < race.getStandingEyeHeight());
        }
    }

    @Test
    public void fullSizeUrukIsTallerThanOrc() {
        CharacterRaceDefinition orc = CharacterRaceRegistry.get(
                CharacterRaceRegistry.ORC);
        CharacterRaceDefinition uruk = CharacterRaceRegistry.get(
                CharacterRaceRegistry.URUK);
        CharacterRaceDefinition human = CharacterRaceRegistry.get(
                CharacterRaceRegistry.HUMAN);
        CharacterRaceDefinition elf = CharacterRaceRegistry.get(
                CharacterRaceRegistry.ELF);

        assertTrue(uruk.getHeight() > orc.getHeight());
        assertTrue(uruk.getStandingEyeHeight() > orc.getStandingEyeHeight());
        assertEquals(1.80F, uruk.getHeight(), EPSILON);
        assertEquals(1.53F, uruk.getStandingEyeHeight(), EPSILON);
        assertEquals(human.getHeight(), uruk.getHeight(), EPSILON);
        assertEquals(elf.getHeight(), uruk.getHeight(), EPSILON);
    }

    @Test
    public void runtimeProfilesCannotOverrideAnyRegisteredDimensions() {
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            CharacterRaceGameplayProfile incorrectRuntimeProfile =
                    new CharacterRaceGameplayProfile(
                            race.getId(), "lotr.common.entity.npc.Incorrect",
                            0.10F, 0.10F, 0.10F, 0.10F,
                            26.0D, 1.10D, 2.0D, true);

            CharacterRaceDimensions resolved =
                    CharacterRaceDimensions.fromProfile(
                            race.getId(), incorrectRuntimeProfile);
            assertEquals(race.getId(), race.getWidth(),
                    resolved.getWidth(), EPSILON);
            assertEquals(race.getId(), race.getHeight(),
                    resolved.getHeight(), EPSILON);
            assertEquals(race.getId(), race.getStandingEyeHeight(),
                    resolved.getStandingEyeHeight(), EPSILON);
            assertEquals(race.getId(), race.getSneakingEyeHeight(),
                    resolved.getSneakingEyeHeight(), EPSILON);
        }
    }
}
