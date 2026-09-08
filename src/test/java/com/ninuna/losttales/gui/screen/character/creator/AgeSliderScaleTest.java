package com.ninuna.losttales.gui.screen.character.creator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The bent track: every year up to the knee has its own place, the
 * stretch past it climbs to the oldest age, and going one way then the
 * other lands where it started.
 */
public class AgeSliderScaleTest {

    @Test
    public void theEndsOfTheTrackAreTheYoungestAndOldest() {
        assertEquals(AgeSliderScale.MIN, AgeSliderScale.ageAt(0.0F));
        assertEquals(AgeSliderScale.MAX, AgeSliderScale.ageAt(1.0F));
        assertEquals(0.0F, AgeSliderScale.positionOf(AgeSliderScale.MIN), 0.0001F);
        assertEquals(1.0F, AgeSliderScale.positionOf(AgeSliderScale.MAX), 0.0001F);
    }

    @Test
    public void theKneeSitsWhereTheTrackBends() {
        assertEquals(AgeSliderScale.KNEE_POSITION,
                AgeSliderScale.positionOf(AgeSliderScale.KNEE), 0.0001F);
        assertEquals(AgeSliderScale.KNEE,
                AgeSliderScale.ageAt(AgeSliderScale.KNEE_POSITION));
    }

    @Test
    public void everyAgeUpToTheKneeRoundTrips() {
        for (int age = AgeSliderScale.MIN; age <= AgeSliderScale.KNEE; age++) {
            assertEquals("age " + age, age,
                    AgeSliderScale.ageAt(AgeSliderScale.positionOf(age)));
        }
    }

    @Test
    public void thePositionNeverGoesBackwardsAsAgeGrows() {
        float previous = -1.0F;
        for (int age = AgeSliderScale.MIN; age <= AgeSliderScale.MAX; age += 7) {
            float position = AgeSliderScale.positionOf(age);
            assertTrue("age " + age, position >= previous);
            assertTrue(position >= 0.0F && position <= 1.0F);
            previous = position;
        }
    }

    @Test
    public void pastTheKneeTheTrackStillLandsNearWhatItWasAsked() {
        for (int age = AgeSliderScale.KNEE; age <= AgeSliderScale.MAX; age += 97) {
            int landed = AgeSliderScale.ageAt(AgeSliderScale.positionOf(age));
            // Float arithmetic across a logarithm; within a year or two.
            assertTrue("age " + age + " landed " + landed,
                    Math.abs(landed - age) <= 2);
        }
    }

    @Test
    public void outOfRangeIsClamped() {
        assertEquals(AgeSliderScale.MIN, AgeSliderScale.ageAt(-0.5F));
        assertEquals(AgeSliderScale.MAX, AgeSliderScale.ageAt(1.5F));
        assertEquals(AgeSliderScale.MIN, AgeSliderScale.clamp(-40));
        assertEquals(AgeSliderScale.MAX, AgeSliderScale.clamp(1000000));
    }

    @Test
    public void aNudgeIsAYearBelowTheKneeAndTenAboveIt() {
        assertEquals(26, AgeSliderScale.nudge(25, 1, false));
        assertEquals(24, AgeSliderScale.nudge(25, -1, false));
        assertEquals(35, AgeSliderScale.nudge(25, 1, true));
        assertEquals(1010, AgeSliderScale.nudge(1000, 1, false));
        assertEquals(1100, AgeSliderScale.nudge(1000, 1, true));
        assertEquals(AgeSliderScale.KNEE + 10,
                AgeSliderScale.nudge(AgeSliderScale.KNEE, 1, false));
    }

    @Test
    public void aCoarseNudgeStopsAtTheKneeRatherThanLeapingIt() {
        assertEquals(AgeSliderScale.KNEE, AgeSliderScale.nudge(995, 1, true));
        assertEquals(AgeSliderScale.MIN, AgeSliderScale.nudge(3, -1, true));
        assertEquals(AgeSliderScale.MAX, AgeSliderScale.nudge(9990, 1, true));
    }
}
