package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import net.minecraft.client.Minecraft;

/**
 * How a page's option stands as a button on the tool strip, and before
 * its row in the page's options: a sprite of the UI sheet with its lit
 * artwork, a chip of a colour, or a small pattern of pixels standing in
 * for artwork not painted yet. Each casts the one shadow, as one picture
 * with it, and crosses to its lit look as the button lights. An option
 * set to nothing is drawn struck through.
 */
public abstract class OptionGlyph {
    /** Ink of a struck option's stroke. */
    private static final int STRIKE_RGB = LostTalesColors.rgb(LostTalesColors.CRIMSON);
    /** Ink a pattern lights to. */
    private static final int LIT_RGB = LostTalesColors.rgb(LostTalesColors.HONEY);

    public abstract int width();

    public abstract int height();

    /**
     * Draws the glyph with its top left at {@code x}, {@code y}, {@code lit}
     * of the way to its lit look (0 to 1), at {@code alpha} (0-255).
     */
    public abstract void draw(float x, float y, float lit, int alpha);

    /** The sheet sprite the glyph rests as, for a sub-window's strip; null for a chip or a pattern. */
    public LostTalesUiSheet sprite() {
        return null;
    }

    /** A sprite of the UI sheet and its lit artwork. */
    public static OptionGlyph sprite(final LostTalesUiSheet resting,
                                     final LostTalesUiSheet litSprite) {
        return new OptionGlyph() {
            @Override
            public LostTalesUiSheet sprite() {
                return resting;
            }

            @Override
            public int width() {
                return resting.getWidth();
            }

            @Override
            public int height() {
                return resting.getHeight();
            }

            @Override
            public void draw(float x, float y, float lit, int alpha) {
                LostTalesUiSheet.drawPairWithShadow(resting, litSprite, lit,
                        x, y, alpha);
            }
        };
    }

    /**
     * A square of a colour, {@link Pattern#SIZE} wide: a fellowship colour. Lit,
     * its edge takes the chat's ivory.
     */
    public static OptionGlyph chip(final int rgb) {
        return new OptionGlyph() {
            @Override
            public int width() {
                return Pattern.SIZE;
            }

            @Override
            public int height() {
                return Pattern.SIZE;
            }

            @Override
            public void draw(float x, float y, float lit, int alpha) {
                int size = Pattern.SIZE;
                int shadow = LostTalesUiInk.argb(LostTalesUiInk.SHADOW,
                        LostTalesUiInk.shadowAlpha(alpha));
                LostTalesUiInk.fillRect(x + size, y + 1, x + size + 1,
                        y + size + 1, shadow);
                LostTalesUiInk.fillRect(x + 1, y + size, x + size,
                        y + size + 1, shadow);
                int edge = LostTalesUiInk.argb(LostTalesUiInk.blend(rgb,
                        LostTalesUiInk.IVORY, lit), alpha);
                LostTalesUiInk.fillRect(x, y, x + size, y + size, edge);
                LostTalesUiInk.fillRect(x + 1, y + 1, x + size - 1,
                        y + size - 1, LostTalesUiInk.argb(rgb, alpha));
            }
        };
    }

    /**
     * A pattern of pixels, one string a row, {@code #} for ink: artwork
     * that waits to be painted. It rests in {@code rgb} and lights to
     * honey.
     */
    public static OptionGlyph pattern(int rgb, String... rows) {
        return new Pattern(rgb, rows);
    }

    /** A pattern in the chat's ivory. */
    public static OptionGlyph pattern(String... rows) {
        return new Pattern(LostTalesUiInk.IVORY, rows);
    }

    /** The same glyph with a stroke across it, bottom left to top right: an option set to nothing. */
    public OptionGlyph struck() {
        final OptionGlyph plain = this;
        return new OptionGlyph() {
            @Override
            public int width() {
                return plain.width();
            }

            @Override
            public int height() {
                return plain.height();
            }

            @Override
            public void draw(float x, float y, float lit, int alpha) {
                plain.draw(x, y, lit, alpha);
                int steps = Math.min(plain.width(), plain.height());
                int ink = LostTalesUiInk.argb(STRIKE_RGB, alpha);
                for (int step = 0; step < steps; step++) {
                    float px = x + step * (plain.width() - 1)
                            / (float)Math.max(1, steps - 1);
                    float py = y + plain.height() - 1 - step;
                    LostTalesUiInk.fillRect(Math.round(px), py,
                            Math.round(px) + 1, py + 1, ink);
                }
            }
        };
    }

    /** As {@link MenuWindow.Picture}, before an option's row in its menu. */
    MenuWindow.Picture asPicture() {
        final OptionGlyph glyph = this;
        return new MenuWindow.Picture() {
            @Override
            public void draw(Minecraft minecraft, float iconX, float labelTop,
                             int alpha) {
                glyph.draw(iconX + (TabIcons.SLOT - glyph.width()) / 2,
                        labelTop + LostTalesUiInk.centredStart(
                                LostTalesUiInk.CAP_HEIGHT, glyph.height()),
                        0.0F, alpha);
            }
        };
    }

    /** Pixels in rows, its shadow left out under its own ink, so it fades as one picture. */
    static final class Pattern extends OptionGlyph {
        /** The size the patterns are drawn at, as the strip's own glyphs are. */
        static final int SIZE = 5;

        private final int rgb;
        private final boolean[][] ink;
        private final int width;

        Pattern(int rgb, String... rows) {
            this.rgb = rgb;
            int widest = 0;
            for (String row : rows) {
                widest = Math.max(widest, row.length());
            }
            this.width = widest;
            this.ink = new boolean[rows.length][widest];
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    this.ink[y][x] = rows[y].charAt(x) == '#';
                }
            }
        }

        @Override
        public int width() {
            return this.width;
        }

        @Override
        public int height() {
            return this.ink.length;
        }

        private boolean inked(int x, int y) {
            return y >= 0 && y < this.ink.length && x >= 0 && x < this.width
                    && this.ink[y][x];
        }

        @Override
        public void draw(float x, float y, float lit, int alpha) {
            int shadow = LostTalesUiInk.argb(LostTalesUiInk.SHADOW,
                    LostTalesUiInk.shadowAlpha(alpha));
            int offset = LostTalesUiInk.SHADOW_OFFSET;
            for (int row = 0; row < this.ink.length; row++) {
                for (int column = 0; column < this.width; column++) {
                    if (inked(column, row)
                            && !inked(column + offset, row + offset)) {
                        LostTalesUiInk.fillRect(x + column + offset,
                                y + row + offset, x + column + offset + 1,
                                y + row + offset + 1, shadow);
                    }
                }
            }
            int color = LostTalesUiInk.argb(
                    LostTalesUiInk.blend(this.rgb, LIT_RGB, lit), alpha);
            for (int row = 0; row < this.ink.length; row++) {
                for (int column = 0; column < this.width; column++) {
                    if (this.ink[row][column]) {
                        LostTalesUiInk.fillRect(x + column, y + row,
                                x + column + 1, y + row + 1, color);
                    }
                }
            }
        }
    }
}
