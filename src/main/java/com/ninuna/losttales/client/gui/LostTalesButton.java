package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesButtonStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

/**
 * A resizable framed button. Override drawContents for an icon or model.
 *
 * <p>It answers the pointer the way every other button in the mod does
 * ({@link LostTalesUiButtonMotion}): the frame keeps its place and what
 * the button holds rises, drops onto the surface while pressed, and
 * springs back. A selected button stays risen, since it is held down by
 * what it chose.</p>
 */
public class LostTalesButton extends GuiButton {
    private boolean selected;
    /** How this button answers the pointer; one beat per button. */
    private final LostTalesUiButtonMotion motion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);

    public LostTalesButton(int id, int x, int y, int width, int height,
                          String label) {
        super(id, x, y, width, height, label);
        if (width < LostTalesButtonStyle.MIN_SIZE
                || height < LostTalesButtonStyle.MIN_SIZE) {
            throw new IllegalArgumentException("Button dimensions must be at least "
                    + LostTalesButtonStyle.MIN_SIZE);
        }
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isHovered() {
        return this.visible && this.field_146123_n;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.visible) {
            this.field_146123_n = false;
            return;
        }
        this.field_146123_n = mouseX >= this.xPosition
                && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
        boolean highlighted = this.enabled
                && (this.field_146123_n || this.selected);
        // A button that cannot be pressed does not answer the pointer,
        // and a selected one stays risen because its own state holds it
        // there.
        boolean answers = this.enabled && this.field_146123_n;
        this.motion.advance(System.nanoTime(), highlighted,
                answers || this.selected,
                answers && org.lwjgl.input.Mouse.isButtonDown(0),
                LostTalesConfig.enableGuiAnimations);
        LostTalesButtonStyle.drawFrame(minecraft, this.xPosition, this.yPosition,
                this.width, this.height, highlighted);
        this.mouseDragged(minecraft, mouseX, mouseY);
        // The frame stands still and what the button holds moves inside
        // it, as every framed button in the mod does.
        LostTalesUiButton.beginPose(this.motion, this.xPosition,
                this.yPosition, this.width, this.height);
        try {
            drawContents(minecraft, mouseX, mouseY, highlighted);
        } finally {
            LostTalesUiButton.endPose();
        }
    }

    /** How far the button has crossed to its lit look, for a subclass. */
    protected final float lit() {
        return this.motion.lit();
    }

    /** The default content is a centered label using the shared palette. */
    protected void drawContents(Minecraft minecraft, int mouseX, int mouseY,
                                boolean highlighted) {
        int color = !this.enabled ? LostTalesColors.TEXT_DIM
                : highlighted ? LostTalesColors.IVORY : LostTalesColors.ROSE_BEIGE;
        if (this.packedFGColour != 0) {
            color = this.packedFGColour;
        }
        FontRenderer font = minecraft.fontRenderer;
        int x = this.xPosition + this.width / 2
                - font.getStringWidth(this.displayString) / 2;
        // GuiButton centers the eight-pixel glyph area; FONT_HEIGHT includes
        // the line gap and would put this one pixel too high.
        int y = this.yPosition + (this.height - 8) / 2;
        // Separate passes preserve the palette shadow instead of deriving a
        // darker text color through vanilla's drawStringWithShadow.
        LostTalesSkyrimUiStyle.beginContent();
        font.drawString(this.displayString, x + 1, y + 1,
                LostTalesColors.BLACK_SHADOW);
        LostTalesSkyrimUiStyle.beginContent();
        font.drawString(this.displayString, x, y, color);
    }
}
