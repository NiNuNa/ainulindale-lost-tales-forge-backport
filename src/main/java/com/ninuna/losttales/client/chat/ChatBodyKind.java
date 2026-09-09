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
     * A command the player sent: the body opens behind nothing — its
     * own slash stands where the chevron stands, in the sender's colour
     * and with the chevron's gap after it, and the rest of the command
     * as typed follows in the chat's white; a command is not prose and
     * nothing in it is a mention, an emoji or a share.
     */
    COMMAND("");

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

    /**
     * Whether the body opens behind nothing at all rather than the
     * chevron or a label: its own first run is the opener, and is
     * copied with the rest of it.
     */
    boolean opensBare() {
        return this == COMMAND;
    }
}
