package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import org.lwjgl.input.Mouse;

/**
 * How one small icon button answers the pointer: how far it has crossed
 * to its lit artwork, where it is drawn against the place it was laid
 * out, and how far it has turned. One of these per button, anywhere the
 * mod draws a glyph that can be pressed.
 *
 * <p>Four beats. Arriving, the button dips a little against the rise to
 * come, rises past its mark, and settles onto it. Pressed, it drops onto
 * the surface at once, and stays down long enough to be seen however
 * quickly the mouse lets go. Then it springs back through a few
 * alternating lobes that decay to nothing. Leaving, it returns faster
 * than it rose.</p>
 *
 * <p>Nothing happens while a button is idle. A slow wander of about a
 * pixel does not read as breathing on artwork sampled one texel to one
 * pixel: the texels land on one row or the next, so each crossing is a
 * visible jump rather than a drift. Motion of that size only reads as
 * motion while it is quick, which is why every beat here is short.</p>
 *
 * <p>Travel is the vocabulary; nothing scales. A five-pixel glyph has no
 * crisp step between one texel and two, so a squash needs drawn frames.
 * A {@link Character} may add a turn, which costs a display pixel or two
 * of straightness at the peak of a beat and nothing at all at rest,
 * since every pose the button holds is square.</p>
 *
 * <p>The places a button <em>holds</em> — resting, risen, pressed — are
 * whole GUI pixels, so each lands on the display grid whatever the GUI
 * Scale. The travel between them is left exactly as the curve gives it
 * and drawn through a translated matrix, because a rigid glyph rounded
 * to the grid every frame would step across it instead of moving.</p>
 *
 * <p>Each beat is read from the moment it began rather than accumulated,
 * so the pose is the same however often the screen is drawn, and a beat
 * cut short hands the next one the place it stood.</p>
 *
 * <p>Presentation only. A button answers the pointer on the box it was
 * laid out in, never the box it is drawn in: a risen button tested where
 * it is drawn would slide out from under the pointer and light and
 * unlight every frame.</p>
 */
public final class LostTalesUiButtonMotion {
    /**
     * The rise, in GUI pixels. One pixel is the clear space a framed
     * button keeps around what it holds, so a button rises to the edge
     * of its own clearing and no further.
     */
    public static final double RISE = 1.0D;
    /** How far below its resting row a held button sits. */
    public static final double PRESS = 1.0D;
    /** The dip against the rise; a beat passed through, not held. */
    private static final double DIP = 0.34D;
    /**
     * How far past its mark the rise reaches before settling. Stated
     * rather than taken from a back-out curve, whose own overshoot is a
     * few hundredths of the travel and would be well under a pixel here.
     */
    private static final double OVERSHOOT = 0.5D;
    /** The first lobe's reach once the button is let go. */
    private static final double RING = 1.0D;
    /** Share of the arrival spent dipping before the rise begins. */
    private static final float ANTICIPATION = 0.28F;
    private static final long ARRIVE_NANOS = 170L * 1000000L;
    private static final long LEAVE_NANOS = 110L * 1000000L;
    private static final long PRESS_NANOS = 55L * 1000000L;
    private static final long RING_NANOS = 260L * 1000000L;
    /** Lobes the spring back spends, alternating and decaying. */
    public static final int RING_LOBES = 3;
    /** A place this near a whole pixel counts as being on it. */
    private static final double SETTLED = 0.01D;
    /** How long a crossing to the lit artwork takes to cover most of its way. */
    private static final double LIT_SECONDS = 0.05D;
    /**
     * How long a press is held before the spring begins, whatever the
     * mouse does. A click can be shorter than the drop, and a quick one
     * can be over between two draws.
     */
    private static final long MIN_PRESS_NANOS = 90L * 1000000L;

