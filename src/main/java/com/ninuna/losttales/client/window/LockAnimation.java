package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import org.lwjgl.opengl.GL11;

/**
 * The padlock a window's lock control carries, and the motion that turns it.
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
 * trails the whole thing rather than moving with it. The pointer is
 * answered once, as every button answers it ({@code TabRow}'s button
 * motion lifts and springs the whole padlock), and the lock itself
 * stands still while the pointer rests on it: only its colourway crosses
 * over. The turn is read from elapsed time, so it looks the same at any
 * frame rate. It is timed by hand rather than by a motion file, and
 * keeps to the motion settings all the same: it plays at the speed in
 * force, and with the Animations switch off or motion reduced the
 * shackle simply stands where the lock is.</p>
 */
final class LockAnimation {
    /**
     * A frame's cell as an offset into its colourway's block:
     * {@code {u offset, v, width, height}}, open to shut. The bottom row
     * of every cell is the lock's body, so a frame is drawn with its
     * bottom on the control's baseline.
     */
    private static final int[][] FRAMES = {
            {0, 43, 9, 7}, {0, 51, 9, 6}, {0, 58, 8, 7}, {0, 66, 7, 8},
            {0, 75, 6, 8}, {0, 84, 5, 8}, {10, 84, 5, 8}, {10, 75, 5, 8},
            {10, 66, 5, 8}, {10, 58, 5, 7}, {10, 51, 5, 6}, {10, 43, 5, 7}};

    /** How far the lock has crossed to its hovered colourway, and when. */
    private float hoverFade;
    private long hoverFadeNanos;

    /** Sheet column each colourway's block starts at. */
    private static final int RESTING_U = 61;
    private static final int OPEN_HOVER_U = 77;
    private static final int SHUT_HOVER_U = 93;

    /**
     * How far past the lock's box the pose may carry the lock or its
     * shadow: what the flat picture they are drawn as reaches.
     */
    private static final int POSE_ROOM = 3;

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
    private boolean previouslyLocked;
    private boolean seen;
    private long lastSeenNanos;

    /**
     * The control's padlock at {@code (x, top)}. {@code top} is the top
     * of the {@link #WIDTH} by {@link #HEIGHT} box the turn is drawn in.
     */
    void draw(final int x, final int top, boolean locked, boolean hovered,
              final int alpha) {
        final Pose pose = advance(locked, hovered, System.nanoTime());
        final int frame = Math.round((FRAMES.length - 1) * this.swing);
        final int lit = Math.round(alpha * this.hoverFade);
        final int shut = Math.round(lit * this.swing);
        // One flat picture, the lock over its shadow, so the lock's
        // painted hollows show what lies behind it and never its own
        // shadow; its box reaches past the lock's for the pose's squash,
        // lean and trailing shadow.
        LostTalesUiFlatLayers.drawFlat(x - POSE_ROOM, top - POSE_ROOM,
                x + WIDTH + POSE_ROOM, top + HEIGHT + POSE_ROOM,
                new LostTalesUiFlatLayers.Layers() {
                    @Override
                    public void draw() {
                        drawLockShadow(pose, frame, x, top, alpha);
                        LostTalesUiFlatLayers.nextLayer();
                        drawLock(pose, frame, x, top, alpha, lit, shut);
                    }
                });
    }

