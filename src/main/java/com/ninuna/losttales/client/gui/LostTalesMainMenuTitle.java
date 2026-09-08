package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Draws the authored title as one image in place of vanilla's split logo. */
public final class LostTalesMainMenuTitle {
    public static final String TEXTURE_PATH = "textures/gui/title/title.png";
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("losttales", TEXTURE_PATH);

    private LostTalesMainMenuTitle() {}

    /** Both vanilla logo variants begin with the piece at UV (0, 0). */
    public static void drawPiece(GuiScreen screen, int x, int y, int u, int v,
                                 int width, int height, List<?> buttons,
                                 boolean hasSubtitle) {
        if (u != 0 || v != 0) {
            return;
        }
        MainMenuTitleLayout layout = layout(screen, buttons, hasSubtitle);
        if (layout.width <= 0.0F) {
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        LostTalesSkyrimUiStyle.beginContent();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int magFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        try {
            // Smooth reduction of the large source image; other textures keep
            // their own sampling, including the menu's 1:1 pixel-art buttons.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            double left = layout.left;
            double right = left + layout.width;
            double top = MainMenuTitleLayout.TOP;
            double bottom = top + layout.height;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(left, bottom, 0.0D, 0.0D, 1.0D);
            tessellator.addVertexWithUV(right, bottom, 0.0D, 1.0D, 1.0D);
            tessellator.addVertexWithUV(right, top, 0.0D, 1.0D, 0.0D);
            tessellator.addVertexWithUV(left, top, 0.0D, 0.0D, 0.0D);
            tessellator.draw();
        } finally {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        }
    }

    /** Positions LOTR's subtitle below the full title instead of at a fixed y. */
    public static int subtitleY(int fallback, List<?> buttons, GuiScreen screen) {
        MainMenuTitleLayout layout = layout(screen, buttons, true);
        return layout.width > 0.0F ? layout.subtitleY() : fallback;
    }

    private static MainMenuTitleLayout layout(GuiScreen screen, List<?> buttons,
                                              boolean hasSubtitle) {
        int buttonsTop = screen.height;
        for (Object value : buttons) {
            if (value instanceof GuiButton && ((GuiButton) value).visible) {
                buttonsTop = Math.min(buttonsTop, ((GuiButton) value).yPosition);
            }
        }
        int subtitleHeight = hasSubtitle ? Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT : 0;
        return MainMenuTitleLayout.fit(screen.width, buttonsTop, subtitleHeight);
    }
}
