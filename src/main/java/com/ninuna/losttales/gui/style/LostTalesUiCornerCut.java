package com.ninuna.losttales.gui.style;

/**
 * The corner a picture gives up to a mark standing in it — a head to its
 * status sphere, a channel's icon to its unread sphere or ping tile — so
 * the mark sits in the picture rather than on it, with one clear pixel
 * between the two along every side the mark has ink on.
 *
 * <p>The cut is the mark's own shape grown by a pixel up, down, left and
 * right, never its box: a round sphere's grown shape follows its rounded
 * corner, so the picture keeps the pixels a box would take. Every mark
 * reaches past the picture's right edge, so on each row the cut is
 * everything from one column rightward. It is kept as bands of rows, top
 * first, each with the column it cuts from; a row no band reaches is
 * whole.</p>
 */
public final class LostTalesUiCornerCut {
    /** No cut: the whole picture stands. */
    public static final LostTalesUiCornerCut NONE =
            new LostTalesUiCornerCut(new float[0], new float[0]);

    /** Each band's first row, top first; a band runs to the next one's first row. */
    private final float[] rows;
    /** The column each band cuts from; positive infinity for a whole band. */
    private final float[] columns;

    private LostTalesUiCornerCut(float[] rows, float[] columns) {
        this.rows = rows;
        this.columns = columns;
    }

    /**
     * The cut a mark makes standing with its top-left at {@code markX},
     * {@code markY}: the mark's ink grown by a pixel on every side. Its
     * shape is given row by row, top first, as the column each row's ink
     * starts at, counted from the mark's left; every row has ink.
     */
    public static LostTalesUiCornerCut around(float markX, float markY,
                                              int[] inkLeft) {
        int height = inkLeft.length;
        if (height == 0) {
            return NONE;
        }
        // One band per row the grown shape reaches — the row above the
        // mark, its own rows, the row below — and a last one making the
        // rows under it whole again.
        float[] rows = new float[height + 3];
        float[] columns = new float[height + 3];
        for (int row = -1; row <= height; row++) {
            int left = Integer.MAX_VALUE;
            if (row >= 0 && row < height) {
                left = inkLeft[row] - 1;
            }
            if (row - 1 >= 0) {
                left = Math.min(left, inkLeft[row - 1]);
            }
            if (row + 1 < height) {
                left = Math.min(left, inkLeft[row + 1]);
            }
            rows[row + 1] = markY + row;
            columns[row + 1] = markX + left;
        }
        rows[height + 2] = markY + height + 1;
        columns[height + 2] = Float.POSITIVE_INFINITY;
        return new LostTalesUiCornerCut(rows, columns);
    }

    /** The same cut moved by {@code dx}, {@code dy}: a shadow's, a pixel down and right. */
    public LostTalesUiCornerCut moved(float dx, float dy) {
        float[] rows = new float[this.rows.length];
        float[] columns = new float[this.columns.length];
        for (int band = 0; band < rows.length; band++) {
            rows[band] = this.rows[band] + dy;
            columns[band] = this.columns[band] + dx;
        }
        return new LostTalesUiCornerCut(rows, columns);
    }

    /** Whether nothing is cut. */
    public boolean isNone() {
        return this.rows.length == 0;
    }

    /** How many bands the cut has. */
    public int bands() {
        return this.rows.length;
    }

    /** The first row of a band; the band runs to the next band's first row. */
    public float bandTop(int band) {
        return this.rows[band];
    }

    /** The column a band cuts from; positive infinity for a whole band. */
    public float bandColumn(int band) {
        return this.columns[band];
    }

    /**
     * Where the picture is cut from on the row at {@code y}: the column
     * of the band it lies in, or positive infinity where it is whole.
     */
    public float cutFrom(float y) {
        float column = Float.POSITIVE_INFINITY;
        for (int band = 0; band < this.rows.length; band++) {
            if (y >= this.rows[band]) {
                column = this.columns[band];
            }
        }
        return column;
    }

    /** Whether the pixel whose top-left is {@code x}, {@code y} is cut away. */
    public boolean cuts(float x, float y) {
        return x >= cutFrom(y);
    }
}
