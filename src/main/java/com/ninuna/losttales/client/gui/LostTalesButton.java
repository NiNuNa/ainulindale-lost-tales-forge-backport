package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.gui.style.LostTalesButtonStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

/** A resizable framed button. Override drawContents for an icon or model. */
public class LostTalesButton extends GuiButton {
    private boolean selected;

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
        LostTalesButtonStyle.drawFrame(minecraft, this.xPosition, this.yPosition,
                this.width, this.height, highlighted);
        this.mouseDragged(minecraft, mouseX, mouseY);
        drawContents(minecraft, mouseX, mouseY, highlighted);
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
