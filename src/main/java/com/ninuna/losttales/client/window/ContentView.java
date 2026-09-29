package com.ninuna.losttales.client.window;

/**
 * One tab's content alone on the whole screen, as a video player's full
 * screen: no tab row, tool strip, input bar or frame, and no other window.
 * The window glides out past the screen's edges
 * ({@link Window.ScreenFill#CONTENT}), its row and strip over the top and
 * its bar under the bottom, so what the tab holds stands as it always
 * does, only larger, while every other window fades away.
 *
 * <p>The tool strip's button or Alt+Enter brings it. Escape, Alt+Enter
 * again or the line at the top of the screen end it, and so do the tab
 * leaving the front of its window, the window closing, the keys going to
 * another window and the screen closing. One tab at a time; nothing of it
 * is saved.</p>
 */
public final class ContentView {
    private static String windowId;
    private static WindowTab tab;
    private static long enteredNanos;

    private ContentView() {}

    /** Shows the tab in front of {@code window} alone; false for a window with none. */
    public static synchronized boolean enter(Window window) {
        WindowTab front = window == null ? null : window.getActiveTab();
        if (front == null) {
            return false;
        }
        windowId = window.getId();
        tab = front;
        enteredNanos = System.nanoTime();
        return true;
    }

    /** Puts the tab back into its window; false while none stands alone. */
    public static synchronized boolean leave() {
        if (windowId == null) {
            return false;
        }
        windowId = null;
        tab = null;
        return true;
    }

    /** Whether a tab stands alone now. */
    public static synchronized boolean isOn() {
        return windowId != null;
    }

    /** Whether {@code window}'s tab stands alone now. */
    public static synchronized boolean isOn(Window window) {
        return window != null && window.getId().equals(windowId);
    }

    /** Whether the window of that id holds the tab standing alone now. */
    public static synchronized boolean isOn(String id) {
        return id != null && id.equals(windowId);
    }

    /** The window whose tab stands alone; null for none. */
    public static synchronized String windowId() {
        return windowId;
    }

    /** When the tab came to stand alone, for the line at the top saying how to leave. */
    static synchronized long enteredNanos() {
        return enteredNanos;
    }

    /**
     * The part of the screen {@code window} is laid in: the whole screen
     * past its own furniture while its tab stands alone, else its own fill.
     */
    public static Window.ScreenFill fillOf(Window window) {
        return isOn(window) ? Window.ScreenFill.CONTENT : window.getFill();
    }

    /**
     * Ends the view once its tab has left the front of its window, the
     * window is gone, or the keys went to another window ({@code keys}):
     * a page there ({@code pageHasKeys}), or the conversation typed in
     * while a conversation stands alone. A page standing alone keeps the
     * view while the keys are in no page at all, as while its sub-window
     * has them.
     */
    static synchronized void follow(Window keys, boolean pageHasKeys) {
        if (windowId == null) {
            return;
        }
        Window window = WindowLayout.window(windowId);
        boolean elsewhere = keys != null && !keys.getId().equals(windowId)
                && (pageHasKeys || !(tab instanceof PageTab));
        if (window == null || !tab.equals(window.getActiveTab()) || elsewhere) {
            windowId = null;
            tab = null;
        }
    }
}
