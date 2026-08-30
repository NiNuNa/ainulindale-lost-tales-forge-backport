package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The chat's own artwork, one sprite sheet: the emoji and item buttons,
 * the send arrow, the tab row's controls and their hover states, the
 * tab borders, the window grip, and the hatch laid over empty message
 * rows. Each constant is a cell of {@code textures/gui/chat.png} in
 * texels; the sheet is drawn 1:1 in GUI pixels, so a sprite's width and
 * height are also its size on screen. The padlock's frames are the one
 * thing not held here: they are a regular grid, and
 * {@link ChatLockAnimation} walks it. {@code ChatIconSheetTest} locks
 * the constants to the bundled PNG the way the emoji sheet is locked.
 */
enum ChatIconSheet {
    EMOJI(0, 0, 10, 10),
    EMOJI_HOVER(11, 0, 10, 10),
    /** The dagger the item picker's button carries. */
    ITEM(22, 0, 10, 10),
    ITEM_HOVER(32, 0, 10, 10),
    /** The input bar's send arrow; it has no hover artwork of its own. */
    SEND(88, 1, 8, 8),
    PLUS(0, 11, 5, 5),
    PLUS_HOVER(6, 11, 5, 5),
    CLOSE(12, 11, 5, 5),
    CLOSE_HOVER(18, 11, 5, 5),
    COG(24, 11, 5, 5),
    COG_HOVER(30, 11, 5, 5),
    /** The bubble the typing line is announced with. */
    SPEECH_BUBBLE(14, 17, 9, 7),
    GRIP(0, 17, 6, 8),
    GRIP_HOVER(7, 17, 6, 8),
    /** The favourite heart: plain, and filled in the palette's wine. */
    HEART(36, 11, 5, 5),
    HEART_FAVORITE(42, 11, 5, 5),
    /**
     * The insert-toolbar chevron's animation, five frames from pointing
     * right (the inserts are out and fold back toward it) to pointing
     * left (they are away and open leftward), each cell exactly its own
     * artwork so a frame centres on the control however wide it is.
     */
    TOGGLE_1(48, 11, 3, 5),
    TOGGLE_2(52, 11, 2, 5),
    TOGGLE_3(55, 11, 1, 5),
    TOGGLE_4(57, 11, 2, 5),
    TOGGLE_5(60, 11, 3, 5),
    TOGGLE_1_HOVER(64, 11, 3, 5),
    TOGGLE_2_HOVER(68, 11, 2, 5),
    TOGGLE_3_HOVER(71, 11, 1, 5),
    TOGGLE_4_HOVER(73, 11, 2, 5),
    TOGGLE_5_HOVER(76, 11, 3, 5),
    /**
     * A tab's two border pieces. Each carries the corner it turns at the
     * top, the line that runs down the tab's side, and the tab's own
     * interior tone behind them, so a tab is these two with a line
     * between their tips and that same tone filling the span. The
     * selected pair is two rows taller: the tab it draws stands that
     * much above the resting ones.
     */
    TAB_LEFT(0, 59, 4, 17),
    TAB_RIGHT(5, 59, 4, 17),
    TAB_HOVER_LEFT(10, 59, 4, 17),
    TAB_HOVER_RIGHT(15, 59, 4, 17),
    TAB_SELECTED_LEFT(20, 57, 4, 19),
    TAB_SELECTED_RIGHT(25, 57, 4, 19),
    /**
     * The hatch laid over message rows the history does not reach: a
     * 45° line every eight texels. The pattern's period divides the
     * cell in both directions, so whole cells meet seamlessly wherever
     * the region is tiled with them.
     */
    EMPTY_HATCH(0, 26, 16, 16);

