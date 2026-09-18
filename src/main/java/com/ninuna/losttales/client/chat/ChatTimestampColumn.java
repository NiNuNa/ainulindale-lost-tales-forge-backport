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
 * its name's row and first row of words, and the time of each later
 * message of the group, brought out while the pointer rests on that
 * message. The group's own time stands behind its name
 * ({@link ChatStampMarker}).</p>
 *
 * <p>The area is the avatar with {@link #AVATAR_GAP} clear pixels either
 * side, its presence sphere included — or, where that is narrower, the
 * widest time the font can write at the small size, {@code 12:59 PM},
 * as it is drawn, leaning in italics and with its shadow, and a clear
 * display pixel either side ({@link #timeRoom}): five pixels either side
 * of the avatar at GUI scale 2, six at 3, eight at 4. It never changes
 * width as the clock turns, and the avatar and the times stand centred
 * in it, the odd pixel on the separator's side. The gaps are what the
 * eye sees, so they are measured in ink: the window's frame stands just
 * outside the window, so the area starts at the window's own edge, and
 * a glyph's width in this font carries one column of spacing after it,
 * which is not ink.</p>
 *
 * <p>A window's area can be driven out of it and back in from the
 * window's tool strip: it slides out past the window's left edge,
 * avatars, times and separator with it, and the words follow it until
 * they stand {@link #WORDS_GAP} from the window's edge, the gap they
 * keep from the separator ({@link #drivenTo}). The window keeps its
 * size; its words take the area's room.</p>
 *
 * <p>The closed feed has no area: its lines begin {@link #EDGE_GAP} from
 * its edge and wear the small head beside the name. Everything that lays
 * a window out — the renderer's panel, background band, separator,
 * avatars and times, and the per-window wrapping — reads these offsets
 * from here, so rendering, wrapping and hit testing can never disagree
 * about where the message content starts.</p>
 */
final class ChatTimestampColumn {
    /** The closed feed's gap at its edge. */
    static final int EDGE_GAP = 3;
    static final int SEPARATOR_WIDTH = 1;
    /** The fewest clear pixels either side of the avatar, its sphere included. */
    static final int AVATAR_GAP = 5;
    /** Clear pixels between the separator and the words. */
    static final int WORDS_GAP = 5;
    /** The narrowest the area is: the avatar and its gaps. */
    static final int AVATAR_WIDTH =
            AVATAR_GAP + ChatAvatar.ICON_WIDTH + AVATAR_GAP;
    /**
     * How far an italic glyph leans past its upright ink, each way, in
     * the font's own pixels: its top a pixel right, its foot a pixel left.
     */
    static final int ITALIC_LEAN = 1;

    /** The closed feed: no area at all. */
    private static final ChatTimestampColumn FEED =
            new ChatTimestampColumn(false, 0, 0.0F);

    /** Whether the window has the area; the closed feed has none. */
    final boolean enabled;
    /** The area's width, from the window's edge to the separator. */
    final int width;
    /**
     * How far the area stands in the window: 1 whole, 0 driven out past
     * its left edge, and the share between while it slides.
     */
    final float share;

    private ChatTimestampColumn(boolean enabled, int width, float share) {
        this.enabled = enabled;
        this.width = Math.max(0, width);
        this.share = Math.max(0.0F, Math.min(1.0F, share));
    }

    /** The area of an open window right now. */
    static ChatTimestampColumn current(FontRenderer font) {
        if (font == null) {
            return forTimeWidth(0.0F);
        }
        return forTimeWidth(timeRoom(widestTimeFootprint(font),
                LostTalesChatVisualStyle.stackSmallScale(),
                ChatWindowFrame.displayScaleFactor()
                        * LostTalesChatVisualStyle.chatScale()));
    }

    /**
     * A window's area as it stands this frame: the open window's, driven
     * out as far as the window's own motion has taken it.
     */
    static ChatTimestampColumn of(ChatWindowFrame frame, FontRenderer font) {
        return current(font).drivenTo(frame == null ? 1.0F : frame.areaShare());
    }

    /** The same area standing {@code share} of the way in. */
    ChatTimestampColumn drivenTo(float share) {
        return new ChatTimestampColumn(this.enabled, this.width, share);
    }

    /** No area at all; what the closed feed always uses. */
    static ChatTimestampColumn feed() {
        return FEED;
    }

    /** Whether any of the area stands in the window. */
    boolean shows() {
        return this.enabled && this.share > 0.0F;
    }

    /** How far the area has slid out past the window's left edge. */
    float slide() {
        return (1.0F - this.share) * (this.width + SEPARATOR_WIDTH);
    }

    /**
     * An open window's area for times needing {@code timeWidth} pixels,
     * their clear room included; the geometry's own test hook.
     */
    static ChatTimestampColumn forTimeWidth(float timeWidth) {
        return new ChatTimestampColumn(true, Math.max(AVATAR_WIDTH,
                (int)Math.ceil(Math.max(0.0F, timeWidth) - 1.0E-4F)), 1.0F);
    }

    /**
     * The room a time needs in the area, in the chat's pixels: its
     * footprint of {@code footprint} font pixels drawn at
     * {@code smallScale}, and one clear display pixel either side, on a
     * display drawing {@code displayPixelsPerPixel} of its pixels to one
     * of the chat's.
     */
    static float timeRoom(int footprint, float smallScale,
                          float displayPixelsPerPixel) {
        return footprint * smallScale
                + 2.0F / Math.max(0.001F, displayPixelsPerPixel);
    }

    /**
     * The widest time of day as it is drawn, in the font's own pixels:
     * its ink ({@link #widestTime}), leaning a pixel past it either way
     * in italics, and the one shadow a pixel right of it.
     */
    static int widestTimeFootprint(FontRenderer font) {
        return widestTime(font) + 2 * ITALIC_LEAN
                + LostTalesChatVisualStyle.SHADOW_OFFSET;
    }

    /**
     * The ink of the widest time of day the font can write, in its own
     * pixels: two digits of hour and two of minute, each the widest
     * digit, the colon, a space and the wider half of the day. A glyph's
     * width carries a column of spacing after it; the last of them is
     * past the ink.
     */
    static int widestTime(FontRenderer font) {
        int half = Math.max(font.getStringWidth("AM"),
                font.getStringWidth("PM"));
        return LostTalesChatVisualStyle.widestDigitWidth(font) * 4
                + font.getCharWidth(':') + font.getCharWidth(' ') + half - 1;
    }

    /** Where the avatar's square starts: centred in the area, sphere and all. */
    float avatarX() {
        return Math.floorDiv(this.width - ChatAvatar.ICON_WIDTH, 2) - slide();
    }

    /**
     * Where a time starts whose ink is {@code width} pixels wide: centred
     * in the area. The renderer lays it on the display's grid, the odd
     * display pixel left.
     */
    float timeX(float width) {
        return (this.width - width) / 2.0F - slide();
    }

    /** Left edge of the vertical separator. */
    float separatorX() {
        return this.width - slide();
    }

    /**
     * Where message content begins: past the separator's gap in an open
     * window — {@link #WORDS_GAP} from the window's edge once the area is
     * driven out — the edge gap in from the edge in the feed.
     */
    float messageX() {
        return this.enabled
                ? separatorX() + SEPARATOR_WIDTH + WORDS_GAP : EDGE_GAP;
    }
}