    /**
     * What a button does when it answers the pointer. Each character
     * plays the same four beats and differs in how far and how quickly,
     * and in whether the glyph turns as it travels.
     */
    public enum Character {
        /**
         * Rises and springs without turning. What a glyph carrying a
         * face, a letter or a picture should use, since a tilted face
         * reads as a mistake.
         */
        LIFT(1.0F, 1.0F, 0.0F),
        /**
         * The same with a turn, for a glyph big enough and lopsided
         * enough to show one: a magnifier hanging off its handle, a
         * send arrow tipping as it throws. Check the artwork first. A
         * glyph unchanged by a quarter turn shows nothing however far it
         * is turned, and one only a few texels across has no
         * whole-pixel form at any angle between none and a quarter, so
         * the chat's cog, {@code +} and cross do not turn at all.
         */
        TURN(1.0F, 1.0F, 5.0F),
        /**
         * A smaller turn, for a glyph that reads as twisting into
         * something else rather than spinning: the {@code +} that
         * becomes a {@code -}.
         */
        TWIST(1.0F, 1.0F, 2.5F),
        /**
         * Further and quicker, with no turn: a decisive control such as
         * a cross, which should answer like a switch.
         */
        SNAP(1.25F, 0.68F, 0.0F);

        private final float reach;
        private final float pace;
        private final float turn;

        Character(float reach, float pace, float turn) {
            this.reach = reach;
            this.pace = pace;
            this.turn = turn;
        }

        /** Degrees the glyph turns once the button is fully risen. */
        public float getTurnDegrees() {
            return this.turn;
        }
    }

    private enum Beat { REST, ARRIVING, HELD, RINGING }

    private final Character character;

    private Beat beat = Beat.REST;
    /** When the running beat began, and the place the one before it left. */
    private long beatNanos;
    private double from;
    /** Where the spring back settles: the place it was let go from. */
    private double ringBase;
    /** Where the button stands now, in GUI pixels down from its resting row. */
    private double offset;
    private float lit;
    private boolean hovered;
    private boolean held;
    private boolean animated = true;
    private boolean started;
    /** Whether the mouse has let go and the spring is waiting on the drop. */
    private boolean releaseWaiting;
    /** The instant of the last step, so each caller need not keep a clock. */
    private long lastNanos;

    public LostTalesUiButtonMotion(Character character) {
        this.character = character == null ? Character.LIFT : character;
    }

