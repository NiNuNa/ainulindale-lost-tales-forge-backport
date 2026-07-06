package com.ninuna.losttales.client.keybinding;

import com.ninuna.losttales.gui.hud.LostTalesHudHelper;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.screen.LostTalesCharacterMenuGui;
import com.ninuna.losttales.gui.screen.LostTalesQuestJournalGui;
import com.ninuna.losttales.gui.screen.LostTalesHudPlacementGui;
import net.minecraft.client.Minecraft;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Keyboard;

public class LostTalesKeyBindings {
    public static final String CATEGORY = "key.categories.losttales.mappings";

    private final KeyBinding characterMenu = new KeyBinding("key.losttales.characterMenu", Keyboard.KEY_CAPITAL, CATEGORY);
    private final KeyBinding questJournal = new KeyBinding("key.losttales.questJournal", Keyboard.KEY_J, CATEGORY);
    private final KeyBinding toggleHud = new KeyBinding("key.losttales.toggleHud", Keyboard.KEY_H, CATEGORY);
    private final KeyBinding use = new KeyBinding("key.losttales.use", Keyboard.KEY_R, CATEGORY);

    public void register() {
        ClientRegistry.registerKeyBinding(this.characterMenu);
        ClientRegistry.registerKeyBinding(this.questJournal);
        ClientRegistry.registerKeyBinding(this.toggleHud);
        ClientRegistry.registerKeyBinding(this.use);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (this.characterMenu.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new LostTalesCharacterMenuGui(Minecraft.getMinecraft().currentScreen));
        }
        if (this.questJournal.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new LostTalesQuestJournalGui(Minecraft.getMinecraft().currentScreen));
        }
        if (this.toggleHud.isPressed()) {
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                Minecraft.getMinecraft().displayGuiScreen(new LostTalesHudPlacementGui(Minecraft.getMinecraft().currentScreen));
            } else {
                LostTalesHudHelper.toggleLostTalesHud();
            }
        }
        if (this.use.isPressed()) {
            LostTalesQuickLootHudRenderer.dropSelectedItem();
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.dwheel != 0 && Keyboard.isKeyDown(Keyboard.KEY_LMENU) && LostTalesQuickLootHudRenderer.isLookingAtContainer()) {
            LostTalesQuickLootHudRenderer.moveSelection(event.dwheel > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }
}
