package com.ninuna.losttales.config.client;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.screen.LostTalesHudPlacementGui;
import net.minecraftforge.common.config.ConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard Forge 1.7.10 config screen opened from the Mods list.
 *
 * The actual values stay in LostTalesConfig so commands, keybinds, and the GUI
 * all edit one source of truth.
 */
public class LostTalesConfigGui extends GuiConfig {
    private static final int BUTTON_HUD_PLACEMENT = 62100;

    public LostTalesConfigGui(GuiScreen parentScreen) {
        super(
                parentScreen,
                getConfigElements(),
                LostTalesMetaData.MOD_ID,
                false,
                false,
                LostTalesMetaData.MOD_NAME + " Config"
        );
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(new GuiButton(BUTTON_HUD_PLACEMENT, Math.max(4, this.width - 154), 8, 150, 20, "HUD Placement Preview"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button != null && button.id == BUTTON_HUD_PLACEMENT) {
            this.mc.displayGuiScreen(new LostTalesHudPlacementGui(this));
            return;
        }
        super.actionPerformed(button);
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        Configuration config = LostTalesConfig.createConfiguration();
        if (config == null) {
            return elements;
        }

        config.load();
        LostTalesConfig.applyGuiMetadata(config);
        elements.add(new ConfigElement(config.getCategory(LostTalesConfig.CATEGORY_CLIENT)));
        elements.add(new ConfigElement(config.getCategory(LostTalesConfig.CATEGORY_QUESTS)));
        return elements;
    }
}
