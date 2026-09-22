package com.ninuna.losttales.client.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.input.LostTalesKeyPress.Platform;
import net.minecraft.util.Util;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

public final class LostTalesKeyPressTest {
    private static final char NONE = (char)Keyboard.CHAR_NONE;

    private static LostTalesKeyPress press(char character, int key,
                                           boolean ctrl, boolean leftAlt,
                                           boolean rightAlt,
                                           Platform platform) {
        return LostTalesKeyPress.of(character, key, ctrl, leftAlt, rightAlt,
                false, platform);
    }

    @Test
    public void altGrOnWindowsTypesItsSymbolInsteadOfTheCtrlShortcut() {
        // A German layout: Alt Gr+8 is "[", Alt Gr+Q is "@".
        LostTalesKeyPress bracket = press('[', Keyboard.KEY_8, true, false,
                true, Platform.WINDOWS);
        LostTalesKeyPress at = press('@', Keyboard.KEY_Q, true, false, true,
                Platform.WINDOWS);

        assertTrue(bracket.types);
        assertFalse(bracket.command);
        assertFalse(bracket.isCommand(Keyboard.KEY_8));
        assertTrue(at.types);
        assertFalse(at.alt);
    }

    @Test
    public void ctrlWithAltOnWindowsTypesAsAltGrDoes() {
        assertTrue(press('{', Keyboard.KEY_7, true, true, false,
                Platform.WINDOWS).types);
    }

    @Test
    public void ctrlOnWindowsIsAShortcut() {
        LostTalesKeyPress tab = press(NONE, Keyboard.KEY_1, true,
                false, false, Platform.WINDOWS);
        LostTalesKeyPress find = press((char)6, Keyboard.KEY_F, true, false,
                false, Platform.WINDOWS);

        assertTrue(tab.isCommand(Keyboard.KEY_1));
        assertFalse(tab.types);
        assertTrue(find.isCommand(Keyboard.KEY_F));
    }

    @Test
    public void altGrWithAKeyThatTypesNothingKeepsTheCtrlShortcut() {
        LostTalesKeyPress arrow = press(NONE, Keyboard.KEY_LEFT,
                true, false, true, Platform.WINDOWS);

        assertTrue(arrow.isCommand(Keyboard.KEY_LEFT));
        assertFalse(arrow.alt);
    }

    @Test
    public void altAloneOnWindowsIsAShortcutThoughItCarriesALetter() {
        // Windows hands Alt+Z over with its letter.
        LostTalesKeyPress snap = press('z', Keyboard.KEY_Z, false, true,
                false, Platform.WINDOWS);
        LostTalesKeyPress rightAlt = press('z', Keyboard.KEY_Z, false, false,
                true, Platform.WINDOWS);

        assertFalse(snap.types);
        assertTrue(snap.alt);
        assertFalse(snap.command);
        assertTrue(rightAlt.alt);
    }

    @Test
    public void ctrlOnLinuxIsAShortcutThoughItCarriesTheDigit() {
        LostTalesKeyPress tab = press('1', Keyboard.KEY_1, true, false, false,
                Platform.OTHER);

        assertFalse(tab.types);
        assertTrue(tab.isCommand(Keyboard.KEY_1));
    }

    @Test
    public void theRightAltOnLinuxIsAltGr() {
        LostTalesKeyPress polish = press('ł', Keyboard.KEY_L, false,
                false, true, Platform.OTHER);
        LostTalesKeyPress snap = press('z', Keyboard.KEY_Z, false, true, false,
                Platform.OTHER);

        assertTrue(polish.types);
        assertFalse(polish.alt);
        assertFalse(snap.types);
        assertTrue(snap.alt);
    }

    @Test
    public void optionOnAMacTypesAndCmdIsTheShortcut() {
        LostTalesKeyPress omega = press('Ω', Keyboard.KEY_Z, false, true,
                false, Platform.MAC);
        LostTalesKeyPress tab = press('1', Keyboard.KEY_1, true, false, false,
                Platform.MAC);
        LostTalesKeyPress arrow = press(NONE, Keyboard.KEY_LEFT,
                false, true, false, Platform.MAC);

        assertTrue(omega.types);
        assertFalse(omega.alt);
        assertTrue(tab.isCommand(Keyboard.KEY_1));
        assertTrue(arrow.alt);
    }

    @Test
    public void aCharacterAFieldRefusesStillTypesAndNeverReachesAShortcut() {
        // Alt Gr+3 on some layouts: a character Minecraft's fields refuse.
        LostTalesKeyPress section = press('§', Keyboard.KEY_3, true,
                false, true, Platform.WINDOWS);

        assertTrue(section.types);
        assertFalse(section.isCommand(Keyboard.KEY_3));
    }

    @Test
    public void controlCharactersTypeNothing() {
        assertFalse(press((char)127, Keyboard.KEY_BACK, false, false, false,
                Platform.WINDOWS).types);
        assertFalse(press('\t', Keyboard.KEY_TAB, false, false, false,
                Platform.OTHER).types);
        assertTrue(press('a', Keyboard.KEY_A, false, false, false,
                Platform.WINDOWS).types);
    }

    @Test
    public void shiftIsKeptAndChangesNothingElse() {
        LostTalesKeyPress search = LostTalesKeyPress.of((char)1,
                Keyboard.KEY_A, true, false, false, true, Platform.WINDOWS);
        LostTalesKeyPress capital = LostTalesKeyPress.of('A', Keyboard.KEY_A,
                false, false, false, true, Platform.WINDOWS);

        assertTrue(search.shift);
        assertTrue(search.isCommand(Keyboard.KEY_A));
        assertTrue(capital.types);
    }

    @Test
    public void eachSystemMapsToHowItReportsAltGr() {
        assertEquals(Platform.WINDOWS,
                LostTalesKeyPress.platformOf(Util.EnumOS.WINDOWS));
        assertEquals(Platform.MAC,
                LostTalesKeyPress.platformOf(Util.EnumOS.OSX));
        assertEquals(Platform.OTHER,
                LostTalesKeyPress.platformOf(Util.EnumOS.LINUX));
        assertEquals(Platform.OTHER,
                LostTalesKeyPress.platformOf(Util.EnumOS.UNKNOWN));
    }
}
