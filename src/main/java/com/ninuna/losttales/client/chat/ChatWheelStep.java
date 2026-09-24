package com.ninuna.losttales.client.chat;

/**
 * What one turn of the mouse wheel moves in the chat. A turn is
 * {@link #LINES} whole lines, one with Shift, counted in the unit of
 * whatever it scrolls: message lines for a window's
 * history, rows for a menu, and list lines of a menu row's height for a
 * picker. Menus and pickers keep their own pace whatever height a
 * message row has.
 */
final class ChatWheelStep {
    /**
     * Lines one turn moves. Short, because a view glides to the new
     * offset rather than jumping to it: a long step would arrive before
     * the eye could follow it. Shift moves one line at a time.
     */
    static final int LINES = 2;
    /** Pixels one line moves a picker's list: a menu row's height. */
    static final int PICKER_LINE_PIXELS = ChatMenu.ROW_HEIGHT;

    private ChatWheelStep() {}

    /**
     * The lines a turn of {@code wheel} moves: positive for a turn away
     * from the player, which shows older lines, and none for no turn.
     */
    static int lines(int wheel, boolean shift) {
        if (wheel == 0) {
            return 0;
        }
        return (wheel > 0 ? 1 : -1) * (shift ? 1 : LINES);
    }

    /** Pixels the history moves: whole message lines. */
    static int historyPixels(int lines) {
        return lines * LostTalesChatOverlayRenderer.LINE_HEIGHT;
    }

    /** Rows a menu moves: always whole rows. */
    static int menuRows(int lines) {
        return lines;
    }

    /** Pixels a picker's list moves. */
    static int pickerPixels(int lines) {
        return lines * PICKER_LINE_PIXELS;
    }
}
