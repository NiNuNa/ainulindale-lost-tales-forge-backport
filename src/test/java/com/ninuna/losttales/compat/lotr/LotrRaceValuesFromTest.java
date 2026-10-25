package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A race that takes its values from a LOTR NPC keeps the NPC's numbers, and
 * the log names each number of its file the NPC replaced (CHAR-38).
 */
public final class LotrRaceValuesFromTest {

    @Test
    public void eachReplacedNumberIsNamedWithTheFilesBeside() {
        CharacterRaceGameplayProfile file = profile(20.0D, 1.0D, 2.0D);
        CharacterRaceGameplayProfile npc = profile(30.0D, 1.1D, 2.0D);

        assertEquals(Arrays.asList("health 30 in place of 20", "speed x1.1 in place of x1"),
                LotrRaceProfileAdapter.replacedNumbers(file, npc));
    }

    @Test
    public void numbersTheNpcLeftAsTheFileWroteThemAreNotNamed() {
        CharacterRaceGameplayProfile file = profile(26.0D, 1.1D, 4.0D);

        assertEquals(Collections.<String>emptyList(),
                LotrRaceProfileAdapter.replacedNumbers(file, profile(26.0D, 1.1D, 4.0D)));
        assertEquals(Collections.<String>emptyList(),
                LotrRaceProfileAdapter.replacedNumbers(file, file));
        assertEquals(Collections.<String>emptyList(),
                LotrRaceProfileAdapter.replacedNumbers(file, null));
    }

    @Test
    public void anAttackOfTheNpcsStandsWithUpToThreeDecimals() {
        assertEquals(Arrays.asList("attack 3.125 in place of 3"),
                LotrRaceProfileAdapter.replacedNumbers(profile(20.0D, 1.0D, 3.0D),
                        profile(20.0D, 1.0D, 3.125D)));
        assertEquals("0.333", LotrRaceProfileAdapter.number(1.0D / 3.0D));
        assertEquals("26", LotrRaceProfileAdapter.number(26.0D));
    }

    private static CharacterRaceGameplayProfile profile(double health, double speed,
                                                        double attack) {
        return new CharacterRaceGameplayProfile("server:beorning", "", 0.6F, 1.8F,
                1.62F, 1.54F, health, speed, attack);
    }
}
