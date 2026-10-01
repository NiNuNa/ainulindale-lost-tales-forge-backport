package com.ninuna.losttales.client.window;

import java.util.HashSet;
import java.util.Set;

/**
 * What the screen shows: the tabs of the key that opened it — T the
 * conversations, a page's key or Ctrl+, that page —
 * every tab of a window pinned to the GUI ({@link WindowLayout#isOnGui}),
 * and every tab the player opened by hand since. The rest wait hidden in
 * their windows for their own key; a window with nothing shown is not
 * drawn. With no screen open every tab counts as shown. For the session
 * only: a view ends as the screen closes.
 */
public final class WindowView {
    /** What a view is for. */
    public enum Kind {
        /** No screen is open: every tab counts as shown. */
        NONE,
        /** The chat's key: the conversations. */
        CHAT,
        /** A page's key: that page. */
        PAGE
    }

    private static Kind kind = Kind.NONE;
    /** The page a page's view is for; null otherwise. */
    private static PageTab page;
    /** Tabs the player opened by hand while this view stands. */
    private static final Set<WindowTab> BY_HAND = new HashSet<WindowTab>();
    /** Whether the windows pinned to the HUD are being drawn: only what the HUD shows counts. */
    private static boolean pinnedPass;

    private WindowView() {}

    /** The screen opens for the chat: the conversations. */
    public static synchronized void forChat() {
        set(Kind.CHAT, null);
    }

    /** The screen opens for a page, or turns to it by its key: that page alone. */
    public static synchronized void forPage(PageTab shown) {
        set(Kind.PAGE, shown);
    }

    /** The screen closed: every tab counts as shown again. */
    public static synchronized void clear() {
        set(Kind.NONE, null);
    }

    private static void set(Kind next, PageTab shown) {
        kind = next;
        page = shown;
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
    public static synchronized void show(WindowTab tab) {
        if (tab != null && kind != Kind.NONE && !shows(tab)) {
            BY_HAND.add(tab);
        }
    }

    public static synchronized Kind kind() {
        return kind;
    }

    /** Whether the view stands for this page: its key turned the screen to it. */
    public static synchronized boolean isFor(PageTab shown) {
        return kind == Kind.PAGE && shown != null && shown.equals(page);
    }

    /** Whether the view shows the tab, whether or not it can be shown now. */
    public static synchronized boolean shows(WindowTab tab) {
        if (tab == null) {
            return false;
        }
        if (pinnedPass) {
            return WindowLayout.isOnHud(tab);
        }
        if (kind == Kind.NONE || WindowLayout.isOnGui(tab)
                || BY_HAND.contains(tab)) {
            return true;
        }
        switch (kind) {
            case CHAT:
                return !(tab instanceof PageTab);
            case PAGE:
                return tab.equals(page);
            default:
                return false;
        }
    }

    /** Whether the tab stands on screen now: the view shows it and it can be shown. */
    public static boolean isShown(WindowTab tab) {
        return tab != null && tab.isAvailable() && shows(tab);
    }
}
