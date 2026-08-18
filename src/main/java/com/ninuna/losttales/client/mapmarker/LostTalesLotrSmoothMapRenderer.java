package com.ninuna.losttales.client.mapmarker;

import java.lang.reflect.Field;
import lotr.client.LOTRTextures;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRConfig;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draws the LOTR world texture without its integer-clipped edge jumps. */
final class LostTalesLotrSmoothMapRenderer {
    private static final float[] SHEET_COVERAGE = new float[2];
    /** One generated texel covers one native map-image pixel. */
    static final int NOISE_TILE_SIZE = 256;
    /** Comparable to LOTR's paper wash, but quiet enough to preserve colour. */
    static final float NOISE_OPACITY = 0.10F;

    private static Field posXField;
    private static Field posYField;
    private static Field zoomScaleField;
    private static Field mapWidthField;
    private static Field mapHeightField;
    private static Field mapXMinField;
    private static Field mapXMaxField;
    private static Field mapYMinField;
    private static Field mapYMaxField;
    private static Field mapXMinWorldField;
    private static Field mapXMaxWorldField;
    private static Field mapYMinWorldField;
    private static Field mapYMaxWorldField;
    private static Field mapTextureField;
    private static Field sepiaMapTextureField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;
    private static DynamicTexture proceduralNoiseTexture;
    private static ResourceLocation proceduralNoiseLocation;
    private static TextureManager proceduralNoiseOwner;
    private static boolean proceduralNoiseUnavailable;

    private LostTalesLotrSmoothMapRenderer() {}

