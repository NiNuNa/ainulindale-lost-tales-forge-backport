package com.ninuna.losttales.client.keybinding;

import com.ninuna.losttales.gui.hud.LostTalesHudHelper;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.screen.LostTalesCharacterMenuGui;
import com.ninuna.losttales.gui.screen.LostTalesHudPlacementGui;
import com.ninuna.losttales.gui.screen.LostTalesQuestJournalGui;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class LostTalesKeyBindings {
    public static final String CATEGORY = "key.categories.losttales.mappings";

    private static final KeyBinding CHARACTER_MENU = new KeyBinding("key.losttales.characterMenu", Keyboard.KEY_CAPITAL, CATEGORY);
    private static final KeyBinding QUEST_JOURNAL = new KeyBinding("key.losttales.questJournal", Keyboard.KEY_J, CATEGORY);
    private static final KeyBinding TOGGLE_HUD = new KeyBinding("key.losttales.toggleHud", Keyboard.KEY_H, CATEGORY);
    private static final KeyBinding USE = new KeyBinding("key.losttales.use", Keyboard.KEY_R, CATEGORY);
    private static final KeyBinding MODIFIER = new KeyBinding("key.losttales.modifier", Keyboard.KEY_LMENU, CATEGORY);

    public void register() {
        ClientRegistry.registerKeyBinding(CHARACTER_MENU);
        ClientRegistry.registerKeyBinding(QUEST_JOURNAL);
        ClientRegistry.registerKeyBinding(TOGGLE_HUD);
        ClientRegistry.registerKeyBinding(USE);
        ClientRegistry.registerKeyBinding(MODIFIER);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        handleBindingPresses();
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        handleBindingPresses();
    }

    private static void handleBindingPresses() {
        Minecraft minecraft = Minecraft.getMinecraft();

        if (CHARACTER_MENU.isPressed()) {
            minecraft.displayGuiScreen(new LostTalesCharacterMenuGui(minecraft.currentScreen));
        }
        if (QUEST_JOURNAL.isPressed()) {
            minecraft.displayGuiScreen(new LostTalesQuestJournalGui(minecraft.currentScreen));
        }
        if (TOGGLE_HUD.isPressed()) {
            if (isModifierKeyDown()) {
                minecraft.displayGuiScreen(new LostTalesHudPlacementGui(minecraft.currentScreen));
            } else {
                LostTalesHudHelper.toggleLostTalesHud();
            }
        }
        if (USE.isPressed()) {
            LostTalesQuickLootHudRenderer.dropSelectedItem();
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.dwheel != 0 && isModifierKeyDown() && LostTalesQuickLootHudRenderer.isLookingAtContainer()) {
            LostTalesQuickLootHudRenderer.moveSelection(event.dwheel > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }

    public static KeyBinding getUseKeyBinding() {
        return USE;
    }

    public static KeyBinding getModifierKeyBinding() {
        return MODIFIER;
    }

    public static boolean isModifierKeyDown() {
        return isKeyDown(MODIFIER);
    }

    public static String getModifierKeyDisplayName() {
        return getKeyDisplayName(MODIFIER);
    }

    public static String getUseKeyDisplayName() {
        return getKeyDisplayName(USE);
    }

    public static String getCharacterMenuKeyDisplayName() {
        return getKeyDisplayName(CHARACTER_MENU);
    }

    public static String getQuestJournalKeyDisplayName() {
        return getKeyDisplayName(QUEST_JOURNAL);
    }

    public static boolean isCharacterMenuKey(int keyCode) {
        return isKeyboardKey(CHARACTER_MENU, keyCode);
    }

    public static boolean isQuestJournalKey(int keyCode) {
        return isKeyboardKey(QUEST_JOURNAL, keyCode);
    }

    private static boolean isKeyboardKey(KeyBinding keyBinding, int keyCode) {
        return keyBinding != null && keyCode != Keyboard.KEY_NONE && keyBinding.getKeyCode() == keyCode;
    }

    private static boolean isKeyDown(KeyBinding keyBinding) {
        if (keyBinding == null) return false;
        int keyCode = keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE) return false;
        if (keyCode >= 0) {
            return Keyboard.isKeyDown(keyCode);
        }

        int mouseButton = keyCode + 100;
        return mouseButton >= 0 && Mouse.isButtonDown(mouseButton);
    }

    private static String getKeyDisplayName(KeyBinding keyBinding) {
        if (keyBinding == null) return "";
        int keyCode = keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE) return "None";
        if (keyCode < 0) {
            return "Mouse " + (keyCode + 101);
        }
        return getFriendlyKeyboardName(keyCode);
    }

    private static String getFriendlyKeyboardName(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_CAPITAL:
                return "Caps Lock";
            case Keyboard.KEY_LMENU:
                return "Left Alt";
            case Keyboard.KEY_RMENU:
                return "Right Alt";
            case Keyboard.KEY_LSHIFT:
                return "Left Shift";
            case Keyboard.KEY_RSHIFT:
                return "Right Shift";
            case Keyboard.KEY_LCONTROL:
                return "Left Ctrl";
            case Keyboard.KEY_RCONTROL:
                return "Right Ctrl";
            case Keyboard.KEY_RETURN:
                return "Enter";
            case Keyboard.KEY_ESCAPE:
                return "Escape";
            case Keyboard.KEY_SPACE:
                return "Space";
            case Keyboard.KEY_TAB:
                return "Tab";
            default:
                String name = Keyboard.getKeyName(keyCode);
                return name == null ? "Key " + keyCode : name;
        }
    }
}
