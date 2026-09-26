package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * The Motion Lab's sample: the picked motion played over and over on
 * small blocks. A motion of parts plays each of its beats in turn with a
 * rest between them, a part that moves a row of words on a row of
 * blocks; a transition goes on and off with a rest at each end; a
 * follower chases a target that jumps from side to side. The sample is
 * drawn larger than it plays, so a pixel's travel reads.
 *
 * <p>The caller clips it to its box and hands it the time; it keeps no
 * clock of its own beyond the last frame, for a follower's step.</p>
 */
final class MotionLabSample {
    /** How much larger the sample is drawn than it plays. */
    private static final float ZOOM = 3.0F;
    /** How long the sample rests between two beats. */
    private static final long HOLD_NANOS = 600L * 1000000L;
    /** How long a follower's target stands before it jumps. */
    private static final long FOLLOW_FLIP_NANOS = 700L * 1000000L;
    /** The blocks a part that moves a row of words is sampled with. */
    private static final int SAMPLE_WORDS = 5;
    /** The farthest a transition's or a follower's block travels, in the sample's own pixels. */
    private static final float SPAN = 30.0F;
    /** The colours the parts are told apart by, in turn. */
    private static final int[] COLOURS = {LostTalesColors.IVORY,
            LostTalesColors.HONEY, LostTalesColors.SEAFOAM,
            LostTalesColors.ORCHID};

    private String id;
    private MotionPlayer player;
    private MotionTransition transition;
    private List<String> beats = new ArrayList<String>();
    private int beat;
    private long restUntil;
    private boolean on;
    private double followValue;
    private double followTarget = 1.0D;
    private long followFlipNanos;
    private long lastFrameNanos;

    /** Plays {@code motion} from its start. */
    void restart(Motion motion, long now) {
        this.id = motion.id();
        this.player = null;
        this.transition = null;
        this.beats = new ArrayList<String>();
        this.beat = 0;
        this.restUntil = 0L;
        this.followValue = 0.0D;
        this.followTarget = 1.0D;
        this.followFlipNanos = now;
        if (motion.isFollower()) {
            return;
        }
        if (MotionCodec.isTransition(motion)) {
            this.transition = new MotionTransition(motion.id());
            this.transition.settle(false);
            this.on = true;
            return;
        }
        for (MotionPart part : motion.parts().values()) {
            for (String name : part.beats().keySet()) {
                if (!this.beats.contains(name)) {
                    this.beats.add(name);
                }
            }
        }
        this.player = new MotionPlayer(motion.id());
        this.player.settle(Motion.REST);
        playBeat(motion, now);
    }

    /** The motion the sample plays; null before one is. */
    String id() {
        return this.id;
    }

    private void playBeat(Motion motion, long now) {
        if (this.beats.isEmpty()) {
            return;
        }
        String name = this.beats.get(this.beat % this.beats.size());
        MotionBeat found = motion.beat(name);
        // A beat the code sends somewhere of its own goes back to rest
        // in the sample.
        boolean named = found != null && found.to().length() > 0;
        this.player.play(name, named ? null : Motion.REST, now);
    }

