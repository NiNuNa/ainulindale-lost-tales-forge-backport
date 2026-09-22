package com.ninuna.losttales.client.input;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Util;
import org.lwjgl.input.Keyboard;

/**
 * One key press on a screen, read once: the key, the character it typed,
 * and whether it is typing or a shortcut. A press that types a character
 * is typing and nothing else, so a shortcut never takes a symbol: the
 * brackets, braces and {@code @} that many keyboard layouts put under
 * Alt Gr stay typeable in every field, even where Ctrl with the same key
 * is a shortcut. A shortcut is a press that types nothing.
 *
 * <p>LWJGL hands the typed character over with the press, and each
 * system reports Alt Gr its own way. Windows holds the left Ctrl down
 * with the right Alt, and types with Ctrl and Alt held together as it
 * does with Alt Gr. Linux reports the right Alt alone. A Mac types with
 * Option and gives its shortcuts to Cmd, which Minecraft reads as
 * Ctrl.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesKeyPress {
    /** How a system reports the keys that type characters. */
    enum Platform {
        /** Ctrl with Alt is Alt Gr; either alone makes a shortcut. */
        WINDOWS,
        /** Option types characters; Cmd makes a shortcut. */
        MAC,
        /** The right Alt is Alt Gr; Ctrl or the left Alt make a shortcut. */
        OTHER
    }

    private static final Platform PLATFORM = platformOf(Util.getOSType());

    /** The character the press typed, or {@link Keyboard#CHAR_NONE}. */
    public final char character;
    /** The key pressed, as a {@link Keyboard} key code. */
    public final int key;
    /** Whether the press types {@link #character}. */
    public final boolean types;
    /** Whether Ctrl (Cmd on a Mac) is held and the press types nothing. */
    public final boolean command;
    /** Whether Alt (Option on a Mac) is held without Ctrl and the press types nothing. */
    public final boolean alt;
    /** Whether Shift is held. */
    public final boolean shift;

    private LostTalesKeyPress(char character, int key, boolean types,
                              boolean command, boolean alt, boolean shift) {
        this.character = character;
        this.key = key;
        this.types = types;
        this.command = command;
        this.alt = alt;
        this.shift = shift;
    }

    /** The press a screen's {@code keyTyped} was handed, with the modifiers held now. */
    public static LostTalesKeyPress read(char character, int key) {
        boolean keyboard = Keyboard.isCreated();
        return of(character, key, keyboard && GuiScreen.isCtrlKeyDown(),
                keyboard && Keyboard.isKeyDown(Keyboard.KEY_LMENU),
                keyboard && Keyboard.isKeyDown(Keyboard.KEY_RMENU),
                keyboard && GuiScreen.isShiftKeyDown(), PLATFORM);
    }

    /** A press with the modifiers given: Ctrl is Cmd on a Mac. */
    static LostTalesKeyPress of(char character, int key, boolean ctrl,
                                boolean leftAlt, boolean rightAlt,
                                boolean shift, Platform platform) {
        boolean types = typesCharacter(character, ctrl, leftAlt, rightAlt,
                platform);
        return new LostTalesKeyPress(character, key, types, ctrl && !types,
                (leftAlt || rightAlt) && !ctrl && !types, shift);
    }

    /** Whether {@code key} was pressed, whatever it typed. */
    public boolean is(int key) {
        return this.key == key;
    }

    /** Whether this is Ctrl (Cmd on a Mac) with {@code key}, typing nothing. */
    public boolean isCommand(int key) {
        return this.command && this.key == key;
    }

    static boolean typesCharacter(char character, boolean ctrl,
                                  boolean leftAlt, boolean rightAlt,
                                  Platform platform) {
        if (Character.isISOControl(character)) {
            return false;
        }
        switch (platform) {
            case WINDOWS:
                return ctrl == (leftAlt || rightAlt);
            case MAC:
                return !ctrl;
            default:
                return !ctrl && !leftAlt;
        }
    }

    static Platform platformOf(Util.EnumOS system) {
        if (system == Util.EnumOS.WINDOWS) {
            return Platform.WINDOWS;
        }
        return system == Util.EnumOS.OSX ? Platform.MAC : Platform.OTHER;
    }
}
