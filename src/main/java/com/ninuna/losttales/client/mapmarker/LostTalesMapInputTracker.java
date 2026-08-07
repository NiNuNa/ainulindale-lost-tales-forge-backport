package com.ninuna.losttales.client.mapmarker;

/**
 * One owner for every mouse gesture on the map screen.
 *
 * <p>The map has to tell three things apart that all begin with the same left
 * press: picking up a marker, panning, and dropping a "go here" marker on
 * empty map. Whether a press was a click or a drag is not known until the
 * button comes back up, so placement waits for the release and the threshold
 * that decides it lives here rather than being re-guessed at each call
 * site.</p>
 */
final class LostTalesMapInputTracker {
    /** Movement, in GUI pixels, that turns a press into a drag. */
    static final int DRAG_THRESHOLD_PIXELS = 3;

    private boolean pressActive;
    private int pressX;
    private int pressY;
    private boolean pressDragged;

    void press(int x, int y) {
        this.pressActive = true;
        this.pressX = x;
        this.pressY = y;
        this.pressDragged = false;
    }

    /** Feeds a drag sample; true once this press counts as a drag. */
    boolean moved(int x, int y) {
        if (this.pressActive && isDrag(this.pressX, this.pressY, x, y)) {
            this.pressDragged = true;
        }
        return this.pressDragged;
    }

    /** Ends the press; true only when it stayed within the click threshold. */
    boolean releaseAsClick(int x, int y) {
        boolean click = this.pressActive && !this.pressDragged
                && !isDrag(this.pressX, this.pressY, x, y);
        this.pressActive = false;
        this.pressDragged = false;
        return click;
    }

    boolean isPressActive() {
        return this.pressActive;
    }

    /** Abandons the press without letting it complete as a click. */
    void cancelPress() {
        this.pressActive = false;
        this.pressDragged = false;
    }

    void clear() {
        cancelPress();
    }

    static boolean isDrag(int fromX, int fromY, int toX, int toY) {
        return Math.abs(toX - fromX) > DRAG_THRESHOLD_PIXELS
                || Math.abs(toY - fromY) > DRAG_THRESHOLD_PIXELS;
    }
}
