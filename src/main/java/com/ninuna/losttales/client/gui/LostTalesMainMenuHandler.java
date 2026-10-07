package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.gui.style.LostTalesButtonStyle;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;

/**
 * Styles the main menu's standard controls and lays them out.
 *
 * <p>Keyed on {@link GuiMainMenu} rather than an exact class: LOTR
 * replaces the menu with a subclass of it, and that subclass is the one
 * every real installation shows. It also drives a second screen through
 * the same initialisation, so only the screen Minecraft is actually
 * showing is touched.</p>
 *
 * <p>The layout is read from the menu's own buttons after the menu has
 * finished building them. LOTR moves the whole column down and swaps every
 * button for one of its own in {@code initGui}, and this runs afterwards,
 * so what is measured is where the buttons ended up.</p>
 */
public final class LostTalesMainMenuHandler {

    @SubscribeEvent
    public void onMainMenuInitialised(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event == null || !(event.gui instanceof GuiMainMenu)) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.currentScreen != event.gui) {
            // The menu draws a second screen behind itself through the
            // same path; that one is scenery and owns no buttons of ours.
            return;
        }
        styleMenuButtons(event);
        MainMenuButtonLayout.arrange(event.buttonList);
        MainMenuButtonLayout.position(event.buttonList, event.gui.width, event.gui.height);
    }

    /** Standard menu actions are dispatched by id in GuiMainMenu. */
    private static void styleMenuButtons(GuiScreenEvent.InitGuiEvent.Post event) {
        for (int index = 0; index < event.buttonList.size(); index++) {
            Object value = event.buttonList.get(index);
            if (!(value instanceof GuiButton) || value instanceof LostTalesButton) {
                continue;
            }
            GuiButton original = (GuiButton) value;
            if (original.width < LostTalesButtonStyle.MIN_SIZE
                    || original.height < LostTalesButtonStyle.MIN_SIZE) {
                continue;
            }
            switch (original.id) {
                case 0: // Options
                case 1: // Singleplayer
                case 2: // Multiplayer
                case 4: // Quit
                case 5: // Language
                case 6: // Forge mod list
                case 11: // Play demo
                case 12: // Reset demo
                case 14: // Realms
                    break;
                default:
                    continue;
            }
            LostTalesButton replacement = original.id == 5
                    ? new LostTalesLanguageButton(original.id, original.xPosition,
                            original.yPosition, original.width, MainMenuButtonLayout.HEIGHT,
                            original.displayString)
                    : new LostTalesButton(original.id, original.xPosition,
                            original.yPosition, original.width, MainMenuButtonLayout.HEIGHT,
                            original.displayString);
            replacement.enabled = original.enabled;
            replacement.visible = original.visible;
            replacement.packedFGColour = original.packedFGColour;
            event.buttonList.set(index, replacement);
        }
    }
}
