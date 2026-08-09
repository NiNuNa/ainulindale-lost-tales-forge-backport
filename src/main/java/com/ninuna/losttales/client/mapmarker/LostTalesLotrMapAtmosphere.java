package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
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
     * How far apart the clouds sit and how wide one is, both in map pixels.
     *
     * <p>The sky is weather over a stretch of Middle-earth, so it is measured
     * in Middle-earth: a cloud covers a fixed patch of ground, and the zoom
     * grows and shrinks it exactly as it grows and shrinks the ground beneath.
     * Holding it at a constant number of screen pixels instead — which is what
     * it used to do — made the sky a texture on the window rather than a layer
     * over the world, and no amount of parallax on top of that reads as
     * height.</p>
     */
    private static final float CLOUD_WORLD_CELL = 34.0F;
    private static final float CLOUD_WORLD_WIDTH = 22.0F;
    private static final float CLOUD_ASPECT = 0.5F;
    private static final float CLOUD_JITTER = 0.45F;
    /**
     * Share of cells that have a cloud in them at all, before the sky's own
     * clustering thins some regions out and crowds others.
     */
    private static final float CLOUD_DENSITY = 0.5F;
    /** How broad a bank of cloud is, in map pixels. */
    private static final float CLOUD_CLUSTER_SIZE = 260.0F;
    /**
     * How much faster than the ground the sky moves across the screen.
     *
     * <p>The clouds are between the reader and the map, so panning has to
     * carry them further than it carries the ground: their offset from where
     * the eye is aimed is magnified by this, which is the whole of the
     * parallax and is the same effect at every zoom because it is applied to a
     * map-space offset rather than to a screen-space scroll.</p>
     */
    private static final float CLOUD_PARALLAX = 1.35F;
    /**
     * How far above the sheet the sky hangs, in map pixels.
     *
     * <p>Only visible once the map is leaned: a layer with height over a
     * surface seen from an angle stands up from the point it shades, and
     * without that a cloud on a tilted map sits on the ground like a stain.
     * Tuned rather than derived from {@link #CLOUD_PARALLAX}, because the
     * map's eye distance is a screen measurement and this is a world one; the
     * two describe the same layer from two directions and are meant to be
     * moved together.</p>
     */
    private static final float CLOUD_ALTITUDE = 26.0F;
    /** Cells the drift wraps after, so long-running worlds keep their sky. */
    private static final float DRIFT_WRAP_CELLS = 1024.0F;
    /**
     * Map pixels a cloud drifts per tick. Slow enough not to pull the eye,
     * quick enough that a cloud visibly moves while you look at it.
     */
    private static final float CLOUD_DRIFT = 0.028F;
    /**
     * Narrowest a cloud may be drawn, in screen pixels, before the sky is
     * dropped.
     *
     * <p>The same rule the decorations use, and the only one either of them
     * has: a layer is dropped once there is nothing left of it to see, and is
     * never faded for the zoom being where it is.</p>
     */
    private static final float MIN_CLOUD_WIDTH = 2.0F;
    /** Noise channels the sky takes for itself. */
    private static final int CLOUD_CHANNEL = 64;
    /**
     * The haze the far half of a leaning map washes into.
     *
     * <p>Aerial perspective, and the cheapest honest depth cue there is: the
     * further ground is from the eye, the more air is in the way, and the more
     * it takes on the colour of that air. The projection already makes the far
     * edge small; this is what makes it read as <em>far</em> rather than
     * merely as small.</p>
     *
     * <p>Pale parchment by day, and mixed towards whatever the hour and the
     * weather have made of the sky, so a night map hazes into its own dark
     * rather than into a band of daylight.</p>
     */
    private static final float[] HAZE_COLOUR = { 0.87F, 0.85F, 0.79F };
    /**
     * The most the far edge may be washed out, at full lean.
     *
     * <p>Bounded well short of hiding anything: the map is a navigation
     * instrument, and the far edge still has to be a place you can read a
     * coastline off.</p>
     */
    private static final float MAX_HAZE_ALPHA = 0.3F;
    /**
     * Bands the gradient is drawn in.
     *
     * <p>Vertex colours interpolate in a straight line and the haze does not,
     * so it is drawn in a few steps and let to interpolate within each. Eight
     * is past the point where the joins can be seen.</p>
     */
    private static final int HAZE_BANDS = 8;
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
     * How large the sky is drawn for a given zoom, in screen pixels per map
     * pixel.
     *
     * <p>The one place the sky is tied to the map. It is the ground's own
     * scale, magnified by how much nearer the sky is: that single number
     * carries both halves of the parallax, because it decides how large a
     * cloud is drawn <em>and</em> how far a pan moves it, and the two cannot
     * then disagree.</p>
     */
    static float skyScale(float zoomScale) {
        return zoomScale * CLOUD_PARALLAX;
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
            drawSky(gui, worldTime, rain, thunder, posX, posY, zoomScale,
                    viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    /**
     * Draws the clouds over the sheet rather than onto it.
     *
     * <p>A cloud is not printed on the paper. Its <em>position</em> belongs to
     * the map and goes through the sheet's own projection — pan, zoom, turn,
     * lean — but the cloud itself then faces the reader, the way everything
     * else standing over the map does. Drawing it through the sheet matrix
     * instead, which is what it used to do, laid it flat on the ground and
     * sheared it with the lean: weather painted onto Middle-earth rather than
     * weather above it.</p>
     */
    private static void drawSky(
            LostTalesLotrMapGui gui, long worldTime,
            float rain, float thunder, float posX, float posY,
            float zoomScale, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return;
        }
        boolean clipped = LostTalesLotrMapLayout.beginViewportClip(
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
                drawCloudLayer(tessellator, gui, worldTime, rain, thunder,
                        posX, posY, zoomScale,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            } finally {
                // The tessellator is shared with everything else drawn this
                // frame; left open once, nothing after it draws at all.
                tessellator.draw();
            }
        } finally {
            LostTalesLotrMapLayout.endViewportClip(clipped);
        }
    }

    /**
     * The sky: a lattice over Middle-earth, walked at whatever spacing the
     * zoom leaves it on screen.
     *
     * <p>Cells are map positions, so a bank of cloud is over the same stretch
     * of country every time the map is opened. What the zoom changes is how
     * large they are drawn and how many of them are worth drawing — the same
     * two answers everything else standing over the map gets, and for the same
     * reasons.</p>
     */
    private static void drawCloudLayer(
            Tessellator tessellator, LostTalesLotrMapGui gui, long worldTime,
            float rain, float thunder, float posX, float posY,
            float zoomScale, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float scale = skyScale(zoomScale);
        if (!(scale > 0.0F)
                || CLOUD_WORLD_WIDTH * scale < MIN_CLOUD_WIDTH) {
            return;
        }
        float drift = cloudDrift(worldTime, CLOUD_WORLD_CELL);
        float centerX = (viewportXMin + viewportXMax) * 0.5F;
        float centerY = (viewportYMin + viewportYMax) * 0.5F;
        // How much sky is on screen now, rather than the most there could ever
        // be: a turned or leaning sheet reaches past the viewport's corners
        // and a flat one does not, and giving a flat map the wide box anyway
        // meant walking several times the cells for nothing.
        float[] box = new float[2];
        LostTalesLotrMapRotation.rotatedCoverage(
                viewportXMax - viewportXMin, viewportYMax - viewportYMin,
                LostTalesLotrMapRotation.degreesOf(gui),
                LostTalesLotrMapRotation.leanOf(gui), box);
        float coverage = Math.max(box[0], box[1]);
        float reach = coverage * 0.5F / scale + CLOUD_WORLD_WIDTH;
        float cell = CLOUD_WORLD_CELL;
        // Only the sky over ground the map image actually has, and read at
        // where the drift has carried the lattice to. Clamping the map
        // positions rather than the cells is what keeps that right: the
        // lattice runs on for ever and the drift walks along it, while
        // Middle-earth does not.
        int width = LostTalesLotrMapRotation.mapImageWidth();
        int height = LostTalesLotrMapRotation.mapImageHeight();
        float visibleXMin = Math.max(0.0F, posX - reach);
        float visibleXMax = width > 0
                ? Math.min(width, posX + reach) : posX + reach;
        float visibleYMin = Math.max(0.0F, posY - reach);
        float visibleYMax = height > 0
                ? Math.min(height, posY + reach) : posY + reach;
        int cellXMin = (int)Math.floor((visibleXMin + drift) / cell);
        int cellXMax = (int)Math.ceil((visibleXMax + drift) / cell);
        int cellYMin = (int)Math.floor(visibleYMin / cell);
        int cellYMax = (int)Math.ceil(visibleYMax / cell);
        float lift = CLOUD_ALTITUDE * scale
                * LostTalesLotrMapRotation.leanSine(gui);
        float[] anchor = new float[2];
        for (int cellX = cellXMin; cellX <= cellXMax; cellX++) {
            for (int cellY = cellYMin; cellY <= cellYMax; cellY++) {
                drawCloudSite(tessellator, gui, cellX, cellY,
                        rain, thunder, drift, posX, posY, scale, lift,
                        centerX, centerY, anchor,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            }
        }
    }

    private static void drawCloudSite(
            Tessellator tessellator, LostTalesLotrMapGui gui,
            int cellX, int cellY,
            float rain, float thunder, float drift, float posX, float posY,
            float scale, float lift, float centerX, float centerY,
            float[] anchor,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        // A cell without a cloud in it is what stops the sky being an even
        // lattice of them; the clustering is what gives it banks and clear
        // stretches rather than one steady scatter.
        if (!LostTalesMapDecorationPlacement.hasClusteredSite(
                cellX, cellY, CLOUD_CHANNEL, CLOUD_DENSITY,
                CLOUD_WORLD_CELL, CLOUD_CLUSTER_SIZE)) {
            return;
        }
        float mapX = LostTalesMapDecorationPlacement.siteX(
                cellX, cellY, CLOUD_CHANNEL, CLOUD_WORLD_CELL,
                CLOUD_JITTER) - drift;
        float mapY = LostTalesMapDecorationPlacement.siteY(
                cellX, cellY, CLOUD_CHANNEL, CLOUD_WORLD_CELL,
                CLOUD_JITTER);
        anchor[0] = (mapX - posX) * scale + centerX;
        anchor[1] = (mapY - posY) * scale + centerY;
        float depth = LostTalesLotrMapRotation.rotateAndProject(anchor, gui);
        float width = CLOUD_WORLD_WIDTH * scale * depth;
        float height = width * CLOUD_ASPECT;
        if (width < MIN_CLOUD_WIDTH
                || anchor[0] + width < viewportXMin
                || anchor[0] - width > viewportXMax
                || anchor[1] + height < viewportYMin
                || anchor[1] - height > viewportYMax) {
            return;
        }
        float[] weather = resolveWeather(
                cellX, cellY, CLOUD_CHANNEL, rain, thunder);
        int variant = (int)(cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 3) * CLOUD_VARIANTS)
                % CLOUD_VARIANTS;
        drawCloud(tessellator, anchor[0], anchor[1] - lift * depth,
                width, height, variant, weather);
    }

    /** One cloud, as its own frame of the sheet, facing the reader. */
    private static void drawCloud(
            Tessellator tessellator, float centerX, float centerY,
            float width, float height, int variant, float[] weather) {
        float halfWidth = width * 0.5F;
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
     * How strongly the ground at a screen height is hazed.
     *
     * <p>Only the far half of the map, and only while it is leaning. The
     * lean's own projection divides by a number that runs in a straight line
     * down the screen, so how far away a piece of ground is runs in a straight
     * line too — which makes where the haze belongs a question about screen
     * height and nothing else, whatever the map is turned to.</p>
     *
     * @param far how far towards the top of the viewport, 0 at the middle
     */
    static float hazeStrength(float lean, float far) {
        float clampedLean = Math.max(0.0F, Math.min(1.0F, lean));
        if (clampedLean <= 0.0F || !(far > 0.0F)) {
            return 0.0F;
        }
        return MAX_HAZE_ALPHA * clampedLean
                * smoothstep(Math.min(1.0F, far));
    }

    /**
     * Draws the haze over the far half of a leaning map.
     *
     * <p>Over the ground and everything standing on it, and under everything
     * the player navigates by: roads, region names and markers are all drawn
     * later and stay as crisp at the far edge as at the near one. Hazing those
     * too would be more faithful and would cost the map its legs.</p>
     */
    static void renderDistanceHaze(
            LostTalesLotrMapGui gui, long worldTime, float rain, float thunder,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float lean = LostTalesLotrMapRotation.leanOf(gui);
        float centerY = (viewportYMin + viewportYMax) * 0.5F;
        float reach = centerY - viewportYMin;
        if (lean <= 0.0F || !(reach > 0.0F)
                || viewportXMax <= viewportXMin) {
            return;
        }
        float[] shade = shadeFor(worldTime, rain, thunder);
        // Mixed towards the hour's own colour by how much of the map that
        // colour is already responsible for, so the haze belongs to the same
        // sky the rest of the map is lit by.
        float towardsNight = Math.min(1.0F, shade[3] / MAX_TOTAL_ALPHA);
        float red = mix(HAZE_COLOUR[0], shade[0], towardsNight);
        float green = mix(HAZE_COLOUR[1], shade[1], towardsNight);
        float blue = mix(HAZE_COLOUR[2], shade[2], towardsNight);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            try {
                for (int band = 0; band < HAZE_BANDS; band++) {
                    float topShare = 1.0F - band / (float)HAZE_BANDS;
                    float bottomShare =
                            1.0F - (band + 1) / (float)HAZE_BANDS;
                    float top = centerY - reach * topShare;
                    float bottom = centerY - reach * bottomShare;
                    float topAlpha = hazeStrength(lean, topShare);
                    float bottomAlpha = hazeStrength(lean, bottomShare);
                    tessellator.setColorRGBA_F(
                            red, green, blue, bottomAlpha);
                    tessellator.addVertex(viewportXMin, bottom, 0.0D);
                    tessellator.addVertex(viewportXMax, bottom, 0.0D);
                    tessellator.setColorRGBA_F(red, green, blue, topAlpha);
                    tessellator.addVertex(viewportXMax, top, 0.0D);
                    tessellator.addVertex(viewportXMin, top, 0.0D);
                }
            } finally {
                tessellator.draw();
            }
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
