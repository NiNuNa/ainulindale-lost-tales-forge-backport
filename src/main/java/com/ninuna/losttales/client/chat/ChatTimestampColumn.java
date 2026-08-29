package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.gui.FontRenderer;

/**
 * The one description of an open window's left-hand columns, in the
 * chat's own (unscaled) pixels measured from the window's left edge:
 *
 * <pre>
 * edge | 3 | timestamp | 3 | separator | 3 | message
 * </pre>
 *
 * <p>The gaps are what the eye sees, so both ends of them are measured
 * in ink. The window's left frame edge is a drawn pixel of its own, so
 * the column starts past it rather than on it; and a glyph's width in
 * this font carries one column of spacing after it, so the width
 * reserved for the timestamp is one less than the widths summed —
 * otherwise the gap after the {@code ]} is four pixels while the gap
 * before the {@code [} is two.</p>
 *
 * With timestamps off there is no column at all and the message begins
 * {@link #EDGE_GAP} pixels from the edge. Everything that lays a window
 * out — the renderer's panel, background band, separator and timestamp
 * text, and the per-window wrapping — reads these offsets from here, so
 * rendering, wrapping and hit testing can never disagree about where the
 * message content starts.
 *
 * <p>The column is sized for the widest {@code [HH:mm]} the font can
 * produce rather than for any one timestamp, so it never changes width
 * as the clock turns.</p>
 */
final class ChatTimestampColumn {
    /** Gap at the window edge, around the separator, and before text. */
    static final int EDGE_GAP = 3;
    static final int SEPARATOR_WIDTH = 1;
    /** The window's left frame edge: drawn on the border, one pixel wide. */
    static final int BORDER_WIDTH = 1;

    private static final ChatTimestampColumn DISABLED =
            new ChatTimestampColumn(false, 0);

    final boolean enabled;
    /** Width reserved for the timestamp text itself. */
    final int timestampWidth;

    private ChatTimestampColumn(boolean enabled, int timestampWidth) {
        this.enabled = enabled;
        this.timestampWidth = Math.max(0, timestampWidth);
    }

    /** The column for the open chat screen right now. */
    static ChatTimestampColumn current(FontRenderer font) {
        if (!LostTalesConfig.showChatTimestamps || font == null) {
            return DISABLED;
        }
        int digit = 0;
        for (char candidate = '0'; candidate <= '9'; candidate++) {
            digit = Math.max(digit, font.getCharWidth(candidate));
        }
        // Each width carries a column of spacing after its glyph; the
        // last of them is past the ink, so it is not part of the column.
        return new ChatTimestampColumn(true,
                font.getCharWidth('[') + digit * 4 + font.getCharWidth(':')
                        + font.getCharWidth(']') - 1);
    }

    /** No column at all; what the closed feed always uses. */
    static ChatTimestampColumn disabled() {
        return DISABLED;
    }

    /** A column of a known timestamp width; the geometry's own test hook. */
    static ChatTimestampColumn forWidth(int timestampWidth) {
        return new ChatTimestampColumn(true, timestampWidth);
    }

    /** Where the timestamp text begins: clear of the frame edge. */
    int timestampX() {
        return BORDER_WIDTH + EDGE_GAP;
    }

    /** Left edge of the vertical separator. */
    int separatorX() {
        return timestampX() + this.timestampWidth + EDGE_GAP;
    }

    /**
     * Where message content begins. This is the only offset a disabled
     * column has; everything timestamp-specific exists only while the
     * column does.
     */
    int messageX() {
        return this.enabled
                ? separatorX() + SEPARATOR_WIDTH + EDGE_GAP : EDGE_GAP;
    }
}