    static final String TEXTURE_PATH = "textures/gui/chat.png";
    static final int SHEET_WIDTH = 97;
    static final int SHEET_HEIGHT = 90;
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
        drawWithShadow(this.u, this.v, this.width, this.height, x, y, alpha);
    }

    /**
     * The sprite as a flat silhouette in one colour, shadow and all:
     * how a control whose sheet holds a single colourway of its glyph
     * says it is inert.
     */
    void drawSilhouetteWithShadow(int rgb, int x, int y, int alpha) {
        drawShadow(this.u, this.v, this.width, this.height, x, y, alpha);
        LostTalesSilhouetteRenderState.begin(rgb);
        try {
            draw(x, y, alpha);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /**
     * Any cell of the sheet, with the chat's shadow under it. The
     * padlock's frames are addressed this way: they are a grid rather
     * than named cells, so they carry their own coordinates.
     */
    static void drawWithShadow(int u, int v, int width, int height,
                               int x, int y, int alpha) {
        drawShadow(u, v, width, height, x, y, alpha);
        draw(u, v, width, height, x, y, alpha);
    }

    /** The shared drop shadow: the same cell, offset, in the shadow tone. */
    private static void drawShadow(int u, int v, int width, int height,
                                   int x, int y, int alpha) {
        int shadowAlpha = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadowAlpha <= 0) {
            return;
        }
        LostTalesSilhouetteRenderState.begin(LostTalesChatVisualStyle.SHADOW);
        try {
            draw(u, v, width, height,
                    x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    y + LostTalesChatVisualStyle.SHADOW_OFFSET, shadowAlpha);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /**
     * Tiles the sprite over a region in the colours it was authored in,
     * at full strength across the region's middle and falling off
     * linearly to nothing at its top and bottom edges, so the fill has
     * no edge of its own to read as a border.
     *
     * <p>Whole cells first, the partial last column and row cut by
     * their UVs, so the pattern never stretches and the region's edge
     * never samples texels beyond the cell. A row is also cut where the
     * ramp turns, so the peak is a vertex rather than a corner
     * interpolated across a cell; the cut carries its place in the cell
     * with it, so the pattern runs on through it unbroken. One texture
     * bind and a handful of quads.</p>
     */
    void drawTiledFadingFromMiddle(float left, float top, float right,
                                   float bottom, int alpha) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (minecraft == null || right <= left || bottom <= top
                || safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        LostTalesChatVisualStyle.beginContent();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        // The GUI draws under an alpha test that throws away nearly
        // transparent fragments. A ramp ending in nothing is exactly
        // that, so left on it would cut the fade off at a hard edge
        // partway down instead of letting it reach nothing; every other
        // gradient the chat draws turns it off for the same reason.
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        try {
            float middle = (top + bottom) / 2.0F;
            float half = (bottom - top) / 2.0F;
            float u0 = this.u / (float)SHEET_WIDTH;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            float y = top;
            while (y < bottom) {
                // Where this row starts inside the cell: a row cut short
                // by the ramp's turn leaves the next one to carry the
                // pattern on from exactly where it stopped.
                float inCell = (y - top) % this.height;
                float y1 = Math.min(bottom, y + this.height - inCell);
                if (y < middle && y1 > middle) {
                    y1 = middle;
                }
                float v0 = (this.v + inCell) / (float)SHEET_HEIGHT;
                float v1 = (this.v + inCell + (y1 - y))
                        / (float)SHEET_HEIGHT;
                int topAlpha = rampAlpha(safeAlpha, y, middle, half);
                int bottomAlpha = rampAlpha(safeAlpha, y1, middle, half);
                for (float x = left; x < right; x += this.width) {
                    float x1 = Math.min(right, x + this.width);
                    float u1 = (this.u + (x1 - x)) / (float)SHEET_WIDTH;
                    tessellator.setColorRGBA_I(0xFFFFFF, bottomAlpha);
                    tessellator.addVertexWithUV(x, y1, 0.0D, u0, v1);
                    tessellator.addVertexWithUV(x1, y1, 0.0D, u1, v1);
                    tessellator.setColorRGBA_I(0xFFFFFF, topAlpha);
                    tessellator.addVertexWithUV(x1, y, 0.0D, u1, v0);
                    tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
                }
                y = y1;
            }
            tessellator.draw();
        } finally {
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** The ramp at one height: full in the middle, nothing at the edges. */
    private static int rampAlpha(int alpha, float y, float middle,
                                 float half) {
        if (half <= 0.0F) {
            return alpha;
        }
        float ramp = 1.0F - Math.abs(y - middle) / half;
        return Math.round(alpha * Math.max(0.0F, Math.min(1.0F, ramp)));
    }

    /** The sprite at its own size, 1:1, at the given opacity. */
    void draw(float x, float y, int alpha) {
        draw(this.u, this.v, this.width, this.height, x, y, alpha);
    }

    /** Any cell of the sheet at its own size, 1:1, at the given opacity. */
    static void draw(int u, int v, int width, int height,
                     float x, float y, int alpha) {
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
            float u0 = u / (float)SHEET_WIDTH;
            float u1 = (u + width) / (float)SHEET_WIDTH;
            float v0 = v / (float)SHEET_HEIGHT;
            float v1 = (v + height) / (float)SHEET_HEIGHT;
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
