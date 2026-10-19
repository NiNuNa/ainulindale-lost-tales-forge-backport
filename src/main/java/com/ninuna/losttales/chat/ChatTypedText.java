package com.ninuna.losttales.chat;

import java.nio.charset.Charset;

/**
 * What a player typed for one of their lines, kept by the server with the
 * line for as long as it keeps the line, so its author can edit it later,
 * after a relog too (CHAT-83): the words as they stood in the bar, the
 * emoticons not yet turned into emoji and an action's {@code /me} before
 * them. Empty where it is the line's own words. Only the author's copy
 * carries it.
 */
public final class ChatTypedText {
    /** What opens an action as it is typed. */
    public static final String ACTION_PREFIX = "/me ";
    /** The most bytes typed text takes: a message's, and an action's prefix. */
    public static final int MAX_UTF8_BYTES = ChatMessageValidator.MAX_UTF8_BYTES
            + ACTION_PREFIX.length();

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ChatTypedText() {}

    /**
     * Whether {@code typed} may be kept: empty, or within its bound and a
     * message's words, an action's after its {@code /me}.
     */
    public static boolean isValid(String typed) {
        if (typed == null) {
            return false;
        }
        if (typed.length() == 0) {
            return true;
        }
        if (typed.getBytes(UTF_8).length > MAX_UTF8_BYTES) {
            return false;
        }
        String words = typed.startsWith(ACTION_PREFIX)
                ? typed.substring(ACTION_PREFIX.length()) : typed;
        return ChatMessageValidator.isValid(words);
    }

    /**
     * What is kept of {@code typed} for a line saying {@code message}:
     * nothing where the two are the same or the typed words cannot be kept.
     */
    public static String kept(String typed, String message) {
        if (typed == null || !isValid(typed) || typed.equals(message)) {
            return "";
        }
        return typed;
    }
}
