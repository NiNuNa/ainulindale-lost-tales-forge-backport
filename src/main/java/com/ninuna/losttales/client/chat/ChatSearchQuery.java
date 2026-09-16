package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What the search field asks for, read the way Discord reads its own:
 * every plain word must appear in the message, whatever its case, and
 * {@code from:Name} narrows the search to a speaker — the name they
 * spoke as or their account, by its start, so {@code from:ald} finds
 * Aldric. Free of Minecraft imports.
 */
final class ChatSearchQuery {
    static final ChatSearchQuery EMPTY = new ChatSearchQuery("",
            Collections.<String>emptyList());
    private static final String FROM = "from:";

    /** The speaker's name to match by its start, lower-cased; empty for anyone. */
    final String from;
    /** The words every message must hold, lower-cased. */
    final List<String> words;

    private ChatSearchQuery(String from, List<String> words) {
        this.from = from;
        this.words = words;
    }

    /** The query typed, its {@code from:} taken out of the words. */
    static ChatSearchQuery parse(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return EMPTY;
        }
        String from = "";
        List<String> words = new ArrayList<String>();
        for (String token : raw.trim().split("\\s+")) {
            String lowered = token.toLowerCase(Locale.ROOT);
            if (lowered.startsWith(FROM)) {
                String name = lowered.substring(FROM.length());
                if (name.length() > 0) {
                    from = name;
                }
            } else if (lowered.length() > 0) {
                words.add(lowered);
            }
        }
        return from.length() == 0 && words.isEmpty() ? EMPTY
                : new ChatSearchQuery(from, Collections.unmodifiableList(words));
    }

    boolean isEmpty() {
        return this.from.length() == 0 && this.words.isEmpty();
    }

    /**
     * Whether a message said by {@code identityName} of the account
     * {@code accountName} answers the query. An empty query answers for
     * nothing: a search for nothing lights nothing.
     */
    boolean matches(String text, String identityName, String accountName) {
        if (isEmpty()) {
            return false;
        }
        if (this.from.length() > 0 && !startsWith(identityName, this.from)
                && !startsWith(accountName, this.from)) {
            return false;
        }
        String lowered = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (int index = 0; index < this.words.size(); index++) {
            if (!lowered.contains(this.words.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(String name, String start) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(start);
    }
}
