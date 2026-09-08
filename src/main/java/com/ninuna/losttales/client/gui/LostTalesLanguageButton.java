package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** The vanilla language artwork over the shared button frame. */
final class LostTalesLanguageButton extends LostTalesButton {
    private static final int ICON_SIZE = 13;
    /** Row spans of the speech-bubble silhouette within vanilla's 20x20 cell. */
    private static final int[] ROW_LEFT = {6, 5, 4, 4, 4, 4, 4, 4, 4, 5, 5, 4, 4};
    private static final int[] ROW_WIDTH = {9, 11, 13, 13, 13, 13, 13, 13, 13, 11, 10, 4, 2};

    LostTalesLanguageButton(int id, int x, int y, int width, int height,
                           String label) {
        super(id, x, y, width, height, label);
    }

    @Override
    protected void drawContents(Minecraft minecraft, int mouseX, int mouseY,
                                boolean highlighted) {
        // Match modern SpriteIconButton: center both dimensions using integer
        // halves. Odd-sized artwork stays on whole GUI pixels at every scale.
        int x = this.xPosition + this.width / 2 - ICON_SIZE / 2;
        int y = this.yPosition + this.height / 2 - ICON_SIZE / 2;
        int textureY = highlighted ? 130 : 110;
        minecraft.getTextureManager().bindTexture(buttonTextures);
        LostTalesSkyrimUiStyle.beginContent();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, this.enabled ? 1.0F : 0.5F);
        try {
            // Sample only the icon, excluding the gray button behind it.
            // Exact texel edges keep the original pixel art at 1:1 GUI scale.
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (int row = 0; row < ICON_SIZE; row++) {
                int left = x + ROW_LEFT[row] - 4;
                int right = left + ROW_WIDTH[row];
                double u0 = ROW_LEFT[row] / 256.0D;
                double u1 = (ROW_LEFT[row] + ROW_WIDTH[row]) / 256.0D;
                double v0 = (textureY + row) / 256.0D;
                double v1 = (textureY + row + 1) / 256.0D;
                tessellator.addVertexWithUV(left, y + row + 1, 0.0D, u0, v1);
                tessellator.addVertexWithUV(right, y + row + 1, 0.0D, u1, v1);
                tessellator.addVertexWithUV(right, y + row, 0.0D, u1, v0);
                tessellator.addVertexWithUV(left, y + row, 0.0D, u0, v0);
            }
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
