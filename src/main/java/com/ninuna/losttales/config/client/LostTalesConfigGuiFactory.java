package com.ninuna.losttales.config.client;

import cpw.mods.fml.client.IModGuiFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Set;

/**
 * Enables the Forge 1.7.10 Mod List "Config" button for Lost Tales.
 */
public class LostTalesConfigGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
        // Nothing to initialise. The GUI reads the already-loaded Forge config file.
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return LostTalesConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
}