    static boolean render(LOTRGuiMap gui, boolean sepia,
                          float alpha, boolean drawOverlay) {
        if (gui == null || !ensureReflection()) {
            return false;
        }
        try {
            float scale = zoomScaleField.getFloat(gui);
            int viewportWidth = mapWidthField.getInt(null);
            int viewportHeight = mapHeightField.getInt(null);
            int viewportXMin = mapXMinField.getInt(null);
            int viewportXMax = mapXMaxField.getInt(null);
            int viewportYMin = mapYMinField.getInt(null);
            int viewportYMax = mapYMaxField.getInt(null);
            float posX = posXField.getFloat(gui);
            float posY = posYField.getFloat(gui);
            float degrees = LostTalesLotrMapRotation.degreesOf(gui);
            // A turned viewport looks at ground beyond its own corners, so the
            // image is sampled and drawn over the box that covers it and the
            // rotation is applied to the quad afterwards. The box is kept in
            // fractions of a pixel: rounding it moved the whole map image a
            // pixel at a time while the markers on top moved smoothly, which
            // is most of what made turning look unsteady.
            LostTalesLotrMapRotation.rotatedCoverage(
                    viewportWidth, viewportHeight, degrees,
                    LostTalesLotrMapRotation.leanOf(gui), SHEET_COVERAGE);
            float mapXMin = viewportXMin
                    - (SHEET_COVERAGE[0] - viewportWidth) * 0.5F;
            float mapXMax = mapXMin + SHEET_COVERAGE[0];
            float mapYMin = viewportYMin
                    - (SHEET_COVERAGE[1] - viewportHeight) * 0.5F;
            float mapYMax = mapYMin + SHEET_COVERAGE[1];
            Clip clip = calculateClip(
                    posX, posY, scale,
                    SHEET_COVERAGE[0], SHEET_COVERAGE[1],
                    LOTRGenLayerWorld.imageWidth,
                    LOTRGenLayerWorld.imageHeight,
                    mapXMin, mapXMax, mapYMin, mapYMax);
            if (clip == null) {
                return false;
            }

            // One matrix for the whole sheet: the image, the fine noise
            // over it and the region names written on it all go through the
            // same one, so nothing on the paper can come loose from it.
            boolean sheeted =
                    LostTalesLotrMapRotation.pushSheetTransform(gui);
            beginSheetClipping(viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax, sheeted);
            try {
                drawMapImage(sepia, alpha, drawOverlay,
                        posX, posY, scale, clip,
                        mapXMin, mapXMax, mapYMin, mapYMax,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
                if (shouldDrawOpaqueBackground(alpha)) {
                    LostTalesMapTerrainRenderer.render(
                            gui, sepia || LOTRConfig.osrsMap,
                            scale, posX, posY,
                            mapXMin, mapXMax, mapYMin, mapYMax,
                            viewportXMin, viewportXMax,
                            viewportYMin, viewportYMax);
                }
            } finally {
                endSheetClipping(sheeted);
            }
            // Straight onto the ground, and only on the pass that draws it:
            // the faction overlay comes through here a second time with its
            // own alpha, and shading the map twice would double it.
            if (shouldDrawOpaqueBackground(alpha)) {
                renderAtmosphere(gui, posX, posY, scale,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax, !sepia);
            }
            return true;
        } catch (Throwable ignored) {
            reflectionFailed = true;
            reflectionReady = false;
            return false;
        }
    }

    /**
     * The hour and the clouds, on the Lost Tales map only.
     *
     * <p>LOTR's own windowed map and its menu background are left exactly as
     * the base mod draws them.</p>
     */
    private static void renderAtmosphere(
            LOTRGuiMap gui, float posX, float posY, float scale,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax,
            boolean drawWeatherLayers) {
        if (!(gui instanceof LostTalesLotrMapGui)
                || !LostTalesLotrMapLayout.isFullscreenLayoutActive(gui)) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }
        float rain = minecraft.theWorld.getRainStrength(1.0F);
        float thunder = minecraft.theWorld.getWeightedThunderStrength(1.0F);
        // Some 1.7.10 dimension providers report a zero interpolation value
        // on the first rainy frames even though the world's weather flag is
        // already active. The map should answer the actual state immediately.
        if (minecraft.theWorld.isRaining()) {
            rain = Math.max(rain, 0.4F);
        }
        if (minecraft.theWorld.isThundering()) {
            thunder = Math.max(thunder, 0.4F);
        }
        long worldTime = minecraft.theWorld.getWorldTime();
        LostTalesLotrMapAtmosphere.render(
                (LostTalesLotrMapGui)gui, worldTime, rain, thunder,
                posX, posY, scale,
                viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        // Lighting is independent of the chosen LOTR map palette. Keeping
        // this return after the light pass prevents sepia mode from silently
        // freezing at a flat shade while retaining its established lack of
        // decorative weather layers.
        if (!drawWeatherLayers) {
            return;
        }
        LostTalesLotrMapAtmosphere.renderCloudShadows(
                (LostTalesLotrMapGui)gui, worldTime, rain, thunder,
                posX, posY, scale,
                viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        // Road dots are ink on the ground. Drawing their unchanged native
        // pass here puts standing scenery and weather in front; their names
        // are deferred to LOTR's later label position and remain readable.
        ((LostTalesLotrMapGui)gui).renderRoadsBelowClouds();
        // Decorations stand above that road ink. Labels and markers are all
        // drawn later in LOTR's own order.
        LostTalesMapDecorationRenderer.render(
                (LostTalesLotrMapGui)gui,
                minecraft.theWorld.getTotalWorldTime(),
                posX, posY, scale,
                viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        // Last of the ground layers: haze lies over the country and the things
        // standing on it.
        LostTalesLotrMapAtmosphere.renderDistanceHaze(
                (LostTalesLotrMapGui)gui, worldTime, rain, thunder,
                viewportXMin, viewportXMax, viewportYMin, viewportYMax);
        // Rain and clouds are above the standing artwork, but remain
        // translucent and below labels, markers and controls drawn after this
        // map-image pass.
        LostTalesLotrMapAtmosphere.renderClouds(
                (LostTalesLotrMapGui)gui, worldTime, rain, thunder,
                posX, posY, scale,
                viewportXMin, viewportXMax, viewportYMin, viewportYMax);
    }

    /**
     * Keeps the sheet inside its own frame, and out of the depth buffer.
     *
     * <p>A turned or leaning sheet is drawn over a quad larger than the
     * viewport, so a windowed map would otherwise spill past its border.</p>
     *
     * <p>Depth is switched off for the same reason the icon passes switch it
     * off. The screen is drawn back to front by the order the calls are made
     * in, and the GUI's depth buffer is shared with whatever the HUD left in
     * it, so a depth test here can only reject layers the order already
     * placed correctly. The close terrain temporarily enables and clears its
     * own depth inside this bracket; all ordinary sheet and UI passes remain
     * explicitly order-driven. Both bits are saved and given back by the
     * surrounding {@code glPopAttrib}.</p>
     */
    private static void beginSheetClipping(
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax, boolean sheeted) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!sheeted || minecraft == null) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        int scaleFactor = Math.max(1, resolution.getScaleFactor());
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                viewportXMin * scaleFactor,
                (resolution.getScaledHeight() - viewportYMax) * scaleFactor,
                Math.max(0, viewportXMax - viewportXMin) * scaleFactor,
                Math.max(0, viewportYMax - viewportYMin) * scaleFactor);
    }

