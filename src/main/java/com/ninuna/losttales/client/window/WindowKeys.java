package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** The keys the windows' shortcuts are made with, and how a key is named. */
public final class WindowKeys {
    private WindowKeys() {}

    /** The key every shortcut is made with here: Cmd on a Mac, Ctrl elsewhere. */
    public static int commandKey() {
        return Minecraft.isRunningOnMac ? Keyboard.KEY_LMETA
                : Keyboard.KEY_LCONTROL;
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
