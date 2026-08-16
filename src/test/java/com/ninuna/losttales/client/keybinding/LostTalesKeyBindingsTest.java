package com.ninuna.losttales.client.keybinding;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class LostTalesKeyBindingsTest {
    @Test
    public void mapLegendReusesTheLostTalesModifierBinding() {
        assertSame(LostTalesKeyBindings.getModifierKeyBinding(),
                LostTalesKeyBindings.getMapLegendKeyBinding());
        assertEquals(Keyboard.KEY_LMENU,
                LostTalesKeyBindings.getMapLegendKeyBinding().getKeyCode());
    }

    @Test
    public void mapHasItsOwnRebindableMKey() {
        assertEquals(Keyboard.KEY_M,
                LostTalesKeyBindings.getMapKeyBinding().getKeyCode());
        assertTrue(LostTalesKeyBindings.isMapKey(Keyboard.KEY_M));
    }
}
