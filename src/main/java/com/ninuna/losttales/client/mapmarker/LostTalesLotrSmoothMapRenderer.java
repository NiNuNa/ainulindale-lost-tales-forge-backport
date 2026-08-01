package com.ninuna.losttales.client.mapmarker;

import java.lang.reflect.Field;
import lotr.client.LOTRTextures;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRConfig;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draws the LOTR world texture without its integer-clipped edge jumps. */
final class LostTalesLotrSmoothMapRenderer {
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
            int mapWidth = mapWidthField.getInt(null);
            int mapHeight = mapHeightField.getInt(null);
            int mapXMin = mapXMinField.getInt(null);
            int mapXMax = mapXMaxField.getInt(null);
            int mapYMin = mapYMinField.getInt(null);
            int mapYMax = mapYMaxField.getInt(null);
            Clip clip = calculateClip(
                    posXField.getFloat(gui), posYField.getFloat(gui),
                    scale, mapWidth, mapHeight,
                    LOTRGenLayerWorld.imageWidth,
                    LOTRGenLayerWorld.imageHeight,
                    mapXMin, mapXMax, mapYMin, mapYMax);
            if (clip == null) {
                return false;
            }

            // A fixed backing quad replaces the four dynamically rounded edge
            // strips on the opaque base pass. Never repeat it on LOTR's
            // translucent faction-overlay pass: an opaque quad there would
            // cover the red control-zone geometry drawn between the passes.
            if (shouldDrawOpaqueBackground(alpha)) {
                Gui.drawRect(mapXMin, mapYMin, mapXMax, mapYMax,
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
            boolean filterChanged = minecraft != null
                    && mapTexture != null;
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
                LOTRTextures.drawMapOverlay(
                        minecraft == null ? null : minecraft.thePlayer,
                        mapXMin, mapXMax, mapYMin, mapYMax, 0.0D,
                        clip.uMin, clip.uMax, clip.vMin, clip.vMax);
            }
            return true;
        } catch (Throwable ignored) {
            reflectionFailed = true;
            reflectionReady = false;
            return false;
        }
    }

    static Clip calculateClip(
            float posX, float posY, float scale,
            int mapWidth, int mapHeight,
            int imageWidth, int imageHeight,
            int mapXMin, int mapXMax,
            int mapYMin, int mapYMax) {
        if (!(scale > 0.0F) || imageWidth <= 0 || imageHeight <= 0
                || mapWidth <= 0 || mapHeight <= 0) {
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
