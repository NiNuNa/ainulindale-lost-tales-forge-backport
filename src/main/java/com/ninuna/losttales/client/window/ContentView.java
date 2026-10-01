package com.ninuna.losttales.client.window;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A page filling its own window, as a video fills its player: the
 * window's tab row, tool strip and input bar slide out past the window's
 * edges ({@link Window.ScreenFill#CONTENT}), and what the page holds
 * takes the whole box, the frame still round it. The window keeps its
 * place and size; a window filling the screen gives its page the whole
 * screen within its frame. The other windows stay as they are.
 *
 * <p>The tool strip's button or Alt+Enter brings it for the window the
 * keys are in. Escape, Alt+Enter again or the line at the window's top
 * end it, and so do the page leaving the front of its window, the window
 * closing, a snap key and the screen closing. Each window has its own;
 * nothing of it is saved.</p>
 *
 * <p>While playing, a window pinned to the HUD always shows its page
 * filling it: nothing on its row, strip or bar can be pressed there. They
 * slide out as the screen closes and back in as it opens.</p>
 */
public final class ContentView {
    /** By window id, the page filling the window and when it came to. */
    private static final Map<String, Filled> FILLED =
            new LinkedHashMap<String, Filled>();

    private static final class Filled {
        final WindowTab page;
        final long enteredNanos;

        Filled(WindowTab page, long enteredNanos) {
            this.page = page;
            this.enteredNanos = enteredNanos;
        }
    }

    private ContentView() {}

    /** Lets the page in front of {@code window} fill it; false for a window with none. */
    public static synchronized boolean enter(Window window) {
        WindowTab front = window == null ? null : window.getActiveTab();
        if (front == null) {
            return false;
        }
        FILLED.put(window.getId(), new Filled(front, System.nanoTime()));
        return true;
    }

    /** Gives {@code window} its row, strip and bar back; false while its page did not fill it. */
    public static synchronized boolean leave(Window window) {
        return window != null && FILLED.remove(window.getId()) != null;
    }

    /** Gives every window its row, strip and bar back: the screen closes. */
    public static synchronized void leaveAll() {
        FILLED.clear();
    }

    /** Whether {@code window}'s page fills it now. */
    public static synchronized boolean isOn(Window window) {
        return window != null && FILLED.containsKey(window.getId());
    }

    /** Whether the window of that id has its page filling it. */
    public static synchronized boolean isOn(String id) {
        return id != null && FILLED.containsKey(id);
    }

    /** When the window's page came to fill it, for the line saying how to leave; 0 for none. */
    static synchronized long enteredNanos(String id) {
        Filled filled = id == null ? null : FILLED.get(id);
        return filled == null ? 0L : filled.enteredNanos;
    }

    /**
     * The part of the screen {@code window} is laid in: its own box with
     * its row, strip and bar past its edges while its page fills it, as it
     * always does pinned to the HUD while playing, else its own fill.
     */
    public static Window.ScreenFill fillOf(Window window) {
        return isOn(window) || WindowView.inPinnedPass()
                ? Window.ScreenFill.CONTENT : window.getFill();
    }

    /**
     * Gives a window its row, strip and bar back once the page that
     * filled it has left the front of the window, or the window is gone.
     */
    static synchronized void follow() {
        Iterator<Map.Entry<String, Filled>> each =
                FILLED.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<String, Filled> entry = each.next();
            Window window = WindowLayout.window(entry.getKey());
            if (window == null
                    || !entry.getValue().page.equals(window.getActiveTab())) {
                each.remove();
            }
        }
    }
}
