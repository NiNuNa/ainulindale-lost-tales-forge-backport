package com.ninuna.losttales.config.client;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;
import com.ninuna.losttales.client.camera.CameraPresetFileStore;
import com.ninuna.losttales.client.camera.ThirdPersonCameraRuntime;
import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.gui.GuiButton;
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
        return new CommittingGuiConfig(
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

    /**
     * Forge 1.7.10 normally commits text fields only while processing the
     * child's Done button. Persist the edited backing Configuration in that
     * same call so a parent refresh cannot recreate entries from stale data.
     */
    private static final class CommittingGuiConfig extends GuiConfig {
        @SuppressWarnings("rawtypes")
        private CommittingGuiConfig(
                GuiScreen parentScreen,
                java.util.List<IConfigElement> configElements,
                String modId, String configId,
                boolean requireWorldRestart,
                boolean requireMinecraftRestart,
                String title, String titleLine2) {
            super(parentScreen, configElements, modId, configId,
                    requireWorldRestart, requireMinecraftRestart,
                    title, titleLine2);
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            boolean done = button != null && button.id == 2000;
            if (done && this.entryList != null
                    && this.entryList.hasChangedEntry(true)) {
                this.entryList.saveConfigElements();
                saveBackingConfigurations();
            }
            super.actionPerformed(button);
            if (done) {
                saveBackingConfigurations();
                LostTalesConfig.reload();
                LostTalesThirdPersonConfig.reload();
                CameraPresetFileStore.reload();
                ThirdPersonCameraRuntime.resetSession();
            }
        }

        private static void saveBackingConfigurations() {
            LostTalesConfig.savePendingGuiConfiguration();
            LostTalesThirdPersonConfig.savePendingGuiConfiguration();
        }
    }
}
