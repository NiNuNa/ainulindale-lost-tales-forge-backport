package com.ninuna.losttales.config.client;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;

/**
 * Forge 1.7.10 category entry whose child screen commits on its own Done
 * button instead of requiring a second Done click on the parent screen.
 */
public final class LostTalesSavingCategoryEntry
        extends GuiConfigEntries.CategoryEntry {
    @SuppressWarnings("rawtypes")
    public LostTalesSavingCategoryEntry(
            GuiConfig owningScreen,
            GuiConfigEntries owningEntryList,
            IConfigElement configElement) {
        super(owningScreen, owningEntryList, configElement);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected GuiScreen buildChildScreen() {
        String childConfigId = this.owningScreen.modID
                + ".category." + this.configElement.getName();
        String parentTitle = this.owningScreen.titleLine2 == null
                ? "" : this.owningScreen.titleLine2;
        return new GuiConfig(
                this.owningScreen,
                this.configElement.getChildElements(),
                this.owningScreen.modID,
                childConfigId,
                this.owningScreen.allRequireWorldRestart
                        || this.configElement.requiresWorldRestart(),
                this.owningScreen.allRequireMcRestart
                        || this.configElement.requiresMcRestart(),
                this.owningScreen.title,
                parentTitle + " > " + this.name);
    }
}
