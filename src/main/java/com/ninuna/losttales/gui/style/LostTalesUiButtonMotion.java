package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.motion.Motion;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionPlayer;
import com.ninuna.losttales.client.motion.MotionTrack;
import com.ninuna.losttales.client.motion.Motions;
import org.lwjgl.input.Mouse;

/**
 * How one small icon button answers the pointer: how far it has crossed
 * to its lit artwork, where it is drawn against the place it was laid
 * out, and how far it has turned. One of these per button, anywhere the
 * mod draws a glyph that can be pressed.
 *
 * <p>Four beats, each a beat of the button's motion ({@link Character}),
 * so the files say how far and how quickly: arriving, the button dips a
 * little against the rise to come, rises past its mark, and settles onto
 * it; pressed, it drops onto the surface at once and stays down long
 * enough to be seen however quickly the mouse lets go; let go, it springs
 * back through a few alternating lobes that decay to nothing; leaving, it
 * returns faster than it rose. This class keeps the rules between the
 * beats: a press or a spring is never cut short by the pointer moving on,
 * and a spring ends where the pointer then is.</p>
 *
 * <p>Nothing happens while a button is idle. A slow wander of about a
 * pixel does not read as breathing on artwork sampled one texel to one
 * pixel: the texels land on one row or the next, so each crossing is a
 * visible jump rather than a drift. Travel is the vocabulary; nothing
 * scales. A {@link Character} may add a turn, which costs a display pixel
 * or two of straightness at the peak of a beat and nothing at rest.</p>
 *
 * <p>The places a button holds are whole GUI pixels; the travel between
 * them is drawn through a translated matrix, because a rigid glyph
 * rounded to the grid every frame would step across it instead of
 * moving. Presentation only: a button answers the pointer on the box it
 * was laid out in, never the box it is drawn in.</p>
 */
public final class LostTalesUiButtonMotion {
    /** The part of a button's motion that moves: the glyph. */
    static final String GLYPH = "glyph";
    /** The pose of a button pressed onto the surface. */
    static final String PRESSED = "pressed";
    private static final String PRESS_BEAT = "press";
    private static final String RELEASE_BEAT = "release";
    /** Degrees the glyph turns per pixel it still has to travel. */
    private static final String TURN_PARAM = "turn";
    /** How long a press is held before the spring begins, in milliseconds. */
    private static final String MIN_PRESS_PARAM = "min_press";

    /**
     * What a button does when it answers the pointer: its motion, which
     * says how far and how quickly each beat goes and whether the glyph
     * turns as it travels.
     */
    public enum Character {
        /**
         * Rises and springs without turning. What a glyph carrying a
         * face, a letter or a picture should use, since a tilted face
         * reads as a mistake.
         */
        LIFT(MotionIds.UI_BUTTON_LIFT),
        /**
         * The same with a turn, for a glyph big enough and lopsided
         * enough to show one: a magnifier hanging off its handle, a send
         * arrow tipping as it throws. A glyph unchanged by a quarter turn
         * shows nothing however far it is turned, and one only a few
         * texels across has no whole-pixel form at any angle between none
         * and a quarter, so the chat's cog, {@code +} and cross do not
         * turn at all.
         */
        TURN(MotionIds.UI_BUTTON_TURN),
        /**
         * Further and quicker, with no turn: a decisive control such as
         * a cross, which should answer like a switch.
         */
        SNAP(MotionIds.UI_BUTTON_SNAP);

        private final String motionId;

        Character(String motionId) {
            this.motionId = motionId;
        }

        /** The motion the character plays. */
        public String motionId() {
            return this.motionId;
        }

        /** Degrees the glyph turns per pixel still to travel, its motion's {@code turn}. */
        public float getTurnDegrees() {
            return Motions.param(this.motionId, TURN_PARAM, 0.0F);
        }
    }

    private enum Beat { REST, ARRIVING, HELD, RINGING }

    private final Character character;
    private final MotionPlayer player;

    private Beat beat = Beat.REST;
    /** When the running beat began. */
    private long beatNanos;
    private float lit;
    private boolean hovered;
    private boolean held;
    private boolean started;
    /** Whether the mouse has let go and the spring is waiting on the drop. */
    private boolean releaseWaiting;
    /** The instant of the last step, so each caller need not keep a clock. */
    private long lastNanos;
    /** The instant the pose was last read at. */
    private long drawNanos;

    public LostTalesUiButtonMotion(Character character) {
        this.character = character == null ? Character.LIFT : character;
        this.player = new MotionPlayer(this.character.motionId());
    }

