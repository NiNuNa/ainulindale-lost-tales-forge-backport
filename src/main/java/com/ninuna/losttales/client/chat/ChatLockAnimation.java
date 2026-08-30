package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import org.lwjgl.opengl.GL11;

/**
 * The padlock a chat control carries, and the motion that turns it.
 *
 * <p>The sheet holds the lock as a grid rather than as named cells: one
 * block of twelve frames per colourway, the shackle swung fully open in
 * the first and shut in the last. The frames run down the block's left
 * column and back up its right one, which is the order they were drawn
 * in and the order {@link #FRAMES} lists them. Every frame is anchored
 * on the lock's body — the bottom-left corner of its cell — so the body
 * stands still while the shackle swings over it; {@link #WIDTH} and
 * {@link #HEIGHT} are the room the whole turn needs.</p>
 *
 * <p>Three colourways say what the control means. Away from the pointer
 * it wears the resting one; under it, green while the window is open and
 * wine while it is shut, one crossfading into the other exactly as far
 * as the shackle has swung.</p>
 *
 * <p>The turn answers the lock itself, not the pointer: it starts when
 * the window is locked or unlocked and carries the shackle from the pose
 * it was in to the one it is now in, whether or not the pointer is still
 * there. It is not a straight walk between the two. The lock winds up
 * against the way it is about to travel, swings through, overshoots and
 * rings back to rest, squashing as it lands and stretching as it springs
 * open; the body leans with the shackle as it goes over, and the shadow
 * trails the whole thing rather than moving with it. Hovering plays a
 * shorter tell of the same shape — a nudge the way a click would send it
 * — so the control says which way it is about to go before it is
 * pressed. All of it is read from elapsed time, so it looks the same at
 * any frame rate, and none of it goes through the shared GUI animation:
 * this is the padlock's own motion, as the key hints have theirs.</p>
 */
final class ChatLockAnimation {
    /**
     * A frame's cell as an offset into its colourway's block:
     * {@code {u offset, v, width, height}}, open to shut. The bottom row
     * of every cell is the lock's body, so a frame is drawn with its
     * bottom on the control's baseline.
     */
    private static final int[][] FRAMES = {
            {0, 27, 9, 7}, {0, 35, 9, 6}, {0, 42, 8, 7}, {0, 50, 7, 8},
            {0, 59, 6, 8}, {0, 68, 5, 8}, {10, 68, 5, 8}, {10, 59, 5, 8},
            {10, 50, 5, 8}, {10, 42, 5, 7}, {10, 35, 5, 6}, {10, 27, 5, 7}};

    /** Sheet column each colourway's block starts at. */
    /** How far the lock has crossed to its hovered colourway, and when. */
    private float hoverFade;
    private long hoverFadeNanos;

    private static final int RESTING_U = 50;
    private static final int OPEN_HOVER_U = 66;
    private static final int SHUT_HOVER_U = 82;

    /** Room the turn needs: the widest frame by the tallest. */
    static final int WIDTH = 9;
    static final int HEIGHT = 8;
    /** Width of the shut padlock, which is narrower than that box. */
    static final int SHUT_WIDTH = FRAMES[FRAMES.length - 1][2];
    /**
     * Height of the padlock at rest, which is shorter than the box the
     * swing needs: the frames stand on the box's floor and the open
     * shackle reaches up out of it. A control centres the lock on this,
     * not on the box, or the resting padlock sits low in its strip.
     */
    static final int SHUT_HEIGHT = FRAMES[FRAMES.length - 1][3];

    /** The wind-up against the way it is about to travel. */
    private static final long TURN_ANTICIPATE_NANOS = 55000000L;
    /** The swing itself; the frames advance across exactly this. */
    private static final long TURN_ACTION_NANOS = 130000000L;
    /** The overshoot ringing back to rest, the frames already home. */
    private static final long TURN_SETTLE_NANOS = 230000000L;
    /** The nudge hovering arrives on, before it settles into its stance. */
    private static final long TELL_NANOS = 220000000L;
    /**
     * The strain a shut padlock is under while the pointer rests on it:
     * a fast tremor riding a slow swell, the two on periods that do not
     * divide each other so the shake never traces one line. The swell is
     * cubed, which spends most of its time low and gathers into a short
     * heave — a thing pushing against a catch rather than a thing
     * wobbling.
     */
    private static final long STRAIN_PERIOD_NANOS = 190000000L;
    private static final long SWELL_PERIOD_NANOS = 1150000000L;
    private static final double TWO_PI = Math.PI * 2.0D;
    /**
     * Longest gap between draws the motion counts: a control that was
     * off screen, or a stalled frame, comes back at rest rather than
     * finishing a turn nobody watched.
     */
    private static final long STALE_AFTER_NANOS = 400000000L;
    /** How far behind the padlock's own pose its shadow trails. */
    private static final float SHADOW_LAG = 0.55F;
    private static final float SHADOW_ROTATION_SHARE = 0.6F;

