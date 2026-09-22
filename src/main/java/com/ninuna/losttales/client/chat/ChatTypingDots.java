package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.Motions;

/**
 * The three dots a typing bubble shows over a head: their size and
 * spacing in the bubble's own pixels, and how each one breathes — a
 * rise and fall over one period, the three staggered so the eye reads
 * them left to right, as a messenger's typing marks do. Time is the
 * clock's, never the frame's, so the dots move at one pace whatever
 * the frame rate. With the Animations switch off or motion reduced
 * the three stand still at full strength, an ellipsis.
 */
final class ChatTypingDots {
    static final int COUNT = 3;
    static final int DOT_SIZE = 2;
    static final int GAP = 2;
    /** The width the three dots and the gaps between them take. */
    static final int WIDTH = COUNT * DOT_SIZE + (COUNT - 1) * GAP;
    static final long PERIOD_NANOS = 1200L * 1000000L;
    static final long STAGGER_NANOS = 200L * 1000000L;
    /** A dot's dimmest opacity, so none of the three ever goes out. */
    static final float FLOOR = 0.35F;

    private ChatTypingDots() {}

    /** The opacity of dot {@code index} at {@code nowNanos}, {@link #FLOOR} to one. */
    static float opacity(int index, long nowNanos) {
        if (!Motions.flourishes()) {
            return 1.0F;
        }
        long phase = Math.floorMod(Motions.paced(nowNanos) - index * STAGGER_NANOS,
                PERIOD_NANOS);
        float share = phase / (float) PERIOD_NANOS;
        float lift = share < 0.5F ? share * 2.0F : (1.0F - share) * 2.0F;
        return FLOOR + (1.0F - FLOOR) * lift;
    }
}
