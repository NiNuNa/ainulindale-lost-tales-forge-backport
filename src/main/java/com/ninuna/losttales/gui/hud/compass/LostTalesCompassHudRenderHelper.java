package com.ninuna.losttales.gui.hud.compass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class LostTalesCompassHudRenderHelper {
    private LostTalesCompassHudRenderHelper() {}

    public static float normalizeViewYaw(float viewYaw) {
        return (viewYaw % 360.0F + 360.0F) % 360.0F;
    }

    public static float shortestDeltaDegrees(float targetDeg, float refDeg) {
        return (targetDeg - refDeg + 540.0F) % 360.0F - 180.0F;
    }

    public static float edgeCenterFactor(float px, float centerX, float halfWidth, float fadeEdgePx) {
        float dist = Math.abs(px - centerX);
        float fadeStart = Math.max(0.0F, halfWidth - fadeEdgePx);
        if (dist <= fadeStart) return 1.0F;
        if (dist >= halfWidth) return 0.0F;
        float t = (halfWidth - dist) / (halfWidth - fadeStart);
        return 0.5F - 0.5F * (float) Math.cos(Math.PI * t);
    }

    public static float focusEmphasis(float centerDistPx, float focusRadiusPx) {
        float t = 1.0F - Math.min(1.0F, centerDistPx / focusRadiusPx);
        return t * t * (3.0F - 2.0F * t);
    }

    /** atan2(-dx, dz), normalized into [0, 360). Matches Minecraft yaw where south is 0 degrees. */
    public static float angleDegToTarget(double dx, double dz) {
        float deg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return deg < 0.0F ? deg + 360.0F : deg;
    }

    public static PlayerPos lerpPlayerPos(EntityPlayer player, float partialTicks) {
        double x = player.prevPosX + (player.posX - player.prevPosX) * partialTicks;
        double y = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
        double z = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks;
        return new PlayerPos(x, y, z);
    }

    public static void drawCenteredString(FontRenderer fontRenderer, String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.length() == 0) return;
        int left = MathHelper.floor_float(x - fontRenderer.getStringWidth(text) / 2.0F);
        int top = MathHelper.floor_float(y);
        if (shadow) {
            fontRenderer.drawStringWithShadow(text, left, top, color);
        } else {
            fontRenderer.drawString(text, left, top, color);
        }
    }

    public static void drawTexturedRect(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float alpha) {
        if (alpha <= 0.0F) return;

        minecraft.getTextureManager().bindTexture(texture);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, MathHelper.clamp_float(alpha, 0.0F, 1.0F));

        float u0 = (float) u / (float) textureWidth;
        float u1 = (float) (u + width) / (float) textureWidth;
        float v0 = (float) v / (float) textureHeight;
        float v1 = (float) (v + height) / (float) textureHeight;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
        tessellator.draw();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawTexturedRectWithShadow(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float alpha, float shadowAlpha) {
        drawTexturedRect(minecraft, texture, x + 1.0F, y + 1.0F, u, v, width, height, textureWidth, textureHeight, shadowAlpha);
        drawTexturedRect(minecraft, texture, x, y, u, v, width, height, textureWidth, textureHeight, alpha);
    }

    public static void drawVerticalGradientLine(float x, float yTop, float yBottom, float alphaTop, float alphaBottom) {
        if (yTop == yBottom) return;

        float top = yTop;
        float bottom = yBottom;
        float topAlpha = MathHelper.clamp_float(alphaTop, 0.0F, 1.0F);
        float bottomAlpha = MathHelper.clamp_float(alphaBottom, 0.0F, 1.0F);
        if (bottom < top) {
            top = yBottom;
            bottom = yTop;
            topAlpha = MathHelper.clamp_float(alphaBottom, 0.0F, 1.0F);
            bottomAlpha = MathHelper.clamp_float(alphaTop, 0.0F, 1.0F);
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, topAlpha);
        tessellator.addVertex(x, top, 0.0D);
        tessellator.addVertex(x + 1.0F, top, 0.0D);
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, bottomAlpha);
        tessellator.addVertex(x + 1.0F, bottom, 0.0D);
        tessellator.addVertex(x, bottom, 0.0D);
        tessellator.draw();

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static final class PlayerPos {
        public final double x;
        public final double y;
        public final double z;

        private PlayerPos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
