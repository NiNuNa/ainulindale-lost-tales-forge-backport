package com.ninuna.losttales.gui.screen;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public class LostTalesCharacterMenuGui extends GuiScreen {
    private final GuiScreen parent;

    public LostTalesCharacterMenuGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, "Character Menu", this.width / 2, 100, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj, "Backported screen placeholder", this.width / 2, 114, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_CAPITAL) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == Keyboard.KEY_J) {
            this.mc.displayGuiScreen(new LostTalesQuestJournalGui(this.parent));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