    private static void endSheetClipping(boolean sheeted) {
        if (!sheeted) {
            return;
        }
        if (Minecraft.getMinecraft() != null) {
            GL11.glPopAttrib();
        }
        GL11.glPopMatrix();
    }

    private static void drawMapImage(
            boolean sepia, float alpha, boolean drawOverlay,
            float posX, float posY, float scale, Clip clip,
            float mapXMin, float mapXMax, float mapYMin, float mapYMax,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax)
            throws IllegalAccessException {
        // A fixed backing quad replaces the four dynamically rounded edge
        // strips on the opaque base pass. Never repeat it on LOTR's
        // translucent faction-overlay pass: an opaque quad there would
        // cover the red control-zone geometry drawn between the passes.
        if (shouldDrawOpaqueBackground(alpha)) {
            Gui.drawRect((int)Math.floor(mapXMin),
                    (int)Math.floor(mapYMin),
                    (int)Math.ceil(mapXMax), (int)Math.ceil(mapYMax),
                    LOTRTextures.getMapOceanColor(sepia));
        }
        updateCompatibilityBounds(clip);

        Minecraft minecraft = Minecraft.getMinecraft();
        ResourceLocation mapTexture = (ResourceLocation)(
                (LOTRConfig.osrsMap || sepia)
                        ? sepiaMapTextureField.get(null)
                        : mapTextureField.get(null));
        int oldMinFilter = GL11.GL_NEAREST;
        int oldMagFilter = GL11.GL_NEAREST;
        boolean filterChanged = minecraft != null && mapTexture != null;
        if (filterChanged) {
            minecraft.getTextureManager().bindTexture(mapTexture);
            oldMinFilter = GL11.glGetTexParameteri(
                    GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            oldMagFilter = GL11.glGetTexParameteri(
                    GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
        try {
            LOTRTextures.drawMap(
                    minecraft == null ? null : minecraft.thePlayer,
                    sepia,
                    clip.drawnXMin, clip.drawnXMax,
                    clip.drawnYMin, clip.drawnYMax,
                    0.0D,
                    clip.uMin, clip.uMax,
                    clip.vMin, clip.vMax, alpha);
        } finally {
            if (filterChanged) {
                minecraft.getTextureManager().bindTexture(mapTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, oldMinFilter);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, oldMagFilter);
            }
        }
        if (drawOverlay && !LOTRConfig.osrsMap) {
            drawProceduralNoise(minecraft, posX, posY, scale,
                    mapXMin, mapXMax, mapYMin, mapYMax);
        }
    }

    /**
     * Fine deterministic colour variation over the otherwise flat map fills.
     *
     * <p>The small texture is generated once from several seamless fields of
     * different sizes. Broad cloudy value changes keep it organic while a
     * little fine variation retains the pixel-art finish. It repeats in
     * native map-image coordinates, so zoom changes the grain with the map
     * and no stretched 256-pixel image edge can appear.</p>
     */
    private static void drawProceduralNoise(
            Minecraft minecraft,
            float posX, float posY, float scale,
            float mapXMin, float mapXMax,
            float mapYMin, float mapYMax) {
        if (minecraft == null || proceduralNoiseUnavailable
                || !(scale > 0.0F)
                || !(mapXMax > mapXMin)
                || !(mapYMax > mapYMin)) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            ResourceLocation noise = ensureProceduralNoise(minecraft);
            if (noise == null) {
                return;
            }
            minecraft.getTextureManager().bindTexture(noise);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            // Average fine noise while zoomed out, but retain authored pixel
            // edges once a map texel is large enough to inspect.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            // GUI rendering commonly leaves the legacy alpha test enabled.
            // Its default cutoff rejected most of the deliberately subtle
            // flecks before blending, making the generated texture vanish.
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, NOISE_OPACITY);
            double halfMapWidth = (mapXMax - mapXMin) / scale * 0.5D;
            double halfMapHeight = (mapYMax - mapYMin) / scale * 0.5D;
            double uMin = noiseTextureCoordinate(posX - halfMapWidth);
            double uMax = noiseTextureCoordinate(posX + halfMapWidth);
            double vMin = noiseTextureCoordinate(posY - halfMapHeight);
            double vMax = noiseTextureCoordinate(posY + halfMapHeight);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(mapXMin, mapYMax,
                    0.0D, uMin, vMax);
            tessellator.addVertexWithUV(mapXMax, mapYMax,
                    0.0D, uMax, vMax);
            tessellator.addVertexWithUV(mapXMax, mapYMin,
                    0.0D, uMax, vMin);
            tessellator.addVertexWithUV(mapXMin, mapYMin,
                    0.0D, uMin, vMin);
            tessellator.draw();
        } catch (Throwable ignored) {
            // The map is still fully usable without cosmetic noise. Do not
            // let a driver-specific dynamic-texture failure disable its
            // background renderer or retry the allocation every frame.
            proceduralNoiseUnavailable = true;
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private static ResourceLocation ensureProceduralNoise(
            Minecraft minecraft) {
        TextureManager manager = minecraft == null
                ? null : minecraft.getTextureManager();
        if (manager == null) {
            return null;
        }
        if (proceduralNoiseTexture != null
                && proceduralNoiseLocation != null
                && proceduralNoiseOwner == manager) {
            return proceduralNoiseLocation;
        }
        DynamicTexture texture = new DynamicTexture(
                NOISE_TILE_SIZE, NOISE_TILE_SIZE);
        fillProceduralNoise(texture.getTextureData(),
                NOISE_TILE_SIZE, NOISE_TILE_SIZE);
        texture.updateDynamicTexture();
        proceduralNoiseLocation = manager.getDynamicTextureLocation(
                "losttales_map_noise", texture);
        proceduralNoiseTexture = texture;
        proceduralNoiseOwner = manager;
        return proceduralNoiseLocation;
    }

    static void fillProceduralNoise(
            int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0
                || pixels.length < width * height) {
            throw new IllegalArgumentException(
                    "Procedural map noise buffer is too small");
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = proceduralNoisePixel(
                        x, y, width, height);
            }
        }
    }

