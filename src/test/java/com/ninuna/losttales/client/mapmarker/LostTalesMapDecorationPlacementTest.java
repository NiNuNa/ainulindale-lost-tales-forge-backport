package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapDecorationPlacementTest {
    /** The ring radius the water kinds are probed at. */
    private static final int PROBE = 2;
    private static final int CHANNEL = 4096;
    private static final float CELL = 8.0F;
    private static final float JITTER = 0.42F;

    /** Ground of the sampled kind in every direction. */
    private static final LostTalesMapDecorationPlacement.GroundSampler
            EVERYWHERE =
            new LostTalesMapDecorationPlacement.GroundSampler() {
                @Override
                public boolean matches(int mapX, int mapY) {
                    return true;
                }
            };

    /** And none of it anywhere. */
    private static final LostTalesMapDecorationPlacement.GroundSampler
            NOWHERE =
            new LostTalesMapDecorationPlacement.GroundSampler() {
                @Override
                public boolean matches(int mapX, int mapY) {
                    return false;
                }
            };

    /** One matching pixel and nothing else: the case that must never pass. */
    private static LostTalesMapDecorationPlacement.GroundSampler singleTile(
            final int atX, final int atY) {
        return new LostTalesMapDecorationPlacement.GroundSampler() {
            @Override
            public boolean matches(int mapX, int mapY) {
                return mapX == atX && mapY == atY;
            }
        };
    }

    /** A disc of matching ground, and nothing beyond it. */
    private static LostTalesMapDecorationPlacement.GroundSampler patch(
            final int centreX, final int centreY, final double radius) {
        return new LostTalesMapDecorationPlacement.GroundSampler() {
            @Override
            public boolean matches(int mapX, int mapY) {
                int deltaX = mapX - centreX;
                int deltaY = mapY - centreY;
                return deltaX * deltaX + deltaY * deltaY <= radius * radius;
            }
        };
    }

    /** A channel of a given half-width, which is what a river looks like. */
    private static LostTalesMapDecorationPlacement.GroundSampler channel(
            final int atX, final int halfWidth) {
        return new LostTalesMapDecorationPlacement.GroundSampler() {
            @Override
            public boolean matches(int mapX, int mapY) {
                return Math.abs(mapX - atX) <= halfWidth;
            }
        };
    }

    @Test
    public void broadGroundCarriesDecoration() {
        assertTrue(LostTalesMapDecorationPlacement.isBroadSite(
                EVERYWHERE, 400, 300, PROBE));
    }

    @Test
    public void groundOfTheWrongKindNeverDoes() {
        assertFalse(LostTalesMapDecorationPlacement.isBroadSite(
                NOWHERE, 400, 300, PROBE));
        assertFalse("a sampler that is not there must not crash the map",
                LostTalesMapDecorationPlacement.isBroadSite(
                        null, 0, 0, PROBE));
        assertFalse("a probe of no size must not pass everything",
                LostTalesMapDecorationPlacement.isBroadSite(
                        EVERYWHERE, 0, 0, 0));
    }

    /**
     * The cases the rule exists for. A wave on a village pond, on a single
     * stray water pixel, or on every stream in Eriador is worse than no waves
     * at all — and the same holds for a tree on a lone forest pixel.
     */
    @Test
    public void pondsSingleTilesAndRiversAreRejected() {
        assertFalse("a single pixel grew a decoration",
                LostTalesMapDecorationPlacement.isBroadSite(
                        singleTile(400, 300), 400, 300, PROBE));
        assertFalse("a patch smaller than the probe ring qualified",
                LostTalesMapDecorationPlacement.isBroadSite(
                        patch(400, 300, 1.5D), 400, 300, PROBE));
        assertFalse("a river qualified",
                LostTalesMapDecorationPlacement.isBroadSite(
                        channel(400, 1), 400, 300, PROBE));
    }

    /** And ground clearly larger than both rings has to qualify. */
    @Test
    public void groundLargerThanTheProbeQualifies() {
        assertTrue(LostTalesMapDecorationPlacement.isBroadSite(
                patch(400, 300, PROBE * 2 + 2.0D), 400, 300, PROBE));
    }

    /**
     * The case a single ring got wrong. A channel three pixels across is
     * water in every direction a small ring reaches, so it passed; only a
     * wider ring shows it is water one way and land the other.
     *
     * <p>Up to five pixels, which at this map's scale is well over half a
     * kilometre. A channel broader than that is an estuary and is meant to
     * carry waves.</p>
     */
    @Test
    public void aWideRiverIsStillARiver() {
        for (int halfWidth = 0; halfWidth <= 2; halfWidth++) {
            assertFalse("a channel " + (halfWidth * 2 + 1)
                            + " pixels across grew waves",
                    LostTalesMapDecorationPlacement.isBroadSite(
                            channel(400, halfWidth), 400, 300, PROBE));
        }
    }

    /**
     * A narrower probe is what lets ships onto a river that waves are kept
     * off, so the radius has to actually change the answer.
     */
    @Test
    public void aNarrowerProbeAcceptsNarrowerWater() {
        assertTrue("a ship could not reach a river",
                LostTalesMapDecorationPlacement.isBroadSite(
                        channel(400, 1), 400, 300, 1));
        assertFalse("a single-pixel stream is still too thin for a hull",
                LostTalesMapDecorationPlacement.isBroadSite(
                        channel(400, 0), 400, 300, 1));
    }

    /**
     * Placement is a hash of the position, so a coastline has the same
     * decorations on it every time the map is opened and on every client.
     */
    @Test
    public void placementIsFixedAndVaried() {
        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellY = -20; cellY <= 20; cellY++) {
                assertEquals(
                        LostTalesMapDecorationPlacement.siteX(
                                cellX, cellY, CHANNEL, CELL, JITTER),
                        LostTalesMapDecorationPlacement.siteX(
                                cellX, cellY, CHANNEL, CELL, JITTER),
                        0.0F);
                assertEquals(
                        LostTalesMapDecorationPlacement.hasSite(
                                cellX, cellY, CHANNEL, 0.5F),
                        LostTalesMapDecorationPlacement.hasSite(
                                cellX, cellY, CHANNEL, 0.5F));
            }
        }

        int occupied = 0;
        for (int cellX = 0; cellX < 40; cellX++) {
            for (int cellY = 0; cellY < 40; cellY++) {
                if (LostTalesMapDecorationPlacement.hasSite(
                        cellX, cellY, CHANNEL, 0.55F)) {
                    occupied++;
                }
            }
        }
        assertTrue("the lattice was empty", occupied > 500);
        assertTrue("the lattice was full, so decorations would tile",
                occupied < 1300);
    }

    /**
     * Two kinds sharing a cell must not share a layout, or every tree would
     * stand exactly where a wave would have been.
     */
    @Test
    public void differentKindsGetDifferentLayouts() {
        int agreements = 0;
        for (int cellX = 0; cellX < 30; cellX++) {
            for (int cellY = 0; cellY < 30; cellY++) {
                float first = LostTalesMapDecorationPlacement.siteX(
                        cellX, cellY, CHANNEL, CELL, JITTER);
                float second = LostTalesMapDecorationPlacement.siteX(
                        cellX, cellY, CHANNEL + 64, CELL, JITTER);
                if (Math.abs(first - second) < 0.001F) {
                    agreements++;
                }
            }
        }
        assertTrue("two kinds were laid out identically", agreements < 20);
    }

    /** A site stays inside its own cell, so the lattice cannot cross itself. */
    @Test
    public void aSiteStaysInsideItsCell() {
        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellY = -5; cellY <= 5; cellY++) {
                float x = LostTalesMapDecorationPlacement.siteX(
                        cellX, cellY, CHANNEL, CELL, JITTER);
                float y = LostTalesMapDecorationPlacement.siteY(
                        cellX, cellY, CHANNEL, CELL, JITTER);
                assertTrue("a decoration left its cell",
                        x >= cellX * CELL && x <= (cellX + 1) * CELL);
                assertTrue("a decoration left its cell",
                        y >= cellY * CELL && y <= (cellY + 1) * CELL);
            }
        }
    }

    /**
     * Decorations never shrink, so what limits them is crowding — and the
     * answer to crowding is to draw fewer of them, not to give up on the kind.
     * Whatever the zoom, the lattice is halved enough times to keep its sites
     * about the wanted distance apart on screen.
     */
    @Test
    public void thinningKeepsOneSpacingAtEveryZoom() {
        float width = 16.0F;
        assertEquals("a close map needs no thinning at all", 0,
                LostTalesMapDecorationRenderer.thinningLevels(80.0F, width));

        int previous = -1;
        for (float baseSpacing = 64.0F; baseSpacing > 0.005F;
             baseSpacing *= 0.5F) {
            int levels = LostTalesMapDecorationRenderer.thinningLevels(
                    baseSpacing, width);
            assertTrue("thinning went backwards at " + baseSpacing,
                    levels >= previous);
            // Whatever the zoom, the thinned lattice lands near the spacing
            // it is aiming for rather than collapsing or spreading away.
            float thinned = baseSpacing * (1 << levels);
            assertTrue("sites ended up crowded at " + baseSpacing
                            + ": " + thinned,
                    thinned >= width || levels >= 10);
            previous = levels;
        }
        assertTrue("a map pulled far out was never thinned", previous > 0);
    }

    /**
     * The property that makes thinning acceptable at all: pulling the map out
     * may remove a decoration but must never move one, so a site that a
     * coarser level keeps is kept by every finer level too.
     */
    @Test
    public void thinningOnlyEverRemovesSitesAndNeverMovesThem() {
        int[] coarse = new int[2];
        int[] fine = new int[2];
        for (int cellX = -8; cellX <= 8; cellX++) {
            for (int cellY = -8; cellY <= 8; cellY++) {
                for (int level = 1; level <= 5; level++) {
                    LostTalesMapDecorationPlacement.representative(
                            cellX, cellY, level, CHANNEL, coarse);

                    // Whichever of this cell's four children it keeps, asked
                    // the same way the renderer asks while fading them.
                    int childX = Integer.MIN_VALUE;
                    int childY = Integer.MIN_VALUE;
                    for (int x = cellX * 2; x <= cellX * 2 + 1; x++) {
                        for (int y = cellY * 2; y <= cellY * 2 + 1; y++) {
                            if (LostTalesMapDecorationPlacement
                                    .survivesCoarser(x, y, level - 1,
                                            CHANNEL)) {
                                childX = x;
                                childY = y;
                            }
                        }
                    }
                    assertTrue("a cell kept none of its children",
                            childX != Integer.MIN_VALUE);

                    LostTalesMapDecorationPlacement.representative(
                            childX, childY, level - 1, CHANNEL, fine);
                    assertEquals("a site moved when the map was thinned",
                            coarse[0], fine[0]);
                    assertEquals("a site moved when the map was thinned",
                            coarse[1], fine[1]);

                    // And the site it keeps is one of its own, never a
                    // neighbour's.
                    int span = 1 << level;
                    assertTrue("a coarse cell reached outside itself",
                            coarse[0] >= cellX * span
                                    && coarse[0] < (cellX + 1) * span);
                    assertTrue("a coarse cell reached outside itself",
                            coarse[1] >= cellY * span
                                    && coarse[1] < (cellY + 1) * span);
                }
            }
        }
    }

    /** And exactly one child of each cell is the one its parent kept. */
    @Test
    public void everyCellKeepsExactlyOneOfItsChildren() {
        for (int cellX = -6; cellX <= 6; cellX++) {
            for (int cellY = -6; cellY <= 6; cellY++) {
                int survivors = 0;
                for (int childX = cellX * 2; childX <= cellX * 2 + 1;
                     childX++) {
                    for (int childY = cellY * 2; childY <= cellY * 2 + 1;
                         childY++) {
                        if (LostTalesMapDecorationPlacement.survivesCoarser(
                                childX, childY, 0, CHANNEL)) {
                            survivors++;
                        }
                    }
                }
                assertEquals("a cell kept the wrong number of children",
                        1, survivors);
            }
        }
    }

    /** A frame loop that is stable, in range, and not in lockstep everywhere. */
    @Test
    public void animationLoopsSteadilyAndOutOfPhase() {
        LostTalesMapDecorationSprite sprite =
                LostTalesMapDecorationSprite.WAVE;
        boolean advanced = false;
        int previous = sprite.frameAt(0L, 0);
        for (long tick = 0L; tick < 400L; tick++) {
            int frame = sprite.frameAt(tick, 0);
            assertTrue("the animation left its sheet",
                    frame >= 0 && frame < sprite.getFrames());
            advanced |= frame != previous;
            previous = frame;
        }
        assertTrue("the animation never advanced", advanced);
        // A negative world time must not throw the frame off the sheet.
        assertTrue(sprite.frameAt(-500L, 7) >= 0);

        boolean differs = false;
        for (int phase = 0; phase < 20; phase++) {
            differs |= sprite.frameAt(100L, phase)
                    != sprite.frameAt(100L, 0);
        }
        assertTrue("every wave beats in unison", differs);
    }

    /**
     * The fade has to be what decides whether a decoration is drawn, never
     * the safety budget behind it.
     *
     * <p>This is a regression guard with a bug behind it: the cell window was
     * being sized for the most ground a fully tilted map could ever show, so
     * at the very zoom where waves were meant to be at their fullest the
     * budget tripped and the whole kind vanished. A ceiling that quietly
     * deletes a feature is worse than no ceiling.</p>
     */
    @Test
    public void theBudgetNeverDecidesWhetherADecorationIsDrawn() {
        float[] viewports = { 854.0F, 1280.0F, 1920.0F };
        float[] heights = { 480.0F, 720.0F, 1080.0F };
        for (int kind = 0;
             kind < LostTalesMapDecorationRenderer.kindCount(); kind++) {
            for (int screen = 0; screen < viewports.length; screen++) {
                for (float zoomExp = -3.6F; zoomExp <= 4.6F;
                     zoomExp += 0.1F) {
                    float zoomScale = (float)Math.pow(2.0D, zoomExp);
                    long cells = LostTalesMapDecorationRenderer
                            .visitedCells(kind, viewports[screen],
                                    heights[screen], zoomScale);
                    assertTrue("kind " + kind + " tripped its budget at zoom "
                                    + zoomExp + " with " + cells + " cells",
                            cells <= LostTalesMapDecorationRenderer
                                    .cellBudget());
                }
            }
        }
    }

    /**
     * Decorations are symbols standing on the map, not scale drawings, so
     * every kind keeps one size on screen at every zoom.
     */
    @Test
    public void everyKindHasAFixedScreenSize() {
        LostTalesMapDecorationSprite[] sprites =
                LostTalesMapDecorationSprite.values();
        for (int index = 0; index < sprites.length; index++) {
            LostTalesMapDecorationSprite sprite = sprites[index];
            assertTrue(sprite.name() + " has no width",
                    sprite.getScreenWidth() > 0.0F);
            assertTrue(sprite.name() + " has no height",
                    sprite.getScreenHeight() > 0.0F);
            assertTrue(sprite.name() + " has no frames",
                    sprite.getFrames() >= 1);
            assertEquals(sprite.name() + " must cover its whole sheet",
                    1.0D, sprite.frameUMax(sprite.getFrames() - 1), 0.0001D);
            assertEquals(sprite.name() + " must start at its sheet's edge",
                    0.0D, sprite.frameUMin(0), 0.0001D);
        }
    }
}
