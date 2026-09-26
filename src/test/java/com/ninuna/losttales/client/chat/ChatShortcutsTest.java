package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowKeys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Every row of the Shortcuts list reads words of the shipped language
 * file, so none shows its bare key, and no two shortcuts share one; the
 * search finds a key by the name its icon writes. The file is read as it
 * ships, not loaded into the game's translator, which other tests read
 * without it.
 */
public final class ChatShortcutsTest {
    @Test
    public void everyAreaShortcutAndWordHasItsWords() throws IOException {
        Set<String> written = languageKeys();
        for (String key : ChatShortcuts.languageKeys()) {
            assertTrue(key, written.contains(key));
        }
    }

    @Test
    public void aKeyIsFoundByTheNameItsIconWrites() {
        assertEquals("f", WindowKeys.keyName(Keyboard.KEY_F));
        assertEquals("backspace", WindowKeys.keyName(Keyboard.KEY_BACK));
        assertEquals("pgup", WindowKeys.keyName(Keyboard.KEY_PRIOR));
        assertEquals("pgdn", WindowKeys.keyName(Keyboard.KEY_NEXT));
    }

    @Test
    public void noTwoShortcutsShareTheirWords() {
        Set<String> shortcuts = new HashSet<String>();
        for (String key : ChatShortcuts.languageKeys()) {
            if (key.startsWith("gui.losttales.chat.shortcut.")) {
                assertTrue(key, shortcuts.add(key));
            }
        }
        assertTrue(!shortcuts.isEmpty());
    }

    private static Set<String> languageKeys() throws IOException {
        InputStream stream = ChatShortcutsTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        assertNotNull("en_US.lang missing", stream);
        Set<String> keys = new HashSet<String>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0) {
                    keys.add(line.substring(0, equals));
                }
            }
        } finally {
            reader.close();
        }
        return keys;
    }
}
