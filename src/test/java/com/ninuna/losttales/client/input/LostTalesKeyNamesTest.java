package com.ninuna.losttales.client.input;

import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A key with a name of its own is named in the game's language; any
 * other key keeps the name LWJGL gives it, which is the key's label on
 * the keyboard.
 */
public final class LostTalesKeyNamesTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final List<String> KEYS = Arrays.asList(
            LostTalesInputBinding.NAME_PREFIX + "left_shift",
            LostTalesInputBinding.NAME_PREFIX + "escape",
            LostTalesInputBinding.NAME_PREFIX + "none",
            LostTalesInputBinding.NAME_PREFIX + "mouse",
            LostTalesInputBinding.WHEEL_KEY);

    @Before
    public void readInAnotherLanguage() {
        inject(LostTalesInputBinding.NAME_PREFIX + "left_shift=Umschalt L\n"
                + LostTalesInputBinding.NAME_PREFIX + "escape=Echap\n"
                + LostTalesInputBinding.NAME_PREFIX + "none=Keine\n"
                + LostTalesInputBinding.NAME_PREFIX + "mouse=Maus %s\n"
                + LostTalesInputBinding.WHEEL_KEY + "=Rad\n");
    }

    /** The English lines of the keys read here, as the lang file has them. */
    @After
    public void readInEnglishAgain() {
        StringBuilder english = new StringBuilder();
        for (String key : KEYS) {
            english.append(key).append('=')
                    .append(LostTalesLangFile.english().get(key)).append('\n');
        }
        inject(english.toString());
    }

    @Test
    public void namedKeysReadInTheGamesLanguage() {
        assertEquals("Umschalt L", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_LSHIFT));
        assertEquals("Echap", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_ESCAPE));
        assertEquals("Keine", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.UNBOUND, Keyboard.KEY_NONE));
        assertEquals("Maus 2", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.MOUSE_BUTTON, -99));
        assertEquals("Rad", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.MOUSE_WHEEL, 0));
    }

    /** A key without a name of its own keeps LWJGL's, in every language. */
    @Test
    public void otherKeysKeepLwjglsName() {
        assertEquals("F5", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_F5));
        assertEquals("A", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_A));
    }

    /** Every key the code names has its English line, in both its short and its full name. */
    @Test
    public void everyNamedKeyHasItsEnglishLines() {
        for (int keyCode = 0; keyCode <= 255; keyCode++) {
            String named = LostTalesInputBinding.namedKey(keyCode);
            if (named != null) {
                assertTrue(named, EnglishWords.INSTANCE.has(
                        LostTalesInputBinding.NAME_PREFIX + named));
            }
        }
        for (String id : Arrays.asList("cmd", "meta", "none", "mouse", "code")) {
            assertTrue(id, EnglishWords.INSTANCE.has(LostTalesInputBinding.NAME_PREFIX + id));
        }
        for (String id : Arrays.asList("mouse", "caps", "left_alt", "right_alt",
                "left_shift", "right_shift", "left_ctrl", "right_ctrl", "escape", "tab")) {
            assertTrue(id, EnglishWords.INSTANCE.has("gui.losttales.keys.full." + id));
        }
        assertTrue(EnglishWords.INSTANCE.has(LostTalesInputBinding.WHEEL_KEY));
    }

    @Test
    public void englishWordsTheKeysAsBefore() {
        assertEquals("L Shift", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "left_shift"));
        assertEquals("M3", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "mouse", Integer.valueOf(3)));
        assertEquals("Key 250", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "code", Integer.valueOf(250)));
        assertEquals("Left Shift", EnglishWords.INSTANCE.format(
                "gui.losttales.keys.full.left_shift"));
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
