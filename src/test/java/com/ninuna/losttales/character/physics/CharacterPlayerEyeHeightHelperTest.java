package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CharacterPlayerEyeHeightHelperTest {

    private static final double EPSILON = 0.000001D;

    @Test
    public void serverHumanFitsUnderTwoBlockCeiling() {
        CharacterRaceDefinition human = CharacterRaceRegistry.get(CharacterRaceRegistry.HUMAN);
        double feetY = 64.0D;
        double eyeY = feetY + CharacterPlayerEyeHeightHelper.toPlayerEyeHeightField(
                human.getStandingEyeHeight(), 0.0F, 0.0F);

        // Vanilla samples the eyes at +/- 0.05 blocks for suffocation.
        assertEquals(65.53D, eyeY, EPSILON);
        assertEquals(65, (int)Math.floor(eyeY + 0.05D));
        assertTrue(eyeY + 0.05D < 66.0D);
    }

    @Test
    public void everyRaceKeepsSuffocationSamplesInsideBodyDuringMovement() {
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            for (float eyeHeight : new float[] {
                    race.getStandingEyeHeight(), race.getSneakingEyeHeight()}) {
                for (double feetY : new double[] {64.0D, 64.5D, 65.25D, -12.5D}) {
                    for (float yOffset : new float[] {0.0F, 1.62F}) {
                        for (float ySize : new float[] {0.0F, 0.2F, 0.5F}) {
                            double posY = feetY + (double)yOffset - (double)ySize;
                            double eyeY = posY
                                    + CharacterPlayerEyeHeightHelper.toPlayerEyeHeightField(
                                            eyeHeight, yOffset, ySize);
                            assertEquals(race.getId(), feetY + eyeHeight, eyeY, EPSILON);
                            assertTrue(race.getId(), eyeY - 0.05D > feetY);
                            assertTrue(race.getId(), eyeY + 0.05D < feetY + race.getHeight());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void solidBlockAtEyeLevelStillContainsSuffocationSamples() {
        double eyeY = 64.0D + CharacterPlayerEyeHeightHelper.toPlayerEyeHeightField(
                1.53F, 0.0F, 0.0F);
        assertEquals(65, (int)Math.floor(eyeY - 0.05D));
        assertEquals(65, (int)Math.floor(eyeY + 0.05D));
    }

    @Test
    public void clientEyeOffsetCanBeNegativeForShortRaces() {
        float field = CharacterPlayerEyeHeightHelper.toPlayerEyeHeightField(
                1.02F, 1.62F, 0.0F);
        assertEquals(-0.60F, field, EPSILON);
    }
}
