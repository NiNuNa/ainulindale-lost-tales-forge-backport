package com.ninuna.losttales.compat.discord;

/**
 * One of the server's own posts to Discord — the server started or is
 * going down, a player joined, left, died, or earned an achievement — as
 * the webhook shows it: an embed with a coloured edge, one line of plain
 * text on the author row, and the player's head beside it when the
 * notice is about a player. The kind is what the config switches on and
 * off; the words, colour and icon are decided once, in
 * {@link DiscordServerNotices}, so every notice of a kind looks the same.
 *
 * <p>The text is shown as Discord's embed author name, which renders no
 * markdown and can ping nobody, so it is carried as plain text and never
 * escaped. Discord bounds that field at {@link #MAX_TEXT_LENGTH}; longer
 * text is cut with a mark rather than refused.</p>
 */
public final class DiscordNotice {
    /** What the notice announces; each kind has its own config switch. */
    public enum Kind {
        SERVER_STARTED, SERVER_STOPPING, PLAYER_JOINED, PLAYER_LEFT,
        PLAYER_DIED, ACHIEVEMENT
    }

    /** Discord's own bound on an embed author name. */
    public static final int MAX_TEXT_LENGTH = 256;
    private static final String ELLIPSIS = "...";

    private final Kind kind;
    private final String text;
    private final String iconUrl;
    private final int color;

    DiscordNotice(Kind kind, String text, String iconUrl, int color) {
        this.kind = kind;
        this.text = bound(text);
        this.iconUrl = iconUrl == null ? "" : iconUrl;
        this.color = color & 0xFFFFFF;
    }

    public Kind getKind() {
        return this.kind;
    }

    /** The one line the notice says, plain text. */
    public String getText() {
        return this.text;
    }

    /** The https picture beside the text; empty for none. */
    public String getIconUrl() {
        return this.iconUrl;
    }

    /** The embed's edge colour as {@code 0xRRGGBB}. */
    public int getColor() {
        return this.color;
    }

    private static String bound(String text) {
        String value = text == null ? "" : text.trim();
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH - ELLIPSIS.length()).trim()
                + ELLIPSIS;
    }
}