    /**
     * Brings the motion up to this instant. {@code lit} is whether the
     * button wears its lit artwork, which is the pointer resting on it
     * or, for a button that says so, its own state; {@code hovered} and
     * {@code held} are the pointer alone. The time since the last step
     * is taken from {@code nowNanos}, so a caller keeps no clock of its
     * own. {@code animated} is the
     * player's own setting: the beats keep running while it is off so
     * turning it back on picks them up, but the button is drawn where it
     * was laid out and the crossing to the lit artwork is all that
     * shows.
     */
    public void advance(long nowNanos, boolean lit, boolean hovered,
                        boolean held, boolean animated) {
        this.animated = animated;
        double elapsedSeconds = this.lastNanos == 0L ? 0.0D
                : Math.max(0.0D, (nowNanos - this.lastNanos) / 1.0E9D);
        this.lastNanos = nowNanos;
        this.lit = (float)LostTalesGuiEasing.approach(this.lit,
                lit ? 1.0D : 0.0D, elapsedSeconds, LIT_SECONDS);
        if (Math.abs((lit ? 1.0F : 0.0F) - this.lit) < 0.02F) {
            this.lit = lit ? 1.0F : 0.0F;
        }
        if (!this.started) {
            // The first frame states where the button already is rather
            // than playing it a beat it has not been given.
            this.started = true;
            this.hovered = hovered;
            this.held = held;
            this.beatNanos = nowNanos;
            this.beat = held ? Beat.HELD
                    : hovered ? Beat.ARRIVING : Beat.REST;
        } else if (held != this.held) {
            // A press takes the button wherever it stands. Letting go
            // waits for the drop to have been seen, below.
            if (held) {
                begin(Beat.HELD, nowNanos);
            } else {
                this.releaseWaiting = true;
            }
            this.held = held;
        } else if (hovered != this.hovered) {
            // Neither a press nor the spring back is cut short by the
            // pointer moving on; the spring is left to finish and then
            // falls where it should.
            if (this.beat != Beat.HELD && this.beat != Beat.RINGING) {
                begin(hovered ? Beat.ARRIVING : Beat.REST, nowNanos);
            }
            this.hovered = hovered;
        }
        long elapsed = Math.max(0L, nowNanos - this.beatNanos);
        // A click can be over in less time than the drop takes, and a
        // very quick one can be gone between two draws. The press is
        // therefore held for long enough to be seen before the spring
        // begins, so a button always answers a click visibly.
        if (this.releaseWaiting && this.beat == Beat.HELD
                && elapsed >= paced(MIN_PRESS_NANOS)) {
            long ended = this.beatNanos + paced(MIN_PRESS_NANOS);
            this.offset = placeAt(paced(MIN_PRESS_NANOS));
            this.ringBase = this.hovered ? -rise() : 0.0D;
            begin(Beat.RINGING, ended);
            this.releaseWaiting = false;
            elapsed = Math.max(0L, nowNanos - ended);
        }
        long ringNanos = paced(RING_NANOS);
        if (this.beat == Beat.RINGING && elapsed >= ringNanos) {
            // The spring finishes on its base, and what follows begins
            // at the instant it ended rather than at this frame, so a
            // long frame carries its leftover time into the next beat
            // instead of holding the button still for one draw.
            long ended = this.beatNanos + ringNanos;
            this.offset = this.ringBase;
            begin(this.hovered ? Beat.ARRIVING : Beat.REST, ended);
            elapsed = Math.max(0L, nowNanos - ended);
        }
        this.offset = placeAt(elapsed);
    }

    /**
     * The common case: a button lit, hovered and held by the pointer
     * alone. The pointer's button is read straight from the mouse,
     * because a press that is missed costs a flourish rather than an
     * action; a caller whose button is also lit by its own state — a
     * field with the keys, a panel already open — states that with
     * {@link #advance(long, boolean, boolean, boolean, boolean)}.
     */
    public void advance(long nowNanos, boolean hovered, boolean animated) {
        advance(nowNanos, hovered, hovered, hovered && Mouse.isButtonDown(0),
                animated);
    }

    private void begin(Beat next, long nowNanos) {
        this.from = this.offset;
        this.beat = next;
        this.beatNanos = nowNanos;
    }

    /** This character's rise, and the reach of everything measured from it. */
    private double rise() {
        return RISE * this.character.reach;
    }

    private long paced(long nanos) {
        return (long)(nanos * this.character.pace);
    }

    /** Where the running beat puts the button after this long. */
    private double placeAt(long elapsed) {
        if (this.beat == Beat.HELD) {
            return lerp(this.from, PRESS * this.character.reach,
                    LostTalesGuiEasing.easeOutCubic(
                            share(elapsed, paced(PRESS_NANOS))));
        }
        if (this.beat == Beat.RINGING) {
            float progress = share(elapsed, paced(RING_NANOS));
            // The first lobe is what carries the button up out of the
            // press, so the base is reached across that lobe rather
            // than at once.
            double settling = lerp(this.from, this.ringBase,
                    LostTalesGuiEasing.easeOutCubic(
                            Math.min(1.0F, progress * RING_LOBES)));
            return settling - RING * this.character.reach
                    * ringOut(progress, RING_LOBES);
        }
        if (this.beat == Beat.ARRIVING) {
            float progress = share(elapsed, paced(ARRIVE_NANOS));
            if (this.from > -SETTLED) {
                return arriveFromRest(progress, this.character.reach);
            }
            // Already up, where a dip would read as a stumble.
            return lerp(this.from, -rise(),
                    LostTalesGuiEasing.easeOutCubic(progress));
        }
        return lerp(this.from, 0.0D, LostTalesGuiEasing.easeOutCubic(
                share(elapsed, paced(LEAVE_NANOS))));
    }

