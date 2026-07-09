package com.ninuna.losttales.gui.hud.compass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
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

    public static float edgeIntervalFactor(float centerDistPx, float fadeStartDistPx, float fadeEndDistPx) {
        if (fadeEndDistPx <= fadeStartDistPx) {
            return centerDistPx < fadeEndDistPx ? 1.0F : 0.0F;
        }
        if (centerDistPx <= fadeStartDistPx) return 1.0F;
        if (centerDistPx >= fadeEndDistPx) return 0.0F;
        float t = (fadeEndDistPx - centerDistPx) / (fadeEndDistPx - fadeStartDistPx);
        return t * t * (3.0F - 2.0F * t);
    }

    /** atan2(-dx, dz), normalized into [0, 360). Matches Minecraft yaw where south is 0 degrees. */
    public static float angleDegToTarget(double dx, double dz) {
        float deg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return deg < 0.0F ? deg + 360.0F : deg;
    }

    public static PlayerPos lerpPlayerPos(EntityPlayer player, float partialTicks) {
        return lerpEntityPos(player, partialTicks);
    }

    /**
     * Interpolates an entity between its previous and current tick positions.
     *
     * <p>Compass markers are rendered every frame, while entities only update
     * their logical position once per game tick.  Using raw {@code posX/Y/Z}
     * for moving mobs makes hostile markers jump at 20 Hz even when the player
     * camera and static markers are smooth.  This mirrors Minecraft's normal
     * entity render interpolation and keeps moving hostile compass markers,
     * labels, and distance text aligned with the visible mob movement.</p>
     */
    public static PlayerPos lerpEntityPos(Entity entity, float partialTicks) {
        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
        double y = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks;
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
        return new PlayerPos(x, y, z);
    }

    /**
     * Draws centered text at fractional GUI coordinates.
     *
     * <p>FontRenderer in Minecraft 1.7.10 only exposes integer draw positions.
     * If we simply floor the centered X every frame, compass labels snap from
     * pixel to pixel while the marker sprites move smoothly.  Keeping the
     * integer part in FontRenderer and applying the fractional part to the GL
     * matrix mirrors the NeoForge renderer's pose-stack approach and makes the
     * name and distance labels track the icon smoothly.</p>
     */
    public static void drawCenteredString(FontRenderer fontRenderer, String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.length() == 0) return;

        float left = x - fontRenderer.getStringWidth(text) / 2.0F;
        drawString(fontRenderer, text, left, y, color, shadow);
    }

    public static void drawString(FontRenderer fontRenderer, String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.length() == 0) return;

        int left = MathHelper.floor_float(x);
        int top = MathHelper.floor_float(y);
        float fracX = x - left;
        float fracY = y - top;

        GL11.glPushMatrix();
        GL11.glTranslatef(fracX, fracY, 0.0F);
        if (shadow) {
            fontRenderer.drawStringWithShadow(text, left, top, color);
        } else {
            fontRenderer.drawString(text, left, top, color);
        }
        GL11.glPopMatrix();
    }

    public static void drawTexturedRect(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float alpha) {
        drawTexturedRectTinted(minecraft, texture, x, y, u, v, width, height, textureWidth, textureHeight, 1.0F, 1.0F, 1.0F, alpha);
    }

    /**
     * Draws a textured GUI quad with alpha testing temporarily disabled.
     *
     * <p>The compass HUD PNG already contains its own soft transparency.  Legacy
     * 1.7.10 GUI rendering may have alpha testing enabled, which discards faint
     * pixels and makes that authored fade look blocky.  This helper keeps the
     * texture's normal filtering untouched and only disables alpha test for the
     * duration of the draw call.</p>
     */
    public static void drawTexturedRectNoAlphaTest(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float alpha) {
        drawTexturedRectTintedAdvanced(minecraft, texture, x, y, u, v, width, height, textureWidth, textureHeight, 1.0F, 1.0F, 1.0F, alpha, true);
    }

    /**
     * Draws a textured GUI quad with a simple GL color tint.
     *
     * <p>Minecraft 1.7.10 does not have GuiGraphics-style ARGB blits, so the
     * compass renderer tints the currently-bound texture through the legacy
     * fixed-function GL color. This is especially important for marker shadows:
     * drawing the sprite a second time without a black tint produces a pale/white
     * duplicate instead of the dark text-like shadow used by the NeoForge version.</p>
     */
    public static void drawTexturedRectTinted(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float red, float green, float blue, float alpha) {
        drawTexturedRectTintedAdvanced(minecraft, texture, x, y, u, v, width, height, textureWidth, textureHeight, red, green, blue, alpha, false);
    }

    private static void drawTexturedRectTintedAdvanced(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float red, float green, float blue, float alpha, boolean disableAlphaTest) {
        if (alpha <= 0.0F) return;

        minecraft.getTextureManager().bindTexture(texture);

        boolean previousAlphaTest = false;

        if (disableAlphaTest) {
            previousAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(
                MathHelper.clamp_float(red, 0.0F, 1.0F),
                MathHelper.clamp_float(green, 0.0F, 1.0F),
                MathHelper.clamp_float(blue, 0.0F, 1.0F),
                MathHelper.clamp_float(alpha, 0.0F, 1.0F)
        );

        try {
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
        } finally {
            if (disableAlphaTest && previousAlphaTest) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static void drawTexturedRectWithShadow(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float alpha, float shadowAlpha) {
        drawTexturedRectWithShadowTinted(minecraft, texture, x, y, u, v, width, height, textureWidth, textureHeight, 1.0F, 1.0F, 1.0F, alpha, shadowAlpha);
    }

    public static void drawTexturedRectWithShadowTinted(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float red, float green, float blue, float alpha, float shadowAlpha) {
        drawTexturedRectTinted(minecraft, texture, x + 1.0F, y + 1.0F, u, v, width, height, textureWidth, textureHeight, 0.0F, 0.0F, 0.0F, shadowAlpha);
        drawTexturedRectTinted(minecraft, texture, x, y, u, v, width, height, textureWidth, textureHeight, red, green, blue, alpha);
    }

    /**
     * Draws a 1-pixel wide vertical gradient line for HUD ornaments.
     *
     * <p>Like the textured HUD backdrops, these fading ornament lines rely on
     * low-alpha pixels/vertices.  Some 1.7.10 GUI render paths leave alpha
     * testing enabled, which can clip most of the gradient and make the line
     * look missing.  Temporarily disabling alpha test keeps the NeoForge-style
     * soft fade while restoring the previous render state afterward.</p>
     */
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

        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, topAlpha);
            tessellator.addVertex(x, top, 0.0D);
            tessellator.addVertex(x + 1.0F, top, 0.0D);
            tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, bottomAlpha);
            tessellator.addVertex(x + 1.0F, bottom, 0.0D);
            tessellator.addVertex(x, bottom, 0.0D);
            tessellator.draw();
        } finally {
            GL11.glShadeModel(previousShadeModel);
            if (previousTexture2D) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            if (previousAlphaTest) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
            if (previousBlend) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (previousCullFace) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
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
