package com.ninuna.losttales.client.window;

import java.util.HashSet;
import java.util.Set;

/**
 * What the screen shows: the pages of the category whose key opened it —
 * T the channels and whispers, {@code /} the consoles, a page's key or
 * Ctrl+, that page's category ({@link PageCategory#home}) — every page of
 * a window pinned to the GUI ({@link WindowLayout#isOnGui}), and every page
 * the player opened by hand since. The rest wait hidden in their windows
 * for their own key; a window with nothing shown is not drawn. With no
 * screen open every page counts as shown. For the session only: a view
 * ends as the screen closes.
 */
public final class WindowView {
    /** The category the screen shows; null while no screen is open. */
    private static PageCategory category;
    /** Tabs the player opened by hand while this view stands. */
    private static final Set<WindowPage> BY_HAND = new HashSet<WindowPage>();
    /** Whether the windows pinned to the HUD are being drawn: only what the HUD shows counts. */
    private static boolean pinnedPass;

    private WindowView() {}

    /** The screen opens for the chat: the channels and the whispers. */
    public static synchronized void forChat() {
        set(PageCategory.CHANNELS);
    }

    /** The screen opens for a command: the consoles. */
    public static synchronized void forConsole() {
        set(PageCategory.CONSOLES);
    }

    /** The screen opens for a page, or turns to it by its key: the page's category. */
    public static synchronized void forPage(OtherPage shown) {
        set(shown == null ? PageCategory.CHANNELS : shown.category().home());
    }

    /** The screen turns to a category's pages, as that category's key would. */
    public static synchronized void forCategory(PageCategory shown) {
        set(shown == null ? PageCategory.CHANNELS : shown.home());
    }

    /** The screen closed: every tab counts as shown again. */
    public static synchronized void clear() {
        set(null);
    }

    private static void set(PageCategory next) {
        category = next;
        BY_HAND.clear();
    }

    /**
     * The windows pinned to the HUD are about to be drawn: until
     * {@link #endPinnedPass}, only the tabs the HUD shows count as shown.
     */
    static synchronized void beginPinnedPass() {
        pinnedPass = true;
    }

    static synchronized void endPinnedPass() {
        pinnedPass = false;
    }

    /** Whether the windows pinned to the HUD are being drawn now. */
    static synchronized boolean inPinnedPass() {
        return pinnedPass;
    }

    /** A tab the player opened by hand joins what is shown until the screen closes. */
    public static synchronized void show(WindowPage tab) {
        if (tab != null && category != null && !shows(tab)) {
            BY_HAND.add(tab);
        }
    }

    /** The category the screen shows; null while no screen is open. */
    public static synchronized PageCategory category() {
        return category;
    }

    /** Whether the view stands for this page's category: a key of it turned the screen there. */
    public static synchronized boolean isFor(OtherPage shown) {
        return category != null && shown != null
                && shown.category().home() == category;
    }

    /** Whether the view shows the tab, whether or not it can be shown now. */
    public static synchronized boolean shows(WindowPage tab) {
        if (tab == null) {
            return false;
        }
        if (pinnedPass) {
            return WindowLayout.isOnHud(tab);
        }
        return category == null || WindowLayout.isOnGui(tab)
                || BY_HAND.contains(tab) || tab.category().home() == category;
    }

    /** Whether the tab stands on screen now: the view shows it and it can be shown. */
    public static boolean isShown(WindowPage tab) {
        return tab != null && tab.isAvailable() && shows(tab);
    }
}
