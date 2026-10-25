package com.ninuna.losttales.client.window;

import java.util.HashMap;
import java.util.Map;

/**
 * The words each copy of a page holds in its well, and the well typed
 * in. Each copy keeps its own words until they are cleared — the well's
 * cross, Escape in the well — and finds them again whenever it is in
 * front of its window, as each browser tab keeps its own find bar
 * (WIN-25); a copy opening anew starts with none. The one field stands in
 * the well of one window at a time, typing for the copy in front there.
 * What the words find is the content's business — a conversation lights
 * its lines, a page narrows its list — and each reads its copy's words
 * from here. Kept for the connection: leaving the world forgets them all.
 */
public final class WindowSearch {
    /** Each copy's words, by its tab's id; a copy with none has no entry. */
    private static final Map<String, String> WORDS = new HashMap<String, String>();
    /**
     * The turn each copy's words were last cleared on: what was found for
     * the words before is never kept for the next.
     */
    private static final Map<String, Integer> CLEARED = new HashMap<String, Integer>();
    private static int turn;
    /** Goes up as every copy's words are forgotten at once. */
    private static int epoch;
    /** The window whose well the field stands in; null while it stands in none. */
    private static String windowId;
    /** The tab id of the copy that well types for; null while none. */
    private static String copyId;

    private WindowSearch() {}

    /** Whether the field stands in a well. */
    public static synchronized boolean isOpen() {
        return windowId != null;
    }

    /** Whether the field stands in this window's well. */
    public static synchronized boolean isOpenOn(String id) {
        return windowId != null && windowId.equals(id);
    }

    /** The window whose well the field stands in; null while it stands in none. */
    public static synchronized String windowId() {
        return windowId;
    }

    /** The words of the copy the field types for, as typed; empty while it types for none. */
    public static synchronized String query() {
        return copyId == null ? "" : wordsOf(copyId);
    }

    /** A copy's words; empty for none. */
    public static synchronized String wordsOf(WindowPage copy) {
        return copy == null ? "" : wordsOf(copy.id());
    }

    /** The words of the copy whose tab has {@code id}; empty for none. */
    public static synchronized String wordsOf(String id) {
        String words = id == null ? null : WORDS.get(id);
        return words == null ? "" : words;
    }

    /** The words of the copy in front of the window; empty for none. */
    public static String wordsIn(String windowId) {
        return wordsOf(frontOf(windowId));
    }

    /**
     * The copy in front of a window, whose words its well holds; null for
     * no such window, or one with nothing on screen.
     */
    public static WindowPage frontOf(String windowId) {
        Window window = windowId == null ? null : WindowLayout.window(windowId);
        return window == null ? null
                : WindowFrame.activeTab(window, WindowFrame.visibleTabs(window));
    }

    /**
     * The turn the words of the copy whose tab has {@code id} were last
     * cleared on; 0 for never.
     */
    public static synchronized int generation(String id) {
        Integer cleared = id == null ? null : CLEARED.get(id);
        return cleared == null ? 0 : cleared.intValue();
    }

    /** Goes up as every copy's words are forgotten at once, as the world is left. */
    public static synchronized int epoch() {
        return epoch;
    }

    /**
     * The field stands in a window's well, typing for {@code copy}, the
     * copy in front there; one standing elsewhere leaves its well, its
     * copy's words kept.
     */
    public static synchronized void open(String id, WindowPage copy) {
        if (id == null || copy == null) {
            return;
        }
        windowId = id;
        copyId = copy.id();
    }

    /** Whether the field types for {@code copy}. */
    public static synchronized boolean typesFor(WindowPage copy) {
        return copy != null && copy.id().equals(copyId);
    }

    /** The field leaves the well it stands in; the words stay with their copy. */
    public static synchronized void release() {
        windowId = null;
        copyId = null;
    }

    /** The field leaves its well unless that is the window being typed in. */
    public static synchronized void releaseUnless(String activeWindowId) {
        if (windowId != null && !windowId.equals(activeWindowId)) {
            release();
        }
    }

    /** The words typed into the well the field stands in, for its copy; empty words leave the copy none. */
    public static synchronized void setQuery(String text) {
        if (copyId != null) {
            setWords(copyId, text);
        }
    }

    /** Gives the copy whose tab has {@code id} these words; empty ones leave it none. */
    static synchronized void setWords(String id, String text) {
        if (id == null) {
            return;
        }
        if (text == null || text.length() == 0) {
            WORDS.remove(id);
        } else {
            WORDS.put(id, text);
        }
    }

    /**
     * Clears the words of the copy whose tab has {@code id}: what they
     * found goes with them. The cross in its well, Escape in it.
     */
    public static synchronized void clear(String id) {
        if (id == null) {
            return;
        }
        WORDS.remove(id);
        CLEARED.put(id, Integer.valueOf(++turn));
    }

    /** A copy opening anew: whatever an earlier copy of its number searched is forgotten. */
    public static synchronized void forget(WindowPage copy) {
        if (copy != null) {
            clear(copy.id());
        }
    }

    /** Leaving the world: every copy's words go, and the field leaves its well. */
    public static synchronized void forgetAll() {
        WORDS.clear();
        CLEARED.clear();
        epoch++;
        release();
    }
}
