package com.ninuna.losttales.client.chat;

import java.util.Locale;

/**
 * How much of a conversation reaches the player while they are not
 * reading it: one choice per conversation, a channel, a whisper, an NPC
 * or a server's own channel alike, stepped through in its tab menu and
 * in Settings, Channels. Every line counts unread whatever the choice,
 * and the tab's corner mark counts as it always does; the choice decides
 * which lines reach the closed feed and which chime. Do Not Disturb
 * holds every chime whatever the choice.
 */
public enum ChatNotification {
    /**
     * Every line reaches the feed; a mention or a reply to the player
     * chimes, and so does every line of a whisper.
     */
    EVERYTHING("everything"),
    /**
     * Only lines that mention the player or reply to them reach the feed
     * and chime; the rest count unread quietly.
     */
    ONLY_MENTIONS("mentions"),
    /** Lines count unread quietly: none reaches the feed, none chimes. */
    NOTHING("nothing");

    /** The word the layout file keeps the choice under. */
    private final String id;

    ChatNotification(String id) {
        this.id = id;
    }

    /** The word the layout file keeps the choice under. */
    public String id() {
        return this.id;
    }

    /** The choice a word names, or null for a word that names none. */
    public static ChatNotification fromId(String id) {
        String word = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (ChatNotification choice : values()) {
            if (choice.id.equals(word)) {
                return choice;
            }
        }
        return null;
    }

    /**
     * Whether a line chimes: a line {@code addressed} to the player, or
     * any line of a {@code whisper}, where everything reaches the
     * player; only an addressed one where only mentions do; none where
     * nothing does.
     */
    public boolean chimes(boolean addressed, boolean whisper) {
        if (this == NOTHING) {
            return false;
        }
        return addressed || (this == EVERYTHING && whisper);
    }

    /** The next choice, or the one before it {@code back}, round the three. */
    public ChatNotification step(boolean back) {
        ChatNotification[] all = values();
        int next = (ordinal() + (back ? all.length - 1 : 1)) % all.length;
        return all[next];
    }

    /** The key of the words the choice reads as. */
    public String labelKey() {
        return "gui.losttales.chat.notify." + this.id;
    }
}
