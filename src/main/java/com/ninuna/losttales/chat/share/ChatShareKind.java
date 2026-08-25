package com.ninuna.losttales.chat.share;

/**
 * The things a player can share inline in a message besides emojis. Each
 * kind owns a short opener ({@code [i:} for an item from the sender's
 * inventory, {@code [m:} for a map marker the sender can see); the opener
 * and the wire code are protocol surface and must stay stable.
 */
public enum ChatShareKind {
    ITEM('i'),
    MARKER('m');

    public static final char TOKEN_CLOSE = ']';

    private final char code;
    private final String opener;

    ChatShareKind(char code) {
        this.code = code;
        this.opener = "[" + code + ":";
    }

    /** Single-character wire code and the letter after the bracket. */
    public char getCode() {
        return this.code;
    }

    /** The player-typed opening sequence, e.g. {@code [i:}. */
    public String getOpener() {
        return this.opener;
    }

    public static ChatShareKind fromCode(int code) {
        for (ChatShareKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return null;
    }
}
