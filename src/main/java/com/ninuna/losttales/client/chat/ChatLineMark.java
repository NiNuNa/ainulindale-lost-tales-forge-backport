package com.ninuna.losttales.client.chat;

/**
 * The mark a line's words open with, which says what kind of line it is:
 * {@code >} for words someone said, {@code /} for a command someone ran,
 * {@code *} for what a character did, told by the Narrator. The wrapper
 * draws it in front of the words ({@link ChatBodyMarker}); it is never
 * part of what the line says, so a copy leaves it out.
 */
enum ChatLineMark {
    SAID('>'),
    /** The command's words follow without their slash: the mark is the slash. */
    COMMAND('/'),
    ACTION('*');

    final char symbol;
    /** The mark and the space after it, as the wrapper places it. */
    final String separator;

    ChatLineMark(char symbol) {
        this.symbol = symbol;
        this.separator = symbol + " ";
    }

    /** The mark drawn as {@code symbol}; said words for anything else. */
    static ChatLineMark of(char symbol) {
        for (ChatLineMark mark : values()) {
            if (mark.symbol == symbol) {
                return mark;
            }
        }
        return SAID;
    }

    /** Whether a run is one of the marks alone, with its space: a quote's closing mark. */
    static boolean isMark(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() == 1 && of(trimmed.charAt(0)).symbol == trimmed.charAt(0);
    }
}
