package com.ninuna.losttales.client.keybinding;

import com.ninuna.losttales.client.camera.ThirdPersonCameraRuntime;
import com.ninuna.losttales.client.camera.ThirdPersonTargetLockController;
import com.ninuna.losttales.client.character.room.CharacterRoomSession;
import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.client.window.PinnedWindows;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.gui.hud.LostTalesHudHelper;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.screen.LostTalesCharacterMenuGui;
import com.ninuna.losttales.gui.hud.placement.HudPlacementPage;
import com.ninuna.losttales.gui.screen.character.CharactersPage;
import com.ninuna.losttales.gui.screen.fellowship.FellowshipPage;
import com.ninuna.losttales.gui.screen.quest.QuestJournalPage;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class LostTalesKeyBindings {
    public static final String CATEGORY = "key.categories.losttales.mappings";
    /** What the lang keys of the keys' full names begin with. */
    public static final String FULL_NAME_PREFIX = "gui.losttales.keys.full.";
    private static final int MIDDLE_MOUSE_KEY_CODE = -98;

    private static final KeyBinding CHARACTER_MENU = new KeyBinding("key.losttales.characterMenu", Keyboard.KEY_CAPITAL, CATEGORY);
    private static final KeyBinding QUEST_JOURNAL = new KeyBinding("key.losttales.questJournal", Keyboard.KEY_J, CATEGORY);
    /** Unbound until the player gives it a key. */
    private static final KeyBinding FELLOWSHIP = new KeyBinding("key.losttales.fellowship", Keyboard.KEY_NONE, CATEGORY);
    /** Unbound until the player gives it a key, as the Fellowship key is. */
    private static final KeyBinding CHARACTERS = new KeyBinding("key.losttales.characters", Keyboard.KEY_NONE, CATEGORY);
    private static final KeyBinding MAP = new KeyBinding(
            "key.losttales.map", Keyboard.KEY_M, CATEGORY);
    private static final KeyBinding TOGGLE_HUD = new KeyBinding("key.losttales.toggleHud", Keyboard.KEY_H, CATEGORY);
    private static final KeyBinding USE = new KeyBinding("key.losttales.use", Keyboard.KEY_R, CATEGORY);
    private static final KeyBinding MODIFIER = new KeyBinding("key.losttales.modifier", Keyboard.KEY_LMENU, CATEGORY);
    private static final KeyBinding SWAP_SHOULDER = new KeyBinding("key.losttales.swapShoulder", Keyboard.KEY_C, CATEGORY);
    private static final KeyBinding TARGET_LOCK = new KeyBinding("key.losttales.targetLock", MIDDLE_MOUSE_KEY_CODE, CATEGORY);
    private static final KeyBinding CYCLE_TARGET_LEFT = new KeyBinding("key.losttales.cycleTargetLeft", Keyboard.KEY_NONE, CATEGORY);
    private static final KeyBinding CYCLE_TARGET_RIGHT = new KeyBinding("key.losttales.cycleTargetRight", Keyboard.KEY_NONE, CATEGORY);

    public void register() {
        ClientRegistry.registerKeyBinding(CHARACTER_MENU);
        ClientRegistry.registerKeyBinding(QUEST_JOURNAL);
        ClientRegistry.registerKeyBinding(FELLOWSHIP);
        ClientRegistry.registerKeyBinding(CHARACTERS);
        ClientRegistry.registerKeyBinding(MAP);
        ClientRegistry.registerKeyBinding(TOGGLE_HUD);
        ClientRegistry.registerKeyBinding(USE);
        ClientRegistry.registerKeyBinding(MODIFIER);
        ClientRegistry.registerKeyBinding(SWAP_SHOULDER);
        ClientRegistry.registerKeyBinding(TARGET_LOCK);
        ClientRegistry.registerKeyBinding(CYCLE_TARGET_LEFT);
        ClientRegistry.registerKeyBinding(CYCLE_TARGET_RIGHT);
    }

    /**
     * While playing, an arrow pressed with the Modifier Key held goes to
     * the pinned windows: left and right walk the tabs of one, up and down
     * pick which one. The arrows are read as keys and are no bindings of
     * their own, since a key holds one binding and LOTR's alignment keys
     * are the arrows. A press a pinned window took is spent: whatever is
     * bound to that arrow does not answer it too, so this runs first.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPinnedWindowKey(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()) {
            return;
        }
        int key = Keyboard.getEventKey();
        int tabs = key == Keyboard.KEY_RIGHT ? 1
                : key == Keyboard.KEY_LEFT ? -1 : 0;
        int windows = key == Keyboard.KEY_DOWN ? 1
                : key == Keyboard.KEY_UP ? -1 : 0;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tabs == 0 && windows == 0 || minecraft.currentScreen != null
                || !isModifierKeyDown()) {
            return;
        }
        boolean taken = tabs != 0 ? PinnedWindows.walkTabs(minecraft, tabs)
                : PinnedWindows.stepWindow(minecraft, windows);
        if (taken) {
            KeyBinding.setKeyBindState(key, false);
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        handleBindingPresses();
        ThirdPersonCameraRuntime.normalizePerspective(
                Minecraft.getMinecraft());
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
            WindowScreen.openPage(QuestJournalPage.PAGE_ID);
        }
        if (FELLOWSHIP.isPressed()) {
            WindowScreen.openPage(FellowshipPage.PAGE_ID);
        }
        if (CHARACTERS.isPressed()) {
            WindowScreen.openPage(CharactersPage.PAGE_ID);
        }
        if (MAP.isPressed() && minecraft.currentScreen == null) {
            LostTalesLotrMapGui.open();
        }
        if (TOGGLE_HUD.isPressed()) {
            if (isModifierKeyDown()) {
                WindowScreen.openPage(HudPlacementPage.PAGE_ID);
            } else {
                LostTalesHudHelper.toggleLostTalesHud();
            }
        }
        if (USE.isPressed()) {
            // In the character room the key opens the room menu; there
            // is nothing to loot there.
            if (!CharacterRoomSession.openMenu(minecraft)) {
                LostTalesQuickLootHudRenderer.dropSelectedItem();
            }
        }
        if (SWAP_SHOULDER.isPressed()) {
            ThirdPersonCameraRuntime.toggleShoulder(minecraft);
        }
        if (TARGET_LOCK.isPressed()) {
            ThirdPersonTargetLockController.toggle(minecraft);
        }
        if (CYCLE_TARGET_LEFT.isPressed()) {
            ThirdPersonTargetLockController.cycle(minecraft, -1);
        }
        if (CYCLE_TARGET_RIGHT.isPressed()) {
            ThirdPersonTargetLockController.cycle(minecraft, 1);
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.buttonstate && event.button >= 0
                && isMouseButton(TARGET_LOCK, event.button)
                && minecraft != null && minecraft.currentScreen == null
                && LostTalesThirdPersonConfig.enableTargetLock
                && ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft.renderViewEntity)) {
            ThirdPersonTargetLockController.toggle(minecraft);
            event.setCanceled(true);
            return;
        }
        if (event.dwheel == 0 || !isModifierKeyDown()) {
            return;
        }
        if (LostTalesQuickLootHudRenderer.isLookingAtContainer()) {
            LostTalesQuickLootHudRenderer.moveSelection(
                    event.dwheel > 0 ? -1 : 1);
            event.setCanceled(true);
            return;
        }
        if (ThirdPersonCameraRuntime.adjustZoom(
                Minecraft.getMinecraft(), event.dwheel)) {
            event.setCanceled(true);
        }
    }

    public static KeyBinding getUseKeyBinding() {
        return USE;
    }

    public static KeyBinding getModifierKeyBinding() {
        return MODIFIER;
    }

    public static KeyBinding getMapLegendKeyBinding() {
        return MODIFIER;
    }

    public static KeyBinding getCharacterMenuKeyBinding() {
        return CHARACTER_MENU;
    }

    public static KeyBinding getQuestJournalKeyBinding() {
        return QUEST_JOURNAL;
    }

    public static KeyBinding getMapKeyBinding() {
        return MAP;
    }

    public static KeyBinding getFellowshipKeyBinding() {
        return FELLOWSHIP;
    }

    public static KeyBinding getCharactersKeyBinding() {
        return CHARACTERS;
    }

    public static boolean isModifierKeyDown() {
        return isKeyDown(MODIFIER);
    }

    public static String getModifierKeyDisplayName() {
        return getKeyDisplayName(MODIFIER);
    }

    public static boolean isCharacterMenuKey(int keyCode) {
        return isKeyboardKey(CHARACTER_MENU, keyCode);
    }

    public static boolean isQuestJournalKey(int keyCode) {
        return isKeyboardKey(QUEST_JOURNAL, keyCode);
    }

    public static boolean isMapKey(int keyCode) {
        return isKeyboardKey(MAP, keyCode);
    }

    public static boolean isMapMouseButton(int mouseButton) {
        return isMouseButton(MAP, mouseButton);
    }

    public static boolean isMapLegendKey(int keyCode) {
        return isKeyboardKey(MODIFIER, keyCode);
    }

    public static boolean isMapLegendMouseButton(int mouseButton) {
        return isMouseButton(MODIFIER, mouseButton);
    }

    private static boolean isKeyboardKey(KeyBinding keyBinding, int keyCode) {
        return keyBinding != null && keyCode != Keyboard.KEY_NONE && keyBinding.getKeyCode() == keyCode;
    }

    private static boolean isMouseButton(
            KeyBinding keyBinding, int mouseButton) {
        return keyBinding != null && mouseButton >= 0
                && keyBinding.getKeyCode() < Keyboard.KEY_NONE
                && keyBinding.getKeyCode() + 100 == mouseButton;
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

    /**
     * A binding's key written out in the game's language ({@code Left Shift}):
     * the keys with a full name of their own from the lang file, every
     * other key by LWJGL's name for it.
     */
    static String getKeyDisplayName(KeyBinding keyBinding) {
        if (keyBinding == null) return "";
        int keyCode = keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE) {
            return LostTalesInputBinding.word("none");
        }
        if (keyCode < 0) {
            return StatCollector.translateToLocalFormatted(FULL_NAME_PREFIX + "mouse",
                    Integer.valueOf(keyCode + 101));
        }
        return getFriendlyKeyboardName(keyCode);
    }

    private static String getFriendlyKeyboardName(int keyCode) {
        String full = fullNamedKey(keyCode);
        if (full != null) {
            return StatCollector.translateToLocal(FULL_NAME_PREFIX + full);
        }
        switch (keyCode) {
            case Keyboard.KEY_RETURN:
                return LostTalesInputBinding.word("enter");
            case Keyboard.KEY_SPACE:
                return LostTalesInputBinding.word("space");
            default:
                String name = Keyboard.getKeyName(keyCode);
                return name == null
                        ? LostTalesInputBinding.word("code", Integer.valueOf(keyCode))
                        : name;
        }
    }

    /**
     * The id of a key whose full name differs from its short one
     * ({@code left_shift}: Left Shift, not L Shift), or null.
     */
    static String fullNamedKey(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_CAPITAL:
                return "caps";
            case Keyboard.KEY_LMENU:
                return "left_alt";
            case Keyboard.KEY_RMENU:
                return "right_alt";
            case Keyboard.KEY_LSHIFT:
                return "left_shift";
            case Keyboard.KEY_RSHIFT:
                return "right_shift";
            case Keyboard.KEY_LCONTROL:
                return "left_ctrl";
            case Keyboard.KEY_RCONTROL:
                return "right_ctrl";
            case Keyboard.KEY_ESCAPE:
                return "escape";
            case Keyboard.KEY_TAB:
                return "tab";
            default:
                return null;
        }
    }
}
