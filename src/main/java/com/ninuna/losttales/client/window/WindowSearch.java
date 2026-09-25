package com.ninuna.losttales.client.window;

/**
 * The one search the windows hold at a time: the window it is open over,
 * and the words typed into that window's well. What the words find is the
 * content's business — a conversation lights its lines, a page narrows
 * its list — and each reads the words from here.
 */
public final class WindowSearch {
    private static String windowId;
    private static String query = "";
    /**
     * Goes up with every close, and so with every open over another
     * window: what was found for one search is never kept for the next.
     */
    private static int generation;

    private WindowSearch() {}

    public static synchronized boolean isOpen() {
        return windowId != null;
    }

    public static synchronized boolean isOpenOn(String id) {
        return windowId != null && windowId.equals(id);
    }

    /** The words as typed; empty while the search is closed. */
    public static synchronized String query() {
        return query;
    }

    /** The window the search is open over; null while it is closed. */
    public static synchronized String windowId() {
        return windowId;
    }

    /** See {@link #generation}. */
    public static synchronized int generation() {
        return generation;
    }

    /** Opens the search over a window; the one already open there is kept. */
    public static synchronized void open(String id) {
        if (id == null || id.equals(windowId)) {
            return;
        }
        close();
        windowId = id;
    }

    public static synchronized void close() {
        windowId = null;
        query = "";
        generation++;
    }

    /** Closes the search unless it is over the window being typed in. */
    public static synchronized void closeUnless(String activeWindowId) {
        if (windowId != null && !windowId.equals(activeWindowId)) {
            close();
        }
    }

    /** The words as typed. */
    public static synchronized void setQuery(String text) {
        query = text == null ? "" : text;
    }
}
