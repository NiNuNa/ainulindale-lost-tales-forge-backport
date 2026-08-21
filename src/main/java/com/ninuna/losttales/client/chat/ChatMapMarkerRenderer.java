package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * Draws a map-marker glyph from the shared marker atlas at chat scale, in
 * the marker's own colour, with the same silhouette shadow every other
 * chat icon uses. The 17-pixel atlas cell is scaled onto the requested
 * size so the glyph keeps its proportions inside the ten-pixel slot.
 */
final class ChatMapMarkerRenderer {
    /** On-screen glyph edge inside the ten-pixel reserved slot. */
    static final float ICON_SIZE = 10.0F;

    private ChatMapMarkerRenderer() {}

    static void draw(Minecraft minecraft, String iconName, String colorName,
                     float x, float y, float size, int alpha) {
        float[] color = LostTalesCompassMarker.parseColor(colorName);
        drawInternal(minecraft, iconName, x, y, size,
                color[0], color[1], color[2], alpha);
    }

    static void drawShadow(Minecraft minecraft, String iconName,
                           float x, float y, float size,
                           int shadowRgb, int alpha) {
        LostTalesSilhouetteRenderState.begin(shadowRgb);
        try {
            drawInternal(minecraft, iconName, x, y, size,
                    1.0F, 1.0F, 1.0F, alpha);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    private static void drawInternal(Minecraft minecraft, String iconName,
                                     float x, float y, float size,
                                     float red, float green, float blue,
                                     int alpha) {
        if (minecraft == null || size <= 0.0F
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        LostTalesCompassMarkerIcon icon =
                LostTalesCompassMarkerIcon.fromName(iconName);
        minecraft.getTextureManager().bindTexture(
                LostTalesCompassMarkerIcon.TEXTURE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(
                MathHelper.clamp_float(red, 0.0F, 1.0F),
                MathHelper.clamp_float(green, 0.0F, 1.0F),
                MathHelper.clamp_float(blue, 0.0F, 1.0F),
                MathHelper.clamp_float(alpha / 255.0F, 0.0F, 1.0F));
        try {
            float u0 = icon.getU() / (float)LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
            float u1 = (icon.getU() + LostTalesCompassMarkerIcon.WIDTH)
                    / (float)LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
            float v0 = icon.getV() / (float)LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;
            float v1 = (icon.getV() + LostTalesCompassMarkerIcon.HEIGHT)
                    / (float)LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + size, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + size, y + size, 0.0D, u1, v1);
            tessellator.addVertexWithUV(x + size, y, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
