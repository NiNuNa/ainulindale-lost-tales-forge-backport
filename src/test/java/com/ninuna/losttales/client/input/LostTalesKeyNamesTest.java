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

    /** Letters, digits and the function keys stand on their keys as they are, in every language (MAP-17). */
    @Test
    public void lettersDigitsAndFunctionKeysStandAsTheyAre() {
        assertEquals("F5", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_F5));
        assertEquals("F12", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_F12));
        assertEquals("A", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_A));
        assertEquals("7", LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, Keyboard.KEY_7));
        for (int keyCode = 0; keyCode <= 255; keyCode++) {
            String name = Keyboard.getKeyName(keyCode);
            if (name != null && name.length() == 1
                    && Character.isLetterOrDigit(name.charAt(0))) {
                assertEquals(null, LostTalesInputBinding.namedKey(keyCode));
                assertEquals(null, LostTalesInputBinding.numpadKey(keyCode));
            }
        }
    }

    /**
     * Every other key of a keyboard is named by the lang file: the arrows,
     * Tab, Delete, the signs, the locks and the number pad, whose keys
     * read Num and their digit or sign.
     */
    @Test
    public void everyOtherKeyIsNamedByTheLangFile() {
        int[] keys = {Keyboard.KEY_TAB, Keyboard.KEY_DELETE, Keyboard.KEY_INSERT,
                Keyboard.KEY_UP, Keyboard.KEY_DOWN, Keyboard.KEY_LEFT,
                Keyboard.KEY_RIGHT, Keyboard.KEY_MINUS, Keyboard.KEY_EQUALS,
                Keyboard.KEY_LBRACKET, Keyboard.KEY_RBRACKET,
                Keyboard.KEY_SEMICOLON, Keyboard.KEY_APOSTROPHE,
                Keyboard.KEY_GRAVE, Keyboard.KEY_BACKSLASH, Keyboard.KEY_COMMA,
                Keyboard.KEY_PERIOD, Keyboard.KEY_SLASH, Keyboard.KEY_NUMLOCK,
                Keyboard.KEY_SCROLL, Keyboard.KEY_SYSRQ, Keyboard.KEY_PAUSE,
                Keyboard.KEY_APPS};
        for (int keyCode : keys) {
            assertTrue(Keyboard.getKeyName(keyCode),
                    LostTalesInputBinding.namedKey(keyCode) != null);
        }
        assertEquals("5", LostTalesInputBinding.numpadKey(Keyboard.KEY_NUMPAD5));
        assertEquals("+", LostTalesInputBinding.numpadKey(Keyboard.KEY_ADD));
        assertEquals("/", LostTalesInputBinding.numpadKey(Keyboard.KEY_DIVIDE));
        assertEquals("Num 5", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "numpad", "5"));
        assertEquals(",", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "comma"));
        assertEquals("=", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "equals"));
        assertEquals(String.valueOf((char)92), EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "backslash"));
        assertEquals("Tab", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "tab"));
        assertEquals("Up", EnglishWords.INSTANCE.format(
                LostTalesInputBinding.NAME_PREFIX + "up"));
    }

    /** A name on a key is written in the font's own glyphs, which draw where a key's face stands. */
    @Test
    public void everyEnglishKeyNameIsPlainAscii() {
        for (int keyCode = 0; keyCode <= 255; keyCode++) {
            String named = LostTalesInputBinding.namedKey(keyCode);
            if (named == null) {
                continue;
            }
            String words = EnglishWords.INSTANCE.format(
                    LostTalesInputBinding.NAME_PREFIX + named);
            for (int index = 0; index < words.length(); index++) {
                char each = words.charAt(index);
                assertTrue(named, each >= 32 && each < 127);
            }
        }
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
        for (String id : Arrays.asList("cmd", "meta", "none", "mouse", "code", "numpad")) {
            assertTrue(id, EnglishWords.INSTANCE.has(LostTalesInputBinding.NAME_PREFIX + id));
        }
        for (String id : Arrays.asList("mouse", "caps", "left_alt", "right_alt",
                "left_shift", "right_shift", "left_ctrl", "right_ctrl", "escape", "tab",
                "delete", "insert", "page_up", "page_down", "num_lock", "scroll_lock",
                "print_screen")) {
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
