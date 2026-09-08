package com.ninuna.losttales.gui.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Fixed-size sprite corners joined by procedural edges and a translucent fill. */
public final class LostTalesButtonStyle {
    public static final String TEXTURE_PATH = "textures/gui/menu.png";
    public static final int SHEET_SIZE = 64;
    public static final int CORNER_SIZE = 6;
    public static final int CORNER_STRIDE = 7;
    public static final int HIGHLIGHT_U = 14;
    public static final int MIN_SIZE = CORNER_SIZE * 2;
    /** The authored PNG's approximately two-thirds opacity, including corners. */
    public static final int FILL_ALPHA = 0xAB;

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("losttales", TEXTURE_PATH);

    private LostTalesButtonStyle() {}

    /**
     * Draws in whole GUI pixels, at least {@link #MIN_SIZE} in each dimension.
     * Highlighting is shared by hover and persistent selection. The fill never
     * overlaps the corner cells: their translucent pixels must blend only once.
     * Leaves blending ready for the button's text, icon or model.
     */
    public static void drawFrame(Minecraft minecraft, int x, int y,
                                 int width, int height, boolean highlighted) {
        if (width < MIN_SIZE || height < MIN_SIZE) {
            return;
        }
        int right = x + width;
        int bottom = y + height;
        int fill = LostTalesColors.withAlpha(highlighted
                ? LostTalesColors.PLUM_GRAY : LostTalesColors.PLUM_BLACK,
                FILL_ALPHA);
        int edge = highlighted ? LostTalesColors.IVORY
                : LostTalesColors.ROSE_BEIGE;
        int bottomEdge = highlighted ? LostTalesColors.ROSE_GRAY
                : LostTalesColors.PLUM_GRAY;

        // Three disjoint spans leave all four 6x6 corner cells empty.
        rect(x + CORNER_SIZE, y, right - CORNER_SIZE, bottom, fill);
        rect(x, y + CORNER_SIZE, x + CORNER_SIZE,
                bottom - CORNER_SIZE, fill);
        rect(right - CORNER_SIZE, y + CORNER_SIZE, right,
                bottom - CORNER_SIZE, fill);
        // The artwork's border is inset one pixel from its outside edge.
        rect(x + CORNER_SIZE, y + 1, right - CORNER_SIZE, y + 2, edge);
        rect(x + CORNER_SIZE, bottom - 2, right - CORNER_SIZE,
                bottom - 1, bottomEdge);
        rect(x + 1, y + CORNER_SIZE, x + 2, bottom - CORNER_SIZE, edge);
        rect(right - 2, y + CORNER_SIZE, right - 1,
                bottom - CORNER_SIZE, edge);

        minecraft.getTextureManager().bindTexture(TEXTURE);
        LostTalesSkyrimUiStyle.beginContent();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int u = highlighted ? HIGHLIGHT_U : 0;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        corner(tessellator, x, y, u, 0);
        corner(tessellator, right - CORNER_SIZE, y, u + CORNER_STRIDE, 0);
        corner(tessellator, x, bottom - CORNER_SIZE, u, CORNER_STRIDE);
        corner(tessellator, right - CORNER_SIZE, bottom - CORNER_SIZE,
                u + CORNER_STRIDE, CORNER_STRIDE);
        tessellator.draw();
    }

    private static void rect(int left, int top, int right, int bottom, int color) {
        if (right > left && bottom > top) {
            Gui.drawRect(left, top, right, bottom, color);
        }
    }

    private static void corner(Tessellator tessellator, int x, int y,
                               int u, int v) {
        double u0 = u / (double) SHEET_SIZE;
        double v0 = v / (double) SHEET_SIZE;
        double u1 = (u + CORNER_SIZE) / (double) SHEET_SIZE;
        double v1 = (v + CORNER_SIZE) / (double) SHEET_SIZE;
        tessellator.addVertexWithUV(x, y + CORNER_SIZE, 0.0D, u0, v1);
        tessellator.addVertexWithUV(x + CORNER_SIZE, y + CORNER_SIZE,
                0.0D, u1, v1);
        tessellator.addVertexWithUV(x + CORNER_SIZE, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
    }
}
