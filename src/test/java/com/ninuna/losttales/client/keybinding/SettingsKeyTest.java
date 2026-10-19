package com.ninuna.losttales.client.keybinding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

/**
 * WIN-05: the Settings view has its own row in Controls, holding the key
 * pressed with Ctrl: the comma until the player binds another.
 */
public class SettingsKeyTest {

    @After
    public void putTheCommaBack() {
        LostTalesKeyBindings.getSettingsKeyBinding().setKeyCode(Keyboard.KEY_COMMA);
    }

    @Test
    public void theSettingsKeyIsTheCommaAtFirst() {
        assertEquals(Keyboard.KEY_COMMA,
                LostTalesKeyBindings.getSettingsKeyBinding().getKeyCodeDefault());
        assertTrue(LostTalesKeyBindings.isSettingsKey(Keyboard.KEY_COMMA));
        assertEquals(LostTalesKeyBindings.CATEGORY,
                LostTalesKeyBindings.getSettingsKeyBinding().getKeyCategory());
    }

    @Test
    public void aRowBoundToAnotherKeyMovesTheSettingsKey() {
        LostTalesKeyBindings.getSettingsKeyBinding().setKeyCode(Keyboard.KEY_O);
        assertTrue(LostTalesKeyBindings.isSettingsKey(Keyboard.KEY_O));
        assertFalse(LostTalesKeyBindings.isSettingsKey(Keyboard.KEY_COMMA));
    }

    @Test
    public void anUnboundRowOpensNothing() {
        LostTalesKeyBindings.getSettingsKeyBinding().setKeyCode(Keyboard.KEY_NONE);
        assertFalse(LostTalesKeyBindings.isSettingsKey(Keyboard.KEY_NONE));
        assertFalse(LostTalesKeyBindings.isSettingsKey(Keyboard.KEY_COMMA));
    }

    @Test
    public void theRowHasItsNameInControls() throws Exception {
        String key = LostTalesKeyBindings.getSettingsKeyBinding().getKeyDescription();
        InputStream in = SettingsKeyTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        boolean found = false;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + "=") && line.length() > key.length() + 1) {
                    found = true;
                }
            }
        } finally {
            reader.close();
        }
        assertTrue(key, found);
    }
}
