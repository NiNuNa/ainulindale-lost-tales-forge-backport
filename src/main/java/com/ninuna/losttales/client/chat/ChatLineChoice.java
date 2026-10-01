package com.ninuna.losttales.client.chat;

import java.util.Locale;

/**
 * Which of a conversation's new lines a choice lets through: every line,
 * only a line that mentions the player or replies to them, or none. Each
 * conversation (a channel, a whisper, an NPC or a server's own channel)
 * has two of them, each picked in a sub-window its option opens:
 * Notifications, which lines chime, and Show in Feed, which lines reach
 * the closed feed. A
 * conversation starts at its defaults ({@link ChatLayout#defaultNotification},
 * Everything for the feed). The player's own lines and replayed history
 * never chime, and Do Not Disturb holds every chime whatever the choice.
 * Every line counts unread whatever the choices.
 */
public enum ChatLineChoice {
    /** Every new line; the feed shows the conversation's typing too. */
    EVERYTHING("everything"),
    /** Only a line that mentions the player or replies to them. */
    ONLY_MENTIONS("mentions"),
    /** No line; a conversation whose Notifications say so reads as muted. */
    NOTHING("nothing");

    /** The word the layout file keeps the choice under. */
    private final String id;

    ChatLineChoice(String id) {
        this.id = id;
    }

    /** The word the layout file keeps the choice under. */
    public String id() {
        return this.id;
    }

    /** The choice a word names, or null for a word that names none. */
    public static ChatLineChoice fromId(String id) {
        String word = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (ChatLineChoice choice : values()) {
            if (choice.id.equals(word)) {
                return choice;
            }
        }
        return null;
    }

    /**
     * Whether a new line gets through: any line for everything, only one
     * {@code addressed} to the player (a mention or a reply) for only
     * mentions, none for nothing.
     */
    public boolean lets(boolean addressed) {
        return this == EVERYTHING || (this == ONLY_MENTIONS && addressed);
    }

    /** The key of the words the choice reads as, the same for both choices. */
    public String labelKey() {
        return "gui.losttales.chat.choice." + this.id;
    }
}
