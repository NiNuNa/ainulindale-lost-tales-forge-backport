package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.render.player.LostTalesCharacterFigureRenderer;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * The main menu's way into the account's default character: a thin
 * upright button beside Singleplayer and Multiplayer, wearing that
 * character's face.
 *
 * <p>The shared button frame joins sprite corners at the menu's measured
 * height. Its content is the character model instead of a text label.</p>
 *
 * <p>The figure is the account's own until a template names one, and then
 * it is the template's: the body model itself, stood facing out of the
 * button. It is centred exactly — the middle of the button across, and
 * the race's own drawn height centred down it, so a hobbit sits in the
 * middle rather than hanging from where a human's head would be.</p>
 */
public final class LostTalesCharacterMenuButton extends LostTalesButton {

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

    @Override
    protected void drawContents(Minecraft minecraft, int mouseX, int mouseY,
                                boolean highlighted) {
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
