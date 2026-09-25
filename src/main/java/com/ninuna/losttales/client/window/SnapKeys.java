package com.ninuna.losttales.client.window;

import org.lwjgl.input.Keyboard;

/**
 * Snapping from the keyboard, as a desktop's Windows key and arrows snap
 * the window in front — here with Alt, on the window being typed in.
 *
 * <p>Alt+Left and Alt+Right send a window to that half of the screen,
 * and pressed again on the same side cycle it on through two thirds and
 * a third; pressed toward the other side they give the screen back
 * first, as the desktop does, so a window crosses by its own box. A
 * quarter crosses straight to the quarter beside it. From a half,
 * Alt+Up and Alt+Down take the top or bottom quarter of it, and from a
 * quarter the other way take the half back. Anywhere else Alt+Up fills
 * the whole screen and Alt+Down gives it back.</p>
 */
public final class SnapKeys {
    /** Which arrow was pressed. */
    public enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    private SnapKeys() {}

    /** The arrow a key is, or null for any other key. */
    public static Direction direction(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_LEFT:
                return Direction.LEFT;
            case Keyboard.KEY_RIGHT:
                return Direction.RIGHT;
            case Keyboard.KEY_UP:
                return Direction.UP;
            case Keyboard.KEY_DOWN:
                return Direction.DOWN;
            default:
                return null;
        }
    }

    /** Where a window filling {@code from} goes for an arrow pressed with Alt. */
    public static Window.ScreenFill next(Window.ScreenFill from,
                                      Direction direction) {
        Window.ScreenFill fill = from == null
                ? Window.ScreenFill.NONE : from;
        if (direction == null) {
            return fill;
        }
        switch (direction) {
            case LEFT:
                return sideways(fill, true);
            case RIGHT:
                return sideways(fill, false);
            case UP:
                return up(fill);
            default:
                return down(fill);
        }
    }

    private static Window.ScreenFill sideways(Window.ScreenFill fill,
                                                  boolean left) {
        // The cycle on each side: a half, two thirds, a third, a half.
        if (fill == Window.ScreenFill.LEFT) {
            return left ? Window.ScreenFill.LEFT_TWO_THIRDS
                    : Window.ScreenFill.NONE;
        }
        if (fill == Window.ScreenFill.LEFT_TWO_THIRDS) {
            return left ? Window.ScreenFill.LEFT_THIRD
                    : Window.ScreenFill.NONE;
        }
        if (fill == Window.ScreenFill.LEFT_THIRD) {
            return left ? Window.ScreenFill.LEFT
                    : Window.ScreenFill.NONE;
        }
        if (fill == Window.ScreenFill.RIGHT) {
            return left ? Window.ScreenFill.NONE
                    : Window.ScreenFill.RIGHT_TWO_THIRDS;
        }
        if (fill == Window.ScreenFill.RIGHT_TWO_THIRDS) {
            return left ? Window.ScreenFill.NONE
                    : Window.ScreenFill.RIGHT_THIRD;
        }
        if (fill == Window.ScreenFill.RIGHT_THIRD) {
            return left ? Window.ScreenFill.NONE
                    : Window.ScreenFill.RIGHT;
        }
        // A quarter crosses to the quarter beside it.
        if (fill == Window.ScreenFill.TOP_LEFT
                || fill == Window.ScreenFill.TOP_RIGHT) {
            return left ? Window.ScreenFill.TOP_LEFT
                    : Window.ScreenFill.TOP_RIGHT;
        }
        if (fill == Window.ScreenFill.BOTTOM_LEFT
                || fill == Window.ScreenFill.BOTTOM_RIGHT) {
            return left ? Window.ScreenFill.BOTTOM_LEFT
                    : Window.ScreenFill.BOTTOM_RIGHT;
        }
        // Its own box, the whole screen, a middle column or a part the
        // player shaped: that side's half.
        return left ? Window.ScreenFill.LEFT : Window.ScreenFill.RIGHT;
    }

    private static Window.ScreenFill up(Window.ScreenFill fill) {
        if (fill == Window.ScreenFill.LEFT) {
            return Window.ScreenFill.TOP_LEFT;
        }
        if (fill == Window.ScreenFill.RIGHT) {
            return Window.ScreenFill.TOP_RIGHT;
        }
        if (fill == Window.ScreenFill.BOTTOM_LEFT) {
            return Window.ScreenFill.LEFT;
        }
        if (fill == Window.ScreenFill.BOTTOM_RIGHT) {
            return Window.ScreenFill.RIGHT;
        }
        return Window.ScreenFill.FULL;
    }

    private static Window.ScreenFill down(Window.ScreenFill fill) {
        if (fill == Window.ScreenFill.LEFT) {
            return Window.ScreenFill.BOTTOM_LEFT;
        }
        if (fill == Window.ScreenFill.RIGHT) {
            return Window.ScreenFill.BOTTOM_RIGHT;
        }
        if (fill == Window.ScreenFill.TOP_LEFT) {
            return Window.ScreenFill.LEFT;
        }
        if (fill == Window.ScreenFill.TOP_RIGHT) {
            return Window.ScreenFill.RIGHT;
        }
        if (fill == Window.ScreenFill.BOTTOM_LEFT
                || fill == Window.ScreenFill.BOTTOM_RIGHT) {
            return fill;
        }
        return Window.ScreenFill.NONE;
    }
}
