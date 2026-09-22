package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.MotionPlayer;
import com.ninuna.losttales.client.motion.MotionTrack;

/**
 * Where a hovered message's body row stands this frame: its chevron, how
 * far the chevron has lit, and each of its words. A word travels on its
 * own place in the row's stagger, spread across no more than the
 * motion's {@code stagger_span}; the row stretches by its gaps while it
 * travels, by whole pixels, the gaps opening from the first word on and
 * closing as the words land.
 */
final class ChatRowMotion {
    private final MotionPlayer player;
    private final long nowNanos;
    private final int staggerMillis;
    private final float staggerSpanMillis;

    ChatRowMotion(MotionPlayer player, long nowNanos, int staggerMillis,
                  float staggerSpanMillis) {
        this.player = player;
        this.nowNanos = nowNanos;
        this.staggerMillis = staggerMillis;
        this.staggerSpanMillis = staggerSpanMillis;
    }

    /** How far right the chevron stands, in the row's units. */
    float chevronX() {
        return this.player.value(ChatLineHover.CHEVRON, MotionTrack.X,
                this.nowNanos);
    }

    /** How far the chevron has lit toward the lighter shade of its colour, 0 to 1. */
    float brighten() {
        return Math.max(0.0F, Math.min(1.0F, this.player.value(
                ChatLineHover.CHEVRON, MotionTrack.BRIGHTEN, this.nowNanos)));
    }

    /** How far right word {@code index} of a row of {@code count} stands. */
    float wordX(int index, int count) {
        float place = staggerIndex(index, count);
        float x = this.player.value(ChatLineHover.WORDS, MotionTrack.X, place,
                this.nowNanos);
        if (count <= 1) {
            return x;
        }
        float stretch = this.player.value(ChatLineHover.WORDS, MotionTrack.GAP,
                place, this.nowNanos);
        return x + Math.round(stretch * index / (float)(count - 1));
    }

    /**
     * Where the end of a row stands: its last word's place, which is
     * where what follows the words, such as the delivery mark, rides.
     */
    float endX() {
        float last = this.staggerMillis <= 0 ? 0.0F
                : this.staggerSpanMillis / this.staggerMillis;
        return this.player.value(ChatLineHover.WORDS, MotionTrack.X, last,
                this.nowNanos) + Math.round(this.player.value(
                        ChatLineHover.WORDS, MotionTrack.GAP, last,
                        this.nowNanos));
    }

    /** The word's place in the stagger: its index, the row spread over the span at most. */
    private float staggerIndex(int index, int count) {
        if (count <= 1 || this.staggerMillis <= 0) {
            return index;
        }
        float spread = this.staggerMillis * (count - 1.0F);
        return index * Math.min(1.0F, this.staggerSpanMillis / spread);
    }
}
