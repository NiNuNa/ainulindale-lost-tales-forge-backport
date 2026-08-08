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
     * The whole point of the sky's layout: the zoom is not one of its inputs.
     *
     * <p>It used to be. Clouds sat on lattices an octave apart and the zoom
     * chose between them, so pulling the map out shrank every cloud until its
     * lattice gave up and a coarser one faded in behind it. Now a cloud's cell
     * and its position come from where the camera is looking and nothing else,
     * so zooming cannot move one, resize one, or swap it for another.</p>
     */
    @Test
    public void theSkyDoesNotDependOnTheZoomAtAll() {
        float[] positions = { 0.0F, 137.5F, -820.0F, 4096.25F };
        for (int index = 0; index < positions.length; index++) {
            float offset =
                    LostTalesLotrMapAtmosphere.skyOffset(positions[index]);
            assertEquals("the sky moved when nothing but the zoom had",
                    offset,
                    LostTalesLotrMapAtmosphere.skyOffset(positions[index]),
                    0.0F);
        }

        // And the offset is a straight multiple of the map position, so a
        // cloud's cell is a fixed region of the world however far out the
        // map is.
        float first = LostTalesLotrMapAtmosphere.skyOffset(100.0F)
                - LostTalesLotrMapAtmosphere.skyOffset(0.0F);
        float second = LostTalesLotrMapAtmosphere.skyOffset(2100.0F)
                - LostTalesLotrMapAtmosphere.skyOffset(2000.0F);
        assertEquals("the sky must pan at one steady rate",
                first, second, 0.0001F);
        assertTrue("the sky must move with the map at all", first != 0.0F);
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
