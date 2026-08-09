package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class LostTalesLotrMapAtmosphereTest {
    /** The ceiling the shade may never pass, whatever the hour. */
    private static final float MAX_ALPHA = 0.34F;

    @Test
    public void daylightLeavesTheMapAlone() {
        for (long time = 1500L; time <= 10800L; time += 300L) {
            assertEquals("the map was shaded in broad daylight at " + time,
                    0.0F,
                    LostTalesLotrMapAtmosphere.timeOfDayShade(time)[3],
                    0.0001F);
        }
    }

    /**
     * The map has to stay navigable at midnight. This is the whole reason
     * there is a ceiling rather than just a table.
     */
    @Test
    public void theMapNeverGetsDarkerThanItCanBeReadAt() {
        for (long time = 0L; time < 24000L; time += 25L) {
            float alpha =
                    LostTalesLotrMapAtmosphere.timeOfDayShade(time)[3];
            assertTrue("too dark to read at " + time,
                    alpha <= MAX_ALPHA + 0.0001F);
            assertTrue("negative shade at " + time, alpha >= 0.0F);
        }
    }

    /** Nothing may step: dusk and dawn are the transitions people watch. */
    @Test
    public void theShadeMovesSmoothlyAndWrapsAtMidnight() {
        float[] previous = LostTalesLotrMapAtmosphere.timeOfDayShade(0L);
        for (long time = 1L; time <= 24000L; time++) {
            float[] shade =
                    LostTalesLotrMapAtmosphere.timeOfDayShade(time);
            for (int channel = 0; channel < 4; channel++) {
                assertTrue("the shade jumped at " + time,
                        Math.abs(shade[channel] - previous[channel])
                                < 0.004F);
            }
            previous = shade;
        }
        // Tick 24000 is tick zero of the next day, so the two must agree or
        // the map flashes once a night.
        float[] midnight =
                LostTalesLotrMapAtmosphere.timeOfDayShade(24000L);
        float[] dawn = LostTalesLotrMapAtmosphere.timeOfDayShade(0L);
        for (int channel = 0; channel < 4; channel++) {
            assertEquals(dawn[channel], midnight[channel], 0.0001F);
        }
    }

    @Test
    public void aWorldRunningForYearsStillHasAnHour() {
        float[] shade = LostTalesLotrMapAtmosphere.timeOfDayShade(
                24000L * 3650L + 6000L);
        assertEquals(0.0F, shade[3], 0.0001F);
        assertTrue(LostTalesLotrMapAtmosphere.timeOfDayShade(-500L)[3]
                >= 0.0F);
    }

    /**
     * Weather darkens the map and must never darken it past reading, even
     * piled on top of midnight.
     */
    @Test
    public void aStormDarkensTheMapWithoutBlindingIt() {
        float clear = LostTalesLotrMapAtmosphere
                .shadeFor(6000L, 0.0F, 0.0F)[3];
        float wet = LostTalesLotrMapAtmosphere
                .shadeFor(6000L, 1.0F, 0.0F)[3];
        float storm = LostTalesLotrMapAtmosphere
                .shadeFor(6000L, 1.0F, 1.0F)[3];
        assertTrue("rain must show at all", wet > clear);
        assertTrue("a thunderstorm must be darker than rain", storm > wet);

        for (long time = 0L; time < 24000L; time += 50L) {
            for (int step = 0; step <= 4; step++) {
                float strength = step / 4.0F;
                float[] shade = LostTalesLotrMapAtmosphere
                        .shadeFor(time, strength, strength);
                assertTrue("unreadable at " + time + " in weather "
                                + strength,
                        shade[3] <= 0.42F + 0.0001F);
                for (int channel = 0; channel < 4; channel++) {
                    assertTrue("shade left its range at " + time,
                            shade[channel] >= 0.0F
                                    && shade[channel] <= 1.0F);
                }
            }
        }
    }

    /** Fair weather must leave the hour's own shade exactly as it was. */
    @Test
    public void fairWeatherChangesNothing() {
        for (long time = 0L; time < 24000L; time += 137L) {
            float[] plain =
                    LostTalesLotrMapAtmosphere.timeOfDayShade(time);
            float[] fair = LostTalesLotrMapAtmosphere
                    .shadeFor(time, 0.0F, 0.0F);
            for (int channel = 0; channel < 4; channel++) {
                assertEquals(plain[channel], fair[channel], 0.0F);
            }
        }
    }

    /**
     * Clouds are placed by hashing their cell, so every client puts them in
     * the same place without a word being sent about it — and each cell has
     * to differ from its neighbours or the sky becomes a lattice.
     */
    @Test
    public void cloudPlacementIsFixedAndVariedAcrossCells() {
        assertEquals(LostTalesLotrMapAtmosphere.cellNoise(12, -40, 1),
                LostTalesLotrMapAtmosphere.cellNoise(12, -40, 1), 0.0F);

        Set<Float> seen = new HashSet<Float>();
        for (int cellX = -12; cellX <= 12; cellX++) {
            for (int cellY = -12; cellY <= 12; cellY++) {
                float value = LostTalesLotrMapAtmosphere
                        .cellNoise(cellX, cellY, 1);
                assertTrue("noise left its range at " + cellX + "," + cellY,
                        value >= 0.0F && value < 1.0F);
                seen.add(Float.valueOf(value));
            }
        }
        assertTrue("neighbouring cells repeat, so the sky would tile",
                seen.size() > 600);
    }

    /**
     * Clouds are weather over Middle-earth, so the zoom grows and shrinks them
     * exactly as it grows and shrinks the ground under them — and it moves
     * them faster than the ground, because they are between the reader and it.
     *
     * <p>They used to be laid out in screen pixels at one fixed size, which
     * made the sky a texture on the window: no amount of scrolling on top of
     * that reads as a layer with height.</p>
     */
    @Test
    public void theSkyIsSizedInTheWorldAndMovesAheadOfIt() {
        float close = LostTalesLotrMapAtmosphere.skyScale(4.0F);
        float far = LostTalesLotrMapAtmosphere.skyScale(1.0F);
        assertTrue("the sky must be drawn at all", far > 0.0F);
        assertEquals("clouds must scale with the ground, not against it",
                4.0F, close / far, 0.0001F);

        // Nearer than the ground, so a pan carries the sky further than it
        // carries the map: that difference is the parallax.
        for (float zoomScale = 0.1F; zoomScale <= 16.0F; zoomScale *= 2.0F) {
            assertTrue("the sky must move ahead of the ground at " + zoomScale,
                    LostTalesLotrMapAtmosphere.skyScale(zoomScale)
                            > zoomScale);
        }
        assertEquals("the sky must be steady for a given zoom",
                LostTalesLotrMapAtmosphere.skyScale(2.5F),
                LostTalesLotrMapAtmosphere.skyScale(2.5F), 0.0F);
    }

    /**
     * The bug that emptied the sky. The drift is added to where a cloud
     * started, so on a world even a few days old it had carried every cloud
     * thousands of pixels past the cell it came from — and the cells being
     * visited were still the undrifted ones, so nothing was ever in view.
     * However old the world, the drift has to stay inside one wrap.
     */
    @Test
    public void cloudsStayOnTheMapHoweverOldTheWorldIs() {
        float cell = 300.0F;
        float wrap = cell * 1024.0F;
        long[] ages = {
                0L, 24000L, 24000L * 30L, 24000L * 365L,
                24000L * 365L * 20L, Long.MAX_VALUE / 4L
        };
        for (int index = 0; index < ages.length; index++) {
            float drift =
                    LostTalesLotrMapAtmosphere.cloudDrift(ages[index], cell);
            assertTrue("the sky ran away at age " + ages[index]
                            + " with drift " + drift,
                    drift >= 0.0F && drift < wrap);
        }
    }

    /** The sky has to actually move, and to move the same way every time. */
    @Test
    public void theSkyDriftsSteadilyAndRepeatably() {
        float cell = 300.0F;
        float start = LostTalesLotrMapAtmosphere.cloudDrift(1000L, cell);
        assertEquals(start,
                LostTalesLotrMapAtmosphere.cloudDrift(1000L, cell), 0.0F);
        // A minute of world time has to shift the sky by something a player
        // could notice, without it racing across the map.
        float minute = LostTalesLotrMapAtmosphere.cloudDrift(
                1000L + 1200L, cell) - start;
        assertTrue("the sky is not moving", minute > 20.0F);
        assertTrue("the sky is racing", minute < 300.0F);
    }

    /**
     * The haze is a depth cue and nothing else: a flat map has no far edge to
     * have one, and however far the map leans the far edge stays a place a
     * coastline can be read off.
     */
    @Test
    public void theHazeOnlyExistsOnALeaningMapAndNeverHidesIt() {
        for (float far = 0.0F; far <= 1.0F; far += 0.1F) {
            assertEquals("a flat map was hazed", 0.0F,
                    LostTalesLotrMapAtmosphere.hazeStrength(0.0F, far),
                    0.0F);
        }
        assertEquals("the near half must be left alone", 0.0F,
                LostTalesLotrMapAtmosphere.hazeStrength(1.0F, 0.0F), 0.0F);
        assertEquals("and so must anything nearer still", 0.0F,
                LostTalesLotrMapAtmosphere.hazeStrength(1.0F, -0.5F), 0.0F);

        float previous = -1.0F;
        for (float far = 0.0F; far <= 1.0F; far += 0.05F) {
            float haze = LostTalesLotrMapAtmosphere.hazeStrength(1.0F, far);
            assertTrue("the haze came back at " + far, haze >= previous);
            assertTrue("the map cannot be read at " + far, haze <= 0.35F);
            previous = haze;
        }
        assertTrue("the far edge must actually be hazed", previous > 0.15F);

        // And it comes on with the lean rather than arriving with it.
        assertTrue(LostTalesLotrMapAtmosphere.hazeStrength(0.4F, 1.0F)
                < LostTalesLotrMapAtmosphere.hazeStrength(1.0F, 1.0F));
        // Nothing outside its range may drive it past its ceiling.
        assertEquals(LostTalesLotrMapAtmosphere.hazeStrength(1.0F, 1.0F),
                LostTalesLotrMapAtmosphere.hazeStrength(4.0F, 3.0F), 0.0F);
    }

    /**
     * A front rolling in has to read as the sky thickening: the same clouds
     * darken, in the same order, and fair weather leaves every one of them
     * alone.
     */
    @Test
    public void weatherDarkensTheSameCloudsInTheSameOrder() {
        float[] fair = LostTalesLotrMapAtmosphere.resolveWeather(
                3, -7, 0, 0.0F, 0.0F);
        assertEquals("fair weather must leave a cloud white",
                1.0F, fair[0], 0.0001F);

        int darkened = 0;
        int stillFair = 0;
        for (int cellX = -8; cellX <= 8; cellX++) {
            for (int cellY = -8; cellY <= 8; cellY++) {
                float[] light = LostTalesLotrMapAtmosphere.resolveWeather(
                        cellX, cellY, 0, 0.3F, 0.0F);
                float[] heavy = LostTalesLotrMapAtmosphere.resolveWeather(
                        cellX, cellY, 0, 0.9F, 0.0F);
                assertTrue("rain lightened a cloud instead of darkening it",
                        heavy[0] <= light[0] + 0.0001F);
                if (light[0] < 1.0F) {
                    darkened++;
                } else {
                    stillFair++;
                }
            }
        }
        assertTrue("light rain darkened nothing", darkened > 0);
        assertTrue("light rain darkened the whole sky", stillFair > darkened);

        // The same cell, asked twice, is the same cloud — nothing here may
        // depend on the frame or on what was asked before it.
        assertEquals(
                LostTalesLotrMapAtmosphere.resolveWeather(
                        5, 5, 0, 0.5F, 0.2F)[3],
                LostTalesLotrMapAtmosphere.resolveWeather(
                        5, 5, 0, 0.5F, 0.2F)[3], 0.0F);
    }
}