    /**
     * The lock's shadow, trailing the pose rather than moving with it and
     * turning less than it does: it is cast on the strip, not carried by
     * the lock.
     */
    private static void drawLockShadow(Pose pose, int frame, int x, int top,
                                       int alpha) {
        int shadowAlpha = LostTalesUiInk.shadowAlpha(alpha);
        if (shadowAlpha <= 0) {
            return;
        }
        LostTalesSilhouetteRenderState.begin(LostTalesUiInk.SHADOW);
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

    /**
     * The resting colourway always, with the hovered one laid over it as
     * far as the pointer has brought it ({@code lit}): the lock crosses to
     * its lit tones rather than swapping to them, as every other control
     * drawn in two states does. Green under the open padlock,
     * wine under the shut one: the shut colourway is laid over the open
     * one as far as the shackle has come ({@code shut}), so the two cross
     * over with the swing. Each colourway's hollows give way to the next
     * one's, so no two stack, while its ink stays whole under the ink
     * laid over it.
     */
    private static void drawLock(Pose pose, int frame, int x, int top,
                                 int alpha, int lit, int shut) {
        begin(pose, x, top, false);
        try {
            if (lit < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
                drawFrame(frame, RESTING_U, x, top, alpha);
                return;
            }
            drawFrameSplit(frame, RESTING_U, x, top, alpha, alpha - lit);
            drawFrameSplit(frame, OPEN_HOVER_U, x, top, lit, lit - shut);
            drawFrame(frame, SHUT_HOVER_U, x, top, shut);
        } finally {
            end();
        }
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
        LostTalesUiSheet.draw(block + cell[0], cell[1], cell[2], cell[3], x,
                top + HEIGHT - cell[3], alpha);
    }

    /** A frame with its ink at {@code ink} and its hollows at {@code hollow}. */
    private static void drawFrameSplit(int frame, int block, float x,
                                       float top, int ink, int hollow) {
        int[] cell = FRAMES[Math.max(0, Math.min(FRAMES.length - 1, frame))];
        LostTalesUiSheet.drawSplit(block + cell[0], cell[1], cell[2], cell[3],
                x, top + HEIGHT - cell[3], ink, hollow);
    }

    /**
     * Reads the turn the lock's own state started, and returns the pose
     * for this instant: measured from when the turn began rather than
     * accumulated, so the motion is the same however often the screen is
     * drawn. At rest the pose is square, the pointer on the lock or not.
     */
    private Pose advance(boolean locked, boolean hovered, long nowNanos) {
        if (this.seen && nowNanos - this.lastSeenNanos > STALE_AFTER_NANOS) {
            // Away long enough that finishing would read as a glitch.
            this.turnStartedNanos = 0L;
            this.swing = locked ? 1.0F : 0.0F;
        }
        this.lastSeenNanos = nowNanos;
        if (!this.seen) {
            // First sight: the lock stands in the pose it is already in
            // rather than turning into it.
            this.seen = true;
            this.swing = locked ? 1.0F : 0.0F;
            this.previouslyLocked = locked;
        }
        if (locked != this.previouslyLocked) {
            this.previouslyLocked = locked;
            this.turnFrom = this.swing;
            this.turnTo = locked ? 1.0F : 0.0F;
            this.direction = locked ? 1.0F : -1.0F;
            this.turnStartedNanos = nowNanos;
        }
        double sinceDrawn = this.hoverFadeNanos == 0L ? 0.0D
                : (nowNanos - this.hoverFadeNanos) / 1.0E9D;
        this.hoverFadeNanos = nowNanos;
        this.hoverFade = WindowStyle.hoverFade(this.hoverFade,
                hovered, sinceDrawn);
        if (!Motions.flourishes()) {
            this.turnStartedNanos = 0L;
            this.swing = locked ? 1.0F : 0.0F;
            return pose(0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
        }
        if (this.turnStartedNanos != 0L) {
            long elapsed = Motions.paced(nowNanos - this.turnStartedNanos);
            if (elapsed < TURN_ANTICIPATE_NANOS + TURN_ACTION_NANOS
                    + TURN_SETTLE_NANOS) {
                return turnPose(elapsed);
            }
            this.turnStartedNanos = 0L;
            this.swing = this.turnTo;
        }
        return pose(0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    /** Wind-up, swing, then the overshoot ringing out. */
    private Pose turnPose(long elapsedNanos) {
        float lean = this.direction;
        if (elapsedNanos < TURN_ANTICIPATE_NANOS) {
            float progress = LostTalesGuiEasing.easeOutCubic(
                    (float)elapsedNanos / (float)TURN_ANTICIPATE_NANOS);
            this.swing = this.turnFrom;
            // Against the travel, and rising off the strip as it winds.
            return pose(-0.30F * lean * progress, -0.45F * progress,
                    -1.7F * lean * progress,
                    1.0F - 0.035F * progress, 1.0F + 0.055F * progress);
        }
        long afterWindUp = elapsedNanos - TURN_ANTICIPATE_NANOS;
        if (afterWindUp < TURN_ACTION_NANOS) {
            float progress = LostTalesGuiEasing.easeOutCubic(
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

    /** A pose, with the shadow's own trailing offset worked out from it. */
    private static Pose pose(float offsetX, float offsetY,
                             float rotationDegrees, float scaleX,
                             float scaleY) {
        return new Pose(offsetX, offsetY, rotationDegrees, scaleX, scaleY,
                LostTalesUiInk.SHADOW_OFFSET
                        - offsetX * SHADOW_LAG,
                LostTalesUiInk.SHADOW_OFFSET
                        - offsetY * SHADOW_LAG);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
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
