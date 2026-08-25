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
        return new ChatTimestampColumn(true,
                font.getCharWidth('[') + digit * 4 + font.getCharWidth(':')
                        + font.getCharWidth(']'));
    }

    /** No column at all; what the closed feed always uses. */
    static ChatTimestampColumn disabled() {
        return DISABLED;
    }

    /** Where the timestamp text begins. */
    int timestampX() {
        return EDGE_GAP;
    }

    /** Left edge of the vertical separator. */
    int separatorX() {
        return EDGE_GAP + this.timestampWidth + EDGE_GAP;
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
