package com.ninuna.losttales.client.window;

/**
 * What the screen shows: one view at a time ({@link Views}), its own
 * windows, and every window pinned to the GUI
 * ({@link WindowLayout#isOnGui}). A view's key opens the screen on it, and
 * a button on the Views sub-window, or a key on the screen, swaps it. The
 * other views' windows wait hidden; a window with nothing shown is not
 * drawn. With no screen open every page counts as shown. For the session
 * only: the view ends as the screen closes.
 */
public final class WindowView {
    /** The view the screen shows; null while no screen is open. */
    private static View current;
    /** Whether the windows pinned to the HUD are being drawn: only what the HUD shows counts. */
    private static boolean pinnedPass;

    private WindowView() {}

    /** The screen opens for the chat: the Chat view. */
    public static synchronized void forChat() {
        current = Views.of(PageCategory.CHANNELS);
    }

    /** The screen opens for a command: the Consoles view. */
    public static synchronized void forConsole() {
        current = Views.of(PageCategory.CONSOLES);
    }

    /** The screen opens for a page, or swaps to it by its key: its category's view. */
    public static synchronized void forPage(OtherPage shown) {
        current = Views.of(shown == null ? PageCategory.CHANNELS
                : shown.category());
    }

    /** The screen opens on a view, or swaps to it. */
    public static synchronized void forView(View view) {
        current = view == null ? Views.menu() : view;
    }

    /** The screen closed: every tab counts as shown again. */
    public static synchronized void clear() {
        current = null;
    }

    /** The view the screen shows; null while no screen is open. */
    public static synchronized View current() {
        return current;
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

    /**
     * A tab the player went to by hand: the screen swaps to the view of
     * the window holding it while the view shown does not show it.
     */
    public static synchronized void show(WindowPage tab) {
        if (tab == null || current == null || shows(tab)) {
            return;
        }
        Window window = WindowLayout.windowOf(tab);
        if (window != null) {
            current = WindowLayout.viewOf(window);
        }
    }

    /**
     * The view a page opened by hand goes to now: the one the screen
     * shows, whatever the page's category; its category's with no screen
     * open.
     */
    public static synchronized View handView(WindowPage tab) {
        return current != null ? current : Views.of(tab.category());
    }

    /** Whether the screen shows this view now: its button on the Views sub-window stands lit. */
    public static synchronized boolean isOn(View view) {
        return current != null && current == view;
    }

    /** Whether the screen shows this page's category's view. */
    public static synchronized boolean isFor(OtherPage shown) {
        return shown != null && isOn(Views.of(shown.category()));
    }

    /**
     * Whether the view shows the tab, whether or not it can be shown now:
     * it stands in a window of the view, or one pinned to the GUI.
     */
    public static synchronized boolean shows(WindowPage tab) {
        if (tab == null) {
            return false;
        }
        if (pinnedPass) {
            return WindowLayout.isOnHud(tab);
        }
        if (current == null || WindowLayout.isOnGui(tab)) {
            return true;
        }
        Window window = WindowLayout.windowOf(tab);
        return window != null && WindowLayout.viewOf(window) == current;
    }

    /** Whether the tab stands on screen now: the view shows it and it can be shown. */
    public static boolean isShown(WindowPage tab) {
        return tab != null && tab.isAvailable() && shows(tab);
    }
}
