package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
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

    /** Placeholder cloud artwork: four soft shapes in a row. */
    private static final ResourceLocation CLOUD_TEXTURE =
            new ResourceLocation(LostTalesMetaData.MOD_ID,
                    "textures/gui/map/cloud.png");
    private static final int CLOUD_VARIANTS = 4;
    /**
     * How far apart the clouds sit, in screen pixels, and how wide one is
     * drawn.
     *
     * <p>Both are screen measurements, and that is the whole design. Clouds
     * are one size, always: the zoom does not resize them, does not thin them
     * out and does not swap one set for another. They used to be sized in map
     * pixels on lattices an octave apart, which meant that pulling the map out
     * shrank every cloud until its lattice gave up and a coarser one faded in
     * behind it — a sky that visibly rebuilt itself on the way past.</p>
     *
     * <p>What it costs is that a cloud is no longer a fixed patch of ground.
     * It is a fixed patch of <em>sky</em>, which pans over the map and is
     * always the same size to the eye, and which is what looking up actually
     * gives you.</p>
     */
    private static final float CLOUD_SCREEN_CELL = 96.0F;
    /**
     * How wide one cloud is drawn.
     *
     * <p>Small enough to be weather over Middle-earth rather than weather the
     * size of it: at three hundred pixels a single cloud was about as wide as
     * the whole continent is at full zoom-out, which read as fog on the lens.
     * Sized against what else stands on the map — a tree is fourteen pixels,
     * a mountain sixteen — so the sky belongs to the same picture.</p>
     */
    private static final float CLOUD_SCREEN_WIDTH = 72.0F;
    private static final float CLOUD_ASPECT = 0.5F;
    private static final float CLOUD_JITTER = 0.45F;
    /**
     * Share of cells that have a cloud in them at all. Smaller clouds need a
     * thinner lattice, or the sky closes into an even overcast.
     */
    private static final float CLOUD_DENSITY = 0.45F;
    /**
     * Screen pixels the sky moves per map pixel the camera pans.
     *
     * <p>Constant, and it has to be: the moment it followed the zoom, the
     * lattice would be a different set of clouds at every zoom, which is the
     * thing this layout exists to stop. So the sky keeps its own rate, and
     * whether it runs ahead of the ground or behind it depends on how far the
     * map is zoomed — a parallax, which is what a layer above the ground
     * should do.</p>
     */
    private static final float CLOUD_PARALLAX = 2.0F;
    /** Cells the drift wraps after, so long-running worlds keep their sky. */
    private static final float DRIFT_WRAP_CELLS = 1024.0F;
    /**
     * Screen pixels a cloud drifts per tick. Slow enough not to pull the eye,
     * quick enough that a cloud visibly moves while you look at it.
     */
    private static final float CLOUD_DRIFT = 0.12F;
    /** Noise channels the sky takes for itself. */
    private static final int CLOUD_CHANNEL = 64;
    /**
     * The weather a cloud can be carrying, as {@code {r, g, b, alpha}}.
     *
     * <p>Fair weather is white and thin enough to read the map through. Rain
     * and thunder are greyer and heavier, which is the whole difference: the
     * map is a navigation instrument and a storm may not cost it.</p>
     */
    private static final float[] FAIR_CLOUD =
            { 1.0F, 1.0F, 1.0F, 0.26F };
    private static final float[] RAIN_CLOUD =
            { 0.63F, 0.66F, 0.72F, 0.36F };
    private static final float[] THUNDER_CLOUD =
            { 0.42F, 0.44F, 0.51F, 0.42F };

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

    /**
     * Where the sky sits over a map position, in its own screen-sized space.
     *
     * <p>The one place the sky is tied to the map. It is a straight multiple
     * of the map coordinate, so a cloud's cell is a fixed region of
     * Middle-earth however the map is being looked at, and the zoom does not
     * enter into it at all — which is exactly why zooming can neither move a
     * cloud, resize it, nor put a different one in its place.</p>
     */
    static float skyOffset(float mapPosition) {
        return mapPosition * CLOUD_PARALLAX;
    }

    /**
     * How far the sky has drifted, in screen pixels.
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
            // The shade is a flat fill and the clouds are artwork, so the
            // texture unit is off for the first and on for the second.
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawSky(gui, worldTime, rain, thunder, posX, posY,
                    viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    /**
     * Draws the clouds onto the sheet rather than over it.
     *
     * <p>They go through the same matrix as the map image, so they turn and
     * lean with the ground and are foreshortened by it — a cloud is a thing
     * lying on the map, not a sprite standing on it, and it must never pivot
     * to face the reader. Which also means the positions handed to it here are
     * the flat ones: the matrix does the turning, and doing it twice would
     * carry every cloud off the map.</p>
     */
    private static void drawSky(
            LostTalesLotrMapGui gui, long worldTime,
            float rain, float thunder, float posX, float posY,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return;
        }
        boolean sheeted = LostTalesLotrMapRotation.pushSheetTransform(gui);
        boolean clipped = sheeted && beginViewportClip(
                viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        try {
            minecraft.getTextureManager().bindTexture(CLOUD_TEXTURE);
            // Smoothed rather than nearest, unlike everything else on this
            // map: a cloud is the one thing here with no edges, and stepping
            // its rim into blocks is worse than the map's own pixels are big.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            try {
                drawCloudLayer(tessellator, worldTime, rain, thunder,
                        posX, posY,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            } finally {
                // The tessellator is shared with everything else drawn this
                // frame; left open once, nothing after it draws at all.
                tessellator.draw();
            }
        } finally {
            if (clipped) {
                GL11.glPopAttrib();
            }
            if (sheeted) {
                GL11.glPopMatrix();
            }
        }
    }

    /**
     * The sky: one lattice, one size, at every zoom.
     *
     * <p>Laid out in screen pixels and scrolled by where the camera is looking,
     * so panning carries the sky over the map and zooming does not touch it.
     * The cells visited are therefore a fixed handful whatever the map is
     * doing, which is also why there is no budget or cull here to get
     * wrong.</p>
     */
    private static void drawCloudLayer(
            Tessellator tessellator, long worldTime,
            float rain, float thunder, float posX, float posY,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float drift = cloudDrift(worldTime, CLOUD_SCREEN_CELL);
        float scrollX = skyOffset(posX) - drift;
        float scrollY = skyOffset(posY);
        float centerX = (viewportXMin + viewportXMax) * 0.5F;
        float centerY = (viewportYMin + viewportYMax) * 0.5F;
        // A turned or leaning sheet shows past the viewport's own corners, so
        // the lattice is walked over the box the sheet is actually cut to.
        float reach = LostTalesLotrMapRotation.maxCoverage(
                viewportXMax - viewportXMin, viewportYMax - viewportYMin)
                * 0.5F + CLOUD_SCREEN_WIDTH;
        int cellXMin = (int)Math.floor((scrollX - reach) / CLOUD_SCREEN_CELL);
        int cellXMax = (int)Math.ceil((scrollX + reach) / CLOUD_SCREEN_CELL);
        int cellYMin = (int)Math.floor((scrollY - reach) / CLOUD_SCREEN_CELL);
        int cellYMax = (int)Math.ceil((scrollY + reach) / CLOUD_SCREEN_CELL);
        float height = CLOUD_SCREEN_WIDTH * CLOUD_ASPECT;
        for (int cellX = cellXMin; cellX <= cellXMax; cellX++) {
            for (int cellY = cellYMin; cellY <= cellYMax; cellY++) {
                if (cellNoise(cellX, cellY, CLOUD_CHANNEL)
                        >= CLOUD_DENSITY) {
                    // A cell without a cloud in it is what stops the sky
                    // being an even lattice of them.
                    continue;
                }
                float x = (cellX + 0.5F + jitter(cellX, cellY, 1))
                        * CLOUD_SCREEN_CELL - scrollX + centerX;
                float y = (cellY + 0.5F + jitter(cellX, cellY, 2))
                        * CLOUD_SCREEN_CELL - scrollY + centerY;
                float[] weather = resolveWeather(
                        cellX, cellY, CLOUD_CHANNEL, rain, thunder);
                int variant = (int)(cellNoise(
                        cellX, cellY, CLOUD_CHANNEL + 3) * CLOUD_VARIANTS)
                        % CLOUD_VARIANTS;
                drawCloud(tessellator, x, y, height, variant, weather);
            }
        }
    }

    private static float jitter(int cellX, int cellY, int channel) {
        return (cellNoise(cellX, cellY, CLOUD_CHANNEL + channel) - 0.5F)
                * 2.0F * CLOUD_JITTER;
    }

    /** One cloud, as its own frame of the sheet, centred on its cell. */
    private static void drawCloud(
            Tessellator tessellator, float centerX, float centerY,
            float height, int variant, float[] weather) {
        float halfWidth = CLOUD_SCREEN_WIDTH * 0.5F;
        float halfHeight = height * 0.5F;
        double uMin = variant / (double)CLOUD_VARIANTS;
        double uMax = (variant + 1.0D) / CLOUD_VARIANTS;
        tessellator.setColorRGBA_F(
                weather[0], weather[1], weather[2], weather[3]);
        tessellator.addVertexWithUV(centerX - halfWidth,
                centerY + halfHeight, 0.0D, uMin, 1.0D);
        tessellator.addVertexWithUV(centerX + halfWidth,
                centerY + halfHeight, 0.0D, uMax, 1.0D);
        tessellator.addVertexWithUV(centerX + halfWidth,
                centerY - halfHeight, 0.0D, uMax, 0.0D);
        tessellator.addVertexWithUV(centerX - halfWidth,
                centerY - halfHeight, 0.0D, uMin, 0.0D);
    }

    /**
     * Keeps a leaning sheet inside its own frame.
     *
     * <p>Flat and square the clouds cannot reach past the viewport, but the
     * sheet they are drawn on is cut larger than it so that turning and
     * leaning have ground to show, and without this the sky would spill over
     * the panels around the map.</p>
     */
    private static boolean beginViewportClip(
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return false;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        int scaleFactor = Math.max(1, resolution.getScaleFactor());
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                viewportXMin * scaleFactor,
                (resolution.getScaledHeight() - viewportYMax) * scaleFactor,
                Math.max(0, viewportXMax - viewportXMin) * scaleFactor,
                Math.max(0, viewportYMax - viewportYMin) * scaleFactor);
        return true;
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
     * What kind of weather a cloud is carrying, as {@code {r, g, b, a}}.
     *
     * <p>Every cloud has a number of its own, fixed for its cell, and the
     * world's weather is a threshold against it: as rain sets in, the clouds
     * whose number falls under its strength darken, one by one and always the
     * same ones, so a front rolling in reads as the sky thickening rather than
     * as the whole map changing colour at once. Nothing is simulated and
     * nothing is remembered between frames.</p>
     *
     * <p>This is the single seam where a regional layer belongs. Minecraft
     * keeps one weather state per dimension and LOTR adds none of its own, so
     * there is nothing available here that could put snow over Forodwaith and
     * rain over the Shire; when there is — a biome or region sampled from the
     * cloud's own map position and cached — it is this method that reads it,
     * and the drawing above does not change. Snow, sand and fog are not
     * implemented: they would be further entries here.</p>
     */
    static float[] resolveWeather(
            int cellX, int cellY, int seed, float rain, float thunder) {
        float wetness = cellNoise(cellX, cellY, seed + 26);
        if (wetness < Math.max(0.0F, Math.min(1.0F, thunder))) {
            return THUNDER_CLOUD;
        }
        if (wetness < Math.max(0.0F, Math.min(1.0F, rain))) {
            return RAIN_CLOUD;
        }
        return FAIR_CLOUD;
    }
}
