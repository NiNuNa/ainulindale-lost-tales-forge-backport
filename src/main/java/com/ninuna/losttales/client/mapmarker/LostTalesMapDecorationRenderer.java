package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Small things standing on the map: waves, trees, mountains, and ships.
 *
 * <p>Every one of them is a thing in Middle-earth rather than a thing on the
 * screen. Its position is a map position and its size is a map size, so the
 * zoom moves it and grows it exactly as it moves and grows the ground it stands
 * on; nothing here is held at a constant number of screen pixels, and nothing
 * is held at a constant opacity when the map is pulled out. The sprites shrink
 * with their ground and smoothly fade only as their projected artwork becomes
 * too small to read.</p>
 *
 * <p>The one respect in which they are not part of the paper is that they stand
 * up from it. The <em>base</em> goes through the sheet's own projection — pan,
 * zoom, turn, lean — and the sprite is then drawn upright and square to the
 * screen at the size the projection says something standing there should be.
 * That is the whole difference between these and the clouds and region names,
 * which lie on the paper and lean with it.</p>
 *
 * <p>Nothing here is an entity or is stored between sessions. Where each kind
 * stands is worked out once for the world by
 * {@link LostTalesMapDecorationSites} and kept, so a frame walks the
 * decorations rather than the ground they might have been on — which is what
 * lets the map draw all of them at every zoom instead of thinning them out as
 * it is pulled back.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapDecorationRenderer {
    private static final float[] VISIBLE_COVERAGE = new float[2];
    private static final float[] SITE_ANCHOR = new float[2];
    /**
     * A kind of decoration scattered over the ground.
     *
     * <p>The cell and the sprite are both in map pixels, so how much of a
     * forest is covered in trees is a property of Middle-earth and is the same
     * at every zoom. What each kind is worth is in the numbers: waves and trees
     * are common and close together, mountains and ships are landmarks and are
     * not.</p>
     */
    private static final class Scattered {
        private static final int BROAD = 0;
        private static final int SHORE = 1;
        private static final int COASTAL_WATER = 2;
        private final LostTalesMapDecorationSprite sprite;
        private final LostTalesMapTerrain terrain;
        private final int probeRadius;
        /** Broad ground, a mixed shore, or a tapered water band off land. */
        private final int placement;
        /** Lattice spacing, in map pixels. */
        private final float cell;
        private final float density;
        /** How broad a clump of this kind is, in map pixels. */
        private final float clusterSize;
        /** How far one may wander from its site, in map pixels. */
        private final float drift;
        /**
         * Whether this kind casts a shadow.
         *
         * <p>What stands on the ground does. What floats on water does not:
         * a wave is the water, and a hull at this scale casts nothing on it
         * that would read as anything but a smudge.</p>
         */
        private final boolean shadow;
        private final int channel;
        /** Every site of this kind on the map, resolved once. */
        private final LostTalesMapDecorationSites sites =
                new LostTalesMapDecorationSites();
        private final LostTalesMapDecorationSites.SiteRule rule;

        private Scattered(LostTalesMapDecorationSprite sprite,
                          LostTalesMapTerrain terrain, int probeRadius,
                          int placement, float cell, float density,
                          float clusterSize, float drift, boolean shadow,
                          int channel) {
            this.sprite = sprite;
            this.terrain = terrain;
            this.probeRadius = probeRadius;
            this.placement = placement;
            this.cell = cell;
            this.density = density;
            this.clusterSize = clusterSize;
            this.drift = drift;
            this.shadow = shadow;
            this.channel = channel;
            this.rule = new LostTalesMapDecorationSites.SiteRule() {
                @Override
                public boolean hasSite(int cellX, int cellY) {
                    return LostTalesMapDecorationPlacement.hasClusteredSite(
                            cellX, cellY, Scattered.this.channel,
                            Scattered.this.density, Scattered.this.cell,
                            Scattered.this.clusterSize);
                }

                @Override
                public void position(int cellX, int cellY, float[] result) {
                    result[0] = LostTalesMapDecorationPlacement.siteX(
                            cellX, cellY, Scattered.this.channel,
                            Scattered.this.cell, SITE_JITTER);
                    result[1] = LostTalesMapDecorationPlacement.siteY(
                            cellX, cellY, Scattered.this.channel,
                            Scattered.this.cell, SITE_JITTER);
                }

                @Override
                public boolean isGround(float mapX, float mapY) {
                    return Scattered.this.isSite((int)mapX, (int)mapY);
                }
            };
        }

        private boolean isSite(int mapX, int mapY) {
            if (this.placement == SHORE) {
                return LostTalesMapDecorationPlacement.isShoreSite(
                        this.terrain, mapX, mapY, this.probeRadius);
            }
            if (this.placement == COASTAL_WATER) {
                float weight = LostTalesMapDecorationPlacement
                        .coastalWaterWeight(
                                this.terrain, mapX, mapY, this.probeRadius);
                return weight > 0.0F && LostTalesLotrMapAtmosphere.cellNoise(
                        mapX, mapY, this.channel + 31) < weight;
            }
            return LostTalesMapDecorationPlacement.isBroadSite(
                    this.terrain, mapX, mapY, this.probeRadius);
        }
    }

    /** Maximum opacity after the projected-size visibility fade is applied. */
    private static final float MAX_ALPHA = 0.9F;
    /** How far a decoration may differ in size from its fellows. */
    private static final float SIZE_VARIATION = 0.16F;
    /** Largest site scale produced by {@link #SIZE_VARIATION}. */
    private static final float MAX_SITE_SCALE = 1.0F + SIZE_VARIATION;
    /** The near edge grows by this much at the strongest perspective. */
    private static final float MAX_PERSPECTIVE_SCALE =
            1.0F + LostTalesLotrMapRotation.MAX_LEAN;
    /**
     * How far a shadow reaches, as a share of the thing casting it, at a
     * fully dropped eye.
     *
     * <p>Multiplied by how far the eye has actually dropped, so this is the
     * longest it ever gets and a flat map has none at all.</p>
     */
    private static final float SHADOW_SHEAR_X = 0.7F;
    /** Small downward component of the upper-right projection. */
    private static final float SHADOW_DROP_Y = 0.16F;
    /**
     * How dark a shadow is.
     *
     * <p>Low, and multiplied by whatever the sprite's own opacity is, so a
     * decoration fading in over a thinning step brings its shadow with it and
     * the ground never darkens on its own. Tilt moves the silhouette but does
     * not fade it: at flat it is simply hidden directly behind its caster.</p>
     */
    private static final float SHADOW_ALPHA = 0.3F;
    private static final float FULL_LUMINANCE = 1.0F;
    /**
     * How much of the height a fully standing thing would gain it is given.
     *
     * <p>One at the geometry's own answer, which at this map's limit is half
     * again as tall. A third of that is enough to read as height and gentle
     * enough that the artwork still looks like itself; the number is here to
     * be turned up if it wants to be.</p>
     */
    private static final float STANDING_GAIN = 0.35F;
    /** Noise channels each kind takes, well clear of the sky's. */
    private static final int CHANNEL_BASE = 4096;
    private static final int CHANNELS_PER_KIND = 64;
    /** The channel the shared "is this water" answers are remembered under. */
    private static final int CHANNEL_NAVIGABLE = CHANNEL_BASE - 2;

    /**
     * The scattered kinds, in the order they are drawn.
     *
     * <p>Adding a kind is one row here plus its artwork.</p>
     */
    private static final Scattered[] SCATTERED = {
            new Scattered(LostTalesMapDecorationSprite.WAVE,
                    LostTalesMapTerrain.OPEN_WATER, 84,
                    Scattered.COASTAL_WATER,
                    11.0F, 0.78F, 120.0F, 0.0F, false, CHANNEL_BASE),
            // Forests are the thing trees are for, so they are the densest
            // lattice of the lot, and they clump inside a wood rather than
            // covering it evenly.
            new Scattered(LostTalesMapDecorationSprite.TREE,
                    LostTalesMapTerrain.FOREST, 2, Scattered.BROAD,
                    11.0F, 0.8F, 120.0F, 0.0F, true,
                    CHANNEL_BASE + CHANNELS_PER_KIND),
            // Mountains cluster more broadly still: a range is a long thing,
            // and the terrain it is placed on is already shaped like one.
            new Scattered(LostTalesMapDecorationSprite.MOUNTAIN,
                    LostTalesMapTerrain.MOUNTAIN, 2, Scattered.BROAD,
                    15.0F, 0.65F, 170.0F, 0.0F, true,
                    CHANNEL_BASE + CHANNELS_PER_KIND * 2),
            // A ship belongs to a coast, a lake or a river, and even there it
            // is a sight rather than a fleet. A lake is small enough to be
            // mostly shore, so the same rule puts a boat on one without
            // needing to know it is a lake.
            new Scattered(LostTalesMapDecorationSprite.SHIP,
                    LostTalesMapTerrain.NAVIGABLE_WATER, 3, Scattered.SHORE,
                    30.0F, 0.5F, 240.0F, 6.0F, false,
                    CHANNEL_BASE + CHANNELS_PER_KIND * 3),
            // And once in a long while a lone sail well out in the deep, which
            // the rule above deliberately cannot place: it wants a shore in
            // sight. Sparse enough, and spread over cells wide enough, that
            // Belegaer stays empty ocean with the occasional ship in it rather
            // than a shipping lane.
            new Scattered(LostTalesMapDecorationSprite.SHIP,
                    LostTalesMapTerrain.OPEN_WATER, 3, Scattered.BROAD,
                    150.0F, 0.1F, 900.0F, 6.0F, false,
                    CHANNEL_BASE + CHANNELS_PER_KIND * 4)
    };
    private static final float SITE_JITTER = 0.42F;

    /**
     * How far a ship sails from its mooring and back, as an angle per tick.
     *
     * <p>A ship that never moves is scenery; one that crosses the map is a
     * distraction and would need somewhere to be going. So it stands out and
     * comes back on a long, slow swell of its own — a full run out and back
     * takes about two and a half minutes of world time. Deterministic, worked
     * out from the world clock, costing nothing to remember and identical on
     * every client.</p>
     */
    private static final float SHIP_DRIFT_RADIANS_PER_TICK = 0.0021F;
    private static final float TWO_PI = (float)(Math.PI * 2.0D);

    private LostTalesMapDecorationRenderer() {}

    public static void clearCache() {
        // What the map image said about a world that is being left cannot be
        // carried into the next one, and neither can where its trees stood.
        siteKeys = new long[SITE_CACHE_SLOTS];
        siteAnswers = new boolean[SITE_CACHE_SLOTS];
        for (int index = 0; index < SCATTERED.length; index++) {
            SCATTERED[index].sites.clear();
        }
        LostTalesMapTerrain.clear();
        // The frame buffers are scratch and are refilled from nothing every
        // frame, but they hold references to enum constants and a few hundred
        // kilobytes of arrays; a world being left is the right moment to let
        // both go rather than carry them into the next one.
        spriteCount = 0;
        spriteFields = new float[INITIAL_SPRITE_CAPACITY * SPRITE_STRIDE];
        spriteArt = new LostTalesMapDecorationSprite[
                INITIAL_SPRITE_CAPACITY];
        spriteOrder = new long[INITIAL_SPRITE_CAPACITY];
        shadowCount = 0;
        shadowFields = new float[INITIAL_SPRITE_CAPACITY * SHADOW_STRIDE];
        shadowArt = new LostTalesMapDecorationSprite[
                INITIAL_SPRITE_CAPACITY];
    }

    /**
     * Draws every decoration in view.
     *
     * @param posX      map-image position the camera is centred on
     * @param zoomScale screen pixels per map-image pixel
     */
    static void render(LostTalesLotrMapGui gui, long worldTime,
                       float posX, float posY, float zoomScale,
                       int viewportXMin, int viewportXMax,
                       int viewportYMin, int viewportYMax) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null
                || !(zoomScale > 0.0F)
                || !LostTalesMapTerrain.isMapImageReady()) {
            return;
        }
        float centerX = (viewportXMin + viewportXMax) * 0.5F;
        float centerY = (viewportYMin + viewportYMax) * 0.5F;
        // How much ground is actually on screen right now, rather than the
        // most there could ever be. A turned or leaning map looks past its own
        // corners and needs the wider box; a flat one does not, and giving it
        // the wide box anyway meant visiting four times the cells for nothing.
        LostTalesLotrMapRotation.visibleCoverage(
                viewportXMax - viewportXMin, viewportYMax - viewportYMin,
                LostTalesLotrMapRotation.degreesOf(gui),
                LostTalesLotrMapRotation.leanOf(gui), VISIBLE_COVERAGE);
        float reachX = VISIBLE_COVERAGE[0] * 0.5F / zoomScale;
        float reachY = VISIBLE_COVERAGE[1] * 0.5F / zoomScale;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);
        // A sprite standing at the edge of the map is drawn its full width
        // wherever its foot lands, and the sheet reaches past the viewport
        // besides, so without this the decorations spill onto the panels.
        boolean clipped = LostTalesLotrMapLayout.beginViewportClip(
                viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            // Worked out first and drawn afterwards, because what has to be
            // decided across all of them together is the order.
            spriteCount = 0;
            shadowCount = 0;
            frameLeanSine = LostTalesLotrMapRotation.leanSine(gui);
            for (int index = 0; index < SCATTERED.length; index++) {
                collectScattered(gui, SCATTERED[index], worldTime,
                        posX, posY, zoomScale, centerX, centerY,
                        reachX, reachY,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            }
            flushShadows(minecraft);
            flushSprites(minecraft);
        } catch (Throwable ignored) {
            // Decoration is the least important thing on the map; the map
            // itself and everything a player navigates by are already drawn.
        } finally {
            spriteCount = 0;
            shadowCount = 0;
            LostTalesLotrMapLayout.endViewportClip(clipped);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private static void collectScattered(
            LostTalesLotrMapGui gui, Scattered kind,
            long worldTime, float posX, float posY, float zoomScale,
            float centerX, float centerY, float reachX, float reachY,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float worldWidth = kind.sprite.getWorldWidth();
        // Reject the kind only if even its largest possible near-edge site is
        // mathematically clear. Testing the average sprite here made the
        // larger sites enter halfway through their own fade, so one wheel
        // notch revealed the whole kind at a faint but already-visible alpha.
        if (kind.sprite.visibilityAlpha(maximumDrawnWidth(
                worldWidth, zoomScale)) <= 0.0F) {
            return;
        }
        // Where this kind actually stands, worked out once for the world and
        // carried on a slice at a time. What is walked here is the
        // decorations, not the ground they might have been on.
        LostTalesMapDecorationSites sites = kind.sites;
        sites.advance(kind.rule, kind.cell,
                mapImageWidth(), mapImageHeight());
        float worldHeight = kind.sprite.getWorldHeight();
        // A hull under sail is not quite where its mooring is, so the window
        // is opened by as far as one can have run.
        float margin = worldWidth + kind.drift;
        float minX = posX - reachX - margin;
        float maxX = posX + reachX + margin;
        float minY = posY - reachY - margin;
        float maxY = posY + reachY + margin;
        int found = sites.getCount();
        for (int index = 0; index < found; index++) {
            float mapX = sites.getX(index);
            float mapY = sites.getY(index);
            if (mapX < minX || mapX > maxX || mapY < minY || mapY > maxY) {
                continue;
            }
            collectScatteredSite(gui, kind,
                    sites.getCellX(index), sites.getCellY(index),
                    mapX, mapY, MAX_ALPHA, worldTime, posX, posY,
                    zoomScale, centerX, centerY,
                    worldWidth, worldHeight, SITE_ANCHOR,
                    viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
        }
    }

    private static int mapImageWidth() {
        return LostTalesLotrMapRotation.mapImageWidth();
    }

    private static int mapImageHeight() {
        return LostTalesLotrMapRotation.mapImageHeight();
    }

    private static void collectScatteredSite(
            LostTalesLotrMapGui gui, Scattered kind,
            int cellX, int cellY, float mapX, float mapY, float alpha,
            long worldTime, float posX, float posY, float zoomScale,
            float centerX, float centerY, float worldWidth, float worldHeight,
            float[] anchor, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        boolean mirror = LostTalesMapDecorationPlacement.siteMirror(
                cellX, cellY, kind.channel);
        if (kind.drift > 0.0F) {
            // The sail is out: how far it has run this tick, and which way it
            // is pointing while it does.
            float heading = LostTalesLotrMapAtmosphere.cellNoise(
                    cellX, cellY, kind.channel + 7) * TWO_PI;
            float phase = LostTalesLotrMapAtmosphere.cellNoise(
                    cellX, cellY, kind.channel + 8) * TWO_PI;
            float reach = navigableReach(mapX, mapY, heading, kind.drift);
            if (reach > 0.0F) {
                // In double, because a world that has been running for years
                // has a tick count large enough that a float angle would
                // quantise the swell into visible steps.
                double angle = worldTime * (double)SHIP_DRIFT_RADIANS_PER_TICK
                        + phase;
                float alongX = (float)Math.cos(heading);
                float alongY = (float)Math.sin(heading);
                float travelled = (float)Math.sin(angle) * reach;
                mapX += alongX * travelled;
                mapY += alongY * travelled;
                // Facing where it is going, which is the direction the swell
                // is currently carrying it rather than the way it points.
                float speedX = alongX * (float)Math.cos(angle);
                if (speedX != 0.0F) {
                    mirror = speedX < 0.0F;
                }
            }
        }
        collectStanding(gui, kind.sprite, mapX, mapY,
                frameFor(kind, cellX, cellY, worldTime), alpha, mirror,
                LostTalesMapDecorationPlacement.siteScale(
                        cellX, cellY, kind.channel, SIZE_VARIATION),
                kind.shadow, posX, posY, zoomScale, centerX, centerY,
                worldWidth, worldHeight, anchor, viewportXMin, viewportXMax,
                viewportYMin, viewportYMax);
    }

    /**
     * Which drawing of a kind a site shows.
     *
     * <p>A step of an animation where the sheet is one, and one of several
     * drawings where it is not — a tree does not sway, but neighbouring trees
     * do have to differ, or a wood reads as one stamp repeated.</p>
     */
    private static int frameFor(
            Scattered kind, int cellX, int cellY, long worldTime) {
        if (kind.sprite.isAnimated()) {
            return kind.sprite.frameAt(worldTime,
                    LostTalesMapDecorationPlacement.sitePhase(
                            cellX, cellY, kind.channel));
        }
        return LostTalesMapDecorationPlacement.siteVariant(
                cellX, cellY, kind.channel, kind.sprite.getVariants());
    }

    /**
     * How far a hull may actually run along a heading before it would be on
     * land.
     *
     * <p>Checked at both ends of the swell rather than every frame, so a ship
     * either has room for its whole run or stays where it is moored; a ship
     * that stopped dead against a shore each time round would read worse than
     * one that never moved. Nothing here needs a route or a destination — the
     * question is only whether the water is open enough for a boat to be
     * plausibly moving in it.</p>
     *
     * @return the reach to use, or zero when there is no room to move
     */
    private static float navigableReach(
            float mapX, float mapY, float heading, float drift) {
        float alongX = (float)Math.cos(heading) * drift;
        float alongY = (float)Math.sin(heading) * drift;
        if (isNavigable(mapX + alongX, mapY + alongY)
                && isNavigable(mapX - alongX, mapY - alongY)
                && isNavigable(mapX + alongX * 0.5F, mapY + alongY * 0.5F)
                && isNavigable(mapX - alongX * 0.5F, mapY - alongY * 0.5F)) {
            return drift;
        }
        return 0.0F;
    }

    /**
     * Works out where and how large one sprite standing on a map position is,
     * and puts it by to be drawn.
     *
     * <p>The base goes through the same one place every other position on the
     * map goes through, so it pans, zooms, turns and leans with the ground it
     * is standing on, and the projection says how far away that ground ended
     * up. The sprite is then laid out square to the screen at a size that is
     * its map size carried through that projection — which is what makes it
     * stand on the map rather than lie on it, and what makes the far half of a
     * leaning map recede instead of reading as a wall.</p>
     *
     * <p>Put by rather than drawn, because whether this sprite belongs in
     * front of or behind the last one is not a question either of them can
     * answer alone. See {@link #flushSprites}.</p>
     */
    private static void collectStanding(
            LostTalesLotrMapGui gui,
            LostTalesMapDecorationSprite sprite,
            float mapX, float mapY, int frame, float alpha, boolean mirror,
            float sizeScale, boolean shadow, float posX, float posY,
            float zoomScale, float centerX, float centerY,
            float worldWidth, float worldHeight,
            float[] anchor, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        anchor[0] = (mapX - posX) * zoomScale + centerX;
        anchor[1] = (mapY - posY) * zoomScale + centerY;
        float depth = LostTalesLotrMapRotation.rotateAndProject(anchor, gui);
        // Held here because the shadow below borrows the scratch the anchor
        // was worked out in.
        float screenX = anchor[0];
        float screenY = anchor[1];
        float drawn = zoomScale * sizeScale * depth;
        float width = worldWidth * drawn;
        float visibility = sprite.visibilityAlpha(width);
        if (visibility <= 0.0F) {
            return;
        }
        float height = worldHeight * drawn;
        float left = screenX - width * 0.5F;
        float right = left + width;
        float stretch = standingStretch(sprite);
        float bottom = screenY + sprite.footOffset(height) * stretch;
        float top = bottom - height * stretch;
        if (right < viewportXMin || left > viewportXMax
                || bottom < viewportYMin || top > viewportYMax) {
            return;
        }
        alpha *= visibility;
        float uMin = (float)sprite.frameUMin(frame);
        float uMax = (float)sprite.frameUMax(frame);
        if (mirror) {
            // Turning the drawing round costs nothing and doubles how many
            // silhouettes a sheet of two or three drawings is worth.
            float swap = uMin;
            uMin = uMax;
            uMax = swap;
        }
        // Both are keyed to the foot, so a thing and its shadow stay together
        // in the order however the map is turned, and the shadow is added
        // first so it lands under its own sprite.
        int order = depthOf(screenY);
        if (shadow) {
            collectShadow(sprite, left, bottom, right, top, alpha,
                    uMin, uMax,
                    viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
        }
        addQuad(sprite, left, bottom, right, bottom, right, top, left, top,
                alpha, FULL_LUMINANCE, uMin, uMax, order);
    }

    /**
     * How much taller than its artwork a thing is drawn, for standing up.
     *
     * <p>A billboard has no thickness, so on a leaning map it reads as a card
     * lying against the paper rather than as something rising out of it. What
     * would actually happen to a thing with height is that dropping the eye
     * shows more of its side — so the sprite is drawn taller as the map tips,
     * by how much of it is standing up and how far the eye has dropped, and
     * not at all while the map is flat.</p>
     *
     * <p>A vertical scale of a square-on quad and nothing else. No shear, no
     * rotation, no resampling along a diagonal: with nearest-neighbour
     * filtering every pixel of the artwork stays a crisp rectangle, drawn
     * taller than it is wide. {@link #STANDING_GAIN} is deliberately well
     * under what the geometry would ask for, because the honest number is a
     * half again as tall and pixel art stretched that far stops looking like
     * the thing that was drawn.</p>
     */
    private static float standingStretch(
            LostTalesMapDecorationSprite sprite) {
        float standing = sprite.getStanding();
        if (standing <= 0.0F) {
            return 1.0F;
        }
        return 1.0F + STANDING_GAIN * standing * frameLeanSine;
    }

    /**
     * The decoration's own silhouette projected behind it.
     *
     * <p>The bottom edge remains fixed at the visible foot while the top edge
     * shears right and slightly down. Drawing the same texture through that
     * quad gives a mountain a triangular shadow and a tree a tree-shaped
     * shadow instead of approximating either with generic geometry.</p>
     *
     * <p>The complete silhouette exists throughout the tilt. Looking straight
     * down, it lies directly behind the decoration and is covered by it. As
     * the eye drops, the top moves continuously right and down, exposing more
     * of the same shadow without an opacity threshold or a geometry jump.</p>
     *
     * <p>The upright sprite is drawn afterwards. It covers the shared portion
     * of the silhouettes and leaves only the projected right-hand part visible.</p>
     */
    private static void collectShadow(
            LostTalesMapDecorationSprite sprite,
            float left, float bottom, float right, float top, float alpha,
            float uMin, float uMax,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        positionShadowQuad(left, bottom, right, top,
                frameLeanSine, SHADOW_QUAD);
        if (isOffViewport(SHADOW_QUAD, viewportXMin, viewportXMax,
                viewportYMin, viewportYMax)) {
            return;
        }
        addShadowQuad(sprite, SHADOW_QUAD[0], SHADOW_QUAD[1],
                SHADOW_QUAD[2], SHADOW_QUAD[3],
                SHADOW_QUAD[4], SHADOW_QUAD[5],
                SHADOW_QUAD[6], SHADOW_QUAD[7],
                alpha * SHADOW_ALPHA, uMin, uMax);
    }

    /** Testable geometry seam for the fixed-foot silhouette projection. */
    static void positionShadowQuad(
            float left, float bottom, float right, float top,
            float leanSine, float[] result) {
        if (result == null || result.length < 8) {
            return;
        }
        float height = Math.max(0.0F, bottom - top);
        float lean = Math.max(0.0F, Math.min(1.0F, leanSine));
        float shear = height * SHADOW_SHEAR_X * lean;
        float drop = height * SHADOW_DROP_Y * lean;
        result[0] = left;
        result[1] = bottom;
        result[2] = right;
        result[3] = bottom;
        result[4] = right + shear;
        result[5] = top + drop;
        result[6] = left + shear;
        result[7] = top + drop;
    }

    /** Carries one map position onto the screen, through the sheet. */
    private static void project(
            LostTalesLotrMapGui gui, float mapX, float mapY,
            float posX, float posY, float zoomScale,
            float centerX, float centerY, float[] result) {
        result[0] = (mapX - posX) * zoomScale + centerX;
        result[1] = (mapY - posY) * zoomScale + centerY;
        LostTalesLotrMapRotation.rotateAndProject(result, gui);
    }

    private static boolean isOffViewport(
            float[] quad, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float minX = Math.min(Math.min(quad[0], quad[2]),
                Math.min(quad[4], quad[6]));
        float maxX = Math.max(Math.max(quad[0], quad[2]),
                Math.max(quad[4], quad[6]));
        float minY = Math.min(Math.min(quad[1], quad[3]),
                Math.min(quad[5], quad[7]));
        float maxY = Math.max(Math.max(quad[1], quad[3]),
                Math.max(quad[5], quad[7]));
        return maxX < viewportXMin || minX > viewportXMax
                || maxY < viewportYMin || minY > viewportYMax;
    }

    /**
     * One sprite's worth of the frame's collected geometry.
     *
     * <p>Kept as parallel arrays that outlive the frame rather than as objects
     * made and thrown away inside it: this is a render path that runs for
     * every decoration on screen, and it must not hand the collector work to
     * do.</p>
     */
    private static final int SPRITE_STRIDE = 12;
    /**
     * Scratch for a shadow's four corners while they are being projected.
     *
     * <p>The map screen draws on one thread and this never leaves the method
     * that fills it; it exists so that laying out a few thousand shadows does
     * not hand the collector a few thousand arrays to clean up.</p>
     */
    private static final float[] SHADOW_QUAD = new float[8];
    private static final int INITIAL_SPRITE_CAPACITY = 1024;
    /** Same field layout as an upright sprite, held in a separate back pass. */
    private static final int SHADOW_STRIDE = 12;
    private static float[] shadowFields =
            new float[INITIAL_SPRITE_CAPACITY * SHADOW_STRIDE];
    private static LostTalesMapDecorationSprite[] shadowArt =
            new LostTalesMapDecorationSprite[INITIAL_SPRITE_CAPACITY];
    private static int shadowCount;
    /**
     * How far the eye has dropped this frame, as a sine.
     *
     * <p>Read once and kept for the frame rather than asked per sprite: it is
     * the same answer for every decoration on the map, and it is wanted twice
     * for each of them.</p>
     */
    private static float frameLeanSine;
    /**
     * Most sprites one frame may draw.
     *
     * <p>The cell budget already bounds how many can be found; this bounds
     * what is kept, so a viewport far outside anything the map can produce
     * cannot grow these arrays without limit.</p>
     */
    private static final int MAX_SPRITES = 1 << 16;
    private static float[] spriteFields =
            new float[INITIAL_SPRITE_CAPACITY * SPRITE_STRIDE];
    private static LostTalesMapDecorationSprite[] spriteArt =
            new LostTalesMapDecorationSprite[INITIAL_SPRITE_CAPACITY];
    /**
     * The draw order, as {@code depth} in the high bits and the sprite's index
     * in the low ones.
     *
     * <p>Packed into a {@code long} so that sorting it is one call to the
     * primitive sort — no comparator, no boxing, and nothing allocated for a
     * frame that may be holding a few thousand decorations.</p>
     */
    private static long[] spriteOrder = new long[INITIAL_SPRITE_CAPACITY];
    private static int spriteCount;
    /** Bits of a packed key given to the sprite's index. */
    private static final int ORDER_INDEX_BITS = 20;
    private static final long ORDER_INDEX_MASK =
            (1L << ORDER_INDEX_BITS) - 1L;
    /**
     * Sub-pixel steps the depth is measured in, and the offset that keeps it
     * positive for a sprite drawn above the top of the screen.
     */
    private static final float DEPTH_STEPS_PER_PIXEL = 8.0F;
    private static final float DEPTH_ORIGIN = 32768.0F;
    private static final int MAX_DEPTH = (1 << 21) - 1;

    /** Adds one skewed copy of the decoration artwork to the shadow pass. */
    private static void addShadowQuad(
            LostTalesMapDecorationSprite sprite,
            float x0, float y0, float x1, float y1,
            float x2, float y2, float x3, float y3, float alpha,
            float uMin, float uMax) {
        if (shadowCount >= MAX_SPRITES) {
            return;
        }
        int required = (shadowCount + 1) * SHADOW_STRIDE;
        if (required > shadowFields.length) {
            int capacity = Math.min(MAX_SPRITES, shadowArt.length * 2);
            shadowFields = Arrays.copyOf(shadowFields,
                    capacity * SHADOW_STRIDE);
            shadowArt = Arrays.copyOf(shadowArt, capacity);
        }
        int field = shadowCount * SHADOW_STRIDE;
        shadowFields[field] = x0;
        shadowFields[field + 1] = y0;
        shadowFields[field + 2] = x1;
        shadowFields[field + 3] = y1;
        shadowFields[field + 4] = x2;
        shadowFields[field + 5] = y2;
        shadowFields[field + 6] = x3;
        shadowFields[field + 7] = y3;
        shadowFields[field + 8] = alpha;
        shadowFields[field + 9] = 0.0F;
        shadowFields[field + 10] = uMin;
        shadowFields[field + 11] = uMax;
        shadowArt[shadowCount] = sprite;
        shadowCount++;
    }

    /**
     * Puts one textured quad by, in the order it will be drawn.
     *
     * <p>Four corners rather than a rectangle, because a shadow lying on a
     * turned and leaning sheet is not one — and because a sprite that is one
     * costs nothing to express this way.</p>
     */
    private static void addQuad(
            LostTalesMapDecorationSprite sprite,
            float x0, float y0, float x1, float y1,
            float x2, float y2, float x3, float y3,
            float alpha, float luminance, float uMin, float uMax,
            int depth) {
        if (spriteCount >= MAX_SPRITES) {
            return;
        }
        if (spriteCount >= spriteArt.length) {
            growSpriteBuffer();
        }
        int field = spriteCount * SPRITE_STRIDE;
        spriteFields[field] = x0;
        spriteFields[field + 1] = y0;
        spriteFields[field + 2] = x1;
        spriteFields[field + 3] = y1;
        spriteFields[field + 4] = x2;
        spriteFields[field + 5] = y2;
        spriteFields[field + 6] = x3;
        spriteFields[field + 7] = y3;
        spriteFields[field + 8] = alpha;
        spriteFields[field + 9] = luminance;
        spriteFields[field + 10] = uMin;
        spriteFields[field + 11] = uMax;
        spriteArt[spriteCount] = sprite;
        spriteOrder[spriteCount] =
                ((long)depth << ORDER_INDEX_BITS) | spriteCount;
        spriteCount++;
    }

    /**
     * How far back a sprite standing at a screen position is.
     *
     * <p>Where its foot is, and nothing else. The lean is applied last and
     * acts on the screen, so however far the map is turned, further away is
     * further up the screen — which means the order things have to be painted
     * in is simply the order of their feet down it.</p>
     */
    static int depthOf(float screenY) {
        int depth = (int)((screenY + DEPTH_ORIGIN) * DEPTH_STEPS_PER_PIXEL);
        return Math.max(0, Math.min(MAX_DEPTH, depth));
    }

    private static void growSpriteBuffer() {
        int capacity = Math.min(MAX_SPRITES, spriteArt.length * 2);
        float[] fields = new float[capacity * SPRITE_STRIDE];
        System.arraycopy(spriteFields, 0, fields, 0, spriteFields.length);
        spriteFields = fields;
        LostTalesMapDecorationSprite[] art =
                new LostTalesMapDecorationSprite[capacity];
        System.arraycopy(spriteArt, 0, art, 0, spriteArt.length);
        spriteArt = art;
        long[] order = new long[capacity];
        System.arraycopy(spriteOrder, 0, order, 0, spriteOrder.length);
        spriteOrder = order;
    }

    /**
     * Draws the frame's decorations, furthest first.
     *
     * <p>Painting them in the order they were found means a tree behind a
     * mountain is drawn over it whenever the lattice happened to reach it
     * later, which on a leaning map reads as sprites cutting through one
     * another. Painting them by their feet is the whole fix: what stands in
     * front covers what stands behind, exactly as it would on the ground.</p>
     *
     * <p>The order is across every kind at once rather than within each, since
     * a mountain and the trees at its foot are the case that shows. That costs
     * a texture change wherever two kinds interleave, so the run of sprites
     * between changes is batched and the tessellator is only flushed when the
     * artwork actually differs — decorations of one kind come in long runs,
     * because the ground they stand on does.</p>
     */
    private static void flushSprites(Minecraft minecraft) {
        if (spriteCount <= 0) {
            return;
        }
        Arrays.sort(spriteOrder, 0, spriteCount);
        Tessellator tessellator = Tessellator.instance;
        LostTalesMapDecorationSprite bound = null;
        boolean drawing = false;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            for (int index = 0; index < spriteCount; index++) {
                int sprite = (int)(spriteOrder[index] & ORDER_INDEX_MASK);
                LostTalesMapDecorationSprite art = spriteArt[sprite];
                if (art != bound) {
                    if (drawing) {
                        tessellator.draw();
                    }
                    bind(minecraft, art);
                    bound = art;
                    tessellator.startDrawingQuads();
                    drawing = true;
                }
                emitQuad(tessellator, sprite * SPRITE_STRIDE);
            }
        } finally {
            if (drawing) {
                // The tessellator is shared with everything else drawn this
                // frame; left open once, nothing after it draws at all.
                tessellator.draw();
            }
        }
    }

    /** Draws every projected silhouette before upright artwork covers it. */
    private static void flushShadows(Minecraft minecraft) {
        if (shadowCount <= 0) {
            return;
        }
        Tessellator tessellator = Tessellator.instance;
        LostTalesMapDecorationSprite bound = null;
        boolean drawing = false;
        try {
            for (int index = 0; index < shadowCount; index++) {
                LostTalesMapDecorationSprite art = shadowArt[index];
                if (art != bound) {
                    if (drawing) {
                        tessellator.draw();
                    }
                    bind(minecraft, art);
                    bound = art;
                    tessellator.startDrawingQuads();
                    drawing = true;
                }
                int field = index * SHADOW_STRIDE;
                emitQuad(tessellator, shadowFields, field);
            }
        } finally {
            if (drawing) {
                tessellator.draw();
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void emitQuad(Tessellator tessellator, int field) {
        emitQuad(tessellator, spriteFields, field);
    }

    private static void emitQuad(
            Tessellator tessellator, float[] fields, int field) {
        double uMin = fields[field + 10];
        double uMax = fields[field + 11];
        float luminance = fields[field + 9];
        tessellator.setColorRGBA_F(luminance, luminance, luminance,
                fields[field + 8]);
        tessellator.addVertexWithUV(fields[field],
                fields[field + 1], 0.0D, uMin, 1.0D);
        tessellator.addVertexWithUV(fields[field + 2],
                fields[field + 3], 0.0D, uMax, 1.0D);
        tessellator.addVertexWithUV(fields[field + 4],
                fields[field + 5], 0.0D, uMax, 0.0D);
        tessellator.addVertexWithUV(fields[field + 6],
                fields[field + 7], 0.0D, uMin, 0.0D);
    }

    private static void bind(
            Minecraft minecraft, LostTalesMapDecorationSprite sprite) {
        minecraft.getTextureManager().bindTexture(sprite.getTexture());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    /**
     * Whether this map pixel is ground of the kind asked for, remembering the
     * answer.
     *
     * <p>The map image is fixed for the session, so this is asked once per
     * pixel however long the player pans around. It goes through the kind's
     * own rule: a ship wants a coast and everything else wants a broad stretch
     * of its own ground, and asking every kind the same question is how ships
     * ended up moored in the middle of Belegaer.</p>
     */
    private static boolean isSite(Scattered kind, int mapX, int mapY) {
        long key = siteKey(kind.channel, mapX, mapY);
        int slot = findSlot(key);
        if (siteKeys[slot] == key) {
            return siteAnswers[slot];
        }
        return remember(slot, key, kind.isSite(mapX, mapY));
    }

    /** Whether a hull could sit on this map pixel at all. */
    private static boolean isNavigable(float mapX, float mapY) {
        int pixelX = (int)mapX;
        int pixelY = (int)mapY;
        long key = siteKey(CHANNEL_NAVIGABLE, pixelX, pixelY);
        int slot = findSlot(key);
        if (siteKeys[slot] == key) {
            return siteAnswers[slot];
        }
        return remember(slot, key,
                LostTalesMapTerrain.NAVIGABLE_WATER.matches(pixelX, pixelY));
    }

    /**
     * What the map image says about a pixel, kept for the session.
     *
     * <p>Open addressing over two flat arrays rather than a {@code Map}: at
     * the widest zoom the whole of Middle-earth is walked every frame, and a
     * hash map keyed by a boxed {@code Long} would hand the collector tens of
     * thousands of objects a frame to answer questions it already knew the
     * answer to. Nothing here allocates once the arrays exist.</p>
     *
     * <p>A key of zero means the slot is empty, so the one map pixel whose key
     * would be zero is simply asked again each time.</p>
     */
    private static final int SITE_CACHE_SLOTS = 1 << 17;
    private static final int SITE_CACHE_MASK = SITE_CACHE_SLOTS - 1;
    /** How far a probe walks before it gives the slot up as taken. */
    private static final int MAX_PROBES = 8;
    private static long[] siteKeys = new long[SITE_CACHE_SLOTS];
    private static boolean[] siteAnswers = new boolean[SITE_CACHE_SLOTS];

    /**
     * The slot a key belongs in: its own, the first free one after it, or the
     * one it gives up and takes over.
     */
    private static int findSlot(long key) {
        int slot = (int)(key ^ (key >>> 32)) & SITE_CACHE_MASK;
        for (int probe = 0; probe < MAX_PROBES; probe++) {
            long held = siteKeys[slot];
            if (held == key || held == 0L) {
                return slot;
            }
            slot = (slot + 1) & SITE_CACHE_MASK;
        }
        // Every probe taken by something else. Evicting one answer costs the
        // fifty array reads it took to work out, which is cheaper than
        // growing the table for a map that cannot get any larger.
        return slot;
    }

    private static boolean remember(int slot, long key, boolean answer) {
        siteKeys[slot] = key;
        siteAnswers[slot] = answer;
        return answer;
    }

    private static long siteKey(int channel, int mapX, int mapY) {
        return ((long)channel << 48)
                ^ ((long)(mapX & 0xFFFFFF) << 24) ^ (mapY & 0xFFFFFF);
    }

    /** Test seam: how many scattered kinds there are. */
    static int kindCount() {
        return SCATTERED.length;
    }

    /** Test seam: how wide a kind is drawn on screen at a zoom. */
    static float drawnWidth(int kind, float zoomScale) {
        return SCATTERED[kind].sprite.getWorldWidth() * zoomScale;
    }

    /** Test seam: whether a kind is drawn at all at a given zoom. */
    static boolean isDrawn(int kind, float zoomScale) {
        return SCATTERED[kind].sprite.visibilityAlpha(maximumDrawnWidth(
                SCATTERED[kind].sprite.getWorldWidth(), zoomScale)) > 0.0F;
    }

    /** Test seam: projected-size fade for one scattered kind. */
    static float visibilityAlpha(int kind, float zoomScale) {
        return SCATTERED[kind].sprite.visibilityAlpha(
                drawnWidth(kind, zoomScale));
    }

    /** Conservative preflight width; individual sites still use exact size. */
    private static float maximumDrawnWidth(
            float worldWidth, float zoomScale) {
        return worldWidth * zoomScale
                * MAX_SITE_SCALE * MAX_PERSPECTIVE_SCALE;
    }

}