    /**
     * Brings the motion up to this instant. {@code lit} is whether the
     * button wears its lit artwork, which is the pointer resting on it
     * or, for a button that says so, its own state; {@code hovered} and
     * {@code held} are the pointer alone. The time since the last step
     * is taken from {@code nowNanos}, so a caller keeps no clock of its
     * own.
     */
    public void advance(long nowNanos, boolean lit, boolean hovered,
                        boolean held) {
        double elapsedSeconds = this.lastNanos == 0L ? 0.0D
                : Math.max(0.0D, (nowNanos - this.lastNanos) / 1.0E9D);
        this.lastNanos = nowNanos;
        this.drawNanos = nowNanos;
        this.lit = (float)Motions.follow(MotionIds.UI_BUTTON_LIT, this.lit,
                lit ? 1.0D : 0.0D, elapsedSeconds);
        if (Math.abs((lit ? 1.0F : 0.0F) - this.lit) < 0.02F) {
            this.lit = lit ? 1.0F : 0.0F;
        }
        if (!this.started) {
            // The first frame states where the button already is rather
            // than playing it a beat it has not been given.
            this.started = true;
            this.hovered = hovered;
            this.held = held;
            this.player.settle(held ? PRESSED : Motion.REST);
            this.beat = held ? Beat.HELD : Beat.REST;
            this.beatNanos = nowNanos;
            if (hovered && !held) {
                begin(Beat.ARRIVING, nowNanos);
            }
        } else if (held != this.held) {
            // A press takes the button wherever it stands. Letting go
            // waits for the drop to have been seen, below.
            if (held) {
                this.releaseWaiting = false;
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
        // A click can be over in less time than the drop takes, and a
        // very quick one can be gone between two draws. The press is
        // therefore held for long enough to be seen before the spring
        // begins, so a button always answers a click visibly.
        long minPress = Motions.scaledNanos(Math.round(Motions.param(
                this.character.motionId(), MIN_PRESS_PARAM, 0.0F)));
        if (this.releaseWaiting && this.beat == Beat.HELD
                && nowNanos - this.beatNanos >= minPress) {
            this.releaseWaiting = false;
            begin(Beat.RINGING, this.beatNanos + minPress);
        }
        if (this.beat == Beat.RINGING && this.player.isSettled(nowNanos)) {
            // The spring has finished on the pose it was let go toward.
            // What follows is where the pointer is now, from the instant
            // the spring ended rather than this frame, so a long frame
            // carries its leftover time into the next beat.
            long ended = this.beatNanos + Motions.nanos(
                    this.character.motionId(), RELEASE_BEAT);
            this.beat = this.hovered ? Beat.ARRIVING : Beat.REST;
            String wanted = this.hovered ? Motion.ON : Motion.REST;
            if (!wanted.equals(this.player.pose(GLYPH))) {
                begin(this.beat, ended);
            }
        }
    }

    /**
     * The common case: a button lit, hovered and held by the pointer
     * alone. The pointer's button is read straight from the mouse,
     * because a press that is missed costs a flourish rather than an
     * action; a caller whose button is also lit by its own state — a
     * field with the keys, a panel already open — states that with
     * {@link #advance(long, boolean, boolean, boolean)}.
     */
    public void advance(long nowNanos, boolean hovered) {
        advance(nowNanos, hovered, hovered, hovered && Mouse.isButtonDown(0));
    }

    private void begin(Beat next, long nowNanos) {
        this.beat = next;
        this.beatNanos = nowNanos;
        switch (next) {
            case HELD:
                this.player.play(PRESS_BEAT, nowNanos);
                break;
            case RINGING:
                this.player.play(RELEASE_BEAT,
                        this.hovered ? Motion.ON : Motion.REST, nowNanos);
                break;
            case ARRIVING:
                this.player.play(Motion.ON, nowNanos);
                break;
            default:
                this.player.play(Motion.OFF, nowNanos);
                break;
        }
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
        return Motions.enabled()
                ? this.player.value(GLYPH, MotionTrack.Y, this.drawNanos)
                : 0.0D;
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
        if (!Motions.enabled() || turn == 0.0F) {
            return 0.0F;
        }
        // Measured from where the beat is settling rather than from the
        // resting row, so every pose the button actually holds is square
        // and only its travel turns it.
        double travelling = this.player.target(GLYPH, MotionTrack.Y)
                - offsetY();
        return (float)(turn * Math.max(-1.5D, Math.min(1.5D, travelling)));
    }

    /** Whether the button is standing still on the pose it holds. */
    public boolean isSettled() {
        return this.player.isSettled(this.drawNanos);
    }
}
