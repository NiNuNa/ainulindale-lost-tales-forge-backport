package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/** The keys the windows' shortcuts are made with, and how a key is named. */
public final class WindowKeys {
    private WindowKeys() {}

    /** The key every shortcut is made with here: Cmd on a Mac, Ctrl elsewhere. */
    public static int commandKey() {
        return Minecraft.isRunningOnMac ? Keyboard.KEY_LMETA
                : Keyboard.KEY_LCONTROL;
    }

    /** The command key's name in the player's language: Cmd on a Mac, Ctrl elsewhere. */
    public static String commandName() {
        return StatCollector.translateToLocal(Minecraft.isRunningOnMac
                ? "gui.losttales.window.key.command_mac"
                : "gui.losttales.window.key.command");
    }

    /**
     * Words with their shortcut after them, as every tip names its key:
     * {@code Close Page (Ctrl+W)}, {@code Page Search (Ctrl+Shift+A)}.
     */
    public static String withShortcut(String words, boolean shift,
                                      int keyCode) {
        StringBuilder keys = new StringBuilder(commandName()).append('+');
        if (shift) {
            keys.append(LostTalesInputBinding.getFallbackLabel(
                    LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_LSHIFT))
                    .append('+');
        }
        keys.append(LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, keyCode));
        return words + " (" + keys + ")";
    }

    /** The command key with {@code keys}, held together, as a field's hint names them. */
    public static int[] withCommand(int... keys) {
        int[] held = new int[keys.length + 1];
        held[0] = commandKey();
        System.arraycopy(keys, 0, held, 1, keys.length);
        return held;
    }

    /** A key as a search finds it by: its name as a key icon writes it. */
    public static String keyName(int keyCode) {
        return LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, keyCode)
                .toLowerCase(Locale.ROOT);
    }
}
