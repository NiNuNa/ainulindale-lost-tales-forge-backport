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
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Draws the LOTR world texture without its integer-clipped edge jumps. */
final class LostTalesLotrSmoothMapRenderer {
    private static final float[] SHEET_COVERAGE = new float[2];
    /** LOTR's own weight for the grain, so the map's tone is unchanged. */
    private static final float GRAIN_ALPHA = 0.2F;

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

            // One matrix for the whole sheet: the image, the paper grain
            // over it and the region names written on it all go through the
            // same one, so nothing on the paper can come loose from it.
            boolean sheeted =
                    LostTalesLotrMapRotation.pushSheetTransform(gui);
            beginSheetClipping(viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax, sheeted);
            try {
                drawMapImage(sepia, alpha, drawOverlay, clip,
                        mapXMin, mapXMax, mapYMin, mapYMax,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
            } finally {
                endSheetClipping(sheeted);
            }
            // Straight onto the ground, and only on the pass that draws it:
            // the faction overlay comes through here a second time with its
            // own alpha, and shading the map twice would double it.
            if (shouldDrawOpaqueBackground(alpha) && !sepia) {
                renderAtmosphere(gui, posX, posY, scale,
                        viewportXMin, viewportXMax,
                        viewportYMin, viewportYMax);
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
            int viewportYMin, int viewportYMax) {
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
     * placed correctly. Leaning makes that concrete: the lean divides through
     * a {@code w} that varies down the screen, and any pass drawn at a
     * non-zero {@code zLevel} under it is divided too, so its depth stops
     * being one flat value and starts crossing whatever is already in the
     * buffer part of the way across the map. Both bits are saved and given
     * back by the surrounding {@code glPopAttrib}.</p>
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
            boolean sepia, float alpha, boolean drawOverlay, Clip clip,
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
            drawPaperGrain(minecraft, viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
        }
    }

    /**
     * The paper grain the ground is printed on.
     *
     * <p>LOTR's own routine takes texture coordinates and ignores them: it
     * stretches one copy of the grain across whatever rectangle it is given.
     * So the rectangle is the whole of it, and the rectangle it used to be
     * given was the sheet — which is cut to the viewport's diagonal so it can
     * be turned, and widened again so it can lean. The single copy grew and
     * shrank with the sheet, and that is what made the grain smear as the map
     * tipped.</p>
     *
     * <p>So the quad is cut to the size the sheet reaches at full lean — one
     * size, whatever angle the map is at — and the texture is repeated across
     * it at exactly the density LOTR uses: one copy per viewport. Laid flat
     * and square, the screen shows the middle copy and nothing else, which is
     * the map the base mod draws; turned or leaned, it reaches into the
     * neighbours rather than stretching the one.</p>
     *
     * <p>The repeat is mirrored. The grain is not a tiling texture and butting
     * copies against each other drew its edges as a grid across the map;
     * mirroring makes every join match its neighbour exactly, which for a
     * noise this fine is the difference between a seam and no seam.</p>
     */
    private static void drawPaperGrain(
            Minecraft minecraft, int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        float viewportWidth = viewportXMax - viewportXMin;
        float viewportHeight = viewportYMax - viewportYMin;
        if (minecraft == null || LOTRTextures.overlayTexture == null
                || !(viewportWidth > 0.0F) || !(viewportHeight > 0.0F)) {
            return;
        }
        float coverage = LostTalesLotrMapRotation.maxCoverage(
                viewportWidth, viewportHeight);
        float centerX = (viewportXMin + viewportXMax) * 0.5F;
        float centerY = (viewportYMin + viewportYMax) * 0.5F;
        float half = coverage * 0.5F;
        double halfU = coverage / viewportWidth / 2.0D;
        double halfV = coverage / viewportHeight / 2.0D;

        minecraft.getTextureManager().bindTexture(
                LOTRTextures.overlayTexture);
        int oldWrapS = GL11.glGetTexParameteri(
                GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
        int oldWrapT = GL11.glGetTexParameteri(
                GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, GRAIN_ALPHA);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(centerX - half, centerY + half,
                    0.0D, 0.5D - halfU, 0.5D + halfV);
            tessellator.addVertexWithUV(centerX + half, centerY + half,
                    0.0D, 0.5D + halfU, 0.5D + halfV);
            tessellator.addVertexWithUV(centerX + half, centerY - half,
                    0.0D, 0.5D + halfU, 0.5D - halfV);
            tessellator.addVertexWithUV(centerX - half, centerY - half,
                    0.0D, 0.5D - halfU, 0.5D - halfV);
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_BLEND);
            // Given straight back: this texture is shared, and a repeating
            // wrap left behind bleeds wherever it is drawn next.
            minecraft.getTextureManager().bindTexture(
                    LOTRTextures.overlayTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S, oldWrapS);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T, oldWrapT);
        }
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
