package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class LostTalesLotrMapAtmosphereTest {
    /** The ceiling the shade may never pass, whatever the hour. */
    private static final float MAX_ALPHA = 0.34F;
    /** The zoom range the map screen allows. */
    private static final float MIN_ZOOM_EXP = -3.6F;
    private static final float MAX_ZOOM_EXP = 4.6F;

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

    @Test
    public void cloudsFadeOutRatherThanPilingUpWhenZoomedOut() {
        assertEquals(1.0F,
                LostTalesLotrMapAtmosphere.cloudCoverage(1), 0.0F);
        assertEquals(1.0F,
                LostTalesLotrMapAtmosphere.cloudCoverage(520), 0.0F);
        assertEquals(0.0F,
                LostTalesLotrMapAtmosphere.cloudCoverage(700), 0.0F);
        assertEquals(0.0F,
                LostTalesLotrMapAtmosphere.cloudCoverage(100000), 0.0F);

        float previous = 1.0F;
        for (int cells = 520; cells <= 700; cells++) {
            float coverage =
                    LostTalesLotrMapAtmosphere.cloudCoverage(cells);
            assertTrue("clouds came back as the map pulled out",
                    coverage <= previous + 0.0001F);
            previous = coverage;
        }
    }

    /**
     * Why there were no clouds at all: a cell fixed in map pixels put
     * thousands of them in view once the map was pulled out to the whole of
     * Middle-earth, and the sky gave up rather than draw them. At every zoom
     * the map allows, what is on screen now has to stay inside the cap.
     */
    @Test
    public void cloudsSurviveEveryZoomTheMapAllows() {
        for (float zoomExp = MIN_ZOOM_EXP; zoomExp <= MAX_ZOOM_EXP;
             zoomExp += 0.2F) {
            float zoomScale = (float)Math.pow(2.0D, zoomExp);
            float cell = LostTalesLotrMapAtmosphere.cloudCell(zoomScale);
            assertTrue("a cell vanished at zoom " + zoomExp, cell > 0.0F);
            // A wide viewport, turned, which is the most ground the map has
            // to cover at once.
            float reach = (float)Math.sqrt(2.0D)
                    * 1000.0F / zoomScale / 2.0F;
            long across = (long)Math.ceil(reach * 2.0F / cell) + 3L;
            long cells = across * across;
            assertTrue("clouds gave up at zoom " + zoomExp
                            + " with " + cells + " cells",
                    LostTalesLotrMapAtmosphere.cloudCoverage(
                            (int)Math.min(Integer.MAX_VALUE, cells))
                            > 0.0F);
        }
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
        float cell = LostTalesLotrMapAtmosphere.cloudCell(1.0F);
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
        float cell = LostTalesLotrMapAtmosphere.cloudCell(1.0F);
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

    /** Clouds keep roughly one spacing on screen however far the map is out. */
    @Test
    public void cloudSpacingStaysSteadyAcrossZoom() {
        for (float zoomExp = MIN_ZOOM_EXP; zoomExp <= MAX_ZOOM_EXP;
             zoomExp += 0.2F) {
            float zoomScale = (float)Math.pow(2.0D, zoomExp);
            float onScreen = LostTalesLotrMapAtmosphere
                    .cloudCell(zoomScale) * zoomScale;
            assertTrue("clouds bunched up at zoom " + zoomExp,
                    onScreen >= 110.0F);
            assertTrue("clouds spread too thin at zoom " + zoomExp,
                    onScreen <= 480.0F);
        }
    }
}
