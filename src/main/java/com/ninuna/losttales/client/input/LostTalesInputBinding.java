package com.ninuna.losttales.client.input;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Identifies the legacy LWJGL input encoded in a Minecraft 1.7.10 key binding.
 *
 * <p>Keyboard key codes are positive, an unbound key uses {@link Keyboard#KEY_NONE},
 * and mouse buttons use {@code -100 + buttonIndex}. The classification methods do
 * not cache a {@link KeyBinding}'s assigned code, so callers always see rebinding
 * changes immediately.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesInputBinding {
    private static final int MOUSE_KEY_CODE_OFFSET = 100;
    private static final int MAX_KEYBOARD_KEY_CODE = 255;

    private LostTalesInputBinding() {}

    public static Type getType(KeyBinding keyBinding) {
        return keyBinding == null ? Type.INVALID : getType(keyBinding.getKeyCode());
    }

    public static Type getType(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return Type.UNBOUND;
        }
        if (keyCode > Keyboard.KEY_NONE && keyCode <= MAX_KEYBOARD_KEY_CODE) {
            return Type.KEYBOARD;
        }
        if (keyCode < Keyboard.KEY_NONE) {
            return getMouseButtonIndex(keyCode) >= 0 ? Type.MOUSE_BUTTON : Type.INVALID;
        }
        return Type.INVALID;
    }

    public static int getMouseButtonIndex(int keyCode) {
        return keyCode + MOUSE_KEY_CODE_OFFSET;
    }

    public static String getFallbackLabel(Type type, int keyCode) {
        if (type == null) {
            return "?";
        }

        switch (type) {
            case KEYBOARD:
                return getKeyboardLabel(keyCode);
            case MOUSE_BUTTON:
                int button = getMouseButtonIndex(keyCode);
                return button < 0 ? "?" : "M" + (button + 1);
            case MOUSE_WHEEL:
                return "Wheel";
            case UNBOUND:
                return "None";
            case INVALID:
            default:
                return "?";
        }
    }

    private static String getKeyboardLabel(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_CAPITAL:
                return "Caps";
            case Keyboard.KEY_LMENU:
                return "L Alt";
            case Keyboard.KEY_RMENU:
                return "R Alt";
            case Keyboard.KEY_LSHIFT:
                return "L Shift";
            case Keyboard.KEY_RSHIFT:
                return "R Shift";
            case Keyboard.KEY_LCONTROL:
                return "L Ctrl";
            case Keyboard.KEY_RCONTROL:
                return "R Ctrl";
            case Keyboard.KEY_RETURN:
                return "Enter";
            case Keyboard.KEY_ESCAPE:
                return "Esc";
            case Keyboard.KEY_SPACE:
                return "Space";
            default:
                String name = Keyboard.getKeyName(keyCode);
                return name == null || name.length() == 0 ? "Key " + keyCode : name;
        }
    }

    public enum Type {
        KEYBOARD,
        MOUSE_BUTTON,
        MOUSE_WHEEL,
        UNBOUND,
        INVALID
    }
}
