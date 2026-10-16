package com.ninuna.losttales.gui.style;

/**
 * How a long text folds wherever it is read: words taking more than five
 * rows show their first four and Read more under them, and opened show
 * every row and Show less. A text one row longer than the four kept stays
 * whole, since folded it would be no shorter. The chat's long messages and
 * a profile's History fold this way.
 */
public final class FoldRule {
    /** The rows a folded text keeps. */
    public static final int KEPT_ROWS = 4;
    /** The words under a folded text. */
    public static final String READ_MORE_KEY = "gui.losttales.read_more";
    /** The words under an opened text. */
    public static final String SHOW_LESS_KEY = "gui.losttales.show_less";

    private FoldRule() {}

    /** Whether words taking {@code rows} rows fold. */
    public static boolean folds(int rows) {
        return rows > KEPT_ROWS + 1;
    }

    /** How many of {@code rows} rows are shown: every one while open or too few to fold, else the kept ones. */
    public static int shownRows(int rows, boolean open) {
        return open || !folds(rows) ? Math.max(0, rows) : KEPT_ROWS;
    }

    /** The lang key of the words under a text that folds: Show less while open, Read more while folded. */
    public static String toggleKey(boolean open) {
        return open ? SHOW_LESS_KEY : READ_MORE_KEY;
    }
}
