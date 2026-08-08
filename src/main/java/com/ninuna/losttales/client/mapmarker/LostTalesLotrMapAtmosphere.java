package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * The weather and the hour, over the map.
 *
 * <p>Two effects, both drawn straight onto the ground and under everything a
 * player reads — roads, region names, markers, the rose, the strip. Nothing
 * here may cost readability: the map is a navigation instrument first.</p>
 *
 * <p>All of it is client-side and derived from state the client already has.
 * Nothing is asked of the server and nothing is remembered between frames
 * except the constants below.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesLotrMapAtmosphere {
    private static final int TICKS_PER_DAY = 24000;

    /**
     * How the map is shaded through the day, as {@code {tick, r, g, b, a}}.
     *
     * <p>Tick zero is dawn. Daylight is left alone entirely — a map that is
     * tinted at noon just looks wrong — and the deepest the night gets is a
     * blue the markers and labels still read clearly through. Evening and
     * early morning pass through a warm amber on the way, which is the whole
     * of the effect: the map looks like it is lit by whatever is in the sky.
     * </p>
     */
    private static final float[][] DAY_SHADE = {
            {     0.0F, 0.55F, 0.35F, 0.16F, 0.10F },
            {  1200.0F, 0.55F, 0.35F, 0.16F, 0.00F },
            { 11000.0F, 0.55F, 0.35F, 0.16F, 0.00F },
            { 12300.0F, 0.52F, 0.29F, 0.13F, 0.17F },
            { 13800.0F, 0.20F, 0.16F, 0.26F, 0.28F },
            { 15200.0F, 0.07F, 0.10F, 0.24F, 0.32F },
            { 21800.0F, 0.07F, 0.10F, 0.24F, 0.32F },
            { 23000.0F, 0.45F, 0.28F, 0.18F, 0.20F },
            { 24000.0F, 0.55F, 0.35F, 0.16F, 0.10F }
    };
    /** Hard ceiling on the shade, whatever the table says. Readability wins. */
    private static final float MAX_SHADE_ALPHA = 0.34F;
    /**
     * The weather, and the whole of what can honestly be said about it.
     *
     * <p>Minecraft keeps one weather state for a dimension — it is raining,
     * or it is not — and LOTR adds none of its own. There is no front, no
     * region, nothing that could put a storm over Rohan and leave the Shire
     * dry, so nothing here pretends there is. What the map shows is the
     * world's own sky over the whole of it: overcast while it rains, darker
     * again while it thunders.</p>
     *
     * <p>Which biomes would actually see that rain, and see it as snow, is
     * geographic and is derivable — but from the biome, not from the weather,
     * and it needs the map's ground sampled and cached rather than read every
     * frame. That is the shape a real regional layer would take.</p>
     */
    private static final float[] STORM_COLOUR = { 0.30F, 0.33F, 0.40F };
    private static final float RAIN_SHADE = 0.10F;
    private static final float THUNDER_SHADE = 0.13F;
    /** Ceiling once the weather is counted in too. */
    private static final float MAX_TOTAL_ALPHA = 0.42F;

    /**
     * How far apart the clouds are seeded, in map-image pixels.
     *
     * <p>Clouds are placed one to a cell and jittered well past its edges, so
     * the grid never reads as a grid; what it buys is that only the cells on
     * screen have to be visited, whatever the map is looking at.</p>
     */
    private static final float CLOUD_CELL = 256.0F;
    /**
     * How far apart the clouds should sit on screen, whatever the zoom.
     *
     * <p>Cells fixed in map-image pixels put a sensible number of clouds on
     * screen at one zoom and none at all at another: pulled right out to the
     * whole of Middle-earth there were thousands of cells in view and the
     * clouds gave up rather than draw them, which is why there were none.
     * So the cell doubles as the map pulls out, in steps, and the sky keeps
     * roughly this spacing at every zoom.</p>
     */
    private static final float CLOUD_SCREEN_SPACING = 230.0F;
    /** Beyond this many cells on screen the map is too far out for clouds. */
    private static final int MAX_CLOUD_CELLS = 700;
    /** Cells at which the clouds have finished fading back in. */
    private static final int FADE_CLOUD_CELLS = 520;
    private static final float CLOUD_JITTER = 0.42F;
    private static final float CLOUD_MIN_RADIUS = 0.18F;
    private static final float CLOUD_MAX_RADIUS = 0.46F;
    private static final float CLOUD_ALPHA = 0.30F;
    /**
     * Share of a puff that is solid before it starts fading.
     *
     * <p>A puff that fades the whole way from its middle averages a third of
     * its own weight and reads as nothing at all. A core that holds its
     * colour and a rim that softens is what makes it a cloud.</p>
     */
    private static final float PUFF_CORE = 0.55F;
    /** Cells the drift wraps after, so long-running worlds keep their sky. */
    private static final float DRIFT_WRAP_CELLS = 1024.0F;
    /**
     * Map-image pixels a cloud drifts per tick. Slow enough not to pull the
     * eye, quick enough that a cloud visibly moves while you look at it.
     */
    private static final float CLOUD_DRIFT = 0.06F;
    /** Puffs per formation, so a cloud has a shape rather than being a disc. */
    private static final int CLOUD_PUFFS = 3;
    /** Rim points on a puff. Enough that the edge reads as round. */
    private static final int PUFF_SEGMENTS = 12;

    private LostTalesLotrMapAtmosphere() {
    }

    /**
     * The shade for a moment in the day, as {@code {r, g, b, a}}.
     *
     * <p>Eased between the table's entries rather than run straight between
     * them, so nothing steps as the sun goes down.</p>
     */
    static float[] timeOfDayShade(long worldTime) {
        long ticks = worldTime % TICKS_PER_DAY;
        if (ticks < 0L) {
            ticks += TICKS_PER_DAY;
        }
        float time = ticks;
        for (int index = 1; index < DAY_SHADE.length; index++) {
            float[] to = DAY_SHADE[index];
            if (time > to[0]) {
                continue;
            }
            float[] from = DAY_SHADE[index - 1];
            float span = to[0] - from[0];
            float eased = span <= 0.0F
                    ? 0.0F : smoothstep((time - from[0]) / span);
            return new float[] {
                    mix(from[1], to[1], eased),
                    mix(from[2], to[2], eased),
                    mix(from[3], to[3], eased),
                    Math.min(MAX_SHADE_ALPHA,
                            mix(from[4], to[4], eased))
            };
        }
        float[] last = DAY_SHADE[DAY_SHADE.length - 1];
        return new float[] {
                last[1], last[2], last[3],
                Math.min(MAX_SHADE_ALPHA, last[4])
        };
    }

    /**
     * The hour and the weather as one shade.
     *
     * <p>Laid over one another the way two panes of glass would be, rather
     * than added, so a storm at midnight cannot drive the map past the point
     * it can be read at.</p>
     */
    static float[] shadeFor(long worldTime, float rain, float thunder) {
        float[] shade = timeOfDayShade(worldTime);
        float storm = Math.max(0.0F, Math.min(1.0F, rain)) * RAIN_SHADE
                + Math.max(0.0F, Math.min(1.0F, thunder)) * THUNDER_SHADE;
        if (storm <= 0.0F) {
            return shade;
        }
        float combined = shade[3] + storm * (1.0F - shade[3]);
        if (combined <= 0.0F) {
            return shade;
        }
        // Weighted by how much of the result each pane is responsible for.
        float stormShare = storm * (1.0F - shade[3]) / combined;
        return new float[] {
                mix(shade[0], STORM_COLOUR[0], stormShare),
                mix(shade[1], STORM_COLOUR[1], stormShare),
                mix(shade[2], STORM_COLOUR[2], stormShare),
                Math.min(MAX_TOTAL_ALPHA, combined)
        };
    }

    /**
     * A number in {@code [0, 1)} fixed for a cell and a channel.
     *
     * <p>Clouds have to be in the same place every time the map is opened and
     * in different places from each other, which is a hash rather than a
     * random: no state, no order dependence, and the same answer on every
     * client without anything being sent.</p>
     */
    static float cellNoise(int cellX, int cellY, int channel) {
        int hash = cellX * 0x27D4EB2D;
        hash ^= (cellY + 0x165667B1) * 0x9E3779B1;
        hash ^= (channel + 0x6C078965) * 0x85EBCA6B;
        hash ^= hash >>> 15;
        hash *= 0x2545F491;
        hash ^= hash >>> 13;
        return (hash >>> 8) / (float)(1 << 24);
    }

    /** How strongly clouds show at a given number of visible cells. */
    /**
     * The cell the clouds are laid out on at a given zoom, in map pixels.
     *
     * <p>Doubled or halved from the base rather than scaled freely, so the
     * layout only changes when the map has moved a long way in zoom and the
     * clouds stay put over the ground everywhere in between.</p>
     */
    static float cloudCell(float zoomScale) {
        if (!(zoomScale > 0.0F)) {
            return CLOUD_CELL;
        }
        float wanted = CLOUD_SCREEN_SPACING / zoomScale;
        float cell = CLOUD_CELL;
        while (cell < wanted && cell < CLOUD_CELL * 4096.0F) {
            cell *= 2.0F;
        }
        while (cell > wanted * 2.0F && cell > CLOUD_CELL / 16.0F) {
            cell *= 0.5F;
        }
        return cell;
    }

    /**
     * How far the sky has drifted, in map pixels.
     *
     * <p>Wrapped to a whole number of cells, and a large one, so a world that
     * has been running for years keeps the same arithmetic precision as one
     * started this morning and the lattice still lines up either side of the
     * wrap.</p>
     */
    static float cloudDrift(long worldTime, float cell) {
        float period = cell * DRIFT_WRAP_CELLS;
        if (!(period > 0.0F)) {
            return 0.0F;
        }
        double drifted = worldTime * (double)CLOUD_DRIFT;
        double wrapped = drifted % period;
        if (wrapped < 0.0D) {
            wrapped += period;
        }
        return (float)wrapped;
    }

    static float cloudCoverage(int visibleCells) {
        if (visibleCells <= FADE_CLOUD_CELLS) {
            return 1.0F;
        }
        if (visibleCells >= MAX_CLOUD_CELLS) {
            return 0.0F;
        }
        return smoothstep(1.0F - (float)(visibleCells - FADE_CLOUD_CELLS)
                / (MAX_CLOUD_CELLS - FADE_CLOUD_CELLS));
    }

    private static float smoothstep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    /**
     * Draws the shade and then the clouds over the map's own viewport.
     *
     * @param worldTime the world's time of day
     * @param posX      map-image position the camera is centred on
     * @param zoomScale map-image pixels per screen pixel
     */
    static void render(LostTalesLotrMapGui gui, long worldTime,
                       float rain, float thunder,
                       float posX, float posY, float zoomScale,
                       int viewportXMin, int viewportXMax,
                       int viewportYMin, int viewportYMax) {
        int width = viewportXMax - viewportXMin;
        int height = viewportYMax - viewportYMin;
        if (width <= 0 || height <= 0 || !(zoomScale > 0.0F)) {
            return;
        }
        float[] shade = shadeFor(worldTime, rain, thunder);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glShadeModel(GL11.GL_SMOOTH);

            if (shade[3] > 0.002F) {
                drawShade(shade, viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            }
            drawClouds(gui, worldTime, posX, posY, zoomScale,
                    viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private static void drawShade(float[] shade, int xMin, int xMax,
                                  int yMin, int yMax) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(shade[0], shade[1], shade[2], shade[3]);
        tessellator.addVertex(xMin, yMax, 0.0D);
        tessellator.addVertex(xMax, yMax, 0.0D);
        tessellator.addVertex(xMax, yMin, 0.0D);
        tessellator.addVertex(xMin, yMin, 0.0D);
        tessellator.draw();
    }

    /**
     * Clouds, as formations placed on the ground rather than a sheet of noise
     * pulled across it.
     *
     * <p>Each is anchored where the map says it is, so it pans and zooms with
     * the ground and turns and leans with it — the centres go through the
     * same one place every other position on the map goes through, and the
     * shapes themselves stay flat, which is what keeps them from being
     * sheared into streaks by the lean.</p>
     */
    private static void drawClouds(
            LostTalesLotrMapGui gui, long worldTime,
            float posX, float posY, float zoomScale,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        // The map-image box the screen is looking at, widened so a cloud
        // whose centre is off screen still draws the part that is on it.
        float cell = cloudCell(zoomScale);
        float drift = cloudDrift(worldTime, cell);
        float halfWidth = (viewportXMax - viewportXMin) / zoomScale / 2.0F;
        float halfHeight = (viewportYMax - viewportYMin) / zoomScale / 2.0F;
        float margin = CLOUD_MAX_RADIUS * cell * 2.0F;
        // A turned map looks past its own corners, so the box is grown to the
        // diagonal rather than the sides.
        float reach = (float)Math.sqrt(
                halfWidth * halfWidth + halfHeight * halfHeight);
        // The cells are visited where the clouds started, not where they have
        // drifted to, so the window follows them. Looking in the undrifted
        // place is what emptied the sky: a world a few days old has carried
        // every cloud thousands of pixels past the cell it came from, and all
        // of them were being drawn far outside the map.
        float originX = posX - drift;
        int cellXMin = (int)Math.floor((originX - reach - margin) / cell);
        int cellXMax = (int)Math.ceil((originX + reach + margin) / cell);
        int cellYMin = (int)Math.floor((posY - reach - margin) / cell);
        int cellYMax = (int)Math.ceil((posY + reach + margin) / cell);
        long cells = (long)(cellXMax - cellXMin + 1)
                * (cellYMax - cellYMin + 1);
        float coverage = cloudCoverage(
                (int)Math.min(Integer.MAX_VALUE, cells));
        if (coverage <= 0.002F) {
            return;
        }
        float[] point = new float[2];

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int cellX = cellXMin; cellX <= cellXMax; cellX++) {
            for (int cellY = cellYMin; cellY <= cellYMax; cellY++) {
                drawFormation(gui, tessellator, cellX, cellY, cell, drift,
                        posX, posY, zoomScale, coverage, point,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            }
        }
        tessellator.draw();
    }

    private static void drawFormation(
            LostTalesLotrMapGui gui, Tessellator tessellator,
            int cellX, int cellY, float cell, float drift,
            float posX, float posY, float zoomScale, float coverage,
            float[] point, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        // A cell without a cloud in it is what stops the sky being an even
        // lattice of them.
        if (cellNoise(cellX, cellY, 0) > 0.62F) {
            return;
        }
        float baseX = (cellX + 0.5F + (cellNoise(cellX, cellY, 1) - 0.5F)
                * 2.0F * CLOUD_JITTER) * cell + drift;
        float baseY = (cellY + 0.5F + (cellNoise(cellX, cellY, 2) - 0.5F)
                * 2.0F * CLOUD_JITTER) * cell;
        // The size is a share of the cell, so a sky laid out for a zoomed
        // out map has clouds to match rather than specks.
        float radius = cell * (CLOUD_MIN_RADIUS + cellNoise(cellX, cellY, 3)
                * (CLOUD_MAX_RADIUS - CLOUD_MIN_RADIUS));
        float alpha = CLOUD_ALPHA * coverage
                * (0.55F + cellNoise(cellX, cellY, 4) * 0.45F);
        if (alpha <= 0.002F) {
            return;
        }
        for (int puff = 0; puff < CLOUD_PUFFS; puff++) {
            float offsetX = (cellNoise(cellX, cellY, 5 + puff * 2) - 0.5F)
                    * radius * 1.5F;
            float offsetY = (cellNoise(cellX, cellY, 6 + puff * 2) - 0.5F)
                    * radius * 0.7F;
            float puffRadius = radius
                    * (0.55F + cellNoise(cellX, cellY, 11 + puff) * 0.45F);
            point[0] = (baseX + offsetX - posX) * zoomScale
                    + (viewportXMin + viewportXMax) * 0.5F;
            point[1] = (baseY + offsetY - posY) * zoomScale
                    + (viewportYMin + viewportYMax) * 0.5F;
            LostTalesLotrMapRotation.rotate(point, gui);
            float screenRadius = puffRadius * zoomScale;
            if (point[0] + screenRadius < viewportXMin
                    || point[0] - screenRadius > viewportXMax
                    || point[1] + screenRadius < viewportYMin
                    || point[1] - screenRadius > viewportYMax) {
                continue;
            }
            drawPuff(tessellator, point[0], point[1], screenRadius, alpha);
        }
    }

    /**
     * One soft puff, as a ring of quads that fade to nothing at the rim.
     *
     * <p>No texture: a cloud is a shape that has to hold up at any zoom, and
     * a gradient the hardware interpolates is sharper at every one of them
     * than a sprite would be.</p>
     */
    private static void drawPuff(Tessellator tessellator,
                                 float centerX, float centerY,
                                 float radius, float alpha) {
        float core = radius * PUFF_CORE;
        float lastCoreX = centerX + core;
        float lastCoreY = centerY;
        float lastRimX = centerX + radius;
        float lastRimY = centerY;
        for (int segment = 1; segment <= PUFF_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0D * segment / PUFF_SEGMENTS;
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);
            float coreX = centerX + cos * core;
            float coreY = centerY + sin * core;
            float rimX = centerX + cos * radius;
            float rimY = centerY + sin * radius;
            // Quads throughout rather than a fan, because the pass is already
            // drawing quads and changing primitive would cost a flush.
            tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, alpha);
            tessellator.addVertex(centerX, centerY, 0.0D);
            tessellator.addVertex(lastCoreX, lastCoreY, 0.0D);
            tessellator.addVertex(coreX, coreY, 0.0D);
            tessellator.addVertex(centerX, centerY, 0.0D);

            tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, alpha);
            tessellator.addVertex(lastCoreX, lastCoreY, 0.0D);
            tessellator.addVertex(coreX, coreY, 0.0D);
            tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, 0.0F);
            tessellator.addVertex(rimX, rimY, 0.0D);
            tessellator.addVertex(lastRimX, lastRimY, 0.0D);

            lastCoreX = coreX;
            lastCoreY = coreY;
            lastRimX = rimX;
            lastRimY = rimY;
        }
    }
}
