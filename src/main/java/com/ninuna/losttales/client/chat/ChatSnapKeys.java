package com.ninuna.losttales.client.chat;

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
final class ChatSnapKeys {
    /** Which arrow was pressed. */
    enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    private ChatSnapKeys() {}

    /** The arrow a key is, or null for any other key. */
    static Direction direction(int keyCode) {
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
    static ChatWindow.ScreenFill next(ChatWindow.ScreenFill from,
                                      Direction direction) {
        ChatWindow.ScreenFill fill = from == null
                ? ChatWindow.ScreenFill.NONE : from;
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

    private static ChatWindow.ScreenFill sideways(ChatWindow.ScreenFill fill,
                                                  boolean left) {
        // The cycle on each side: a half, two thirds, a third, a half.
        if (fill == ChatWindow.ScreenFill.LEFT) {
            return left ? ChatWindow.ScreenFill.LEFT_TWO_THIRDS
                    : ChatWindow.ScreenFill.NONE;
        }
        if (fill == ChatWindow.ScreenFill.LEFT_TWO_THIRDS) {
            return left ? ChatWindow.ScreenFill.LEFT_THIRD
                    : ChatWindow.ScreenFill.NONE;
        }
        if (fill == ChatWindow.ScreenFill.LEFT_THIRD) {
            return left ? ChatWindow.ScreenFill.LEFT
                    : ChatWindow.ScreenFill.NONE;
        }
        if (fill == ChatWindow.ScreenFill.RIGHT) {
            return left ? ChatWindow.ScreenFill.NONE
                    : ChatWindow.ScreenFill.RIGHT_TWO_THIRDS;
        }
        if (fill == ChatWindow.ScreenFill.RIGHT_TWO_THIRDS) {
            return left ? ChatWindow.ScreenFill.NONE
                    : ChatWindow.ScreenFill.RIGHT_THIRD;
        }
        if (fill == ChatWindow.ScreenFill.RIGHT_THIRD) {
            return left ? ChatWindow.ScreenFill.NONE
                    : ChatWindow.ScreenFill.RIGHT;
        }
        // A quarter crosses to the quarter beside it.
        if (fill == ChatWindow.ScreenFill.TOP_LEFT
                || fill == ChatWindow.ScreenFill.TOP_RIGHT) {
            return left ? ChatWindow.ScreenFill.TOP_LEFT
                    : ChatWindow.ScreenFill.TOP_RIGHT;
        }
        if (fill == ChatWindow.ScreenFill.BOTTOM_LEFT
                || fill == ChatWindow.ScreenFill.BOTTOM_RIGHT) {
            return left ? ChatWindow.ScreenFill.BOTTOM_LEFT
                    : ChatWindow.ScreenFill.BOTTOM_RIGHT;
        }
        // Its own box, the whole screen, a middle column or a part the
        // player shaped: that side's half.
        return left ? ChatWindow.ScreenFill.LEFT : ChatWindow.ScreenFill.RIGHT;
    }

    private static ChatWindow.ScreenFill up(ChatWindow.ScreenFill fill) {
        if (fill == ChatWindow.ScreenFill.LEFT) {
            return ChatWindow.ScreenFill.TOP_LEFT;
        }
        if (fill == ChatWindow.ScreenFill.RIGHT) {
            return ChatWindow.ScreenFill.TOP_RIGHT;
        }
        if (fill == ChatWindow.ScreenFill.BOTTOM_LEFT) {
            return ChatWindow.ScreenFill.LEFT;
        }
        if (fill == ChatWindow.ScreenFill.BOTTOM_RIGHT) {
            return ChatWindow.ScreenFill.RIGHT;
        }
        return ChatWindow.ScreenFill.FULL;
    }

    private static ChatWindow.ScreenFill down(ChatWindow.ScreenFill fill) {
        if (fill == ChatWindow.ScreenFill.LEFT) {
            return ChatWindow.ScreenFill.BOTTOM_LEFT;
        }
        if (fill == ChatWindow.ScreenFill.RIGHT) {
            return ChatWindow.ScreenFill.BOTTOM_RIGHT;
        }
        if (fill == ChatWindow.ScreenFill.TOP_LEFT) {
            return ChatWindow.ScreenFill.LEFT;
        }
        if (fill == ChatWindow.ScreenFill.TOP_RIGHT) {
            return ChatWindow.ScreenFill.RIGHT;
        }
        if (fill == ChatWindow.ScreenFill.BOTTOM_LEFT
                || fill == ChatWindow.ScreenFill.BOTTOM_RIGHT) {
            return fill;
        }
        return ChatWindow.ScreenFill.NONE;
    }
}
