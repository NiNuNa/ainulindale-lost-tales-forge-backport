package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * Draws a map-marker glyph from the shared marker atlas in a flat colour,
 * with the same silhouette shadow every other chat glyph uses. Only the
 * glyph's opaque artwork is taken from its 17-pixel atlas cell, using the
 * art bounds the icon enum already records. Inline it is fitted into a
 * square box by its larger edge, always uniformly, so a tall glyph stays
 * tall; on buttons it is drawn at its native pixel size.
 */
final class ChatMapMarkerRenderer {
    private ChatMapMarkerRenderer() {}

    static void draw(Minecraft minecraft, String iconName,
                     float boxX, float boxY, float size, int rgb, int alpha) {
        LostTalesCompassMarkerIcon icon =
                LostTalesCompassMarkerIcon.fromName(iconName);
        float scale = fitScale(icon, size);
        drawArt(minecraft, icon, boxX, boxY, size, scale, rgb, alpha);
    }

    static void drawShadow(Minecraft minecraft, String iconName,
                           float boxX, float boxY, float size,
                           int shadowRgb, int alpha) {
        LostTalesCompassMarkerIcon icon =
                LostTalesCompassMarkerIcon.fromName(iconName);
        LostTalesSilhouetteRenderState.begin(shadowRgb);
        try {
            drawArt(minecraft, icon, boxX, boxY, size, fitScale(icon, size),
                    0xFFFFFF, alpha);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    private static float artWidth(LostTalesCompassMarkerIcon icon) {
        return icon.getArtRight() - icon.getArtLeft();
    }

    private static float artHeight(LostTalesCompassMarkerIcon icon) {
        return icon.getArtBottom() - icon.getArtTop();
    }

    private static float fitScale(LostTalesCompassMarkerIcon icon, float size) {
        float largest = Math.max(artWidth(icon), artHeight(icon));
        return largest <= 0.0F ? 0.0F : size / largest;
    }

    /**
     * Draws the artwork at {@code scale}, centred in a {@code box}-sized
     * square at ({@code boxX}, {@code boxY}) — for native drawing the box
     * is the artwork's own larger edge, so it lands exactly on its centre.
     */
    private static void drawArt(Minecraft minecraft,
                                LostTalesCompassMarkerIcon icon,
                                float boxX, float boxY, float box, float scale,
                                int rgb, int alpha) {
        if (minecraft == null || icon == null || scale <= 0.0F
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float width = artWidth(icon) * scale;
        float height = artHeight(icon) * scale;
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        float x = boxX + (box - width) / 2.0F;
        float y = boxY + (box - height) / 2.0F;
        minecraft.getTextureManager().bindTexture(
                LostTalesCompassMarkerIcon.TEXTURE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                MathHelper.clamp_float(alpha / 255.0F, 0.0F, 1.0F));
        try {
            float u0 = (icon.getU() + icon.getArtLeft())
                    / (float)LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
            float u1 = (icon.getU() + icon.getArtRight())
                    / (float)LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
            float v0 = (icon.getV() + icon.getArtTop())
                    / (float)LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;
            float v1 = (icon.getV() + icon.getArtBottom())
                    / (float)LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
            tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
