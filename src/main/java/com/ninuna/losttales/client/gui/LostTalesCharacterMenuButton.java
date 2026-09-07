package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.render.player.LostTalesCharacterFigureRenderer;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;

import java.util.UUID;

/**
 * The main menu's way into the account's default character: a thin
 * upright button beside Singleplayer and Multiplayer, wearing that
 * character's face.
 *
 * <p>It is drawn in the mod's own panel style rather than the menu's.
 * LOTR replaces every vanilla menu button with one of its own that blits
 * a fixed twenty-pixel-tall strip of the red book, so there is no button
 * art on that menu this one could be cut from, and a vanilla widget
 * beside those would read as a different mod again. The panel is drawn
 * from primitives at whatever size it is given, which is what lets the
 * button span two buttons' worth of height on any menu.</p>
 *
 * <p>The figure is the account's own until a template names one, and then
 * it is the template's: the body model itself, stood facing out of the
 * button. It is centred exactly — the middle of the button across, and
 * the race's own drawn height centred down it, so a hobbit sits in the
 * middle rather than hanging from where a human's head would be.</p>
 */
public final class LostTalesCharacterMenuButton extends GuiButton {

    /** Clear of the panel's own border, top and bottom. */
    private static final int FIGURE_MARGIN = 3;

    private final UUID accountId;
    private final CharacterAppearance appearance;

    /**
     * @param label what the button is for, shown when the pointer is on
     *              it; the button itself is too narrow for text.
     */
    public LostTalesCharacterMenuButton(int id, int x, int y, int height,
                                        UUID accountId,
                                        CharacterAppearance appearance,
                                        String label) {
        super(id, x, y, CharacterMenuButtonPlacement.WIDTH, height, label);
        this.accountId = accountId;
        this.appearance = appearance;
    }

    /** Whether the pointer was on the button the last time it was drawn. */
    public boolean isHovered() {
        return this.field_146123_n;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        this.field_146123_n = mouseX >= this.xPosition
                && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
        LostTalesSkyrimUiStyle.drawPanel(this.xPosition, this.yPosition,
                this.width, this.height,
                this.field_146123_n
                        ? LostTalesSkyrimUiStyle.PANEL_SELECTED
                        : LostTalesSkyrimUiStyle.PANEL_FILL);
        // The panel is built from drawRect, which leaves blending off.
        LostTalesSkyrimUiStyle.beginContent();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        // As large as the button can hold, at whole pixels per texel.
        int scale = LostTalesCharacterFigureRenderer.scaleFor(
                this.height - FIGURE_MARGIN * 2);
        int figureHeight = LostTalesCharacterFigureRenderer.height(scale);
        float centerX = this.xPosition + this.width / 2.0F;
        // Its feet, so the figure sits in the middle of the button.
        float feetY = this.yPosition + (this.height + figureHeight) / 2.0F;
        LostTalesCharacterFigureRenderer.drawFigure(minecraft, this.accountId,
                this.appearance, centerX, feetY, scale, 1.0F, 1.0F);
    }
}
