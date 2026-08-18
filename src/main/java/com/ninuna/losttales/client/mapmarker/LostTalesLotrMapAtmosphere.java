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
 * <p>The shade and distance haze lie on the ground. Clouds pass over standing
 * scenery and road dots, while names, markers, the rose and the strip remain
 * above the entire atmosphere. Nothing here may cost readability: the map is
 * a navigation instrument first.</p>
 *
 * <p>All of it is client-side and derived from state the client already has.
 * Nothing is asked of the server and nothing is remembered between frames
 * except the constants below.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesLotrMapAtmosphere {
    private static final int TICKS_PER_DAY = 24000;
    private static final float[] CLOUD_COVERAGE = new float[2];
    private static final float[] CLOUD_ANCHOR = new float[2];
    private static final float[] CLOUD_GROUND_ANCHOR = new float[2];
    private static final float[] CLOUD_SHADOW_POINT = new float[2];
    private static final float[] CLOUD_SHADOW_CORNERS = new float[8];
    /** Per-frame reuse of the expensive five-ring cloud land-mask answer. */
    private static final int CLOUD_LAND_CACHE_SLOTS = 1 << 14;
    private static final int CLOUD_LAND_CACHE_MASK =
            CLOUD_LAND_CACHE_SLOTS - 1;
    private static final long[] CLOUD_LAND_CACHE_KEYS =
            new long[CLOUD_LAND_CACHE_SLOTS];
    private static final float[] CLOUD_LAND_CACHE_VALUES =
            new float[CLOUD_LAND_CACHE_SLOTS];
    private static final int[] CLOUD_LAND_CACHE_FRAMES =
            new int[CLOUD_LAND_CACHE_SLOTS];
    private static int cloudLandCacheFrame = 1;
    private static boolean cloudLandCachePrepared;
    private static final int CLOUD_LAND_SAMPLES = 12;
    private static final double[] CLOUD_LAND_COS =
            new double[CLOUD_LAND_SAMPLES];
    private static final double[] CLOUD_LAND_SIN =
            new double[CLOUD_LAND_SAMPLES];
    static {
        for (int sample = 0; sample < CLOUD_LAND_SAMPLES; sample++) {
            double angle = Math.PI * 2.0D * sample / CLOUD_LAND_SAMPLES;
            CLOUD_LAND_COS[sample] = Math.cos(angle);
            CLOUD_LAND_SIN[sample] = Math.sin(angle);
        }
    }
    private static final LostTalesMapDecorationPlacement.GroundSampler
            LAND_SAMPLER =
            new LostTalesMapDecorationPlacement.GroundSampler() {
                @Override
                public boolean matches(int mapX, int mapY) {
                    return LostTalesMapTerrain.isLand(mapX, mapY);
                }
            };

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
    /** Readability ceiling for the directional light laid over the paper. */
    private static final float MAX_LIGHT_ALPHA = 0.065F;
    private static final float[] DAY_LIGHT = { 1.0F, 0.91F, 0.66F };
    private static final float[] TWILIGHT_LIGHT = { 1.0F, 0.55F, 0.27F };
    private static final float[] MOON_LIGHT = { 0.48F, 0.61F, 0.92F };

    /** Placeholder cloud artwork: four soft shapes in a row. */
    private static final ResourceLocation CLOUD_TEXTURE =
            new ResourceLocation(LostTalesMetaData.MOD_ID,
                    "textures/gui/map/cloud.png");
    /** Falling-rain sheet used by vanilla 1.7.10's weather renderer. */
    private static final ResourceLocation VANILLA_RAIN_TEXTURE =
            new ResourceLocation("minecraft",
                    "textures/environment/rain.png");
    /** Exact atlas used by vanilla 1.7.10's EntityRainFX impact splashes. */
    private static final ResourceLocation VANILLA_PARTICLE_TEXTURE =
            new ResourceLocation("minecraft",
                    "textures/particle/particles.png");
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
    private static final float CLOUD_PARALLAX = 1.04F;
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
    private static final float CLOUD_ALTITUDE = 14.0F;
    private static final float CLOUD_SHADOW_FLAT_ALPHA = 0.018F;
    private static final float CLOUD_SHADOW_LEAN_ALPHA = 0.075F;
    /** Cells the drift wraps after, so long-running worlds keep their sky. */
    private static final float DRIFT_WRAP_CELLS = 1024.0F;
    /**
     * Map pixels a cloud drifts per tick. Slow enough not to pull the eye,
     * quick enough that a cloud visibly moves while you look at it.
     */
    private static final float CLOUD_DRIFT = 0.028F;
    /**
     * Projected width at which a cloud has faded completely. The shared
     * projected-visibility curve brings it in smoothly above this point.
     */
    private static final float CLOUD_MIN_READABLE_WIDTH = 2.0F;
    private static final float CLOUD_MIN_SIZE = 0.72F;
    private static final float CLOUD_MAX_SIZE = 1.35F;
    private static final float CLOUD_MIN_LAYER = 0.99F;
    private static final float CLOUD_MAX_LAYER = 1.01F;
    /** Local motion around the shared drift, in map pixels. */
    private static final float CLOUD_SWAY_REACH = 8.0F;
    private static final float CLOUD_SWAY_MIN_SPEED = 0.00055F;
    private static final float CLOUD_SWAY_MAX_SPEED = 0.0011F;
    /** Maximum animated drops emitted by one wet cloud. */
    private static final int MIN_RAIN_DROPS_PER_CLOUD = 5;
    private static final int MIN_THUNDER_DROPS_PER_CLOUD = 9;
    private static final int MAX_RAIN_DROPS_PER_CLOUD = 36;
    private static final int MAX_THUNDER_DROPS_PER_CLOUD = 54;
    /** Screen-space rain area represented by one animated drop lane. */
    private static final float RAIN_SCREEN_AREA_PER_DROP = 750.0F;
    /** Very tall close-up columns are bounded for rendering cost. */
    private static final float MAX_DENSITY_FALL_DISTANCE = 180.0F;
    /**
     * Projected cloud width at which attached rain has faded completely.
     * This uses the same projected-size curve as ground decorations, but the
     * rain artwork itself remains screen-facing and does not shrink.
     */
    private static final float RAIN_MIN_READABLE_CLOUD_WIDTH = 22.0F;
    /** Rain is screen-facing weather artwork, not ink scaled with the map. */
    private static final float RAIN_SCREEN_WIDTH = 16.0F;
    private static final float RAIN_FLAT_SCREEN_LENGTH = 64.0F;
    private static final float RAIN_TILTED_SCREEN_LENGTH = 88.0F;
    private static final float RAIN_MIN_SPEED = 0.055F;
    private static final float RAIN_MAX_SPEED = 0.082F;
    private static final float RAIN_FLAT_REACH_MULTIPLIER = 1.8F;
    private static final float RAIN_MIN_FLAT_REACH = 8.0F;
    /** By this lean, rain terminates at its projected ground footprint. */
    private static final float RAIN_GROUND_ALIGNMENT_LEAN = 0.35F;
    private static final float RAIN_ALPHA = 0.90F;
    /** Vertical radius of the projected ground footprint at full lean. */
    private static final float RAIN_IMPACT_DEPTH = 0.32F;
    /** EntityRainFX chooses one of these four consecutive atlas frames. */
    private static final int RAIN_PARTICLE_FIRST_FRAME = 19;
    private static final int PARTICLE_ATLAS_COLUMNS = 16;
    /** A generous impact phase keeps the lower-density rain visibly splashing. */
    private static final float RAIN_FALL_PHASE_END = 0.64F;
    private static final float SPLASH_MIN_LEAN = 0.015F;
    private static final int CLOUD_PASS = 0;
    private static final int RAIN_FALL_PASS = 1;
    private static final int RAIN_SPLASH_PASS = 2;
    private static final int LIGHTNING_PASS = 3;
    private static final int CLOUD_SHADOW_PASS = 4;
    /** Cloud centres this close to known land may overhang its coast. */
    private static final int CLOUD_LAND_MARGIN = 30;
    private static final int LIGHTNING_SEGMENTS = 6;
    private static final float LIGHTNING_ALPHA = 0.92F;
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
    /** Extreme near/far optical softness; the central focus plane stays clear. */
    private static final float MAX_FOCUS_VEIL_ALPHA = 0.038F;
    private static final int FOCUS_BANDS = 16;
    private static final int HAZE_WISPS = 7;
    private static final int HAZE_WISP_SEGMENTS = 14;
    private static final float MAX_WISP_ALPHA = 0.024F;
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
     * Directional illumination for the hour as {@code {r,g,b,a,side}}.
     * Side is -1 at dawn, +1 at dusk and zero for overhead sun or moon.
     */
    static float[] lightingFor(long worldTime) {
        float time = (float)(worldTime % TICKS_PER_DAY);
        if (time < 0.0F) {
            time += TICKS_PER_DAY;
        }
        double angle = time / TICKS_PER_DAY * Math.PI * 2.0D;
        float elevation = (float)Math.sin(angle);
        float sun = Math.max(0.0F, elevation);
        float moon = Math.max(0.0F, -elevation);
        float dawnDistance = Math.min(time, TICKS_PER_DAY - time);
        float duskDistance = Math.abs(time - TICKS_PER_DAY * 0.5F);
        float dawn = 1.0F - smoothstep(dawnDistance / 1900.0F);
        float dusk = 1.0F - smoothstep(duskDistance / 1900.0F);
        float twilight = Math.max(dawn, dusk);
        float dayAmount = 0.026F * sun;
        float moonAmount = 0.019F * moon;
        float twilightAmount = 0.056F * twilight;
        float total = Math.min(MAX_LIGHT_ALPHA,
                dayAmount + moonAmount + twilightAmount);
        if (total <= 0.0F) {
            return new float[] { 1.0F, 1.0F, 1.0F, 0.0F, 0.0F };
        }
        float red = (DAY_LIGHT[0] * dayAmount
                + MOON_LIGHT[0] * moonAmount
                + TWILIGHT_LIGHT[0] * twilightAmount) / total;
        float green = (DAY_LIGHT[1] * dayAmount
                + MOON_LIGHT[1] * moonAmount
                + TWILIGHT_LIGHT[1] * twilightAmount) / total;
        float blue = (DAY_LIGHT[2] * dayAmount
                + MOON_LIGHT[2] * moonAmount
                + TWILIGHT_LIGHT[2] * twilightAmount) / total;
        float side = twilight <= 0.001F ? 0.0F
                : (dawn >= dusk ? -1.0F : 1.0F);
        return new float[] {
                clamp01(red), clamp01(green), clamp01(blue), total, side
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
     * Draws the day and weather shade over the map's own viewport.
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
            float[] light = lightingFor(worldTime);
            float weather = Math.max(clamp01(rain), clamp01(thunder));
            light[3] *= 1.0F - weather * 0.68F;
            if (light[3] > 0.001F) {
                drawDirectionalLight(light, viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            }
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    /**
     * Draws semi-transparent clouds above ground decorations and below map
     * navigation graphics.
     */
    static void renderClouds(LostTalesLotrMapGui gui, long worldTime,
                             float rain, float thunder,
                             float posX, float posY, float zoomScale,
                             int viewportXMin, int viewportXMax,
                             int viewportYMin, int viewportYMax) {
        if (!(zoomScale > 0.0F)) {
            return;
        }
        if (!cloudLandCachePrepared) {
            beginCloudLandCacheFrame();
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawSky(gui, worldTime, rain, thunder, posX, posY, zoomScale,
                    viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
        } finally {
            cloudLandCachePrepared = false;
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    /** Draws a restrained cloud footprint on the ground below map artwork. */
    static void renderCloudShadows(
            LostTalesLotrMapGui gui, long worldTime,
            float rain, float thunder,
            float posX, float posY, float zoomScale,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        if (!(zoomScale > 0.0F)) {
            return;
        }
        beginCloudLandCacheFrame();
        cloudLandCachePrepared = true;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.getTextureManager() == null) {
                return;
            }
            minecraft.getTextureManager().bindTexture(CLOUD_TEXTURE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            boolean clipped = LostTalesLotrMapLayout.beginViewportClip(
                    viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
            try {
                Tessellator tessellator = Tessellator.instance;
                tessellator.startDrawingQuads();
                try {
                    drawCloudLayer(tessellator, gui, worldTime,
                            animationTicks(), rain, thunder,
                            posX, posY, zoomScale,
                            viewportXMin, viewportXMax,
                            viewportYMin, viewportYMax,
                            CLOUD_SHADOW_PASS);
                } finally {
                    tessellator.draw();
                }
            } finally {
                LostTalesLotrMapLayout.endViewportClip(clipped);
            }
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
            Tessellator tessellator = Tessellator.instance;
            double animationTime = animationTicks();
            if (rain > 0.0F || thunder > 0.0F) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                minecraft.getTextureManager().bindTexture(
                        VANILLA_RAIN_TEXTURE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                tessellator.startDrawingQuads();
                try {
                    drawCloudLayer(tessellator, gui, worldTime,
                            animationTime, rain, thunder,
                            posX, posY, zoomScale,
                            viewportXMin, viewportXMax,
                            viewportYMin, viewportYMax, RAIN_FALL_PASS);
                } finally {
                    tessellator.draw();
                }

                if (LostTalesLotrMapRotation.leanSine(gui)
                        > SPLASH_MIN_LEAN) {
                    minecraft.getTextureManager().bindTexture(
                            VANILLA_PARTICLE_TEXTURE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                    tessellator.startDrawingQuads();
                    try {
                        drawCloudLayer(tessellator, gui, worldTime,
                                animationTime, rain, thunder,
                                posX, posY, zoomScale,
                                viewportXMin, viewportXMax,
                                viewportYMin, viewportYMax,
                                RAIN_SPLASH_PASS);
                    } finally {
                        tessellator.draw();
                    }
                }
            }
            if (thunder > 0.0F) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                tessellator.startDrawingQuads();
                try {
                    drawCloudLayer(tessellator, gui, worldTime,
                            animationTime, rain, thunder,
                            posX, posY, zoomScale,
                            viewportXMin, viewportXMax,
                            viewportYMin, viewportYMax, LIGHTNING_PASS);
                } finally {
                    tessellator.draw();
                }
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            minecraft.getTextureManager().bindTexture(CLOUD_TEXTURE);
            // Smoothed rather than nearest, unlike everything else on this
            // map: a cloud is the one thing here with no edges, and stepping
            // its rim into blocks is worse than the map's own pixels are big.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            tessellator.startDrawingQuads();
            try {
                drawCloudLayer(tessellator, gui, worldTime,
                        animationTime, rain, thunder,
                        posX, posY, zoomScale,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax, CLOUD_PASS);
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
            double animationTime,
            float rain, float thunder, float posX, float posY,
            float zoomScale, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax, int pass) {
        float scale = skyScale(zoomScale);
        float coverageScale = pass == CLOUD_SHADOW_PASS
                ? zoomScale : scale * CLOUD_MIN_LAYER;
        float minimumReadableWidth = pass == RAIN_FALL_PASS
                || pass == RAIN_SPLASH_PASS
                ? RAIN_MIN_READABLE_CLOUD_WIDTH
                : CLOUD_MIN_READABLE_WIDTH;
        if (!(scale > 0.0F)
                || LostTalesMapProjectedVisibility.alpha(
                        CLOUD_WORLD_WIDTH * scale * CLOUD_MAX_SIZE,
                        minimumReadableWidth) <= 0.0F) {
            return;
        }
        float drift = cloudDrift(worldTime, CLOUD_WORLD_CELL);
        float centerX = (viewportXMin + viewportXMax) * 0.5F;
        float centerY = (viewportYMin + viewportYMax) * 0.5F;
        // How much sky is on screen now, rather than the most there could ever
        // be: a turned or leaning sheet reaches past the viewport's corners
        // and a flat one does not, and giving a flat map the wide box anyway
        // meant walking several times the cells for nothing.
        LostTalesLotrMapRotation.visibleCoverage(
                viewportXMax - viewportXMin, viewportYMax - viewportYMin,
                LostTalesLotrMapRotation.degreesOf(gui),
                LostTalesLotrMapRotation.leanOf(gui), CLOUD_COVERAGE);
        float reachX = CLOUD_COVERAGE[0] * 0.5F
                / coverageScale
                + CLOUD_WORLD_WIDTH * CLOUD_MAX_SIZE + CLOUD_SWAY_REACH;
        float reachY = CLOUD_COVERAGE[1] * 0.5F
                / coverageScale
                + CLOUD_WORLD_WIDTH * CLOUD_MAX_SIZE + CLOUD_SWAY_REACH;
        float cell = CLOUD_WORLD_CELL;
        // The sky continues beyond the map image. Stopping the lattice at
        // Middle-earth's texture edge exposes a straight weather boundary as
        // soon as rotation or parallax looks past it.
        float visibleXMin = posX - reachX;
        float visibleXMax = posX + reachX;
        float visibleYMin = posY - reachY;
        float visibleYMax = posY + reachY;
        int cellXMin = (int)Math.floor((visibleXMin + drift) / cell);
        int cellXMax = (int)Math.ceil((visibleXMax + drift) / cell);
        int cellYMin = (int)Math.floor(visibleYMin / cell);
        int cellYMax = (int)Math.ceil(visibleYMax / cell);
        float leanSine = LostTalesLotrMapRotation.leanSine(gui);
        for (int cellX = cellXMin; cellX <= cellXMax; cellX++) {
            for (int cellY = cellYMin; cellY <= cellYMax; cellY++) {
                drawCloudSite(tessellator, gui, cellX, cellY,
                        rain, thunder, worldTime, animationTime,
                        drift, posX, posY,
                        scale, zoomScale, leanSine,
                        centerX, centerY, CLOUD_ANCHOR,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax, pass);
            }
        }
    }

    private static void drawCloudSite(
            Tessellator tessellator, LostTalesLotrMapGui gui,
            int cellX, int cellY,
            float rain, float thunder, long worldTime, double animationTime,
            float drift, float posX, float posY,
            float scale, float groundScale, float leanSine,
            float centerX, float centerY,
            float[] anchor,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax, int pass) {
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
        float size = mix(CLOUD_MIN_SIZE, CLOUD_MAX_SIZE,
                cellNoise(cellX, cellY, CLOUD_CHANNEL + 4));
        float layer = mix(CLOUD_MIN_LAYER, CLOUD_MAX_LAYER,
                cellNoise(cellX, cellY, CLOUD_CHANNEL + 5));
        float speed = mix(CLOUD_SWAY_MIN_SPEED, CLOUD_SWAY_MAX_SPEED,
                cellNoise(cellX, cellY, CLOUD_CHANNEL + 6));
        float phase = cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 7)
                * (float)(Math.PI * 2.0D);
        mapX += (float)Math.sin(worldTime * (double)speed + phase)
                * CLOUD_SWAY_REACH;
        float landWeight = cachedCloudLandWeight(
                cellX, cellY, Math.round(mapX), Math.round(mapY));
        if (landWeight <= 0.0F || cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 10) >= landWeight) {
            return;
        }
        float localScale = scale * layer;
        anchor[0] = (mapX - posX) * localScale + centerX;
        anchor[1] = (mapY - posY) * localScale + centerY;
        CLOUD_GROUND_ANCHOR[0] = (mapX - posX) * groundScale + centerX;
        CLOUD_GROUND_ANCHOR[1] = (mapY - posY) * groundScale + centerY;
        float groundX = CLOUD_GROUND_ANCHOR[0];
        float groundY = CLOUD_GROUND_ANCHOR[1];
        LostTalesLotrMapRotation.rotateAndProject(
                CLOUD_GROUND_ANCHOR, gui);
        float depth = LostTalesLotrMapRotation.rotateAndProject(anchor, gui);
        float width = CLOUD_WORLD_WIDTH * localScale * size * depth;
        float height = width * CLOUD_ASPECT;
        float groundWidth = CLOUD_WORLD_WIDTH * groundScale * size;
        float groundHeight = groundWidth * CLOUD_ASPECT;
        float visibility = LostTalesMapProjectedVisibility.alpha(
                width, CLOUD_MIN_READABLE_WIDTH);
        float screenY = anchor[1]
                - CLOUD_ALTITUDE * localScale * leanSine * depth;
        if (visibility <= 0.0F) {
            return;
        }
        if (pass == CLOUD_SHADOW_PASS) {
            // A conservative square remains valid after the map rotates;
            // using the unrotated half-height could clip a corner shadow.
            float groundCullRadius = groundWidth * 0.65F;
            if (CLOUD_GROUND_ANCHOR[0] + groundCullRadius < viewportXMin
                    || CLOUD_GROUND_ANCHOR[0]
                            - groundCullRadius > viewportXMax
                    || CLOUD_GROUND_ANCHOR[1]
                            + groundCullRadius < viewportYMin
                    || CLOUD_GROUND_ANCHOR[1]
                            - groundCullRadius > viewportYMax) {
                return;
            }
            int variant = (int)(cellNoise(
                    cellX, cellY, CLOUD_CHANNEL + 3) * CLOUD_VARIANTS)
                    % CLOUD_VARIANTS;
            float[] weather = resolveWeather(
                    cellX, cellY, CLOUD_CHANNEL, rain, thunder);
            drawCloudShadow(tessellator, gui,
                    groundX, groundY,
                    groundWidth, groundHeight, variant,
                    cloudShadowAlpha(weather[3],
                            LostTalesLotrMapRotation.leanOf(gui))
                            * visibility);
            return;
        }
        float rainTop = screenY + height * 0.12F;
        float fallDistance = rainFallDistance(
                height, CLOUD_GROUND_ANCHOR[1] - rainTop, leanSine);
        if (pass == RAIN_FALL_PASS || pass == RAIN_SPLASH_PASS) {
            float rainVisibility = rainVisibility(width);
            float impactDepth = rainImpactDepth(height, leanSine);
            float precipitation = precipitationStrength(
                    cellX, cellY, CLOUD_CHANNEL, rain, thunder);
            float rainHalfWidth = Math.max(width, groundWidth) * 0.5F;
            float rainMinX = Math.min(
                    anchor[0], CLOUD_GROUND_ANCHOR[0]) - rainHalfWidth;
            float rainMaxX = Math.max(
                    anchor[0], CLOUD_GROUND_ANCHOR[0]) + rainHalfWidth;
            if (rainVisibility <= 0.0F || precipitation <= 0.0F
                    || rainMaxX < viewportXMin
                    || rainMinX > viewportXMax) {
                return;
            }
            if (rainTop + fallDistance + impactDepth < viewportYMin
                    || rainTop > viewportYMax) {
                return;
            }
            float rainAlignment = rainGroundAlignment(leanSine);
            float rainImpactX = mix(
                    anchor[0], CLOUD_GROUND_ANCHOR[0], rainAlignment);
            float rainImpactWidth = mix(
                    width, groundWidth, rainAlignment);
            drawRain(tessellator, cellX, cellY, animationTime,
                    anchor[0], rainImpactX, rainTop,
                    width, rainImpactWidth, height, fallDistance,
                    leanSine, precipitation, thunder, rainVisibility,
                    pass == RAIN_SPLASH_PASS);
            return;
        }
        if (pass == LIGHTNING_PASS) {
            float flash = lightningFlash(
                    cellX, cellY, animationTime, thunder);
            if (flash <= 0.0F
                    || anchor[0] + width * 0.5F < viewportXMin
                    || anchor[0] - width * 0.5F > viewportXMax
                    || rainTop + fallDistance < viewportYMin
                    || rainTop > viewportYMax) {
                return;
            }
            drawLightning(tessellator, cellX, cellY,
                    anchor[0], rainTop, fallDistance,
                    width, flash * visibility);
            return;
        }
        if (anchor[0] + width * 0.5F < viewportXMin
                || anchor[0] - width * 0.5F > viewportXMax
                || screenY + height * 0.5F < viewportYMin
                || screenY - height * 0.5F > viewportYMax) {
            return;
        }
        int variant = (int)(cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 3) * CLOUD_VARIANTS)
                % CLOUD_VARIANTS;
        float[] weather = resolveWeather(
                cellX, cellY, CLOUD_CHANNEL, rain, thunder);
        drawCloud(tessellator, anchor[0], screenY,
                width, height, variant, weather, visibility);
    }

    /**
     * Whether a cloud's broad footprint touches land.
     *
     * <p>Concentric samples retain small islands and allow a cloud centred
     * just offshore to overhang the coast, while eliminating the unbroken
     * lattice over open ocean. Sampling the current drifted position lets the
     * bank move naturally without leaving a permanent coastline-shaped
     * mask.</p>
     */
    static boolean cloudFootprintTouchesLand(
            LostTalesMapDecorationPlacement.GroundSampler land,
            int mapX, int mapY, int margin) {
        return cloudLandWeight(land, mapX, mapY, margin) > 0.0F;
    }

    /** Starts a cheap direct-mapped cache shared by every weather pass. */
    private static void beginCloudLandCacheFrame() {
        cloudLandCacheFrame++;
        if (cloudLandCacheFrame != 0) {
            return;
        }
        // A signed integer wrap takes years of continuous map frames, but do
        // not let an ancient stamp accidentally become current when it does.
        for (int slot = 0; slot < CLOUD_LAND_CACHE_SLOTS; slot++) {
            CLOUD_LAND_CACHE_FRAMES[slot] = 0;
        }
        cloudLandCacheFrame = 1;
    }

    /**
     * The same cell is visited by rain, splash, lightning and cloud passes.
     * Its drifted position is identical in all of them, so the five sampling
     * rings need to touch the biome image only once per frame.
     */
    private static float cachedCloudLandWeight(
            int cellX, int cellY, int mapX, int mapY) {
        long key = ((long)cellX << 32) ^ (cellY & 0xFFFFFFFFL);
        int hash = cellX * 0x1F1F1F1F ^ cellY * 0x5F356495;
        int slot = (hash ^ (hash >>> 16)) & CLOUD_LAND_CACHE_MASK;
        if (CLOUD_LAND_CACHE_FRAMES[slot] == cloudLandCacheFrame
                && CLOUD_LAND_CACHE_KEYS[slot] == key) {
            return CLOUD_LAND_CACHE_VALUES[slot];
        }
        float value = cloudLandWeight(
                LAND_SAMPLER, mapX, mapY, CLOUD_LAND_MARGIN);
        CLOUD_LAND_CACHE_KEYS[slot] = key;
        CLOUD_LAND_CACHE_VALUES[slot] = value;
        CLOUD_LAND_CACHE_FRAMES[slot] = cloudLandCacheFrame;
        return value;
    }

    /**
     * Density multiplier for a cloud near the edge of the land mask.
     * Five rings make offshore clouds progressively rarer instead of ending
     * the sky at one conspicuous hard contour.
     */
    static float cloudLandWeight(
            LostTalesMapDecorationPlacement.GroundSampler land,
            int mapX, int mapY, int margin) {
        if (land == null || margin < 0) {
            return 0.0F;
        }
        if (land.matches(mapX, mapY)) {
            return 1.0F;
        }
        if (margin == 0) {
            return 0.0F;
        }
        final int rings = 5;
        for (int ring = 1; ring <= rings; ring++) {
            float radius = margin * ring / (float)rings;
            int matches = 0;
            for (int sample = 0; sample < CLOUD_LAND_SAMPLES; sample++) {
                int probeX = mapX + (int)Math.round(
                        CLOUD_LAND_COS[sample] * radius);
                int probeY = mapY + (int)Math.round(
                        CLOUD_LAND_SIN[sample] * radius);
                if (land.matches(probeX, probeY)) {
                    matches++;
                }
            }
            if (matches > 0) {
                float nearness = 1.0F - (ring - 1) / (float)rings;
                float coverage = matches / (float)CLOUD_LAND_SAMPLES;
                return clamp01(nearness * nearness
                        * (0.72F + coverage * 0.28F));
            }
        }
        return 0.0F;
    }

    /** A smooth twenty-Hz time base without reaching into Minecraft's timer. */
    private static double animationTicks() {
        return System.nanoTime() * 0.000000020D;
    }

    /**
     * How strongly this particular cloud rains.
     *
     * <p>Minecraft supplies one rain state for the whole dimension, so every
     * visible cloud participates while that state is active. Stable cell noise
     * varies the strength without being allowed to turn the only cloud in view
     * completely dry. The global value is eased, so rain still begins with
     * transparent drops instead of gaining a complete shower in one frame.</p>
     */
    static float precipitationStrength(
            int cellX, int cellY, int seed, float rain, float thunder) {
        float weather = Math.max(clamp01(rain), clamp01(thunder));
        if (weather <= 0.0F) {
            return 0.0F;
        }
        float local = mix(0.72F, 1.0F,
                cellNoise(cellX, cellY, seed + 26));
        float strength = smoothstep(weather) * local;
        return Math.min(1.0F,
                strength + clamp01(thunder) * 0.18F * strength);
    }

    /**
     * Screen distance a drop travels below its cloud.
     *
     * <p>Flat is deliberately a short graphic rain field. Leaning blends that
     * field into the real projected distance from the elevated cloud to its
     * ground anchor, which is the depth cue that turns it into a 3D fall.</p>
     */
    static float rainFallDistance(
            float cloudHeight, float cloudToGround, float leanSine) {
        float height = Math.max(0.0F, cloudHeight);
        float flat = Math.max(RAIN_MIN_FLAT_REACH,
                height * RAIN_FLAT_REACH_MULTIPLIER);
        float projected = Math.max(0.0F, cloudToGround);
        return mix(flat, projected, rainGroundAlignment(leanSine));
    }

    static float rainGroundAlignment(float leanSine) {
        return smoothstep(clamp01(
                leanSine / RAIN_GROUND_ALIGNMENT_LEAN));
    }

    /** Stable, smoothly advancing phase of one drop in a cloud. */
    static float rainPhase(
            int cellX, int cellY, int drop, double animationTime) {
        float offset = cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 80 + drop * 2);
        float speed = mix(RAIN_MIN_SPEED, RAIN_MAX_SPEED,
                cellNoise(cellX, cellY,
                        CLOUD_CHANNEL + 81 + drop * 2));
        double cycles = offset + animationTime * speed;
        return (float)(cycles - Math.floor(cycles));
    }

    /** Fixed-size screen artwork; the argument documents what must not scale it. */
    static float rainScreenWidth(float projectedCloudWidth) {
        return RAIN_SCREEN_WIDTH;
    }

    static float rainScreenLength(
            float projectedCloudWidth, float leanSine) {
        return mix(RAIN_FLAT_SCREEN_LENGTH,
                RAIN_TILTED_SCREEN_LENGTH, clamp01(leanSine));
    }

    /** Decoration-style fade driven by the rain cloud's projected footprint. */
    static float rainVisibility(float projectedCloudWidth) {
        return LostTalesMapProjectedVisibility.alpha(
                projectedCloudWidth, RAIN_MIN_READABLE_CLOUD_WIDTH);
    }

    /** Radius of the oval made by rain striking the projected map plane. */
    static float rainImpactDepth(float projectedCloudHeight, float leanSine) {
        return Math.max(0.0F, projectedCloudHeight)
                * RAIN_IMPACT_DEPTH * clamp01(leanSine);
    }

    /**
     * One impact's depth inside that oval. Its available depth narrows toward
     * either horizontal edge, so random endpoints fill an ellipse rather than
     * ending on a rectangular strip.
     */
    static float rainImpactOffset(
            float ovalDepth, float normalizedX, float depthNoise) {
        float x = Math.max(-1.0F, Math.min(1.0F, normalizedX));
        float verticalReach = Math.max(0.0F, ovalDepth)
                * (float)Math.sqrt(Math.max(0.0F, 1.0F - x * x));
        return (clamp01(depthNoise) * 2.0F - 1.0F) * verticalReach;
    }

    /**
     * Drop lanes needed to keep a constant screen-space density.
     * Artwork size never changes; only a larger visible rain footprint gains
     * more independently phased attachments.
     */
    static int rainDropCount(
            float projectedCloudWidth, float fallDistance, float thunder,
            float visibility) {
        float visible = clamp01(visibility);
        if (visible <= 0.0F) {
            return 0;
        }
        float storm = smoothstep(clamp01(thunder));
        int minimum = Math.round(mix(MIN_RAIN_DROPS_PER_CLOUD,
                MIN_THUNDER_DROPS_PER_CLOUD, storm));
        int maximum = Math.round(mix(MAX_RAIN_DROPS_PER_CLOUD,
                MAX_THUNDER_DROPS_PER_CLOUD, storm));
        float area = Math.max(0.0F, projectedCloudWidth)
                * Math.min(MAX_DENSITY_FALL_DISTANCE,
                Math.max(RAIN_FLAT_SCREEN_LENGTH, fallDistance));
        int density = (int)Math.ceil(area / RAIN_SCREEN_AREA_PER_DROP
                * mix(1.0F, 1.55F, storm));
        int fullCount = Math.max(minimum, Math.min(maximum, density));
        return Math.max(1, (int)Math.ceil(fullCount * visible));
    }

    /** Animated screen-facing rain emitted over one cloud's footprint. */
    private static void drawRain(
            Tessellator tessellator, int cellX, int cellY,
            double animationTime,
            float centerX, float impactCenterX, float topY,
            float cloudWidth, float impactWidth,
            float cloudHeight, float fallDistance,
            float leanSine, float precipitation, float thunder,
            float visibility,
            boolean splashPass) {
        int drops = rainDropCount(
                cloudWidth, fallDistance, thunder, visibility);
        // Keep vanilla's complete rain sheet at a fixed screen size. Its
        // origin, spread, opacity and fall distance remain attached to the
        // projected cloud; zooming the map must not resize the weather art.
        float baseWidth = rainScreenWidth(cloudWidth);
        float lean = clamp01(leanSine);
        float baseLength = rainScreenLength(cloudWidth, lean);
        float slant = mix(1.4F, 3.8F, lean);
        float splashMix = smoothstep(
                (lean - SPLASH_MIN_LEAN) / 0.12F);
        float readableVisibility = clamp01(visibility);
        for (int drop = 0; drop < drops; drop++) {
            float phase = rainPhase(cellX, cellY, drop, animationTime);
            float offset = cellNoise(
                    cellX, cellY, CLOUD_CHANNEL + 140 + drop);
            float normalizedX = (offset - 0.5F) * 2.0F;
            float topX = centerX
                    + normalizedX * cloudWidth * 0.41F;
            float impactX = impactCenterX
                    + normalizedX * impactWidth * 0.41F;
            float impactOffset = rainImpactOffset(
                    rainImpactDepth(cloudHeight, lean), normalizedX,
                    cellNoise(cellX, cellY,
                            CLOUD_CHANNEL + 260 + drop));
            float dropFallDistance = Math.max(
                    0.0F, fallDistance + impactOffset);
            int frame = RAIN_PARTICLE_FIRST_FRAME
                    + (int)(cellNoise(cellX, cellY,
                            CLOUD_CHANNEL + 180 + drop) * 4.0F) % 4;
            boolean splashing = splashMix > 0.0F
                    && phase >= RAIN_FALL_PHASE_END;
            if (splashPass) {
                if (!splashing) {
                    continue;
                }
                float splash = (phase - RAIN_FALL_PHASE_END)
                        / (1.0F - RAIN_FALL_PHASE_END);
                drawRainSplash(tessellator, impactX,
                        topY + dropFallDistance,
                        baseWidth, splash, splashMix,
                        precipitation * readableVisibility, frame);
                continue;
            }
            if (splashing) {
                continue;
            }
            float fallPhase = splashMix > 0.0F
                    ? Math.min(1.0F, phase / RAIN_FALL_PHASE_END) : phase;
            float edge = smoothstep(Math.min(1.0F, fallPhase * 7.0F));
            if (splashMix <= 0.0F) {
                edge *= smoothstep(Math.min(1.0F,
                        (1.0F - fallPhase) * 7.0F));
            }
            float alpha = RAIN_ALPHA * precipitation
                    * readableVisibility * edge;
            if (alpha <= 0.001F) {
                continue;
            }
            float y = topY + fallPhase * dropFallDistance;
            float remaining = Math.max(0.0F,
                    topY + dropFallDistance - y);
            float length = Math.min(baseLength, remaining);
            if (length <= 0.05F) {
                continue;
            }
            float width = baseWidth
                    * (1.0F + fallPhase * lean * 0.28F);
            float x = mix(topX, impactX, fallPhase);
            float endPhase = dropFallDistance <= 0.0F
                    ? fallPhase : Math.min(1.0F,
                            fallPhase + length / dropFallDistance);
            float bottomX = mix(topX, impactX, endPhase) - slant;
            tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, alpha);
            addRainTextureQuad(tessellator,
                    x, y, x + width, y,
                    bottomX + width, y + length,
                    bottomX, y + length);
        }
    }

    /** Two large vanilla droplets rebound outwards from the projected map surface. */
    private static void drawRainSplash(
            Tessellator tessellator, float x, float groundY,
            float dropWidth, float progress, float lean,
            float strength, int frame) {
        float clamped = clamp01(progress);
        float eased = smoothstep(clamped);
        float fade = 1.0F - smoothstep(
                Math.max(0.0F, (clamped - 0.58F) / 0.42F));
        float alpha = RAIN_ALPHA * strength * lean * fade;
        if (alpha <= 0.001F) {
            return;
        }
        float spread = 0.9F + eased * 4.4F;
        float rise = (0.7F + (float)Math.sin(Math.PI * eased) * 4.3F)
                * lean;
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, alpha);
        float particleSize = Math.max(2.8F,
                Math.min(3.6F, dropWidth * 0.62F));
        addVanillaParticleRect(tessellator, frame,
                x - spread - particleSize * 0.5F,
                groundY - rise - particleSize * 0.5F,
                particleSize, particleSize);
        addVanillaParticleRect(tessellator, frame,
                x + spread - particleSize * 0.5F,
                groundY - rise - particleSize * 0.5F,
                particleSize, particleSize);
    }

    /** Full vanilla environment-rain sheet, stretched along one falling drop. */
    private static void addRainTextureQuad(
            Tessellator tessellator,
            float x0, float y0, float x1, float y1,
            float x2, float y2, float x3, float y3) {
        tessellator.addVertexWithUV(x0, y0, 0.0D, 0.0D, 0.0D);
        tessellator.addVertexWithUV(x1, y1, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x2, y2, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x3, y3, 0.0D, 0.0D, 1.0D);
    }

    private static void addVanillaParticleRect(
            Tessellator tessellator, int frame,
            float x, float y, float width, float height) {
        addVanillaParticleQuad(tessellator, frame,
                x, y, x + width, y,
                x + width, y + height, x, y + height);
    }

    /** One frame from the same sixteen-column atlas used by EntityRainFX. */
    private static void addVanillaParticleQuad(
            Tessellator tessellator, int frame,
            float x0, float y0, float x1, float y1,
            float x2, float y2, float x3, float y3) {
        int column = frame % PARTICLE_ATLAS_COLUMNS;
        int row = frame / PARTICLE_ATLAS_COLUMNS;
        double uMin = column / (double)PARTICLE_ATLAS_COLUMNS;
        double uMax = (column + 1) / (double)PARTICLE_ATLAS_COLUMNS;
        double vMin = row / (double)PARTICLE_ATLAS_COLUMNS;
        double vMax = (row + 1) / (double)PARTICLE_ATLAS_COLUMNS;
        tessellator.addVertexWithUV(x0, y0, 0.0D, uMin, vMin);
        tessellator.addVertexWithUV(x1, y1, 0.0D, uMax, vMin);
        tessellator.addVertexWithUV(x2, y2, 0.0D, uMax, vMax);
        tessellator.addVertexWithUV(x3, y3, 0.0D, uMin, vMax);
    }

    /** Brief deterministic double flash for an eligible thunder cloud. */
    static float lightningFlash(
            int cellX, int cellY, double animationTime, float thunder) {
        float storm = clamp01(thunder);
        if (storm <= 0.0F || cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 220)
                > 0.08F + storm * 0.2F) {
            return 0.0F;
        }
        float period = mix(52.0F, 105.0F, cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 221));
        float offset = cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 222) * period;
        double cycles = (animationTime + offset) / period;
        float phase = (float)(cycles - Math.floor(cycles));
        float first = 1.0F - smoothstep(Math.min(1.0F, phase / 0.028F));
        float secondDistance = Math.abs(phase - 0.065F);
        float second = secondDistance >= 0.018F ? 0.0F
                : (1.0F - smoothstep(secondDistance / 0.018F)) * 0.62F;
        return Math.max(first, second) * smoothstep(storm);
    }

    /** A narrow crooked bolt ending in a small flash on the map surface. */
    private static void drawLightning(
            Tessellator tessellator, int cellX, int cellY,
            float centerX, float topY, float fallDistance,
            float cloudWidth, float flash) {
        float alpha = LIGHTNING_ALPHA * clamp01(flash);
        if (alpha <= 0.001F || fallDistance <= 0.0F) {
            return;
        }
        float startX = centerX + (cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 230) - 0.5F)
                * cloudWidth * 0.28F;
        float endX = startX + (cellNoise(
                cellX, cellY, CLOUD_CHANNEL + 231) - 0.5F)
                * cloudWidth * 0.22F;
        float lastX = startX;
        float lastY = topY;
        tessellator.setColorRGBA_F(0.86F, 0.92F, 1.0F, alpha);
        for (int segment = 1; segment <= LIGHTNING_SEGMENTS; segment++) {
            float progress = segment / (float)LIGHTNING_SEGMENTS;
            float nextX = mix(startX, endX, progress);
            if (segment < LIGHTNING_SEGMENTS) {
                nextX += (cellNoise(cellX, cellY,
                        CLOUD_CHANNEL + 232 + segment) - 0.5F)
                        * cloudWidth * 0.13F;
            }
            float nextY = topY + fallDistance * progress;
            addBoltSegment(tessellator,
                    lastX, lastY, nextX, nextY,
                    Math.max(0.65F, cloudWidth * 0.018F));
            lastX = nextX;
            lastY = nextY;
        }
        float flare = Math.max(2.0F, cloudWidth * 0.08F) * alpha;
        tessellator.setColorRGBA_F(0.9F, 0.95F, 1.0F, alpha * 0.55F);
        tessellator.addVertex(endX, lastY - flare, 0.0D);
        tessellator.addVertex(endX + flare, lastY, 0.0D);
        tessellator.addVertex(endX, lastY + flare, 0.0D);
        tessellator.addVertex(endX - flare, lastY, 0.0D);
    }

    private static void addBoltSegment(
            Tessellator tessellator,
            float fromX, float fromY, float toX, float toY,
            float thickness) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (!(length > 0.0F)) {
            return;
        }
        float nx = -dy / length * thickness * 0.5F;
        float ny = dx / length * thickness * 0.5F;
        tessellator.addVertex(fromX + nx, fromY + ny, 0.0D);
        tessellator.addVertex(toX + nx, toY + ny, 0.0D);
        tessellator.addVertex(toX - nx, toY - ny, 0.0D);
        tessellator.addVertex(fromX - nx, fromY - ny, 0.0D);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static float cloudShadowAlpha(float cloudAlpha, float lean) {
        float leanAmount = smoothstep(clamp01(lean));
        float weatherWeight = 0.72F + 0.28F
                * clamp01(cloudAlpha / THUNDER_CLOUD[3]);
        return (CLOUD_SHADOW_FLAT_ALPHA
                + CLOUD_SHADOW_LEAN_ALPHA * leanAmount) * weatherWeight;
    }

    /** One cloud-shaped shadow projected through the map's ground transform. */
    private static void drawCloudShadow(
            Tessellator tessellator, LostTalesLotrMapGui gui,
            float centerX, float centerY,
            float width, float height, int variant, float alpha) {
        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;
        projectShadowCorner(gui, centerX - halfWidth,
                centerY + halfHeight, 0);
        projectShadowCorner(gui, centerX + halfWidth,
                centerY + halfHeight, 2);
        projectShadowCorner(gui, centerX + halfWidth,
                centerY - halfHeight, 4);
        projectShadowCorner(gui, centerX - halfWidth,
                centerY - halfHeight, 6);
        double uMin = variant / (double)CLOUD_VARIANTS;
        double uMax = (variant + 1.0D) / CLOUD_VARIANTS;
        tessellator.setColorRGBA_F(0.0F, 0.0F, 0.0F, alpha);
        tessellator.addVertexWithUV(CLOUD_SHADOW_CORNERS[0],
                CLOUD_SHADOW_CORNERS[1], 0.0D, uMin, 1.0D);
        tessellator.addVertexWithUV(CLOUD_SHADOW_CORNERS[2],
                CLOUD_SHADOW_CORNERS[3], 0.0D, uMax, 1.0D);
        tessellator.addVertexWithUV(CLOUD_SHADOW_CORNERS[4],
                CLOUD_SHADOW_CORNERS[5], 0.0D, uMax, 0.0D);
        tessellator.addVertexWithUV(CLOUD_SHADOW_CORNERS[6],
                CLOUD_SHADOW_CORNERS[7], 0.0D, uMin, 0.0D);
    }

    private static void projectShadowCorner(
            LostTalesLotrMapGui gui, float x, float y, int output) {
        CLOUD_SHADOW_POINT[0] = x;
        CLOUD_SHADOW_POINT[1] = y;
        LostTalesLotrMapRotation.rotate(CLOUD_SHADOW_POINT, gui);
        CLOUD_SHADOW_CORNERS[output] = CLOUD_SHADOW_POINT[0];
        CLOUD_SHADOW_CORNERS[output + 1] = CLOUD_SHADOW_POINT[1];
    }

    /** One cloud, as its own frame of the sheet, facing the reader. */
    private static void drawCloud(
            Tessellator tessellator, float centerX, float centerY,
            float width, float height, int variant, float[] weather,
            float visibility) {
        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;
        double uMin = variant / (double)CLOUD_VARIANTS;
        double uMax = (variant + 1.0D) / CLOUD_VARIANTS;
        tessellator.setColorRGBA_F(
                weather[0], weather[1], weather[2],
                weather[3] * visibility);
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
     * Optical softness away from the lower-middle focus plane.
     * This is intentionally a translucent atmospheric veil rather than a
     * framebuffer blur: it keeps pixels and all later navigation text crisp.
     */
    static float focusSoftness(float lean, float screenShare) {
        float clampedLean = clamp01(lean);
        if (clampedLean <= 0.0F) {
            return 0.0F;
        }
        float share = clamp01(screenShare);
        float distance = Math.abs(share - 0.56F);
        float outsideFocus = (distance - 0.24F) / 0.32F;
        return MAX_FOCUS_VEIL_ALPHA * clampedLean
                * smoothstep(outsideFocus);
    }

    /**
     * Draws the haze over the far half of a leaning map.
     *
     * <p>Over ground ink and everything standing on it, and under the names
     * and markers the player navigates by. Those later layers stay as crisp at
     * the far edge as at the near one; hazing them too would be more faithful
     * and would cost the map its legs.</p>
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
            drawFocusVeil(tessellator, red, green, blue, lean,
                    viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
            drawHazeWisps(tessellator, red, green, blue, lean, worldTime,
                    viewportXMin, viewportXMax,
                    viewportYMin, centerY);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    /** Keeps a broad sharp focus plane and softly veils only the extremes. */
    private static void drawFocusVeil(
            Tessellator tessellator, float red, float green, float blue,
            float lean, int xMin, int xMax, int yMin, int yMax) {
        float height = yMax - yMin;
        if (!(height > 0.0F)) {
            return;
        }
        tessellator.startDrawingQuads();
        for (int band = 0; band < FOCUS_BANDS; band++) {
            float topShare = band / (float)FOCUS_BANDS;
            float bottomShare = (band + 1) / (float)FOCUS_BANDS;
            float top = yMin + height * topShare;
            float bottom = yMin + height * bottomShare;
            tessellator.setColorRGBA_F(red, green, blue,
                    focusSoftness(lean, bottomShare));
            tessellator.addVertex(xMin, bottom, 0.0D);
            tessellator.addVertex(xMax, bottom, 0.0D);
            tessellator.setColorRGBA_F(red, green, blue,
                    focusSoftness(lean, topShare));
            tessellator.addVertex(xMax, top, 0.0D);
            tessellator.addVertex(xMin, top, 0.0D);
        }
        tessellator.draw();
    }

    /** Slow, soft ellipses break the far haze into drifting depth layers. */
    private static void drawHazeWisps(
            Tessellator tessellator, float red, float green, float blue,
            float lean, long worldTime,
            int xMin, int xMax, int yMin, float centerY) {
        float width = xMax - xMin;
        float height = centerY - yMin;
        if (!(width > 0.0F) || !(height > 0.0F)) {
            return;
        }
        tessellator.startDrawing(GL11.GL_TRIANGLES);
        for (int wisp = 0; wisp < HAZE_WISPS; wisp++) {
            float phase = cellNoise(wisp, 17, CLOUD_CHANNEL + 300)
                    * (float)(Math.PI * 2.0D);
            float drift = (float)Math.sin(
                    worldTime * 0.00032D + phase) * width * 0.055F;
            float centerX = xMin + width * mix(0.08F, 0.92F,
                    cellNoise(wisp, 23, CLOUD_CHANNEL + 301)) + drift;
            float wispY = yMin + height * mix(0.10F, 0.88F,
                    cellNoise(wisp, 29, CLOUD_CHANNEL + 302));
            float radiusX = width * mix(0.075F, 0.17F,
                    cellNoise(wisp, 31, CLOUD_CHANNEL + 303));
            float radiusY = height * mix(0.045F, 0.12F,
                    cellNoise(wisp, 37, CLOUD_CHANNEL + 304));
            float alpha = MAX_WISP_ALPHA * clamp01(lean)
                    * mix(0.55F, 1.0F,
                    cellNoise(wisp, 41, CLOUD_CHANNEL + 305));
            for (int segment = 0;
                 segment < HAZE_WISP_SEGMENTS; segment++) {
                double angle0 = Math.PI * 2.0D
                        * segment / HAZE_WISP_SEGMENTS;
                double angle1 = Math.PI * 2.0D
                        * (segment + 1) / HAZE_WISP_SEGMENTS;
                tessellator.setColorRGBA_F(red, green, blue, alpha);
                tessellator.addVertex(centerX, wispY, 0.0D);
                tessellator.setColorRGBA_F(red, green, blue, 0.0F);
                tessellator.addVertex(
                        centerX + Math.cos(angle0) * radiusX,
                        wispY + Math.sin(angle0) * radiusY, 0.0D);
                tessellator.addVertex(
                        centerX + Math.cos(angle1) * radiusX,
                        wispY + Math.sin(angle1) * radiusY, 0.0D);
            }
        }
        tessellator.draw();
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

    /** A low-alpha sky-coloured wash, strongest towards its light source. */
    private static void drawDirectionalLight(
            float[] light, int xMin, int xMax, int yMin, int yMax) {
        float side = light[4];
        float left = light[3] * (side < 0.0F ? 1.0F
                : side > 0.0F ? 0.18F : 0.72F);
        float right = light[3] * (side > 0.0F ? 1.0F
                : side < 0.0F ? 0.18F : 0.72F);
        float bottomFactor = side == 0.0F ? 0.30F : 0.58F;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(light[0], light[1], light[2],
                left * bottomFactor);
        tessellator.addVertex(xMin, yMax, 0.0D);
        tessellator.setColorRGBA_F(light[0], light[1], light[2],
                right * bottomFactor);
        tessellator.addVertex(xMax, yMax, 0.0D);
        tessellator.setColorRGBA_F(light[0], light[1], light[2], right);
        tessellator.addVertex(xMax, yMin, 0.0D);
        tessellator.setColorRGBA_F(light[0], light[1], light[2], left);
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
