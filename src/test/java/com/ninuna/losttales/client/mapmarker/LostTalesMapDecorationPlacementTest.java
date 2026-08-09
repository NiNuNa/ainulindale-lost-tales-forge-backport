package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
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
     * A narrower probe accepts narrower water, so the radius has to actually
     * change the answer.
     */
    @Test
    public void aNarrowerProbeAcceptsNarrowerWater() {
        assertTrue("a narrow probe could not reach a river",
                LostTalesMapDecorationPlacement.isBroadSite(
                        channel(400, 1), 400, 300, 1));
        assertFalse("a single-pixel stream is still too thin for a hull",
                LostTalesMapDecorationPlacement.isBroadSite(
                        channel(400, 0), 400, 300, 1));
    }

    /**
     * Where a ship belongs, which is the opposite question to where a wave
     * does. A hull in the middle of Belegaer is not a landmark, it is a speck
     * a thousand miles from anywhere; a hull wants water with a shore in
     * sight, and a river satisfies that by being mostly bank.
     *
     * <p>This is the rule ships are actually placed by. They used to be placed
     * by the broad-water rule instead, which put them everywhere a wave went
     * and nowhere a harbour was.</p>
     */
    @Test
    public void shipsWantACoastRatherThanOpenSea() {
        assertFalse("a ship was moored in the middle of the sea",
                LostTalesMapDecorationPlacement.isShoreSite(
                        EVERYWHERE, 400, 300, 3));
        assertFalse("a ship was moored on dry land",
                LostTalesMapDecorationPlacement.isShoreSite(
                        NOWHERE, 400, 300, 3));
        assertTrue("a ship could not find a coast",
                LostTalesMapDecorationPlacement.isShoreSite(
                        coast(400), 399, 300, 3));
        assertTrue("a ship could not find a river",
                LostTalesMapDecorationPlacement.isShoreSite(
                        channel(400, 1), 400, 300, 3));
        // And it still has to be standing in water itself.
        assertFalse(LostTalesMapDecorationPlacement.isShoreSite(
                coast(400), 405, 300, 3));
    }

    @Test
    public void wavesFormATaperedBandOutsideTheCoast() {
        LostTalesMapDecorationPlacement.GroundSampler water = coast(400);
        float near = LostTalesMapDecorationPlacement.coastalWaterWeight(
                water, 398, 300, 84);
        float middle = LostTalesMapDecorationPlacement.coastalWaterWeight(
                water, 350, 300, 84);
        float far = LostTalesMapDecorationPlacement.coastalWaterWeight(
                water, 320, 300, 84);
        float openSea = LostTalesMapDecorationPlacement.coastalWaterWeight(
                water, 290, 300, 84);

        assertTrue("waves did not gather outside the shoreline", near > 0.0F);
        assertTrue("wave density did not taper towards its outer edge",
                near > middle);
        assertTrue("the extended outer wave band ended too abruptly",
                middle > far && far > 0.0F);
        assertEquals("open ocean still filled with waves",
                0.0F, openSea, 0.0F);
        assertEquals("a wave was placed on land", 0.0F,
                LostTalesMapDecorationPlacement.coastalWaterWeight(
                        water, 405, 300, 28), 0.0F);
    }

    /** Water on one side of a line and land on the other. */
    private static LostTalesMapDecorationPlacement.GroundSampler coast(
            final int atX) {
        return new LostTalesMapDecorationPlacement.GroundSampler() {
            @Override
            public boolean matches(int mapX, int mapY) {
                return mapX < atX;
            }
        };
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
     * The clustering the map's distributions are built on. Every category
     * placed as an even scatter of independent points reads as a lattice with
     * noise on it; what it has to read as is country — dense cores, loose
     * edges, and stretches with nothing in them.
     */
    @Test
    public void clusteringGivesCrowdedAndEmptyCountryAlike() {
        int crowded = 0;
        int bare = 0;
        int occupied = 0;
        int cells = 0;
        for (int cellX = 0; cellX < 90; cellX++) {
            for (int cellY = 0; cellY < 90; cellY++) {
                float weight = LostTalesMapDecorationPlacement.clusterWeight(
                        cellX * CELL, cellY * CELL, CHANNEL, 90.0F);
                assertTrue("the field left its range", weight >= 0.0F);
                if (weight > 1.3F) {
                    crowded++;
                }
                if (weight < 0.25F) {
                    bare++;
                }
                cells++;
                if (LostTalesMapDecorationPlacement.hasClusteredSite(
                        cellX, cellY, CHANNEL, 0.5F, CELL, 90.0F)) {
                    occupied++;
                }
            }
        }
        assertTrue("the map has no crowded country at all", crowded > 200);
        assertTrue("the map has no empty country at all", bare > 200);
        // Still a scatter overall rather than one that has emptied the map or
        // filled it: the clumping redistributes the decorations, it does not
        // delete them.
        assertTrue("clustering emptied the map", occupied > cells / 6);
        assertTrue("clustering filled the map", occupied < cells * 3 / 4);

        // And it is a hash of the position like everything else here, so the
        // same country is crowded on every client and in every session.
        assertEquals(
                LostTalesMapDecorationPlacement.clusterWeight(
                        512.0F, -64.0F, CHANNEL, 90.0F),
                LostTalesMapDecorationPlacement.clusterWeight(
                        512.0F, -64.0F, CHANNEL, 90.0F), 0.0F);
    }

    /**
     * A clump has to be broad enough to read as one. Neighbouring cells that
     * disagree completely are just noise under another name.
     */
    @Test
    public void clustersAreBroadRatherThanPerCell() {
        float steepest = 0.0F;
        for (int cellX = 0; cellX < 60; cellX++) {
            for (int cellY = 0; cellY < 60; cellY++) {
                float here = LostTalesMapDecorationPlacement.clusterWeight(
                        cellX * 10.0F, cellY * 10.0F, CHANNEL, 200.0F);
                float next = LostTalesMapDecorationPlacement.clusterWeight(
                        (cellX + 1) * 10.0F, cellY * 10.0F, CHANNEL, 200.0F);
                steepest = Math.max(steepest, Math.abs(next - here));
            }
        }
        assertTrue("the field changes too fast to read as country: "
                + steepest, steepest < 0.5F);
    }

    /**
     * Trees do not move. A wood where every tree stepped through the same two
     * drawings together read as the paper rippling, so the frames of that
     * sheet are variants to choose between instead.
     */
    @Test
    public void treesAreStillAndVariedInsteadOfAnimated() {
        LostTalesMapDecorationSprite tree =
                LostTalesMapDecorationSprite.TREE;
        assertFalse("trees must not animate", tree.isAnimated());
        assertTrue("a wood drawn from one picture repeats visibly",
                tree.getVariants() >= 2);
        for (long tick = 0L; tick < 500L; tick += 7L) {
            assertEquals("a tree moved", 0, tree.frameAt(tick, 3));
        }
        assertTrue("mountains must not animate either",
                !LostTalesMapDecorationSprite.MOUNTAIN.isAnimated());

        // The variation that stands in for artwork the sheets do not have:
        // a size of each site's own, and half of them turned round.
        int mirrored = 0;
        float smallest = Float.MAX_VALUE;
        float largest = 0.0F;
        for (int cellX = 0; cellX < 40; cellX++) {
            for (int cellY = 0; cellY < 40; cellY++) {
                if (LostTalesMapDecorationPlacement.siteMirror(
                        cellX, cellY, CHANNEL)) {
                    mirrored++;
                }
                float scale = LostTalesMapDecorationPlacement.siteScale(
                        cellX, cellY, CHANNEL, 0.16F);
                smallest = Math.min(smallest, scale);
                largest = Math.max(largest, scale);
            }
        }
        assertTrue("every site faced the same way",
                mirrored > 400 && mirrored < 1200);
        assertTrue("sites must differ in size",
                largest - smallest > 0.2F);
        assertTrue("no site may be turned inside out or doubled",
                smallest > 0.8F && largest < 1.2F);
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
     * The complaint this was written for: pulling the map out used to thin the
     * decorations away and pushing it back in used to fade different ones in,
     * so the same country grew a different forest depending on how you had
     * arrived at it. Nothing is removed for the zoom any more — a decoration
     * is drawn until it is too small to see and then it is not.
     */
    @Test
    public void zoomingNeverSwapsOneScatterForAnother() {
        for (int kind = 0;
             kind < LostTalesMapDecorationRenderer.kindCount(); kind++) {
            assertTrue("kind " + kind + " is missing at the closest zoom",
                    LostTalesMapDecorationRenderer.isDrawn(
                            kind, (float)Math.pow(2.0D, 4.6D)));
            // Zooming out, a kind goes once and stays gone. There is nothing
            // else that can change which decorations are on the map, because
            // the zoom is not an input to placement at all.
            boolean gone = false;
            for (float zoomExp = 4.6F; zoomExp >= -3.6F; zoomExp -= 0.05F) {
                boolean drawn = LostTalesMapDecorationRenderer.isDrawn(
                        kind, (float)Math.pow(2.0D, zoomExp));
                assertFalse("kind " + kind + " came back at " + zoomExp,
                        drawn && gone);
                gone = !drawn;
            }
            assertTrue("kind " + kind + " is still drawn at the widest zoom",
                    gone);
        }
    }

    /**
     * Decorations stand on Middle-earth, so their size is a Middle-earth size:
     * the zoom grows and shrinks them exactly as it grows and shrinks the
     * ground they are standing on.
     */
    @Test
    public void everyKindIsSizedInMapPixels() {
        LostTalesMapDecorationSprite[] sprites =
                LostTalesMapDecorationSprite.values();
        for (int index = 0; index < sprites.length; index++) {
            LostTalesMapDecorationSprite sprite = sprites[index];
            assertTrue(sprite.name() + " has no width",
                    sprite.getWorldWidth() > 0.0F);
            assertTrue(sprite.name() + " has no height",
                    sprite.getWorldHeight() > 0.0F);
            assertTrue(sprite.name() + " has no frames",
                    sprite.getFrames() >= 1);
            assertEquals(sprite.name() + " must cover its whole sheet",
                    1.0D, sprite.frameUMax(sprite.getFrames() - 1), 0.0001D);
            assertEquals(sprite.name() + " must start at its sheet's edge",
                    0.0D, sprite.frameUMin(0), 0.0001D);
        }
    }

    /**
     * What stands in front has to be painted over what stands behind. The
     * lean is applied last and acts on the screen, so "further away" is
     * "further up the screen" at every angle the map can be turned to, and
     * ordering by the sprite's foot is the whole of it.
     */
    @Test
    public void decorationsArePaintedFromTheBackForwards() {
        int far = LostTalesMapDecorationRenderer.depthOf(20.0F);
        int middle = LostTalesMapDecorationRenderer.depthOf(140.0F);
        int near = LostTalesMapDecorationRenderer.depthOf(300.0F);
        assertTrue("the order must run down the screen",
                far < middle && middle < near);

        // Sub-pixel differences have to survive, or two sprites a hair apart
        // swap places from frame to frame and the map shimmers.
        assertTrue("the order is too coarse to separate close neighbours",
                LostTalesMapDecorationRenderer.depthOf(140.0F)
                        < LostTalesMapDecorationRenderer.depthOf(140.3F));

        // A sprite far outside the viewport must not wrap round the order and
        // come back in front of everything.
        assertTrue(LostTalesMapDecorationRenderer.depthOf(-900000.0F)
                <= LostTalesMapDecorationRenderer.depthOf(0.0F));
        assertTrue(LostTalesMapDecorationRenderer.depthOf(900000.0F)
                >= LostTalesMapDecorationRenderer.depthOf(0.0F));
        assertTrue("the order must never go negative",
                LostTalesMapDecorationRenderer.depthOf(-900000.0F) >= 0);
    }

    /**
     * The invariant the whole layer is built on: pushing the map closer makes
     * a decoration larger and pulling it out makes it smaller, in proportion,
     * with no compensation anywhere. And what ends a kind is being too small
     * to draw, not the zoom being at any particular place.
     */
    @Test
    public void zoomingGrowsDecorationsAndCullsThemWhenTiny() {
        for (int kind = 0;
             kind < LostTalesMapDecorationRenderer.kindCount(); kind++) {
            float close = LostTalesMapDecorationRenderer.drawnWidth(
                    kind, 4.0F);
            float far = LostTalesMapDecorationRenderer.drawnWidth(
                    kind, 1.0F);
            assertEquals("a decoration must scale with the ground",
                    4.0F, close / far, 0.0001F);

            // Drawn at every zoom where there is something to see, and gone
            // once there is not. Nothing in between: no fade, and no zoom at
            // which some of a kind is drawn and the rest is not.
            assertTrue("kind " + kind + " is missing at a readable size",
                    LostTalesMapDecorationRenderer.isDrawn(kind, 1.0F));
            assertTrue("kind " + kind + " is missing zoomed in",
                    LostTalesMapDecorationRenderer.isDrawn(kind, 24.0F));
            assertFalse("a sub-pixel decoration must be culled",
                    LostTalesMapDecorationRenderer.isDrawn(kind, 0.02F));

            boolean foundPartial = false;
            for (float scale = 0.02F; scale <= 1.0F; scale += 0.01F) {
                float alpha = LostTalesMapDecorationRenderer
                        .visibilityAlpha(kind, scale);
                foundPartial |= alpha > 0.0F && alpha < 1.0F;
            }
            assertTrue("kind " + kind + " has a hard visibility step",
                    foundPartial);
        }
    }

    @Test
    public void largeLandmarksOutliveSmallSurfaceDetail() {
        float scale = 0.3F;
        assertTrue(LostTalesMapDecorationSprite.MOUNTAIN
                .visibilityAlpha(
                        LostTalesMapDecorationSprite.MOUNTAIN
                                .getWorldWidth() * scale)
                > LostTalesMapDecorationSprite.WAVE.visibilityAlpha(
                        LostTalesMapDecorationSprite.WAVE
                                .getWorldWidth() * scale));
    }

    @Test
    public void largeNearEdgeSitesAreNotHeldUntilTheAverageSpriteAppears() {
        int mountainKind = 2;
        float scale = 0.14F;

        assertEquals("the average mountain should still be fully clear",
                0.0F, LostTalesMapDecorationRenderer.visibilityAlpha(
                        mountainKind, scale), 0.0F);
        assertTrue("the whole-kind preflight would pop larger mountains in",
                LostTalesMapDecorationRenderer.isDrawn(
                        mountainKind, scale));
    }

    /**
     * What is standing up and what is lying down. It decides how much taller a
     * thing is drawn as the map tips, so it is a statement about what the
     * artwork depicts rather than about the artwork.
     */
    @Test
    public void howMuchOfEachKindIsStandingUp() {
        assertEquals("a mountain is all height", 1.0F,
                LostTalesMapDecorationSprite.MOUNTAIN.getStanding(), 0.0F);
        assertEquals("a wave is not standing on anything", 0.0F,
                LostTalesMapDecorationSprite.WAVE.getStanding(), 0.0F);
        assertTrue("a tree stands, but not like a mountain",
                LostTalesMapDecorationSprite.TREE.getStanding() > 0.0F
                        && LostTalesMapDecorationSprite.TREE.getStanding()
                                < LostTalesMapDecorationSprite.MOUNTAIN
                                        .getStanding());

        LostTalesMapDecorationSprite[] sprites =
                LostTalesMapDecorationSprite.values();
        for (int index = 0; index < sprites.length; index++) {
            float standing = sprites[index].getStanding();
            assertTrue(sprites[index].name() + " left its range",
                    standing >= 0.0F && standing <= 1.0F);
        }
    }

    @Test
    public void transparentRowsBelowArtworkDoNotLiftItsVisibleFoot() {
        assertEquals("mountain ink must end exactly at its ground anchor",
                1.0F,
                LostTalesMapDecorationSprite.MOUNTAIN.footOffset(16.0F),
                0.0F);
        assertEquals("ship ink has two transparent rows below its hull",
                2.0F,
                LostTalesMapDecorationSprite.SHIP.footOffset(16.0F),
                0.0F);
    }

    @Test
    public void shadowKeepsItsBaseAndShearsTheActualSpriteToTheRight() {
        float[] quad = new float[8];
        LostTalesMapDecorationRenderer.positionShadowQuad(
                10.0F, 60.0F, 30.0F, 20.0F, 1.0F, quad);

        assertEquals(10.0F, quad[0], 0.0F);
        assertEquals(60.0F, quad[1], 0.0F);
        assertEquals(30.0F, quad[2], 0.0F);
        assertEquals(60.0F, quad[3], 0.0F);
        assertTrue("the projected top did not reach right", quad[4] > 30.0F);
        assertTrue("the projected top dropped below its source", quad[5] > 20.0F);
        assertEquals("projection changed the sprite silhouette's width",
                20.0F, quad[4] - quad[6], 0.001F);
    }

    @Test
    public void shadowStartsBehindItsSpriteAndMovesWithoutASnap() {
        float[] flat = new float[8];
        float[] barelyTilted = new float[8];
        LostTalesMapDecorationRenderer.positionShadowQuad(
                10.0F, 60.0F, 30.0F, 20.0F, 0.0F, flat);
        LostTalesMapDecorationRenderer.positionShadowQuad(
                10.0F, 60.0F, 30.0F, 20.0F, 0.001F, barelyTilted);

        assertArrayEquals(new float[] {
                10.0F, 60.0F, 30.0F, 60.0F,
                30.0F, 20.0F, 10.0F, 20.0F
        }, flat, 0.0F);
        assertTrue(barelyTilted[4] > flat[4]);
        assertTrue(barelyTilted[4] - flat[4] < 0.1F);
        assertTrue(barelyTilted[5] > flat[5]);
        assertTrue(barelyTilted[5] - flat[5] < 0.1F);
    }
}
