package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Draws emote sprites from the bundled sheet. Quad size and texel region are
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

    static void drawTinted(Minecraft minecraft, ChatEmoji emoji,
                           float x, float y, float size,
                           float red, float green, float blue, int alpha) {
        if (minecraft == null || emoji == null || size <= 0.0F
                || alpha <= 3) {
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
            float u0 = emoji.getTextureU() / (float)ChatEmoji.SHEET_WIDTH;
            float u1 = (emoji.getTextureU() + ChatEmoji.SPRITE_SIZE)
                    / (float)ChatEmoji.SHEET_WIDTH;
            float v0 = emoji.getTextureV() / (float)ChatEmoji.SHEET_HEIGHT;
            float v1 = (emoji.getTextureV() + ChatEmoji.SPRITE_SIZE)
                    / (float)ChatEmoji.SHEET_HEIGHT;
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
