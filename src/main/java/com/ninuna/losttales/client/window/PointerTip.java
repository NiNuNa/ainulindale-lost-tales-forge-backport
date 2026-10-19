package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/**
 * The tip beside the pointer, the one every tip on the window screen is,
 * a window's and a sub-window's alike (LOOK-14). Its words are broken
 * where a tip breaks ({@link WindowStyle#tipLines}); a titled tip wears its
 * first line in the accent and the rest in ivory. It stands
 * {@link #BESIDE} pixels right of the pointer and {@link #ABOVE} above it,
 * below the pointer where the screen's top is too near, held
 * {@link #EDGE} pixels inside the screen's sides, and keeps pace with the
 * pointer to the display pixel, as the cursor does.
 */
public final class PointerTip {
    /** How far right of the pointer a tip's left edge stands. */
    static final int BESIDE = 8;
    /** How far above the pointer a tip's bottom edge stands. */
    static final int ABOVE = 4;
    /** How far below the pointer a tip's top edge stands, where it cannot go above. */
    static final int BELOW = 12;
    /** How near the screen's edges a tip may come. */
    static final int EDGE = 2;

    private final List<String> lines;
    private final boolean titled;
    private final String key;

    private PointerTip(List<String> lines, boolean titled, String key) {
        this.lines = lines;
        this.titled = titled;
        this.key = key;
    }

    /** A tip of {@code text}; null for no words. */
    public static PointerTip of(FontRenderer font, String text) {
        if (text == null || text.length() == 0) {
            return null;
        }
        return new PointerTip(WindowStyle.tipLines(font, text), false, text);
    }

    /**
     * A tip naming something in the accent, {@code title}, with
     * {@code text} under it in ivory; the title alone where there is no
     * text, null where there is no title.
     */
    public static PointerTip titled(FontRenderer font, String title,
                                    String text) {
        if (title == null || title.length() == 0) {
            return null;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(title);
        if (text != null && text.length() > 0) {
            lines.addAll(WindowStyle.tipLines(font, text));
        }
        return new PointerTip(Collections.unmodifiableList(lines), true,
                title + "|" + (text == null ? "" : text));
    }

    /** The same words for the same tip, for its timing. */
    public String key() {
        return this.key;
    }

    List<String> lines() {
        return this.lines;
    }

    /** Where a tip {@code width} wide stands left to right for a pointer at {@code pointerX}. */
    static int left(int width, int pointerX, int screenWidth) {
        return Math.max(EDGE, Math.min(screenWidth - width - EDGE,
                pointerX + BESIDE));
    }

    /** Where a tip {@code height} tall stands top to bottom for a pointer at {@code pointerY}. */
    static int top(int height, int pointerY) {
        int above = pointerY - ABOVE - height;
        return above < EDGE ? pointerY + BELOW : above;
    }

    /**
     * Draws the tip beside the pointer at {@code pointerX}, {@code pointerY}
     * (the screen's whole-pixel pointer), at {@code share} of its strength
     * as it fades in.
     */
    public void draw(FontRenderer font, int pointerX, int pointerY,
                     int screenWidth, float share) {
        if (font == null || share <= 0.0F) {
            return;
        }
        int width = 0;
        for (String line : this.lines) {
            width = Math.max(width, WindowStyle.popupLineWidth(font, line));
        }
        int height = WindowStyle.popupLinesHeight(this.lines.size());
        int x = left(width, pointerX, screenWidth);
        int y = top(height, pointerY);
        // Held against a side, the tip stays still there rather than
        // shifting by a part of a pixel.
        LostTalesTooltipSmoothing.begin(pointerX, pointerY,
                x == pointerX + BESIDE, true);
        try {
            WindowStyle.drawPopup(x, y, x + width, y + height, share);
            LostTalesUiInk.beginContent();
            int alpha = Math.round(255.0F * Math.max(0.0F,
                    Math.min(1.0F, share)));
            for (int index = 0; index < this.lines.size(); index++) {
                LostTalesUiInk.drawText(font, this.lines.get(index),
                        x + WindowStyle.POPUP_INSET,
                        y + WindowStyle.POPUP_INSET
                                + index * WindowStyle.LINE_HEIGHT,
                        this.titled && index == 0
                                ? LostTalesUiTheme.accentRgb()
                                : LostTalesUiInk.IVORY,
                        alpha);
            }
        } finally {
            LostTalesTooltipSmoothing.end();
        }
    }
}
