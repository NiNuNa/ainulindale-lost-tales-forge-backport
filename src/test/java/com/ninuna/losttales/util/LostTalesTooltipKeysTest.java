package com.ninuna.losttales.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Either Shift opens a tooltip's lore and either Ctrl its details (CONTENT-08). */
public final class LostTalesTooltipKeysTest {

    @Test
    public void eitherShiftShowsTheLore() {
        assertTrue(LostTalesTooltipKeys.showsLore(held(Keyboard.KEY_LSHIFT)));
        assertTrue(LostTalesTooltipKeys.showsLore(held(Keyboard.KEY_RSHIFT)));
        assertTrue(LostTalesTooltipKeys.showsLore(
                held(Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT)));
    }

    @Test
    public void eitherCtrlShowsTheDetails() {
        assertTrue(LostTalesTooltipKeys.showsDetails(held(Keyboard.KEY_LCONTROL)));
        assertTrue(LostTalesTooltipKeys.showsDetails(held(Keyboard.KEY_RCONTROL)));
    }

    @Test
    public void otherKeysShowNeither() {
        LostTalesTooltipKeys.Held none = held();
        assertFalse(LostTalesTooltipKeys.showsLore(none));
        assertFalse(LostTalesTooltipKeys.showsDetails(none));
        LostTalesTooltipKeys.Held alt = held(Keyboard.KEY_LMENU, Keyboard.KEY_RMENU,
                Keyboard.KEY_LMETA);
        assertFalse(LostTalesTooltipKeys.showsLore(alt));
        assertFalse(LostTalesTooltipKeys.showsDetails(alt));
        assertFalse("Ctrl opens no lore",
                LostTalesTooltipKeys.showsLore(held(Keyboard.KEY_RCONTROL)));
        assertFalse("Shift opens no details",
                LostTalesTooltipKeys.showsDetails(held(Keyboard.KEY_RSHIFT)));
        assertFalse(LostTalesTooltipKeys.showsLore(null));
        assertFalse(LostTalesTooltipKeys.showsDetails(null));
    }

    private static LostTalesTooltipKeys.Held held(Integer... keys) {
        final Set<Integer> down = new HashSet<Integer>(Arrays.asList(keys));
        return new LostTalesTooltipKeys.Held() {
            @Override
            public boolean isDown(int keyCode) {
                return down.contains(Integer.valueOf(keyCode));
            }
        };
    }
}
