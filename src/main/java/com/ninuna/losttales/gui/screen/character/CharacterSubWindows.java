package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.client.window.SubWindowKind;

/**
 * The Characters tab's kinds of sub-window, registered with the window
 * system as the client starts, before the layout file that remembers
 * their places is read.
 */
public final class CharacterSubWindows {
    /** The capes of one identity. */
    public static final SubWindowKind CAPES = kind("capes");
    /** The world's lore characters, to claim or release. */
    public static final SubWindowKind LORE = kind("lore");
    /** A character's profile and age, under tabs of its own. */
    public static final SubWindowKind PROFILE_EDIT = kind("profile_edit");

    private CharacterSubWindows() {}

    private static SubWindowKind kind(String id) {
        return SubWindowKind.register(id, "gui.losttales.character.sub." + id);
    }

    /** Registers the kinds and the menus behind them. */
    public static void install() {
        CharacterMenus.install();
    }
}
