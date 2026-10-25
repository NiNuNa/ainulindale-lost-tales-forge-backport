package com.ninuna.losttales.util;

import org.lwjgl.input.Keyboard;

/**
 * The keys an item's tooltip opens its lines with (CONTENT-08): either
 * Shift shows the lore, either Ctrl the details, as both keys of a pair do
 * in vanilla's own screens. The key codes are constants, so asking reads
 * no keyboard; the caller says which keys are held.
 */
public final class LostTalesTooltipKeys {

    /** Which keys are held now, by LWJGL key code. */
    public interface Held {
        boolean isDown(int keyCode);
    }

    private LostTalesTooltipKeys() {}

    /** Whether either Shift is held: the lore shows. */
    public static boolean showsLore(Held keys) {
        return keys != null && (keys.isDown(Keyboard.KEY_LSHIFT)
                || keys.isDown(Keyboard.KEY_RSHIFT));
    }

    /** Whether either Ctrl is held: the details show. */
    public static boolean showsDetails(Held keys) {
        return keys != null && (keys.isDown(Keyboard.KEY_LCONTROL)
                || keys.isDown(Keyboard.KEY_RCONTROL));
    }
}
