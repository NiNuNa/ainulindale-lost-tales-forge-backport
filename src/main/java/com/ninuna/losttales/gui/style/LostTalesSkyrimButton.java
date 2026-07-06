package com.ninuna.losttales.gui.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Flat dark button used by the Skyrim-inspired quest journal. */
public class LostTalesSkyrimButton extends GuiButton {
    public LostTalesSkyrimButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
        int fill = this.enabled ? (hovered ? 0xAA1D2229 : 0x99101217) : 0x66101010;
        int border = this.enabled ? (hovered ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.BORDER_DIM) : 0x553A3A3A;
        int text = this.enabled ? (hovered ? LostTalesSkyrimUiStyle.TEXT_BRIGHT : LostTalesSkyrimUiStyle.TEXT) : LostTalesSkyrimUiStyle.TEXT_DIM;

        drawRect(this.xPosition + 1, this.yPosition + 1, this.xPosition + this.width + 1, this.yPosition + this.height + 1, 0x75000000);
        drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, fill);
        drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + 1, border);
        drawRect(this.xPosition, this.yPosition + this.height - 1, this.xPosition + this.width, this.yPosition + this.height, border);
        drawRect(this.xPosition, this.yPosition, this.xPosition + 1, this.yPosition + this.height, border);
        drawRect(this.xPosition + this.width - 1, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, border);
        this.drawCenteredString(minecraft.fontRenderer, LostTalesSkyrimUiStyle.uppercase(this.displayString), this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, text);
    }
}
