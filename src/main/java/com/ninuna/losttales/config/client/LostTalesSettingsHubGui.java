package com.ninuna.losttales.config.client;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * The one door to the mod's settings, opened from the Mods list and the
 * character menu: the client's own settings, and — where the player can
 * change them — the server's. In a world the server button shows only
 * once the server has said the player is an operator; in the main menu
 * it edits the local file, the server this game hosts.
 */
public final class LostTalesSettingsHubGui extends GuiScreen {

    private static final int BUTTON_CLIENT = 1;
    private static final int BUTTON_SERVER = 2;
    private static final int BUTTON_BACK = 3;
    private static final int BUTTON_WIDTH = 200;

    private final GuiScreen parent;

    public LostTalesSettingsHubGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int x = this.width / 2 - BUTTON_WIDTH / 2;
        int y = this.height / 2 - 30;
        this.buttonList.add(new GuiButton(BUTTON_CLIENT, x, y, BUTTON_WIDTH, 20,
                I18n.format("gui.losttales.settings.client")));
        if (LostTalesServerConfigLoadingGui.canOpenServerSettings(this.mc)) {
            this.buttonList.add(new GuiButton(BUTTON_SERVER, x, y + 24, BUTTON_WIDTH, 20,
                    I18n.format(this.mc.theWorld == null
                            ? "gui.losttales.server_settings.hosting_button"
                            : "gui.losttales.server_settings.button")));
        }
        this.buttonList.add(new GuiButton(BUTTON_BACK, this.width / 2 - 50,
                this.height - 40, 100, 20, I18n.format("gui.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || this.mc == null) {
            return;
        }
        if (button.id == BUTTON_CLIENT) {
            this.mc.displayGuiScreen(new LostTalesConfigGui(this));
        } else if (button.id == BUTTON_SERVER) {
            this.mc.displayGuiScreen(LostTalesServerConfigLoadingGui.open(this.mc, this));
        } else if (button.id == BUTTON_BACK) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                LostTalesMetaData.MOD_NAME,
                I18n.format("gui.losttales.settings.subtitle"), this.width, 12);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
