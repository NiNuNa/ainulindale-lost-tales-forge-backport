package com.ninuna.losttales.client.keybinding;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class LostTalesKeyBindingsTest {
    @Test
    public void mapLegendReusesTheLostTalesModifierBinding() {
        assertSame(LostTalesKeyBindings.getModifierKeyBinding(),
                LostTalesKeyBindings.getMapLegendKeyBinding());
        assertEquals(Keyboard.KEY_LMENU,
                LostTalesKeyBindings.getMapLegendKeyBinding().getKeyCode());
    }
}