    /**
     * Draws the sample centred in the box at {@code left}, {@code top},
     * {@code width} by {@code height}, at {@code alpha}; the caller has
     * clipped to the box.
     */
    void draw(Motion motion, float left, float top, float width,
              float height, int alpha, long now) {
        double elapsed = this.lastFrameNanos == 0L ? 0.0D
                : Math.max(0.0D, (now - this.lastFrameNanos) / 1.0E9D);
        this.lastFrameNanos = now;
        if (this.id == null || !this.id.equals(motion.id())) {
            restart(motion, now);
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(left + width / 2.0F, top + height / 2.0F, 0.0F);
            GL11.glScalef(ZOOM, ZOOM, 1.0F);
            float reach = Math.max(8.0F, (width / 2.0F - 8.0F) / ZOOM);
            float span = Math.min(reach, SPAN);
            if (motion.isFollower()) {
                if (now - this.followFlipNanos >= FOLLOW_FLIP_NANOS) {
                    this.followTarget = 1.0D - this.followTarget;
                    this.followFlipNanos = now;
                }
                this.followValue = Motions.follow(motion.id(),
                        this.followValue, this.followTarget, elapsed);
                drawBlock(-span + 2.0F * span * (float)this.followTarget, 0.0F,
                        LostTalesColors.PLUM_GRAY, 1.0F, 0.0F, 0.0F, 1.0F,
                        1.0F, alpha);
                drawBlock(-span + 2.0F * span * (float)this.followValue, 0.0F,
                        LostTalesColors.IVORY, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F,
                        alpha);
            } else if (this.transition != null) {
                float value = this.transition.advance(now, this.on);
                if (this.transition.isSettled()) {
                    if (this.restUntil == 0L) {
                        this.restUntil = now + HOLD_NANOS;
                    } else if (now >= this.restUntil) {
                        this.on = !this.on;
                        this.restUntil = 0L;
                    }
                }
                LostTalesUiInk.fillRect(-span, 0.0F, span, 1.0F,
                        faded(LostTalesColors.BORDER_DIM, alpha));
                drawBlock(-span + 2.0F * span * value, -2.0F,
                        LostTalesColors.IVORY, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F,
                        alpha);
            } else if (this.player != null) {
                step(motion, now);
                drawParts(motion, now, reach, alpha);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Moves the sample on to its next beat once this one has rested. */
    private void step(Motion motion, long now) {
        if (!this.player.isSettled(now, SAMPLE_WORDS - 1)) {
            return;
        }
        if (this.restUntil == 0L) {
            this.restUntil = now + HOLD_NANOS;
        } else if (now >= this.restUntil) {
            this.restUntil = 0L;
            this.beat++;
            playBeat(motion, now);
        }
    }

    private void drawParts(Motion motion, long now, float reach, int alpha) {
        int count = motion.parts().size();
        int index = 0;
        for (MotionPart part : motion.parts().values()) {
            float rowY = (index - (count - 1) / 2.0F) * 10.0F;
            int colour = COLOURS[index % COLOURS.length];
            boolean words = movesWords(part);
            int items = words ? SAMPLE_WORDS : 1;
            float startX = words ? -reach / 2.0F : 0.0F;
            String name = part.name();
            for (int item = 0; item < items; item++) {
                float place = item;
                float x = this.player.value(name, MotionTrack.X, place, now);
                if (words && items > 1) {
                    x += Math.round(this.player.value(name, MotionTrack.GAP,
                            place, now) * item / (float)(items - 1));
                }
                drawBlock(startX + item * 8.0F + x, rowY
                                + this.player.value(name, MotionTrack.Y, place,
                                        now),
                        colour,
                        this.player.value(name, MotionTrack.FADE, place, now),
                        this.player.value(name, MotionTrack.BRIGHTEN, place,
                                now),
                        this.player.value(name, MotionTrack.TURN, place, now),
                        this.player.value(name, MotionTrack.STRETCH_X, place,
                                now),
                        this.player.value(name, MotionTrack.STRETCH_Y, place,
                                now), alpha);
            }
            index++;
        }
    }

    /** Whether a part moves a row of words: it staggers or stretches. */
    private static boolean movesWords(MotionPart part) {
        for (MotionBeat beat : part.beats().values()) {
            if (beat.staggerMillis() > 0
                    || beat.tracks().containsKey(MotionTrack.GAP)) {
                return true;
            }
        }
        return false;
    }

    /** A colour of the palette at its own opacity times the page's. */
    private static int faded(int argb, int alpha) {
        int own = argb >>> 24;
        return LostTalesUiInk.argb(LostTalesColors.rgb(argb),
                Math.round(own * Math.max(0, Math.min(255, alpha)) / 255.0F));
    }

    /** A six-pixel block, moved, turned, stretched, faded and lit as a part is. */
    private static void drawBlock(float x, float y, int colour, float fade,
                                  float brighten, float turn, float stretchX,
                                  float stretchY, int alpha) {
        int shown = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, fade))
                * Math.max(0, Math.min(255, alpha)) / 255.0F);
        if (shown < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int rgb = LostTalesUiInk.blend(LostTalesColors.rgb(colour),
                LostTalesColors.rgb(LostTalesColors.IVORY),
                Math.max(0.0F, Math.min(1.0F, brighten)));
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0.0F);
            GL11.glRotatef(turn, 0.0F, 0.0F, 1.0F);
            GL11.glScalef(stretchX, stretchY, 1.0F);
            LostTalesUiInk.fillRect(-3.0F, -3.0F, 3.0F, 3.0F,
                    LostTalesUiInk.argb(rgb, shown));
        } finally {
            GL11.glPopMatrix();
        }
    }
}
