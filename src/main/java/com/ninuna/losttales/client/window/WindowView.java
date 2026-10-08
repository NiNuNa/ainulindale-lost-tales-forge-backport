package com.ninuna.losttales.client.window;

/**
 * What the screen shows: one view at a time ({@link PageCategory#isView}),
 * its own windows, and every window pinned to the GUI
 * ({@link WindowLayout#isOnGui}). A category's key opens the screen on its
 * view (T the channels and whispers, {@code /} the consoles, M the map);
 * the Lost Tales Menu key on the Lost Tales Menu's view, whose windows hold
 * whatever the player opened there. A button on the Views sub-window, or
 * a key on the screen, swaps the view. The other views' windows wait
 * hidden; a window with nothing shown is not drawn. With no screen open
 * every page counts as shown. For the session only: the view ends as the
 * screen closes.
 */
public final class WindowView {
    /** The view the screen shows; null while no screen is open. */
    private static PageCategory current;
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

    /** The screen opens for a page, or swaps to it by its key: the page's view. */
    public static synchronized void forPage(OtherPage shown) {
        set(shown == null ? PageCategory.CHANNELS : shown.category().home());
    }

    /** The screen swaps to a view: a category's, or the Lost Tales Menu's. */
    static synchronized void forView(PageCategory view) {
        set(view == null ? PageCategory.MENU : view.home());
    }

    /** The screen closed: every tab counts as shown again. */
    public static synchronized void clear() {
        set(null);
    }

    private static void set(PageCategory next) {
        current = next;
        if (next != null) {
            // A window closed while locked comes back as its view opens.
            WindowLayout.reopen(new WindowLayout.TabFilter() {
                @Override
                public boolean matches(WindowPage tab) {
                    return inView(tab);
                }
            });
        }
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
     * A tab the player went to by hand: the screen swaps to the view that
     * shows it while the view shown does not: the Lost Tales Menu's for a
     * page in one of its windows, else the page's category's.
     */
    public static synchronized void show(WindowPage tab) {
        if (tab == null || current == null || shows(tab)) {
            return;
        }
        Window window = WindowLayout.windowOf(tab);
        if (window == null) {
            return;
        }
        PageCategory view = WindowLayout.viewOf(window);
        PageCategory home = tab.category().home();
        set(view == PageCategory.MENU || home == PageCategory.NEW_PAGE
                ? view : home);
    }

    /**
     * The view a page opened by hand goes to now: its category's while that
     * view is shown, else the Lost Tales Menu's, where anything may stand;
     * its category's with no screen open.
     */
    public static synchronized PageCategory handView(WindowPage tab) {
        PageCategory own = tab.category().home();
        return current == null || current == own ? own : PageCategory.MENU;
    }

    /** Whether the screen shows this view now: its button on the Views sub-window stands lit. */
    public static synchronized boolean isOn(PageCategory view) {
        return current != null && view != null && current == view.home();
    }

    /** Whether the screen shows this page's category's view. */
    public static synchronized boolean isFor(OtherPage shown) {
        return shown != null && isOn(shown.category());
    }

    /** Whether the view shows the tab, whether or not it can be shown now. */
    public static synchronized boolean shows(WindowPage tab) {
        if (tab == null) {
            return false;
        }
        // A window closed while locked waits out of sight for its view.
        if ((current != null || pinnedPass) && WindowLayout.isClosedAway(tab)) {
            return false;
        }
        return inView(tab);
    }

    /** Whether the view stands for the tab, its window closed or not. */
    private static boolean inView(WindowPage tab) {
        if (pinnedPass) {
            return WindowLayout.isOnHud(tab);
        }
        if (current == null || WindowLayout.isOnGui(tab)) {
            return true;
        }
        // A window of the Lost Tales Menu's view shows whole there and
        // nowhere else; in a category's window each page shows in its own
        // category's view, a New Page in the view of the window it is in.
        Window window = WindowLayout.windowOf(tab);
        PageCategory view = window == null ? null : WindowLayout.viewOf(window);
        if (view == PageCategory.MENU || current == PageCategory.MENU) {
            return view == current;
        }
        PageCategory home = tab.category().home();
        return current == (home == PageCategory.NEW_PAGE && view != null
                ? view : home);
    }

    /** Whether the tab stands on screen now: the view shows it and it can be shown. */
    public static boolean isShown(WindowPage tab) {
        return tab != null && tab.isAvailable() && shows(tab);
    }
}