    /**
     * The arrival in full: down against the rise, then up past the mark
     * and back onto it. Both the dip and the reach past the mark are
     * shaped by a half sine, so each leaves from nothing and returns to
     * nothing and the three pieces join without a corner.
     */
    private static double arriveFromRest(float progress, float reach) {
        if (progress < ANTICIPATION) {
            return DIP * reach * Math.sin(Math.PI * progress / ANTICIPATION);
        }
        float rise = (progress - ANTICIPATION) / (1.0F - ANTICIPATION);
        return -reach * (RISE * LostTalesGuiEasing.easeOutCubic(rise)
                + OVERSHOOT * Math.sin(Math.PI * rise));
    }

    /** How far the button has crossed to its lit artwork. */
    public float lit() {
        return this.lit;
    }

    /**
     * Where the button is drawn, in GUI pixels down from the row it was
     * laid out on, and negative while it is up. Nothing at all while the
     * player has animation switched off.
     */
    public double offsetY() {
        return this.animated ? this.offset : 0.0D;
    }

    /**
     * How far the glyph has turned, in degrees. A turn is a transient
     * about the middle of the sprite: the glyph spins while it travels
     * and is square again the moment it settles, whether that is on its
     * row, risen under the pointer, or pressed onto the surface. It
     * turns furthest on a press, which is the longest journey it makes.
     */
    public float turnDegrees() {
        float turn = this.character.getTurnDegrees();
        if (!this.animated || turn == 0.0F) {
            return 0.0F;
        }
        // Measured from where the beat is settling rather than from the
        // resting row, so every pose the button actually holds is
        // square and only its travel turns it. A glyph left standing at
        // an angle reads as pivoting off its own middle, and a still
        // tilt is exactly where a five-pixel sprite shows its
        // stairsteps.
        double travelling = (settlingPlace() - this.offset) / RISE;
        return (float)(turn * Math.max(-1.5D, Math.min(1.5D, travelling)));
    }

    /**
     * Where the running beat is settling: the place the button holds
     * once it stops moving. Resting on its row, risen under the pointer,
     * pressed onto the surface, or the place a spring is decaying to.
     */
    private double settlingPlace() {
        if (this.beat == Beat.HELD) {
            return PRESS * this.character.reach;
        }
        if (this.beat == Beat.RINGING) {
            return this.ringBase;
        }
        return this.beat == Beat.ARRIVING ? -rise() : 0.0D;
    }

    /** Whether the button is standing still on a whole pixel. */
    public boolean isSettled() {
        return Math.abs(this.offset - Math.round(this.offset)) < SETTLED
                && (this.beat == Beat.REST || this.beat == Beat.HELD
                        || (this.beat == Beat.ARRIVING
                                && Math.abs(this.offset + rise()) < SETTLED));
    }

    /**
     * A spring settling through {@code lobes} triangle lobes, each
     * leaning the opposite way to the one before and reaching less far,
     * the way a banner rings out after something brushes past it. Zero
     * at both ends, and one at the first lobe's peak.
     */
    public static float ringOut(float progress, int lobes) {
        if (lobes <= 0 || progress <= 0.0F || progress >= 1.0F) {
            return 0.0F;
        }
        float scaled = progress * lobes;
        int lobe = (int)scaled;
        if (lobe >= lobes) {
            return 0.0F;
        }
        float within = scaled - lobe;
        float triangle = within > 0.5F
                ? (1.0F - within) * 2.0F : within * 2.0F;
        float decay = (lobes - lobe) / (float)lobes;
        return lobe % 2 == 0 ? triangle * decay : -triangle * decay;
    }

    private static float share(long elapsed, long total) {
        return total <= 0L ? 1.0F
                : LostTalesGuiEasing.clamp(elapsed / (float)total);
    }

    private static double lerp(double from, double to, float progress) {
        return from + (to - from) * progress;
    }
}