    /** Stable seamless cloudy grain assembled without a source texture. */
    static int proceduralNoisePixel(int x, int y) {
        return proceduralNoisePixel(
                x, y, NOISE_TILE_SIZE, NOISE_TILE_SIZE);
    }

    private static int proceduralNoisePixel(
            int x, int y, int width, int height) {
        float value = periodicValueNoise(x, y, width, height, 64, 11)
                * 0.48F
                + periodicValueNoise(x, y, width, height, 32, 29) * 0.28F
                + periodicValueNoise(x, y, width, height, 16, 47) * 0.16F
                + periodicValueNoise(x, y, width, height, 8, 71) * 0.08F;
        int fineHash = noiseHash(
                positiveModulo(x, width),
                positiveModulo(y, height), 101);
        float fine = ((fineHash >>> 8) & 255) / 255.0F - 0.5F;
        int gray = Math.round(158.0F + (value - 0.5F) * 178.0F
                + fine * 5.0F);
        gray = Math.max(72, Math.min(224, gray));
        // A few values per small range read as authored pixel texture rather
        // than full-colour photographic noise when inspected up close.
        gray = gray / 3 * 3;
        return 0xFF000000 | gray << 16 | gray << 8 | gray;
    }

    private static float periodicValueNoise(
            int x, int y, int width, int height,
            int period, int seed) {
        int cellsX = Math.max(1, width / period);
        int cellsY = Math.max(1, height / period);
        float gridX = x / (float)period;
        float gridY = y / (float)period;
        int cellX = (int)Math.floor(gridX);
        int cellY = (int)Math.floor(gridY);
        float blendX = smoothstep(gridX - cellX);
        float blendY = smoothstep(gridY - cellY);
        float northWest = latticeValue(
                cellX, cellY, cellsX, cellsY, seed);
        float northEast = latticeValue(
                cellX + 1, cellY, cellsX, cellsY, seed);
        float southWest = latticeValue(
                cellX, cellY + 1, cellsX, cellsY, seed);
        float southEast = latticeValue(
                cellX + 1, cellY + 1, cellsX, cellsY, seed);
        float north = mix(northWest, northEast, blendX);
        float south = mix(southWest, southEast, blendX);
        return mix(north, south, blendY);
    }

