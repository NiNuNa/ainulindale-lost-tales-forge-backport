package com.ninuna.losttales.client.chat;

import net.minecraft.client.gui.FontRenderer;

/**
 * The one description of an open window's left-hand column — the
 * timestamp area — and of where a line's words start, in the chat's own
 * (unscaled) pixels measured from the window's left edge:
 *
 * <pre>
 * edge | 5 | avatar | 5 | separator | 5 | message
 * </pre>
 *
 * <p>The area holds what stands beside a group of messages, the way
 * Discord's gutter does: the group's avatar ({@link ChatAvatar}) beside
 * its name's row and first row of words, and the clock of each later
 * message of the group, brought out while the pointer rests on that
 * message. The group's own time stands behind its name
 * ({@link ChatStampMarker}).</p>
 *
 * <p>The area is the avatar with {@link #AVATAR_GAP} clear pixels either
 * side, its presence sphere included. The widest clock the font can
 * write at the small size, {@code 12:59}, fits in it with
 * {@link #TIME_GAP} either side at every GUI scale; a font whose digits
 * need more widens the area to hold it, so it never changes width as the
 * clock turns, and the avatar and the clocks stand centred in it, the
 * odd pixel on the separator's side. The gaps are what the eye sees, so
 * they are measured in ink: the window's frame stands just outside the
 * window, so the area starts at the window's own edge, and a glyph's
 * width in this font carries one column of spacing after it, which is
 * not ink.</p>
 *
 * <p>The closed feed has no area: its lines begin {@link #EDGE_GAP} from
 * its edge and wear the small head beside the name. Everything that lays
 * a window out — the renderer's panel, background band, separator,
 * avatars and clocks, and the per-window wrapping — reads these offsets
 * from here, so rendering, wrapping and hit testing can never disagree
 * about where the message content starts.</p>
 */
final class ChatTimestampColumn {
    /** The closed feed's gap at its edge. */
    static final int EDGE_GAP = 3;
    static final int SEPARATOR_WIDTH = 1;
    /** Clear pixels either side of the avatar, its sphere included. */
    static final int AVATAR_GAP = 5;
    /** Clear pixels between the separator and the words. */
    static final int WORDS_GAP = 5;
    /** The fewest clear pixels either side of a clock. */
    static final int TIME_GAP = 1;
    /** The narrowest the area is: the avatar and its gaps. */
    static final int AVATAR_WIDTH =
            AVATAR_GAP + ChatAvatar.ICON_WIDTH + AVATAR_GAP;

    /** The closed feed: no area at all. */
    private static final ChatTimestampColumn FEED =
            new ChatTimestampColumn(false, 0);

    /** Whether the window has the area; the closed feed has none. */
    final boolean enabled;
    /** The area's width, from the window's edge to the separator. */
    final int width;

    private ChatTimestampColumn(boolean enabled, int width) {
        this.enabled = enabled;
        this.width = Math.max(0, width);
    }

    /** The area of an open window right now. */
    static ChatTimestampColumn current(FontRenderer font) {
        // The clocks are small text; the area holds them at the size they
        // are drawn at, rounded up to whole pixels.
        return forTimeWidth(font == null ? 0 : (int)Math.ceil(
                widestClock(font)
                        * LostTalesChatVisualStyle.stackSmallScale()));
    }

    /** No area at all; what the closed feed always uses. */
    static ChatTimestampColumn feed() {
        return FEED;
    }

    /**
     * An open window's area for clocks at most {@code timeWidth} pixels
     * of ink wide; the geometry's own test hook.
     */
    static ChatTimestampColumn forTimeWidth(int timeWidth) {
        return new ChatTimestampColumn(true, Math.max(AVATAR_WIDTH,
                Math.max(0, timeWidth) + 2 * TIME_GAP));
    }

    /**
     * The ink of the widest clock the font can write, in its own pixels:
     * two digits of hour and two of minute, each the widest digit, and
     * the colon. A glyph's width carries a column of spacing after it;
     * the last of them is past the ink.
     */
    static int widestClock(FontRenderer font) {
        return LostTalesChatVisualStyle.widestDigitWidth(font) * 4
                + font.getCharWidth(':') - 1;
    }

    /** Where the avatar's square starts: centred in the area, sphere and all. */
    int avatarX() {
        return Math.floorDiv(this.width - ChatAvatar.ICON_WIDTH, 2);
    }

    /**
     * Where a clock {@code inkWidth} pixels of ink wide starts: centred in
     * the area, before the renderer lays it on the display's grid.
     */
    float timeX(float inkWidth) {
        return (this.width - inkWidth) / 2.0F;
    }

    /** Left edge of the vertical separator. */
    int separatorX() {
        return this.width;
    }

    /**
     * Where message content begins: past the separator's gap in an open
     * window, the edge gap in from the edge in the feed.
     */
    int messageX() {
        return this.enabled
                ? separatorX() + SEPARATOR_WIDTH + WORDS_GAP : EDGE_GAP;
    }
}
