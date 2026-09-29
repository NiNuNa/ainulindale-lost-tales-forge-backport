package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;

/** Reads a corner cut band by band, as it is drawn. */
final class CornerCuts {
    private CornerCuts() {}

    /**
     * Where the picture is cut from on the row at {@code y}: the column
     * of the band it lies in, or positive infinity where it is whole.
     */
    static float cutFrom(LostTalesUiCornerCut cut, float y) {
        float column = Float.POSITIVE_INFINITY;
        for (int band = 0; band < cut.bands(); band++) {
            if (y >= cut.bandTop(band)) {
                column = cut.bandColumn(band);
            }
        }
        return column;
    }

    /** Whether the pixel whose top-left is {@code x}, {@code y} is cut away. */
    static boolean cuts(LostTalesUiCornerCut cut, float x, float y) {
        return x >= cutFrom(cut, y);
    }
}
