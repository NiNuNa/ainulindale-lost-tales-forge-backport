package com.ninuna.losttales.client.chat;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Which Server Console entries this client has already shown, by the
 * id the server gave each. A live entry and a replayed batch can name
 * the same event; it is shown once. Bounded to what the console keeps
 * and cleared with the rest of the client's chat state.
 */
final class ClientChatConsoleEvents {
    private static final int MAX_REMEMBERED = 1024;
    private static final LinkedHashSet<Long> SHOWN = new LinkedHashSet<Long>();

    private ClientChatConsoleEvents() {}

    /** Records the id; answers whether it was new. */
    static synchronized boolean noteShown(long eventId) {
        if (!SHOWN.add(Long.valueOf(eventId))) {
            return false;
        }
        while (SHOWN.size() > MAX_REMEMBERED) {
            Iterator<Long> oldest = SHOWN.iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    static synchronized void clear() {
        SHOWN.clear();
    }
}
