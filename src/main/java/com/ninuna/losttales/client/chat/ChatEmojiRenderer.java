package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Draws emoji sprites from the bundled sheet. Quad size and texel region are
 * independent so a degraded slot (chat colours disabled strips the bold codes
 * and reserves only eight pixels) scales the sprite down instead of cropping.
 */
final class ChatEmojiRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "losttales", ChatEmoji.TEXTURE_PATH);

    private ChatEmojiRenderer() {}

    static void draw(Minecraft minecraft, ChatEmoji emoji,
                     float x, float y, float size, int alpha) {
        drawTinted(minecraft, emoji, x, y, size,
                1.0F, 1.0F, 1.0F, alpha);
    }

    /**
     * Solid-colour shadow of the sprite's outline, the emoji equivalent of
     * the font's shadow pass. A tinted copy would keep the sprite's own
     * shading and turn its dark outline black; the silhouette replaces RGB
     * outright and keeps only the sprite's alpha.
     */
    static void drawShadow(Minecraft minecraft, ChatEmoji emoji,
                           float x, float y, float size,
                           int shadowRgb, int alpha) {
        LostTalesSilhouetteRenderState.begin(shadowRgb);
        try {
            drawTinted(minecraft, emoji, x, y, size,
                    1.0F, 1.0F, 1.0F, alpha);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    static void drawTinted(Minecraft minecraft, ChatEmoji emoji,
                           float x, float y, float size,
                           float red, float green, float blue, int alpha) {
        drawRegion(minecraft, emoji, x, y, size, red, green, blue, alpha,
                0.0F, 0.0F, size, size);
    }

    /**
     * Part of the sprite: the stretch from {@code left}, {@code top} to
     * {@code right}, {@code bottom} of its box, measured from the box's
     * corner in the box's own units, with the texels that stretch covers.
     * What a channel's icon is built from where it is cut: half an icon
     * beside half of Discord's, and the corner a mark takes.
     */
    static void drawRegion(Minecraft minecraft, ChatEmoji emoji,
                           float x, float y, float size, int alpha,
                           boolean silhouette, float left, float top,
                           float right, float bottom) {
        if (!silhouette) {
            drawRegion(minecraft, emoji, x, y, size, 1.0F, 1.0F, 1.0F,
                    alpha, left, top, right, bottom);
            return;
        }
        LostTalesSilhouetteRenderState.begin(LostTalesChatVisualStyle.SHADOW);
        try {
            drawRegion(minecraft, emoji, x, y, size, 1.0F, 1.0F, 1.0F,
                    alpha, left, top, right, bottom);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    private static void drawRegion(Minecraft minecraft, ChatEmoji emoji,
                                   float x, float y, float size, float red,
                                   float green, float blue, int alpha,
                                   float left, float top, float right,
                                   float bottom) {
        float fromX = Math.max(0.0F, left);
        float fromY = Math.max(0.0F, top);
        float toX = Math.min(size, right);
        float toY = Math.min(size, bottom);
        if (minecraft == null || emoji == null || size <= 0.0F
                || alpha <= 3 || toX <= fromX || toY <= fromY) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(
                MathHelper.clamp_float(red, 0.0F, 1.0F),
                MathHelper.clamp_float(green, 0.0F, 1.0F),
                MathHelper.clamp_float(blue, 0.0F, 1.0F),
                MathHelper.clamp_float(alpha / 255.0F, 0.0F, 1.0F));
        try {
            // The texels the stretch covers, a cell's worth over the
            // box's size.
            float texels = ChatEmoji.SPRITE_SIZE / size;
            float u0 = (emoji.getTextureU() + fromX * texels)
                    / (float)ChatEmoji.SHEET_WIDTH;
            float u1 = (emoji.getTextureU() + toX * texels)
                    / (float)ChatEmoji.SHEET_WIDTH;
            float v0 = (emoji.getTextureV() + fromY * texels)
                    / (float)ChatEmoji.SHEET_HEIGHT;
            float v1 = (emoji.getTextureV() + toY * texels)
                    / (float)ChatEmoji.SHEET_HEIGHT;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x + fromX, y + toY, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + toX, y + toY, 0.0D, u1, v1);
            tessellator.addVertexWithUV(x + toX, y + fromY, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x + fromX, y + fromY, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
