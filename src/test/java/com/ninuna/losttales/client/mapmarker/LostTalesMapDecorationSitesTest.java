package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class LostTalesMapDecorationSitesTest {
    /** The LOTR biome map, which is what the walk is actually sized for. */
    private static final int IMAGE_WIDTH = 3200;
    private static final int IMAGE_HEIGHT = 4000;
    private static final float CELL = 40.0F;

    /** A rule that keeps every fourth cell and puts it in the middle. */
    private static final class EveryFourth
            implements LostTalesMapDecorationSites.SiteRule {
        private int groundAsked;

        @Override
        public boolean hasSite(int cellX, int cellY) {
            return ((cellX + cellY) & 3) == 0;
        }

        @Override
        public void position(int cellX, int cellY, float[] result) {
            result[0] = (cellX + 0.5F) * CELL;
            result[1] = (cellY + 0.5F) * CELL;
        }

        @Override
        public boolean isGround(float mapX, float mapY) {
            this.groundAsked++;
            // Half the map is ground, so the two filters both have to bite.
            return mapX < IMAGE_WIDTH / 2;
        }
    }

    private static LostTalesMapDecorationSites built(
            LostTalesMapDecorationSites.SiteRule rule) {
        LostTalesMapDecorationSites sites =
                new LostTalesMapDecorationSites();
        // However many slices it takes; the point of the loop is that no one
        // call has to do all of it.
        for (int pass = 0; pass < 500 && !sites.isComplete(); pass++) {
            sites.advance(rule, CELL, IMAGE_WIDTH, IMAGE_HEIGHT);
        }
        return sites;
    }

    /**
     * The walk is spread over frames on purpose: reading the biome image for
     * the whole of Middle-earth at once is a visible stall on the frame the
     * map is opened.
     */
    @Test
    public void theMapIsResolvedASliceAtATime() {
        LostTalesMapDecorationSites sites =
                new LostTalesMapDecorationSites();
        EveryFourth rule = new EveryFourth();

        sites.advance(rule, CELL, IMAGE_WIDTH, IMAGE_HEIGHT);
        assertFalse("the whole map was walked in one go", sites.isComplete());
        int afterOne = sites.getCount();

        for (int pass = 0; pass < 500 && !sites.isComplete(); pass++) {
            sites.advance(rule, CELL, IMAGE_WIDTH, IMAGE_HEIGHT);
        }
        assertTrue("the walk never finished", sites.isComplete());
        assertTrue("later slices found nothing",
                sites.getCount() > afterOne);

        // And once it is done it stays done, however often it is asked again.
        int found = sites.getCount();
        sites.advance(rule, CELL, IMAGE_WIDTH, IMAGE_HEIGHT);
        assertEquals("a finished walk was run again", found, sites.getCount());
    }

    /**
     * What it finds has to be exactly what the rule says is there — this is
     * the set the map draws from at every zoom, so anything missing from it is
     * a decoration that does not exist.
     */
    @Test
    public void everySiteTheRuleAllowsIsFoundExactlyOnce() {
        EveryFourth rule = new EveryFourth();
        LostTalesMapDecorationSites sites = built(rule);

        int expected = 0;
        int lastX = (int)Math.ceil(IMAGE_WIDTH / CELL);
        int lastY = (int)Math.ceil(IMAGE_HEIGHT / CELL);
        for (int cellX = 0; cellX <= lastX; cellX++) {
            for (int cellY = 0; cellY <= lastY; cellY++) {
                if (rule.hasSite(cellX, cellY)
                        && (cellX + 0.5F) * CELL < IMAGE_WIDTH / 2) {
                    expected++;
                }
            }
        }
        assertEquals("the walk missed sites", expected, sites.getCount());
        assertTrue("the map came out empty", sites.getCount() > 100);

        Set<Long> seen = new HashSet<Long>();
        for (int index = 0; index < sites.getCount(); index++) {
            assertTrue("a site was found twice", seen.add(Long.valueOf(
                    ((long)sites.getCellX(index) << 32)
                            ^ sites.getCellY(index))));
            assertEquals("a site moved away from its cell",
                    (sites.getCellX(index) + 0.5F) * CELL,
                    sites.getX(index), 0.0001F);
        }
    }

    /**
     * The expensive half is asking the map image what the ground is, so it may
     * only be asked where the lattice actually put something.
     */
    @Test
    public void theGroundIsOnlyAskedAboutWhereThereIsSomethingToPlace() {
        EveryFourth rule = new EveryFourth();
        built(rule);

        int cells = ((int)Math.ceil(IMAGE_WIDTH / CELL) + 1)
                * ((int)Math.ceil(IMAGE_HEIGHT / CELL) + 1);
        assertTrue("the ground was sampled for cells with no site in them",
                rule.groundAsked < cells / 3);
        assertTrue("the ground was never sampled", rule.groundAsked > 0);
    }

    /**
     * A world being left takes its decorations with it, and a map image of a
     * different size starts the walk again rather than mixing two worlds.
     */
    @Test
    public void leavingAWorldForgetsWhereItsTreesStood() {
        EveryFourth rule = new EveryFourth();
        LostTalesMapDecorationSites sites = built(rule);
        assertTrue(sites.getCount() > 0);

        sites.clear();
        assertEquals(0, sites.getCount());
        assertFalse(sites.isComplete());

        sites.advance(rule, CELL, IMAGE_WIDTH, IMAGE_HEIGHT);
        int firstSlice = sites.getCount();
        sites.advance(rule, CELL, IMAGE_WIDTH / 2, IMAGE_HEIGHT / 2);
        assertTrue("a new map image must start the walk again",
                sites.getCount() <= firstSlice);
    }

    /** Nothing to walk is not something to crash on. */
    @Test
    public void anUnmeasuredMapImageIsSurvived() {
        LostTalesMapDecorationSites sites =
                new LostTalesMapDecorationSites();
        sites.advance(new EveryFourth(), CELL, 0, 0);
        sites.advance(null, CELL, IMAGE_WIDTH, IMAGE_HEIGHT);
        sites.advance(new EveryFourth(), 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT);

        assertEquals(0, sites.getCount());
        assertFalse(sites.isComplete());
    }
}
