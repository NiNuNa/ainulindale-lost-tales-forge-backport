package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.client.character.CharacterTemplateStore;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.gui.screen.character.LostTalesCharacterCreationGui;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;

import java.util.UUID;

/**
 * The way into the account's default-character template from the main
 * menu, before any world is open.
 *
 * <p>The menu is not gated: Singleplayer and Multiplayer stay where they
 * are and do what they always did. A world makes the account's default
 * character whether or not a template was ever written, so a player who
 * ignores this button loses nothing but the chance to say who they start
 * as.</p>
 *
 * <p>Keyed on {@link GuiMainMenu} rather than an exact class: LOTR
 * replaces the menu with a subclass of it, and that subclass is the one
 * every real installation shows. It also drives a second screen through
 * the same initialisation, so only the screen Minecraft is actually
 * showing is touched.</p>
 */
public final class LostTalesMainMenuHandler {

    /** Past every button vanilla and LOTR give the menu. */
    private static final int BUTTON_ID = 0x105774;

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
        if (LostTalesClientAccount.id() == null) {
            // Nothing to keep a template under, so nothing to offer.
            return;
        }
        for (Object button : event.buttonList) {
            if (button instanceof GuiButton
                    && ((GuiButton) button).id == BUTTON_ID) {
                return;
            }
        }
        event.buttonList.add(new GuiButton(BUTTON_ID,
                event.gui.width / 2 - 100, event.gui.height - 44, 200, 20,
                I18n.format("gui.losttales.character.template.button")));
    }

    @SubscribeEvent
    public void onMainMenuButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event == null || !(event.gui instanceof GuiMainMenu)
                || event.button == null || event.button.id != BUTTON_ID) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        event.setCanceled(true);
        minecraft.displayGuiScreen(
                LostTalesCharacterCreationGui.forTemplate(event.gui));
    }

    /** Whether this account has said who it starts as. */
    public static boolean hasTemplate() {
        UUID account = LostTalesClientAccount.id();
        return account != null && CharacterTemplateStore.has(account);
    }
}
