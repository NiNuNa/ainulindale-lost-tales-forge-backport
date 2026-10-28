package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.util.LostTalesWords;

/**
 * One of the server's own posts to Discord — a player joined, left,
 * died, or earned an achievement — as
 * the webhook shows it: an embed with a coloured edge, one line of plain
 * text on the author row, and the player's head beside it when the
 * notice is about a player. The kind is what the config switches on and
 * off; the words, colour and icon are decided once, in
 * {@link DiscordServerNotices}, so every notice of a kind looks the same.
 * A join or a leave is the lang file's line, worded for each linked
 * channel in its own language ({@link #getText}); a death or an
 * achievement is the game's own line as it was broadcast.
 *
 * <p>The text is shown as Discord's embed author name, which renders no
 * markdown and can ping nobody, so it is carried as plain text and never
 * escaped. Discord bounds that field at {@link #MAX_TEXT_LENGTH}; longer
 * text is cut with a mark rather than refused.</p>
 */
public final class DiscordNotice {
    /** What the notice announces; each kind has its own config switch. */
    public enum Kind {
        PLAYER_JOINED, PLAYER_LEFT, PLAYER_DIED, ACHIEVEMENT
    }

    /** Discord's own bound on an embed author name. */
    public static final int MAX_TEXT_LENGTH = 256;
    private static final String ELLIPSIS = "...";

    private final Kind kind;
    /** The icon the line opens with. */
    private final String mark;
    /** The lang key the line is worded by; null for the game's own line. */
    private final String key;
    /** The key's one argument, or the game's own line. */
    private final String said;
    private final String iconUrl;
    private final int color;

    DiscordNotice(Kind kind, String mark, String key, String said, String iconUrl,
                  int color) {
        this.kind = kind;
        this.mark = mark == null ? "" : mark;
        this.key = key;
        this.said = said == null ? "" : said;
        this.iconUrl = iconUrl == null ? "" : iconUrl;
        this.color = color & 0xFFFFFF;
    }

    public Kind getKind() {
        return this.kind;
    }

    /** Whether the notice has nothing to say: no name, no line of the game's. */
    public boolean isEmpty() {
        return this.said.length() == 0;
    }

    /** The one line the notice says in {@code words}, plain text. */
    public String getText(LostTalesWords words) {
        String line = this.key == null ? this.said : words.format(this.key, this.said);
        return bound(this.mark.length() == 0 ? line : this.mark + " " + line);
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
