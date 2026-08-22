package com.ninuna.losttales.client.chat;

import java.util.Arrays;

/**
 * Rectangles of every overlay the chat screen drew this frame — tabs,
 * indicator, buttons, completion popups, the pickers. The screen clears it
 * at the start of each draw, each overlay registers the exact rectangle it
 * painted, and hover, tooltip, and click handling ask it whether the
 * pointer is owned by an overlay before touching the message stack.
 * Because the regions come from the draw itself, visual z-order and
 * interaction z-order cannot disagree. Array-backed and reused; nothing is
 * allocated per frame once it has grown.
 *
 * <p>Rectangles are kept in screen space. Overlays that belong to the
 * input bar are drawn inside its entrance translation and register
 * through {@link #add}, which applies the frame's translation for them;
 * overlays drawn in screen space (the tab row, which moves with the
 * history instead) register through {@link #addScreen}.</p>
 */
final class ChatPointerRegions {
    private static final int INITIAL_CAPACITY = 8;

    private int[] bounds = new int[INITIAL_CAPACITY * 4];
    private int count;
    private int frameOffsetY;

    void reset() {
        reset(0);
    }

    /**
     * Starts a frame; {@code frameOffsetY} is the vertical translation the
     * input bar group is drawn with this frame.
     */
    void reset(int frameOffsetY) {
        this.count = 0;
        this.frameOffsetY = frameOffsetY;
    }

    /**
     * Registers a half-open rectangle drawn in the input bar's translated
     * space; empty or inverted ones are ignored.
     */
    void add(int left, int top, int right, int bottom) {
        addScreen(left, top + this.frameOffsetY, right,
                bottom + this.frameOffsetY);
    }

    /** Registers a half-open rectangle already in screen space. */
    void addScreen(int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top) {
            return;
        }
        if (this.count * 4 == this.bounds.length) {
            this.bounds = Arrays.copyOf(this.bounds, this.bounds.length * 2);
        }
        int offset = this.count * 4;
        this.bounds[offset] = left;
        this.bounds[offset + 1] = top;
        this.bounds[offset + 2] = right;
        this.bounds[offset + 3] = bottom;
        this.count++;
    }

    /** Whether a screen-space point is inside any registered rectangle. */
    boolean contains(int x, int y) {
        for (int index = 0; index < this.count; index++) {
            int offset = index * 4;
            if (x >= this.bounds[offset] && x < this.bounds[offset + 2]
                    && y >= this.bounds[offset + 1]
                    && y < this.bounds[offset + 3]) {
                return true;
            }
        }
        return false;
    }

    int count() {
        return this.count;
    }
}