    private static float latticeValue(
            int x, int y, int cellsX, int cellsY, int seed) {
        int hash = noiseHash(
                positiveModulo(x, cellsX),
                positiveModulo(y, cellsY), seed);
        return (hash & 65535) / 65535.0F;
    }

    private static int noiseHash(int x, int y, int seed) {
        int hash = x * 0x1F1F1F1F ^ y * 0x5F356495
                ^ seed * 0x6D2B79F5;
        hash ^= hash >>> 15;
        hash *= 0x2C1B3C6D;
        hash ^= hash >>> 12;
        hash *= 0x297A2D39;
        hash ^= hash >>> 15;
        return hash;
    }

    private static int positiveModulo(int value, int modulus) {
        int remainder = value % modulus;
        return remainder < 0 ? remainder + modulus : remainder;
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float mix(float first, float second, float amount) {
        return first + (second - first) * amount;
    }

    static double noiseTextureCoordinate(double mapImageCoordinate) {
        return mapImageCoordinate / NOISE_TILE_SIZE;
    }

    static Clip calculateClip(
            float posX, float posY, float scale,
            float mapWidth, float mapHeight,
            int imageWidth, int imageHeight,
            float mapXMin, float mapXMax,
            float mapYMin, float mapYMax) {
        if (!(scale > 0.0F) || imageWidth <= 0 || imageHeight <= 0
                || !(mapWidth > 0.0F) || !(mapHeight > 0.0F)) {
            return null;
        }
        double uMin = (posX - mapWidth / scale / 2.0D) / imageWidth;
        double uMax = (posX + mapWidth / scale / 2.0D) / imageWidth;
        double vMin = (posY - mapHeight / scale / 2.0D) / imageHeight;
        double vMax = (posY + mapHeight / scale / 2.0D) / imageHeight;
        double drawnXMin = mapXMin;
        double drawnXMax = mapXMax;
        double drawnYMin = mapYMin;
        double drawnYMax = mapYMax;

        if (uMin < 0.0D) {
            drawnXMin += -uMin * imageWidth * scale;
            uMin = 0.0D;
        }
        if (uMax > 1.0D) {
            drawnXMax -= (uMax - 1.0D) * imageWidth * scale;
            uMax = 1.0D;
        }
        if (vMin < 0.0D) {
            drawnYMin += -vMin * imageHeight * scale;
            vMin = 0.0D;
        }
        if (vMax > 1.0D) {
            drawnYMax -= (vMax - 1.0D) * imageHeight * scale;
            vMax = 1.0D;
        }
        drawnXMin = clamp(drawnXMin, mapXMin, mapXMax);
        drawnXMax = clamp(drawnXMax, mapXMin, mapXMax);
        drawnYMin = clamp(drawnYMin, mapYMin, mapYMax);
        drawnYMax = clamp(drawnYMax, mapYMin, mapYMax);
        uMin = clamp(uMin, 0.0D, 1.0D);
        uMax = clamp(uMax, 0.0D, 1.0D);
        vMin = clamp(vMin, 0.0D, 1.0D);
        vMax = clamp(vMax, 0.0D, 1.0D);
        return new Clip(
                drawnXMin, drawnXMax, drawnYMin, drawnYMax,
                uMin, uMax, vMin, vMax);
    }

    static boolean shouldDrawOpaqueBackground(float alpha) {
        return alpha >= 0.999F;
    }

    private static double clamp(double value, double minimum,
                                double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void updateCompatibilityBounds(Clip clip)
            throws IllegalAccessException {
        mapXMinWorldField.setInt(null, (int)Math.floor(clip.drawnXMin));
        mapXMaxWorldField.setInt(null, (int)Math.ceil(clip.drawnXMax));
        mapYMinWorldField.setInt(null, (int)Math.floor(clip.drawnYMin));
        mapYMaxWorldField.setInt(null, (int)Math.ceil(clip.drawnYMax));
    }

    private static synchronized boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            posXField = field(LOTRGuiMap.class, "posX");
            posYField = field(LOTRGuiMap.class, "posY");
            zoomScaleField = field(LOTRGuiMap.class, "zoomScale");
            mapWidthField = field(LOTRGuiMap.class, "mapWidth");
            mapHeightField = field(LOTRGuiMap.class, "mapHeight");
            mapXMinField = field(LOTRGuiMap.class, "mapXMin");
            mapXMaxField = field(LOTRGuiMap.class, "mapXMax");
            mapYMinField = field(LOTRGuiMap.class, "mapYMin");
            mapYMaxField = field(LOTRGuiMap.class, "mapYMax");
            mapXMinWorldField = field(LOTRGuiMap.class, "mapXMin_W");
            mapXMaxWorldField = field(LOTRGuiMap.class, "mapXMax_W");
            mapYMinWorldField = field(LOTRGuiMap.class, "mapYMin_W");
            mapYMaxWorldField = field(LOTRGuiMap.class, "mapYMax_W");
            mapTextureField = field(LOTRTextures.class, "mapTexture");
            sepiaMapTextureField = field(
                    LOTRTextures.class, "sepiaMapTexture");
            reflectionReady = true;
            return true;
        } catch (Throwable ignored) {
            reflectionFailed = true;
            return false;
        }
    }

    private static Field field(Class<?> owner, String name)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static final class Clip {
        final double drawnXMin;
        final double drawnXMax;
        final double drawnYMin;
        final double drawnYMax;
        final double uMin;
        final double uMax;
        final double vMin;
        final double vMax;

        private Clip(double drawnXMin, double drawnXMax,
                     double drawnYMin, double drawnYMax,
                     double uMin, double uMax,
                     double vMin, double vMax) {
            this.drawnXMin = drawnXMin;
            this.drawnXMax = drawnXMax;
            this.drawnYMin = drawnYMin;
            this.drawnYMax = drawnYMax;
            this.uMin = uMin;
            this.uMax = uMax;
            this.vMin = vMin;
            this.vMax = vMax;
        }
    }
}
