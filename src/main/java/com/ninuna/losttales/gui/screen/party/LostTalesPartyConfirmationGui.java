package com.ninuna.losttales.gui.screen.party;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/** Client-only confirmation screen for destructive party actions. */
public final class LostTalesPartyConfirmationGui extends GuiScreen {

    private static final int BUTTON_CONFIRM = 1;
    private static final int BUTTON_CANCEL = 2;

    private final GuiScreen parent;
    private final String title;
    private final String detail;
    private final Runnable confirmedAction;

    public LostTalesPartyConfirmationGui(GuiScreen parent,
                                         String title,
                                         String detail,
                                         Runnable confirmedAction) {
        this.parent = parent;
        this.title = title == null ? "" : title;
        this.detail = detail == null ? "" : detail;
        this.confirmedAction = confirmedAction;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int y = this.height / 2 + 32;
        this.buttonList.add(new GuiButton(BUTTON_CONFIRM,
                this.width / 2 - 102, y, 100, 20,
                I18n.format("gui.losttales.party.confirm")));
        this.buttonList.add(new GuiButton(BUTTON_CANCEL,
                this.width / 2 + 2, y, 100, 20,
                I18n.format("gui.cancel")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_CONFIRM) {
            if (this.confirmedAction != null) {
                this.confirmedAction.run();
            }
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == BUTTON_CANCEL) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.title"),
                I18n.format("gui.losttales.party.confirmation"),
                this.width, 12);

        int panelWidth = Math.min(420, this.width - 28);
        int panelHeight = 104;
        int x = (this.width - panelWidth) / 2;
        int y = this.height / 2 - 62;
        LostTalesSkyrimUiStyle.drawPanel(x, y, panelWidth, panelHeight);
        drawCenteredString(this.fontRendererObj,
                LostTalesSkyrimUiStyle.trimToWidth(
                        this.fontRendererObj, this.title, panelWidth - 24),
                this.width / 2, y + 24,
                LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        drawCenteredString(this.fontRendererObj,
                LostTalesSkyrimUiStyle.trimToWidth(
                        this.fontRendererObj, this.detail, panelWidth - 24),
                this.width / 2, y + 48,
                LostTalesSkyrimUiStyle.TEXT_MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
