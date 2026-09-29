package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;

/**
 * What a page answers the player: an action's result, standing over the
 * page's bar rather than in the chat, which keeps to conversation. One
 * answer at a time, a new one taking the old one's place. It comes in as
 * a window's notice does, rising its last pixels, and a done or refused
 * answer stands its hold and fades where it stands
 * ({@code window.answer}); a working one, and a fault, stand until the
 * page says something else.
 */
public final class PageAnswer {
    /** What kind of answer it is, which decides its words' colour and how long it stands. */
    public enum Kind {
        /** Done as asked: saved, accepted. */
        DONE,
        /** Turned down, by the server or the page: its reason. */
        REFUSED,
        /** On its way: the answer it waits for takes its place. */
        WORKING,
        /** A refusal that stands while its cause does: an edit that cannot be read. */
        FAULT
    }

    private String words = "";
    private Kind kind = Kind.DONE;
    private long saidNanos;
    /** Whether the answer stood as it was last shown: it rises while it comes in, and fades where it stands. */
    private boolean standing;
    private final MotionTransition fade =
            new MotionTransition(MotionIds.WINDOW_ANSWER);

    /** Says {@code words}, of {@code kind}, from {@code nowNanos} on; empty words say nothing. */
    void say(String words, Kind kind, long nowNanos) {
        this.words = words == null ? "" : words;
        this.kind = kind == null ? Kind.DONE : kind;
        this.saidNanos = nowNanos;
        this.fade.settle(false);
    }

    /** Takes the answer back at once. */
    void clear() {
        this.words = "";
        this.fade.settle(false);
    }

    /** The words standing, or fading; empty for none. */
    public String words() {
        return this.words;
    }

    public Kind kind() {
        return this.kind;
    }

    /**
     * Whether the answer stands at {@code nowNanos}: a working answer and
     * a fault until something else is said, a done or refused one for
     * {@code holdNanos} after it was said.
     */
    boolean stands(long nowNanos, long holdNanos) {
        if (this.words.length() == 0) {
            return false;
        }
        return this.kind == Kind.WORKING || this.kind == Kind.FAULT
                || nowNanos - this.saidNanos < holdNanos;
    }

    /**
     * How much of the answer shows at {@code nowNanos}: faded in as it
     * came, and out once it no longer stands, when its words go.
     */
    float shown(long nowNanos) {
        long hold = (long)(Motions.param(MotionIds.WINDOW_ANSWER, "hold",
                3000.0F) * 1000000L);
        this.standing = stands(nowNanos, hold);
        this.fade.advance(nowNanos, this.standing);
        if (!this.standing && this.fade.isSettled()
                && this.fade.clamped() <= 0.0F) {
            this.words = "";
        }
        return this.words.length() == 0 ? 0.0F : this.fade.clamped();
    }

    /** Whether the answer stood as it was last shown, so it rises as it fades in rather than sinking as it goes. */
    boolean isStanding() {
        return this.standing;
    }
}
