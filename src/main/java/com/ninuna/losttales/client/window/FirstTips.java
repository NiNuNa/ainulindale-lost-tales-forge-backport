package com.ninuna.losttales.client.window;

/**
 * The three tips a new player's first opening of the chat shows, one
 * after another (W9 a): the {@code +}, where closed channels, direct
 * messages and pages wait; the head button, which picks who speaks; and
 * Ctrl+K, the quick switcher. A click anywhere takes the tip showing
 * away and brings the next; once the last has gone none shows again,
 * which the account's window layout file remembers
 * ({@code tips seen=3}). A tip left showing as the screen closes comes
 * back with the next opening of the chat.
 */
public final class FirstTips {
    /** The tips, in the order they show. */
    public enum Tip {
        /** The {@code +} at the end of the tab row. */
        PLUS("plus"),
        /** The head button on the input bar. */
        HEAD("head"),
        /** Ctrl+K, the quick switcher, in the middle of the window. */
        SWITCHER("switcher");

        /** Its words' lang key, and what a part is asked to point at. */
        public final String id;

        Tip(String id) {
            this.id = id;
        }
    }

    /** The layout file's line that says how many tips have gone. */
    static final String LINE = "tips";
    private static final String SEEN = "seen=";

    private static int seen;
    /** Whether the chat opened this screen, so the tips not yet gone show. */
    private static boolean showing;

    private FirstTips() {}

    /** How many tips a layout file said have gone: none for a new player. */
    static synchronized void load(int gone) {
        seen = Math.max(0, Math.min(Tip.values().length, gone));
        showing = false;
    }

    /** How many tips have gone. */
    static synchronized int seen() {
        return seen;
    }

    /** The tip showing now; null while none does. */
    public static synchronized Tip current() {
        return showing && seen < Tip.values().length
                ? Tip.values()[seen] : null;
    }

    /** The chat opened the screen: the tips not yet gone show, from the first of them. */
    static synchronized void chatOpened() {
        showing = seen < Tip.values().length;
    }

    /** The screen closed: the tip showing waits for the next opening of the chat. */
    static synchronized void screenClosed() {
        showing = false;
    }

    /**
     * A click took the tip showing away, and the next comes in its
     * place; answers whether a tip was showing. How many have gone is
     * written down at once.
     */
    static boolean dismiss() {
        String line;
        synchronized (FirstTips.class) {
            if (current() == null) {
                return false;
            }
            seen++;
            if (seen >= Tip.values().length) {
                showing = false;
            }
            line = describe();
        }
        WindowLayoutStore.saveLine(LINE, line);
        return true;
    }

    /** The layout file's line for how many tips have gone. */
    static synchronized String describe() {
        return LINE + " " + SEEN + seen;
    }

    /** How many tips a {@code tips seen=N} line says have gone; -1 for any other line. */
    static int read(String[] parts) {
        if (parts.length != 2 || !LINE.equals(parts[0])
                || !parts[1].startsWith(SEEN)) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1].substring(SEEN.length()));
        } catch (NumberFormatException unreadable) {
            return -1;
        }
    }
}
