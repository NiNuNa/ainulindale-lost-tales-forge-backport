package com.ninuna.losttales.client.chat;

/**
 * How a line built from a message packet presents its body. The header
 * — channel prefix, timestamp, role tags, head, name and title — reads
 * the same whatever the kind; the kind decides what stands between the
 * sender and the body, and whether the body is read for markup, emoji,
 * links, mentions and shares or shown exactly as it is.
 */
enum ChatBodyKind {
    /**
     * Something said: the body opens behind the chat's chevron and is
     * read for everything a message may carry.
     */
    MESSAGE(""),
    /**
     * A command the player sent: the body opens behind the words that
     * say so and is the command exactly as typed, since a command is
     * not prose and nothing in it is a mention, an emoji or a share.
     */
    COMMAND("chat.losttales.command.used");

    private final String labelKey;

    ChatBodyKind(String labelKey) {
        this.labelKey = labelKey;
    }

    /**
     * The translation key of the words the body opens with; empty for
     * the chevron.
     */
    String getLabelKey() {
        return this.labelKey;
    }

    /** Whether the body is read for markup, emoji, links and mentions. */
    boolean parsesBody() {
        return this == MESSAGE;
    }
}
