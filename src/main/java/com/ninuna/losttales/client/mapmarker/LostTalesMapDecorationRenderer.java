package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import lotr.common.world.map.LOTRRoads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Small things standing on the map: waves, trees, mountains, ships, and
 * travellers walking the roads.
 *
 * <p>These are the opposite of the clouds, and the distinction is the point of
 * this class existing separately. A cloud lies on the map: it goes through the
 * sheet matrix and is turned, leaned and foreshortened with the paper. These
 * stand on it — only the <em>base</em> is a map position, and the sprite itself
 * is drawn upright in screen space so it always faces the reader. Mixing the
 * two is how decorations end up sheared flat by the lean or clouds end up
 * pivoting to face the camera.</p>
 *
 * <p>Nothing here is an entity or is stored between sessions. A frame walks the
 * cells in view, asks the map image what is under each one, and draws. What is
 * remembered is only the answer to that question, which cannot change while the
 * game runs.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapDecorationRenderer {
    /**
     * A kind of decoration scattered over the ground.
     *
     * <p>The cell is in map pixels, so a kind's density is a property of
     * Middle-earth rather than of the window; the sprite drawn at each site is
     * a fixed number of screen pixels, so it never grows. The two together are
     * what decides when a kind is worth drawing: once the cells are closer
     * together on screen than the sprites are wide, the ground is a solid mass
     * of them and the kind fades out.</p>
     */
    private static final class Scattered {
        private final LostTalesMapDecorationSprite sprite;
        private final LostTalesMapTerrain terrain;
        private final int probeRadius;
        /** True when this kind wants a shore rather than open ground. */
        private final boolean shore;
        private final float cell;
        private final float density;
        private final int channel;

        private Scattered(LostTalesMapDecorationSprite sprite,
                          LostTalesMapTerrain terrain, int probeRadius,
                          boolean shore, float cell, float density,
                          int channel) {
            this.sprite = sprite;
            this.terrain = terrain;
            this.probeRadius = probeRadius;
            this.shore = shore;
            this.cell = cell;
            this.density = density;
            this.channel = channel;
        }

        private boolean isSite(int mapX, int mapY) {
            return this.shore
                    ? LostTalesMapDecorationPlacement.isShoreSite(
                            this.terrain, mapX, mapY, this.probeRadius)
                    : LostTalesMapDecorationPlacement.isBroadSite(
                            this.terrain, mapX, mapY, this.probeRadius);
        }
    }

    /**
     * How far apart decorations of one kind sit on screen, in sprite widths.
     *
     * <p>The number the whole layout is built around, and it is a screen
     * measurement because crowding is a screen problem. Decorations never
     * shrink, so pulling the map out packs them tighter and tighter until the
     * ground is a carpet of repeated stamps; rather than fade the lot away at
     * that point — which is what used to happen, and what made them disappear
     * while there was still map to look at — the map keeps this spacing by
     * drawing fewer of them. What is on screen therefore looks the same at
     * every zoom, and a decoration is only ever removed, never moved or
     * resized.</p>
     */
    private static final float SITE_SPACING_RATIO = 3.0F;
    private static final float MAX_ALPHA = 0.9F;
    /**
     * How far the sprite's foot is lifted off its anchor, as a share of its
     * height, so the artwork sits on the ground rather than being bisected
     * by it.
     */
    private static final float FOOT_LIFT = 0.12F;
    /**
     * Defensive ceiling on cells visited for one kind in one frame.
     *
     * <p>Not the thing that decides when a kind stops being drawn — the
     * crowding fade does that, and it always bites first at any zoom the map
     * allows. This only catches a viewport or a zoom far outside what the map
     * screen can currently produce.</p>
     */
    private static final int MAX_VISITED_CELLS = 24000;
    /**
     * How far the lattice may be thinned. Ten halvings is a thousandfold, far
     * past the widest this map can be pulled out to.
     */
    private static final int MAX_THINNING_LEVELS = 10;
    /** Noise channels each kind takes, well clear of the sky's. */
    private static final int CHANNEL_BASE = 4096;
    private static final int CHANNELS_PER_KIND = 64;

    /**
     * The scattered kinds, in the order they are drawn.
     *
     * <p>Cells are sized to what each thing is: waves and trees are common and
     * close together, mountains and ships are landmarks and are not. Adding a
     * kind is one row here plus its artwork.</p>
     */
    private static final Scattered[] SCATTERED = {
            new Scattered(LostTalesMapDecorationSprite.WAVE,
                    LostTalesMapTerrain.OPEN_WATER, 2, false,
                    10.0F, 0.5F, CHANNEL_BASE),
            // Forests are the thing trees are for, so they are the densest
            // lattice of the lot.
            new Scattered(LostTalesMapDecorationSprite.TREE,
                    LostTalesMapTerrain.FOREST, 2, false,
                    5.0F, 0.8F, CHANNEL_BASE + CHANNELS_PER_KIND),
            new Scattered(LostTalesMapDecorationSprite.MOUNTAIN,
                    LostTalesMapTerrain.MOUNTAIN, 2, false,
                    8.0F, 0.6F, CHANNEL_BASE + CHANNELS_PER_KIND * 2),
            // A ship belongs to a coast or a river, never to the middle of
            // Belegaer, and even there it is a rare sight rather than a fleet.
            new Scattered(LostTalesMapDecorationSprite.SHIP,
                    LostTalesMapTerrain.NAVIGABLE_WATER, 3, true,
                    30.0F, 0.16F, CHANNEL_BASE + CHANNELS_PER_KIND * 3)
    };
    private static final float SITE_JITTER = 0.42F;

    /**
     * Answers to "is this ground broad enough", keyed by kind and map pixel.
     *
     * <p>The map image does not change while the game runs, so an answer is
     * good for the session. Bounded and dropped wholesale rather than aged,
     * because the cost of a miss is fifty array reads.</p>
     */
    private static final Map<Long, Boolean> SITES =
            new HashMap<Long, Boolean>();
    private static final int MAX_CACHED_SITES = 32768;

    /** How far along a road a traveller walks per tick, in road points. */
    private static final float TRAVELLER_SPEED = 0.5F;
    /** Road points one traveller is given, so long roads carry more. */
    private static final int ROAD_POINTS_PER_TRAVELLER = 700;
    private static final int MAX_TRAVELLERS_PER_ROAD = 26;
    /**
     * Screen pixels per map pixel below which travellers are not drawn.
     *
     * <p>Roads are a fixed set rather than a lattice, so the number of
     * travellers is already bounded and none of the thinning above applies to
     * them. They only need to stop once the roads themselves are too small to
     * walk along.</p>
     */
    private static final float TRAVELLER_MIN_ZOOM_SCALE = 0.35F;
    private static final float TRAVELLER_FULL_ZOOM_SCALE = 0.8F;

    private LostTalesMapDecorationRenderer() {}

    public static void clearCache() {
        SITES.clear();
        LostTalesMapTerrain.clear();
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
        // the wide box anyway meant visiting four times the cells for nothing
        // — enough to trip the budget below at the very zoom the decorations
        // are meant to be at their fullest.
        float[] coverage = new float[2];
        LostTalesLotrMapRotation.rotatedCoverage(
                viewportXMax - viewportXMin, viewportYMax - viewportYMin,
                LostTalesLotrMapRotation.degreesOf(gui),
                LostTalesLotrMapRotation.leanOf(gui), coverage);
        float reachX = coverage[0] * 0.5F / zoomScale;
        float reachY = coverage[1] * 0.5F / zoomScale;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            for (int index = 0; index < SCATTERED.length; index++) {
                renderScattered(minecraft, gui, SCATTERED[index], worldTime,
                        posX, posY, zoomScale, centerX, centerY,
                        reachX, reachY,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            }
            renderTravellers(minecraft, gui, worldTime, posX, posY,
                    zoomScale, centerX, centerY,
                    viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
        } catch (Throwable ignored) {
            // Decoration is the least important thing on the map; the map
            // itself and everything a player navigates by are already drawn.
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private static void renderScattered(
            Minecraft minecraft, LostTalesLotrMapGui gui, Scattered kind,
            long worldTime, float posX, float posY, float zoomScale,
            float centerX, float centerY, float reachX, float reachY,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float width = kind.sprite.getScreenWidth();
        // How many times the lattice has to be halved for its sites to sit
        // the wanted distance apart on screen. Zoomed right in this is zero
        // and every site is drawn; pulled out it climbs, and each step keeps
        // one site in four — always the same ones, because the choice is
        // nested.
        int levels = thinningLevels(kind.cell * zoomScale, width);
        float coarseCell = kind.cell * (1 << levels);
        float blend = thinningBlend(kind.cell * zoomScale, width, levels);
        int cellXMin = (int)Math.floor((posX - reachX - coarseCell)
                / coarseCell);
        int cellXMax = (int)Math.ceil((posX + reachX + coarseCell)
                / coarseCell);
        int cellYMin = (int)Math.floor((posY - reachY - coarseCell)
                / coarseCell);
        int cellYMax = (int)Math.ceil((posY + reachY + coarseCell)
                / coarseCell);
        if ((long)(cellXMax - cellXMin + 1) * (cellYMax - cellYMin + 1)
                > MAX_VISITED_CELLS) {
            return;
        }
        float height = kind.sprite.getScreenHeight();
        float[] anchor = new float[2];
        int[] site = new int[2];
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        bind(minecraft, kind.sprite);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        try {
            for (int cellX = cellXMin; cellX <= cellXMax; cellX++) {
                for (int cellY = cellYMin; cellY <= cellYMax; cellY++) {
                    drawScatteredSite(tessellator, gui, kind, cellX, cellY,
                            levels, blend, worldTime, posX, posY, zoomScale,
                            centerX, centerY, width, height, anchor, site,
                            viewportXMin, viewportXMax,
                            viewportYMin, viewportYMax);
                }
            }
        } finally {
            // The tessellator is shared with everything else drawn this
            // frame; left open once, nothing after it draws at all.
            tessellator.draw();
        }
    }

    private static void drawScatteredSite(
            Tessellator tessellator, LostTalesLotrMapGui gui, Scattered kind,
            int cellX, int cellY, int levels, float blend, long worldTime,
            float posX, float posY, float zoomScale,
            float centerX, float centerY, float width, float height,
            float[] anchor, int[] site, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        LostTalesMapDecorationPlacement.representative(
                cellX, cellY, levels, kind.channel, site);
        if (!LostTalesMapDecorationPlacement.hasSite(
                site[0], site[1], kind.channel, kind.density)) {
            return;
        }
        float mapX = LostTalesMapDecorationPlacement.siteX(
                site[0], site[1], kind.channel, kind.cell, SITE_JITTER);
        float mapY = LostTalesMapDecorationPlacement.siteY(
                site[0], site[1], kind.channel, kind.cell, SITE_JITTER);
        if (!isSite(kind, (int)mapX, (int)mapY)) {
            return;
        }
        // A site that would survive one level coarser is already settled; one
        // that would not is the detail this level is adding, so it fades in
        // over the last of the zoom rather than appearing whole.
        float alpha = MAX_ALPHA;
        if (blend > 0.0F
                && !LostTalesMapDecorationPlacement.survivesCoarser(
                        cellX, cellY, levels, kind.channel)) {
            alpha *= 1.0F - blend;
        }
        if (alpha <= 0.004F) {
            return;
        }
        drawStanding(tessellator, gui, kind.sprite, mapX, mapY,
                kind.sprite.frameAt(worldTime,
                        LostTalesMapDecorationPlacement.sitePhase(
                                site[0], site[1], kind.channel)),
                alpha, posX, posY, zoomScale, centerX, centerY,
                width, height, anchor, viewportXMin, viewportXMax,
                viewportYMin, viewportYMax);
    }

    /**
     * How many times a lattice must be halved to keep its wanted spacing.
     *
     * <p>Zero while the map is close enough that every site has room; one more
     * for each doubling of the map's remaining reach. This is what replaces
     * the old "give up entirely" fade: there is no zoom at which it returns
     * something meaning "draw nothing".</p>
     */
    static int thinningLevels(float baseSpacing, float spriteWidth) {
        float wanted = SITE_SPACING_RATIO * spriteWidth;
        if (!(baseSpacing > 0.0F) || baseSpacing >= wanted) {
            return 0;
        }
        int levels = 0;
        float spacing = baseSpacing;
        while (spacing < wanted && levels < MAX_THINNING_LEVELS) {
            spacing *= 2.0F;
            levels++;
        }
        return levels;
    }

    /** How far through the step to the next level the zoom currently is. */
    static float thinningBlend(
            float baseSpacing, float spriteWidth, int levels) {
        float wanted = SITE_SPACING_RATIO * spriteWidth;
        if (!(baseSpacing > 0.0F) || levels <= 0) {
            return 0.0F;
        }
        float spacing = baseSpacing * (1 << levels);
        if (spacing <= wanted) {
            return 0.0F;
        }
        // Between this level's spacing and half of it, which is where the
        // level below handed over.
        float advanced = (spacing - wanted) / wanted;
        return Math.max(0.0F, Math.min(1.0F, advanced));
    }

    /**
     * People and carts moving along LOTR's own roads.
     *
     * <p>A traveller is a position along a road worked out from the world
     * clock, so it walks without anything being stored, moves at the same
     * moment on every client, and cannot drift out of step. Roads are long, so
     * how many a road carries follows its length rather than being the same
     * everywhere.</p>
     */
    private static void renderTravellers(
            Minecraft minecraft, LostTalesLotrMapGui gui, long worldTime,
            float posX, float posY, float zoomScale,
            float centerX, float centerY,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float alpha = MAX_ALPHA * fadeBetween(zoomScale,
                TRAVELLER_MIN_ZOOM_SCALE, TRAVELLER_FULL_ZOOM_SCALE);
        if (alpha <= 0.004F) {
            return;
        }
        LostTalesMapDecorationSprite sprite =
                LostTalesMapDecorationSprite.TRAVELLER;
        float width = sprite.getScreenWidth();
        float height = sprite.getScreenHeight();
        float[] anchor = new float[2];
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        bind(minecraft, sprite);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        try {
            Iterator roads = LOTRRoads.getAllRoadsForDisplay();
            int roadIndex = 0;
            while (roads.hasNext()) {
                Object next = roads.next();
                roadIndex++;
                if (!(next instanceof LOTRRoads)) {
                    continue;
                }
                LOTRRoads.RoadPoint[] points = ((LOTRRoads)next).roadPoints;
                if (points == null || points.length < 2) {
                    continue;
                }
                int travellers = Math.max(1, Math.min(
                        MAX_TRAVELLERS_PER_ROAD,
                        points.length / ROAD_POINTS_PER_TRAVELLER));
                for (int walker = 0; walker < travellers; walker++) {
                    drawTraveller(tessellator, gui, sprite, points,
                            roadIndex, walker, travellers, worldTime, alpha,
                            posX, posY, zoomScale, centerX, centerY,
                            width, height, anchor,
                            viewportXMin, viewportXMax,
                            viewportYMin, viewportYMax);
                }
            }
        } finally {
            tessellator.draw();
        }
    }

    private static void drawTraveller(
            Tessellator tessellator, LostTalesLotrMapGui gui,
            LostTalesMapDecorationSprite sprite,
            LOTRRoads.RoadPoint[] points, int roadIndex, int walker,
            int travellers, long worldTime, float alpha,
            float posX, float posY, float zoomScale,
            float centerX, float centerY, float width, float height,
            float[] anchor, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        // Spread along the road, and started somewhere of their own, so a
        // road is not a column of walkers in step.
        float start = LostTalesLotrMapAtmosphere.cellNoise(
                roadIndex, walker, CHANNEL_BASE - 1) * points.length;
        float travelled = start + walker * points.length / (float)travellers
                + worldTime * TRAVELLER_SPEED;
        int index = (int)Math.floorMod((long)travelled, points.length);
        LOTRRoads.RoadPoint point = points[index];
        float mapX = (float)(point.x / (double)LOTRGenLayerWorld.scale)
                + 810.0F;
        float mapY = (float)(point.z / (double)LOTRGenLayerWorld.scale)
                + 730.0F;
        drawStanding(tessellator, gui, sprite, mapX, mapY,
                sprite.frameAt(worldTime, roadIndex * 7 + walker), alpha,
                posX, posY, zoomScale, centerX, centerY, width, height,
                anchor, viewportXMin, viewportXMax,
                viewportYMin, viewportYMax);
    }

    /**
     * Draws one sprite standing on a map position.
     *
     * <p>The base goes through the same one place every other position on the
     * map goes through, so it pans, zooms, turns and leans with the ground it
     * is standing on. The sprite itself is then laid out square to the screen,
     * which is what makes it stand on the map rather than lie on it.</p>
     */
    private static void drawStanding(
            Tessellator tessellator, LostTalesLotrMapGui gui,
            LostTalesMapDecorationSprite sprite,
            float mapX, float mapY, int frame, float alpha,
            float posX, float posY, float zoomScale,
            float centerX, float centerY, float width, float height,
            float[] anchor, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        anchor[0] = (mapX - posX) * zoomScale + centerX;
        anchor[1] = (mapY - posY) * zoomScale + centerY;
        LostTalesLotrMapRotation.rotate(anchor, gui);
        if (anchor[0] + width < viewportXMin
                || anchor[0] - width > viewportXMax
                || anchor[1] + height < viewportYMin
                || anchor[1] - height > viewportYMax) {
            return;
        }
        float left = anchor[0] - width * 0.5F;
        float bottom = anchor[1] + height * FOOT_LIFT;
        float top = bottom - height;
        double uMin = sprite.frameUMin(frame);
        double uMax = sprite.frameUMax(frame);
        // Per site rather than per kind, because a site fading in as the map
        // is pushed closer is the only thing that stops the extra detail
        // arriving all at once.
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, alpha);
        tessellator.addVertexWithUV(left, bottom, 0.0D, uMin, 1.0D);
        tessellator.addVertexWithUV(
                left + width, bottom, 0.0D, uMax, 1.0D);
        tessellator.addVertexWithUV(left + width, top, 0.0D, uMax, 0.0D);
        tessellator.addVertexWithUV(left, top, 0.0D, uMin, 0.0D);
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
     * Whether this map pixel is broad enough ground, remembering the answer.
     *
     * <p>The map image is fixed for the session, so this is asked once per
     * pixel however long the player pans around.</p>
     */
    private static boolean isSite(Scattered kind, int mapX, int mapY) {
        Long key = Long.valueOf(((long)kind.channel << 48)
                ^ ((long)(mapX & 0xFFFFFF) << 24) ^ (mapY & 0xFFFFFF));
        Boolean cached = SITES.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean site = LostTalesMapDecorationPlacement.isBroadSite(
                kind.terrain, mapX, mapY, kind.probeRadius);
        if (SITES.size() >= MAX_CACHED_SITES) {
            SITES.clear();
        }
        SITES.put(key, Boolean.valueOf(site));
        return site;
    }

    /** Test seam: how many scattered kinds there are. */
    static int kindCount() {
        return SCATTERED.length;
    }

    /** Test seam: the spacing a kind's lattice has on screen before thinning. */
    static float baseSpacing(int kind, float zoomScale) {
        return SCATTERED[kind].cell * zoomScale;
    }

    static float spriteWidth(int kind) {
        return SCATTERED[kind].sprite.getScreenWidth();
    }

    /**
     * Test seam: cells a kind would walk at a zoom, on a flat map.
     *
     * <p>The number the budget is measured against. Because the lattice is
     * thinned rather than faded, this no longer grows without bound as the map
     * is pulled out — which is what lets the decorations survive to every zoom
     * instead of being switched off by their own safety ceiling.</p>
     */
    static long visitedCells(int kind, float viewportWidth,
                             float viewportHeight, float zoomScale) {
        Scattered scattered = SCATTERED[kind];
        float width = scattered.sprite.getScreenWidth();
        int levels = thinningLevels(scattered.cell * zoomScale, width);
        float cell = scattered.cell * (1 << levels);
        float reachX = viewportWidth * 0.5F / zoomScale;
        float reachY = viewportHeight * 0.5F / zoomScale;
        long across = (long)Math.ceil((reachX + cell) * 2.0F / cell) + 1L;
        long down = (long)Math.ceil((reachY + cell) * 2.0F / cell) + 1L;
        return across * down;
    }

    static int cellBudget() {
        return MAX_VISITED_CELLS;
    }

    /** Nothing at or below {@code from}, everything at or above {@code to}. */
    static float fadeBetween(float value, float from, float to) {
        if (!(to > from)) {
            return value >= to ? 1.0F : 0.0F;
        }
        float advanced = (value - from) / (to - from);
        if (advanced <= 0.0F) {
            return 0.0F;
        }
        if (advanced >= 1.0F) {
            return 1.0F;
        }
        return advanced * advanced * (3.0F - 2.0F * advanced);
    }
}