    /** 0 at the open pose, 1 at the shut one; what picks the frame. */
    private float swing;
    /** The pose the running turn set out from, so a reversal is smooth. */
    private float turnFrom;
    private float turnTo;
    /** +1 while shutting, -1 while opening: the way everything leans. */
    private float direction;
    private long turnStartedNanos;
    private long hoverStartedNanos;
    private boolean previouslyLocked;
    private boolean previouslyHovered;
    private boolean seen;
    private long lastSeenNanos;

    /**
     * The control's padlock at {@code (x, top)}. {@code top} is the top
     * of the {@link #WIDTH} by {@link #HEIGHT} box the turn is drawn in.
     */
    void draw(int x, int top, boolean locked, boolean hovered, int alpha) {
        Pose pose = advance(locked, hovered, System.nanoTime());
        int frame = Math.round((FRAMES.length - 1) * this.swing);
        // The shadow first, trailing the pose rather than moving with it
        // and turning less than it does: it is cast on the strip, not
        // carried by the lock.
        int shadowAlpha = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadowAlpha > 0) {
            LostTalesSilhouetteRenderState.begin(
                    LostTalesChatVisualStyle.SHADOW);
            try {
                begin(pose, x, top, true);
                try {
                    drawFrame(frame, RESTING_U, x, top, shadowAlpha);
                } finally {
                    end();
                }
            } finally {
                LostTalesSilhouetteRenderState.end();
            }
        }
        begin(pose, x, top, false);
        try {
            // The resting colourway always, with the hovered one laid
            // over it as far as the pointer has brought it: the lock
            // crosses to its lit tones rather than swapping to them, as
            // every other control the chat draws in two states does.
            drawFrame(frame, RESTING_U, x, top, alpha);
            int lit = Math.round(alpha * this.hoverFade);
            if (lit < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                return;
            }
            // Green under the open padlock, wine under the shut one: the
            // shut colourway is laid over the open one as far as the
            // shackle has come, so the two cross over with the swing.
            drawFrame(frame, OPEN_HOVER_U, x, top, lit);
            drawFrame(frame, SHUT_HOVER_U, x, top,
                    Math.round(lit * this.swing));
        } finally {
            end();
        }
    }

    /** The shut padlock at rest: a badge, not a control. */
    static void drawShut(int x, int top, int alpha) {
        int frame = FRAMES.length - 1;
        int shadowAlpha = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadowAlpha > 0) {
            LostTalesSilhouetteRenderState.begin(
                    LostTalesChatVisualStyle.SHADOW);
            try {
                drawFrame(frame, RESTING_U,
                        x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        top + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        shadowAlpha);
            } finally {
                LostTalesSilhouetteRenderState.end();
            }
        }
        drawFrame(frame, RESTING_U, x, top, alpha);
    }

    /**
     * Puts the pose on the matrix. The lock turns and scales about the
     * middle of its body's foot, the one part of it standing on
     * something, so a squash presses it down onto the strip and a lean
     * rocks it rather than sliding it.
     */
    private static void begin(Pose pose, int x, int top, boolean shadow) {
        float pivotX = x + SHUT_WIDTH / 2.0F;
        float pivotY = top + HEIGHT;
        GL11.glPushMatrix();
        GL11.glTranslatef(
                pose.offsetX + (shadow ? pose.shadowOffsetX : 0.0F),
                pose.offsetY + (shadow ? pose.shadowOffsetY : 0.0F), 0.0F);
        GL11.glTranslatef(pivotX, pivotY, 0.0F);
        GL11.glRotatef(pose.rotationDegrees
                * (shadow ? SHADOW_ROTATION_SHARE : 1.0F), 0.0F, 0.0F, 1.0F);
        GL11.glScalef(pose.scaleX, pose.scaleY, 1.0F);
        GL11.glTranslatef(-pivotX, -pivotY, 0.0F);
    }

    private static void end() {
        GL11.glPopMatrix();
    }

    private static void drawFrame(int frame, int block, float x, float top,
                                  int alpha) {
        int[] cell = FRAMES[Math.max(0, Math.min(FRAMES.length - 1, frame))];
        ChatIconSheet.draw(block + cell[0], cell[1], cell[2], cell[3], x,
                top + HEIGHT - cell[3], alpha);
    }

    /**
     * Reads the timelines the lock's own state started, and returns the
     * pose for this instant. Everything is measured from when a turn or
     * a tell began rather than accumulated, so the motion is the same
     * however often the screen is drawn.
     */
    private Pose advance(boolean locked, boolean hovered, long nowNanos) {
        if (this.seen && nowNanos - this.lastSeenNanos > STALE_AFTER_NANOS) {
            // Away long enough that finishing would read as a glitch.
            this.turnStartedNanos = 0L;
            this.hoverStartedNanos = 0L;
            this.swing = locked ? 1.0F : 0.0F;
        }
        this.lastSeenNanos = nowNanos;
        if (!this.seen) {
            // First sight: the lock stands in the pose it is already in
            // rather than turning into it.
            this.seen = true;
            this.swing = locked ? 1.0F : 0.0F;
            this.previouslyLocked = locked;
            this.previouslyHovered = hovered;
        }
        if (locked != this.previouslyLocked) {
            this.previouslyLocked = locked;
            this.turnFrom = this.swing;
            this.turnTo = locked ? 1.0F : 0.0F;
            this.direction = locked ? 1.0F : -1.0F;
            this.turnStartedNanos = nowNanos;
        }
        this.previouslyHovered = hovered;
        double sinceDrawn = this.hoverFadeNanos == 0L ? 0.0D
                : (nowNanos - this.hoverFadeNanos) / 1.0E9D;
        this.hoverFadeNanos = nowNanos;
        this.hoverFade = LostTalesChatVisualStyle.hoverFade(this.hoverFade,
                hovered, sinceDrawn);
        if (!hovered) {
            this.hoverStartedNanos = 0L;
        } else if (this.hoverStartedNanos == 0L) {
            this.hoverStartedNanos = nowNanos;
        }
        if (this.turnStartedNanos != 0L) {
            long elapsed = nowNanos - this.turnStartedNanos;
            if (elapsed < TURN_ANTICIPATE_NANOS + TURN_ACTION_NANOS
                    + TURN_SETTLE_NANOS) {
                return turnPose(elapsed);
            }
            this.turnStartedNanos = 0L;
            this.swing = this.turnTo;
            // A turn that ended under the pointer hands straight over to
            // the stance, rather than the stance starting mid-way.
            this.hoverStartedNanos = hovered ? nowNanos : 0L;
        }
        if (hovered) {
            return hoverPose(nowNanos - this.hoverStartedNanos, nowNanos,
                    locked);
        }
        return pose(0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    /** Wind-up, swing, then the overshoot ringing out. */
    private Pose turnPose(long elapsedNanos) {
        float lean = this.direction;
        if (elapsedNanos < TURN_ANTICIPATE_NANOS) {
            float progress = easeOutCubic(
                    (float)elapsedNanos / (float)TURN_ANTICIPATE_NANOS);
            this.swing = this.turnFrom;
            // Against the travel, and rising off the strip as it winds.
            return pose(-0.30F * lean * progress, -0.45F * progress,
                    -1.7F * lean * progress,
                    1.0F - 0.035F * progress, 1.0F + 0.055F * progress);
        }
        long afterWindUp = elapsedNanos - TURN_ANTICIPATE_NANOS;
        if (afterWindUp < TURN_ACTION_NANOS) {
            float progress = easeOutCubic(
                    (float)afterWindUp / (float)TURN_ACTION_NANOS);
            this.swing = this.turnFrom
                    + (this.turnTo - this.turnFrom) * progress;
            // Out of the wind-up, past rest and a little beyond: the
            // shackle carries the body with it before anything stops.
            return pose(lerp(-0.30F * lean, 0.50F * lean, progress),
                    lerp(-0.45F, 0.40F, progress),
                    lerp(-1.7F * lean, 2.1F * lean, progress),
                    lerp(0.965F, 1.065F, progress),
                    lerp(1.055F, 0.915F, progress));
        }
        float progress = (float)(afterWindUp - TURN_ACTION_NANOS)
                / (float)TURN_SETTLE_NANOS;
        this.swing = this.turnTo;
        float decay = (float)Math.exp(-4.6F * progress);
        float ring = decay * (float)Math.cos(progress * Math.PI * 2.4D);
        return pose(0.50F * lean * ring, 0.40F * ring, 2.1F * lean * ring,
                1.0F + 0.065F * ring, 1.0F - 0.085F * ring);
    }

    /**
     * What the lock does under the pointer. It arrives on one soft hump
     * the way a click would send it — lifting when a click would spring
     * it open, pressing down when a click would shut it — and then holds
     * a stance rather than going still, the arrival easing into the
     * stance so there is no seam between them.
     */
    private Pose hoverPose(long elapsedNanos, long nowNanos,
                           boolean locked) {
        float entry = clamp01((float)elapsedNanos / (float)TELL_NANOS);
        float hump = (float)Math.sin(entry * Math.PI)
                * (float)Math.exp(-1.4F * entry);
        float settled = smoothStep(entry);
        return locked
                ? pressurePose(nowNanos, settled, hump)
                : easePose(nowNanos, settled, hump);
    }

    /**
     * A shut padlock under the pointer is under pressure: something
     * behind the shackle heaving against the catch and not quite getting
     * out. A fast tremor rides a slow swell, the swell cubed so it
     * gathers rather than wobbles — the body stretches up and narrows on
     * each heave, rocks hardest at the top of one, and drops back. It
     * says the lock is holding something in and that a click would let
     * it go.
     */
    private static Pose pressurePose(long nowNanos, float settled,
                                     float hump) {
        double strain = phase(nowNanos, STRAIN_PERIOD_NANOS);
        double swellPhase = phase(nowNanos, SWELL_PERIOD_NANOS);
        float tremor = 0.34F * (float)Math.sin(strain)
                + 0.18F * (float)Math.sin(strain * 1.87D + 0.7D);
        float rise = (float)((Math.sin(swellPhase) + 1.0D) * 0.5D);
        float swell = rise * rise * rise;
        // The rock is strongest where the heave is, so the lean and the
        // strain read as one effort rather than two loops.
        float rock = (1.9F * (float)Math.sin(strain * 0.73D + 0.3D)
                + 1.0F * (float)Math.sin(strain * 1.31D + 1.4D))
                * (0.35F + 0.65F * swell);
        return pose(settled * 0.26F * tremor,
                settled * (-0.78F * swell + 0.20F * tremor) - 0.40F * hump,
                settled * rock - 0.95F * hump,
                1.0F - settled * 0.078F * swell,
                1.0F + settled * 0.104F * swell + 0.024F * hump);
    }

    /**
     * An open padlock under the pointer is at ease: a slow breath and
     * the faintest lean the way a click would shut it. The contrast is
     * the point — the two states should not need reading twice.
     */
    private static Pose easePose(long nowNanos, float settled, float hump) {
        double breath = phase(nowNanos, SWELL_PERIOD_NANOS * 2L);
        float sway = (float)Math.sin(breath);
        return pose(settled * 0.06F * sway + 0.16F * hump,
                settled * (0.16F + 0.09F * sway) + 0.34F * hump,
                settled * (0.35F + 0.28F * sway) + 0.95F * hump,
                1.0F, 1.0F - 0.030F * hump);
    }

    /** Where a loop of {@code periodNanos} stands, in radians. */
    private static double phase(long nowNanos, long periodNanos) {
        return (double)Math.floorMod(nowNanos, periodNanos)
                / (double)periodNanos * TWO_PI;
    }

    private static float smoothStep(float value) {
        float bounded = clamp01(value);
        return bounded * bounded * (3.0F - 2.0F * bounded);
    }

    /** A pose, with the shadow's own trailing offset worked out from it. */
    private static Pose pose(float offsetX, float offsetY,
                             float rotationDegrees, float scaleX,
                             float scaleY) {
        return new Pose(offsetX, offsetY, rotationDegrees, scaleX, scaleY,
                LostTalesChatVisualStyle.SHADOW_OFFSET
                        - offsetX * SHADOW_LAG,
                LostTalesChatVisualStyle.SHADOW_OFFSET
                        - offsetY * SHADOW_LAG);
    }

    private static float easeOutCubic(float value) {
        float bounded = clamp01(value);
        float inverse = 1.0F - bounded;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Where the padlock stands this instant, and where its shadow does. */
    private static final class Pose {
        private final float offsetX;
        private final float offsetY;
        private final float rotationDegrees;
        private final float scaleX;
        private final float scaleY;
        private final float shadowOffsetX;
        private final float shadowOffsetY;

        private Pose(float offsetX, float offsetY, float rotationDegrees,
                     float scaleX, float scaleY, float shadowOffsetX,
                     float shadowOffsetY) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.rotationDegrees = rotationDegrees;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.shadowOffsetX = shadowOffsetX;
            this.shadowOffsetY = shadowOffsetY;
        }
    }
}
