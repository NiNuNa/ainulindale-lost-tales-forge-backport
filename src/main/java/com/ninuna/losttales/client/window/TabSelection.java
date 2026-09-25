package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tabs a player has marked in one window's row, the way a file list
 * marks several of its rows: a plain click leaves one tab marked,
 * Shift+click adds and removes, and what is marked can then be moved or
 * closed as a group.
 *
 * <p>This is pointer state, not layout state. It names tabs but owns
 * nothing: closing a marked tab closes the tab, and forgetting the
 * marks changes no channel, no window and no file. It is deliberately
 * kept out of {@link WindowLayout} for that reason, and lives only
 * as long as the session: the chat drops it with the rest of the
 * client's chat state when a session begins.</p>
 *
 * <p>The marks belong to <em>one</em> window at a time: marking a tab in
 * another window forgets the previous window's marks, so a group can
 * never span two rows and a move can never mean two things.</p>
 *
 * <p>A set of marks is <em>anchored</em> on the tab in front and always
 * holds it. Shift+clicking beside that tab extends what is already
 * there rather than starting somewhere else, so closing or dragging a
 * group takes the tab being typed in with it; Shift+clicking the anchor
 * itself does nothing, since a group with nothing in front of it would
 * leave the input pointing outside its own selection. Marking still
 * never <em>moves</em> the input: the anchor is read from the window,
 * not set here.</p>
 */
public final class TabSelection {
    private static String windowId;
    private static final Set<WindowTab> TABS = new HashSet<WindowTab>();

    private TabSelection() {}

    /** The window the marks belong to, or null when nothing is marked. */
    public static synchronized String windowId() {
        return TABS.isEmpty() ? null : windowId;
    }

    /** Whether the tab is marked; false for a tab in any other window. */
    public static synchronized boolean isSelected(WindowTab tab) {
        return tab != null && TABS.contains(tab);
    }

    /** Whether more than one tab is marked, which is what a group needs. */
    public static synchronized boolean isGroup() {
        return TABS.size() > 1;
    }

    /**
     * Leaves the one tab marked, in its own window: what a plain click
     * does. A null tab forgets the marks.
     */
    public static synchronized void selectOnly(String window, WindowTab tab) {
        TABS.clear();
        windowId = window;
        if (tab != null && window != null) {
            TABS.add(tab);
        }
    }

    /**
     * Adds the tab to the marks, or takes it out again: what Shift+click
     * does. A set that is being started — nothing marked, or the marks
     * belonging to another window — is seeded with {@code anchor}, the
     * tab in front of the window being marked in, so the marks always
     * hold it. The anchor itself is never taken out.
     */
    public static synchronized void toggle(String window, WindowTab anchor,
                                    WindowTab tab) {
        if (tab == null || window == null) {
            return;
        }
        if (!window.equals(windowId) || TABS.isEmpty()) {
            selectOnly(window, anchor);
            TABS.add(tab);
            return;
        }
        if (tab.equals(anchor)) {
            return;
        }
        if (!TABS.remove(tab)) {
            TABS.add(tab);
        }
    }

    /**
     * Marks exactly these tabs, in the given window: what a group keeps
     * after it has been carried somewhere else.
     */
    static synchronized void selectAll(String window, List<WindowTab> tabs) {
        TABS.clear();
        windowId = window;
        if (tabs != null && window != null) {
            TABS.addAll(tabs);
        }
        if (TABS.isEmpty()) {
            windowId = null;
        }
    }

    /**
     * The marked tabs of the window, in its own row order; empty for
     * every other window. The order is the row's, so a group moves and
     * closes in the order it was shown in.
     */
    public static synchronized List<WindowTab> selectedIn(Window window) {
        List<WindowTab> result = new ArrayList<WindowTab>();
        if (window == null || TABS.isEmpty()
                || !window.getId().equals(windowId)) {
            return result;
        }
        List<WindowTab> tabs = window.getTabs();
        for (int index = 0; index < tabs.size(); index++) {
            if (TABS.contains(tabs.get(index))) {
                result.add(tabs.get(index));
            }
        }
        return result;
    }

    /**
     * Drops marks that name nothing any more: a tab that was closed,
     * moved to another window, or a window that is gone. Called after
     * every layout change the screen makes, so the marks always describe
     * tabs that are really in the row.
     */
    public static synchronized void prune() {
        Window window = WindowLayout.window(windowId);
        if (window == null) {
            clear();
            return;
        }
        TABS.retainAll(window.getTabs());
        if (TABS.isEmpty()) {
            windowId = null;
        }
    }

    public static synchronized void clear() {
        TABS.clear();
        windowId = null;
    }
}
