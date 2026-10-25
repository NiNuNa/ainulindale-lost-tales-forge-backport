package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.client.window.SubWindowKind;

/**
 * The Characters page's kinds of sub-window, registered with the window
 * system as the client starts, before the layout file that remembers
 * their places is read.
 */
public final class CharacterSubWindows {
    /** The world's lore characters, to claim or release. */
    public static final SubWindowKind LORE = kind("lore");
    /** A character's profile and age, in sections picked by word buttons. */
    public static final SubWindowKind PROFILE_EDIT = kind("profile_edit");

    private CharacterSubWindows() {}

    private static SubWindowKind kind(String id) {
        return SubWindowKind.register(id, "gui.losttales.character.sub." + id);
    }

    /** Registers the kinds, which loading this class does. */
    public static void install() {
        // The kinds stand once the class is loaded.
    }
}
