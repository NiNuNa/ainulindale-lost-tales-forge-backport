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
 * keys are in. Escape, Alt+Enter again or the exit on the dent at the
 * window's top ({@link ContentViewDent}) end it, and so do the page
 * leaving the front of its window, the window closing, a snap key and the
 * screen closing. Each window has its own. The dent's padlock holds it
 * ({@link Window#isBorderlessHeld}): held, nothing but letting go of the
 * padlock ends it, whatever page is in front, and the layout keeps
 * it.</p>
 *
 * <p>While playing, a window pinned to the HUD always shows its page
 * filling it: nothing on its row, strip or bar can be pressed there. They
 * slide out as the screen closes and back in as it opens.</p>
 */
public final class ContentView {
    /** By window id, the page filling the window while it is not held. */
    private static final Map<String, WindowPage> FILLED =
            new LinkedHashMap<String, WindowPage>();

    private ContentView() {}

    /** Lets the page in front of {@code window} fill it; false for a window with none. */
    public static synchronized boolean enter(Window window) {
        WindowPage front = window == null ? null : window.getActiveTab();
        if (front == null) {
            return false;
        }
        FILLED.put(window.getId(), front);
        return true;
    }

    /**
     * Gives {@code window} its row, strip and bar back; false while its
     * page did not fill it, or is held there.
     */
    public static synchronized boolean leave(Window window) {
        return window != null && !window.isBorderlessHeld()
                && FILLED.remove(window.getId()) != null;
    }

    /** Gives every window its row, strip and bar back but those held: the screen closes. */
    public static synchronized void leaveAll() {
        FILLED.clear();
    }

    /** Whether {@code window}'s page fills it now, held there or not. */
    public static synchronized boolean isOn(Window window) {
        return window != null && (window.isBorderlessHeld()
                || FILLED.containsKey(window.getId()));
    }

    /**
     * Holds the page filling {@code window} there, or lets it go: let go,
     * the page still fills the window, until the exit or Escape ends it.
     */
    static synchronized void hold(Window window, boolean held) {
        if (window == null || !isOn(window)) {
            return;
        }
        if (!held && window.getActiveTab() != null) {
            FILLED.put(window.getId(), window.getActiveTab());
        }
        WindowLayout.setBorderlessHeld(window.getId(), held);
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
        Iterator<Map.Entry<String, WindowPage>> each =
                FILLED.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<String, WindowPage> entry = each.next();
            Window window = WindowLayout.window(entry.getKey());
            if (window == null
                    || !entry.getValue().equals(window.getActiveTab())) {
                each.remove();
            }
        }
    }
}
