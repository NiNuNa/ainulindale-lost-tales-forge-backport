package com.ninuna.losttales.chat;

/**
 * A status line: a few words a player sets for one of their identities —
 * the account, or a character — as a messenger's custom status is, shown
 * under the identity's name in member lists and on its card while the
 * identity is shown online. Kept short, and plain: formatting codes and
 * control characters are no part of it, and a run of spaces is one.
 */
public final class ChatStatusLine {
    /** The longest line, in characters. */
    public static final int MAX_CHARACTERS = 48;
    /** Its bound on the wire: every character at its widest in UTF-8. */
    public static final int MAX_BYTES = MAX_CHARACTERS * 3;

    private ChatStatusLine() {}

    /**
     * A line as it may be kept and shown: formatting codes, control
     * characters and the space round it gone, every run of spaces one
     * space, cut to {@link #MAX_CHARACTERS} without splitting a character.
     * Empty for nothing, or nothing left.
     */
    public static String clean(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder(Math.min(line.length(),
                MAX_CHARACTERS));
        boolean space = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '§') {
                // A formatting code and the character naming it.
                index++;
                continue;
            }
            if (Character.isWhitespace(character)
                    || Character.isSpaceChar(character)) {
                space = kept.length() > 0;
                continue;
            }
            if (Character.isISOControl(character)) {
                continue;
            }
            if (space) {
                kept.append(' ');
                space = false;
            }
            kept.append(character);
        }
        if (kept.length() > MAX_CHARACTERS) {
            int end = MAX_CHARACTERS;
            if (Character.isHighSurrogate(kept.charAt(end - 1))) {
                end--;
            }
            kept.setLength(end);
        }
        return kept.toString().trim();
    }
}
