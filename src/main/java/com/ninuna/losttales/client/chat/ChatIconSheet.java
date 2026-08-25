package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The chat's own control artwork, one sprite sheet: the emoji button,
 * the tab row's controls and their hover states, and the window grip.
 * Each constant is a cell of {@code textures/gui/chat.png} in texels;
 * the sheet is drawn 1:1 in GUI pixels, so a sprite's width and height
 * are also its size on screen. {@code ChatIconSheetTest} locks the
 * constants to the bundled PNG the way the emoji sheet is locked.
 */
enum ChatIconSheet {
    EMOJI(0, 0, 10, 10),
    EMOJI_HOVER(11, 0, 10, 10),
    PLUS(0, 11, 5, 5),
    PLUS_HOVER(6, 11, 5, 5),
    CLOSE(12, 11, 5, 5),
    CLOSE_HOVER(18, 11, 5, 5),
    COG(24, 11, 5, 5),
    COG_HOVER(30, 11, 5, 5),
    /** Shackle swung open to the right: the body is the left five texels. */
    UNLOCKED(0, 17, 9, 7),
    UNLOCKED_HOVER(10, 17, 9, 7),
    LOCKED(20, 17, 5, 7),
    LOCKED_HOVER(26, 17, 5, 7),
    GRIP(0, 25, 6, 8),
    GRIP_HOVER(7, 25, 6, 8);

    static final String TEXTURE_PATH = "textures/gui/chat.png";
    static final int SHEET_WIDTH = 54;
    static final int SHEET_HEIGHT = 54;
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("losttales", TEXTURE_PATH);

    private final int u;
    private final int v;
    private final int width;
    private final int height;

    ChatIconSheet(int u, int v, int width, int height) {
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
    }

    int getTextureU() { return this.u; }
    int getTextureV() { return this.v; }
    int getWidth() { return this.width; }
    int getHeight() { return this.height; }

    /** The sprite at its own size, with the chat's shadow under it. */
    void drawWithShadow(int x, int y, int alpha) {
        int shadowAlpha = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadowAlpha > 0) {
            LostTalesSilhouetteRenderState.begin(
                    LostTalesChatVisualStyle.SHADOW);
            try {
                draw(x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        y + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        shadowAlpha);
            } finally {
                LostTalesSilhouetteRenderState.end();
            }
        }
        draw(x, y, alpha);
    }

    /** The sprite at its own size, 1:1, at the given opacity. */
    void draw(float x, float y, int alpha) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        LostTalesChatVisualStyle.beginContent();
        GL11.glColor4f(1.0F, 1.0F, 1.0F,
                MathHelper.clamp_float(alpha / 255.0F, 0.0F, 1.0F));
        try {
            float u0 = this.u / (float)SHEET_WIDTH;
            float u1 = (this.u + this.width) / (float)SHEET_WIDTH;
            float v0 = this.v / (float)SHEET_HEIGHT;
            float v1 = (this.v + this.height) / (float)SHEET_HEIGHT;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + this.height, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + this.width, y + this.height,
                    0.0D, u1, v1);
            tessellator.addVertexWithUV(x + this.width, y, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
