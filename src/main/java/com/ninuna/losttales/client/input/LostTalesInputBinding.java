package com.ninuna.losttales.client.input;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;
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
    /** What the lang keys of the keys' short names begin with. */
    public static final String NAME_PREFIX = "gui.losttales.keys.name.";
    /** The mouse wheel's name, the same word the page help uses. */
    public static final String WHEEL_KEY = "gui.losttales.keys.mouse.wheel";
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

    /**
     * The key's short name, as a key icon writes it, in the game's
     * language: the keys with a name of their own from the lang file,
     * every other key by LWJGL's name for it ({@code F5}, {@code A}).
     */
    public static String getFallbackLabel(Type type, int keyCode) {
        if (type == null) {
            return "?";
        }

        switch (type) {
            case KEYBOARD:
                return getKeyboardLabel(keyCode);
            case MOUSE_BUTTON:
                int button = getMouseButtonIndex(keyCode);
                return button < 0 ? "?" : word("mouse", Integer.valueOf(button + 1));
            case MOUSE_WHEEL:
                return StatCollector.translateToLocal(WHEEL_KEY);
            case UNBOUND:
                return word("none");
            case INVALID:
            default:
                return "?";
        }
    }

    private static String getKeyboardLabel(int keyCode) {
        String named = namedKey(keyCode);
        if (named != null) {
            return word(named);
        }
        String name = Keyboard.getKeyName(keyCode);
        return name == null || name.length() == 0
                ? word("code", Integer.valueOf(keyCode)) : name;
    }

    /**
     * The id of a key that has a name of its own in the lang file
     * ({@code left_shift}), or null for one LWJGL names.
     */
    static String namedKey(int keyCode) {
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
            case Keyboard.KEY_LMETA:
            case Keyboard.KEY_RMETA:
                // Cmd on a Mac, where it gives the shortcuts Ctrl gives
                // elsewhere; the system key anywhere else.
                return Minecraft.isRunningOnMac ? "cmd" : "meta";
            case Keyboard.KEY_RETURN:
                return "enter";
            case Keyboard.KEY_BACK:
                return "backspace";
            case Keyboard.KEY_HOME:
                return "home";
            case Keyboard.KEY_END:
                return "end";
            case Keyboard.KEY_PRIOR:
                return "page_up";
            case Keyboard.KEY_NEXT:
                return "page_down";
            case Keyboard.KEY_ESCAPE:
                return "escape";
            case Keyboard.KEY_SPACE:
                return "space";
            default:
                return null;
        }
    }

    /** The short-name line under {@code id}, in the game's language. */
    public static String word(String id, Object... arguments) {
        return StatCollector.translateToLocalFormatted(NAME_PREFIX + id, arguments);
    }

    public enum Type {
        KEYBOARD,
        MOUSE_BUTTON,
        MOUSE_WHEEL,
        UNBOUND,
        INVALID
    }
}
