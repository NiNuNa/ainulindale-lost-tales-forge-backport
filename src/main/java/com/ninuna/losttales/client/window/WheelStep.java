package com.ninuna.losttales.client.window;

/**
 * What one turn of the mouse wheel moves in a window. A turn is
 * {@link #LINES} whole lines, one with Shift, counted in the unit of
 * whatever it scrolls: message lines for a history, rows for a menu, and
 * list lines of a menu row's height for a picker. Each keeps its own pace
 * whatever height the others' lines have.
 */
public final class WheelStep {
    /**
     * Lines one turn moves. Short, because a view glides to the new
     * offset rather than jumping to it: a long step would arrive before
     * the eye could follow it. Shift moves one line at a time.
     */
    static final int LINES = 2;

    private WheelStep() {}

    /**
     * The lines a turn of {@code wheel} moves: positive for a turn away
     * from the player, which shows older lines, and none for no turn.
     */
    public static int lines(int wheel, boolean shift) {
        if (wheel == 0) {
            return 0;
        }
        return (wheel > 0 ? 1 : -1) * (shift ? 1 : LINES);
    }

    /** Pixels {@code lines} lines move a list whose lines are {@code lineHeight} tall. */
    public static int pixels(int lines, int lineHeight) {
        return lines * lineHeight;
    }

    /** Rows a menu moves: always whole rows. */
    public static int menuRows(int lines) {
        return lines;
    }
}
