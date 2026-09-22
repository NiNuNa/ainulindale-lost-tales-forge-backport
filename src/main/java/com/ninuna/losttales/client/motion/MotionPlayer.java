package com.ninuna.losttales.client.motion;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * One animated thing playing a motion: a button, a chat line under the
 * pointer. The code says which beat happens and when; the player answers
 * where each part stands at any instant.
 *
 * <p>Every value is read from the time since its beat began, so a motion
 * looks the same at every frame rate and allocates nothing per frame. A
 * beat that begins while another is running starts from wherever the
 * part stands at that instant, so nothing ever jumps. The motion is
 * looked up again at each beat, so what F3+T or the Motion Lab changes
 * shows from the next beat on.</p>
 *
 * <p>The settings reach every beat as it begins: with motion off every
 * part lands at once; {@code animationSpeed} scales every time; reduced
 * motion sets travel tracks down at their end at once, leaves out
 * bumps, rings and keys, and trades a curve that swings for a plain
 * one.</p>
 */
public final class MotionPlayer {
    private static final int TRACKS = MotionTrack.values().length;

    private final String motionId;
    private final Map<String, PartState> parts = new HashMap<String, PartState>();

    /** Where one part stands, and the beat taking it somewhere. */
    private static final class PartState {
        final float[] from = new float[TRACKS];
        final float[] to = new float[TRACKS];
        final float[] factor = new float[TRACKS];
        final MotionLayer[][] layers = new MotionLayer[TRACKS][];
        final boolean[] jump = new boolean[TRACKS];
        /** The running beat's name; null while the part rests. */
        String beat;
        String pose;
        MotionCurve curve = MotionCurve.EASE_OUT;
        long startNanos;
        long delayNanos;
        long durationNanos;
        long staggerNanos;

        PartState() {
            for (MotionTrack track : MotionTrack.values()) {
                this.from[track.ordinal()] = track.neutral();
                this.to[track.ordinal()] = track.neutral();
            }
        }
    }

    public MotionPlayer(String motionId) {
        this.motionId = motionId;
    }

    public String motionId() {
        return this.motionId;
    }

    /**
     * Sets every part down in {@code pose} at once, playing nothing: how
     * a thing is shown the first time, in the state it is already in.
     * A part without the pose keeps where it is.
     */
    public void settle(String pose) {
        Motion motion = Motions.get(this.motionId);
        for (MotionPart part : motion.parts().values()) {
            PartState state = state(part.name());
            float[] values = part.poses().get(pose);
            for (int track = 0; track < TRACKS; track++) {
                float value = values == null ? Float.NaN : values[track];
                if (!Float.isNaN(value)) {
                    state.to[track] = value;
                }
                state.from[track] = state.to[track];
            }
            state.beat = null;
            state.pose = values == null ? state.pose : pose;
        }
    }

    /** Plays {@code beat} on every part that has it, to the pose the beat names. */
    public void play(String beat, long nowNanos) {
        play(beat, null, nowNanos);
    }

    /**
     * Plays {@code beat} on every part that has it, to {@code pose}, or
     * to the pose the beat names when that is null. A part without the
     * beat carries on with what it was doing.
     */
    public void play(String beat, String pose, long nowNanos) {
        Motion motion = Motions.get(this.motionId);
        boolean enabled = Motions.enabled();
        boolean reduced = Motions.reduced();
        for (MotionPart part : motion.parts().values()) {
            MotionBeat found = part.beat(beat);
            if (found == null) {
                continue;
            }
            PartState state = state(part.name());
            float[] standing = new float[TRACKS];
            for (int track = 0; track < TRACKS; track++) {
                standing[track] = sample(state, track, 0, nowNanos);
            }
            String target = pose != null ? pose : found.to();
            float[] values = part.poses().get(target);
            String fromPose = part.poseAt(standing);
            state.beat = beat;
            state.pose = values == null ? state.pose : target;
            state.startNanos = nowNanos;
            // Reduced motion keeps no delays or staggers: what is left of
            // a beat is a short fade, and it starts at once.
            state.delayNanos = reduced ? 0L
                    : Motions.scaledNanos(found.delayMillis());
            state.durationNanos = Motions.beatNanos(found.durationMillis());
            state.staggerNanos = reduced ? 0L
                    : Motions.scaledNanos(found.staggerMillis());
            state.curve = reduced ? found.curve().reduced() : found.curve();
            for (MotionTrack track : MotionTrack.values()) {
                int index = track.ordinal();
                float from = standing[index];
                float to = values == null || Float.isNaN(values[index])
                        ? from : values[index];
                state.from[index] = from;
                state.to[index] = to;
                MotionLayer[] layers = found.layers(track, fromPose);
                state.layers[index] = reduced ? reducedLayers(layers) : layers;
                state.jump[index] = !enabled
                        || reduced && track.isTravel();
                // A flourish is whole on a beat that travels all the way
                // from rest to its pose, and as much smaller as the part
                // has less of that way left.
                float span = Math.abs(to - part.restValue(track));
                state.factor[index] = span <= 0.0F ? 1.0F
                        : Math.min(1.0F, Math.abs(to - from) / span);
            }
        }
    }

    /** Where a part's track stands at {@code nowNanos}. */
    public float value(String part, MotionTrack track, long nowNanos) {
        return value(part, track, 0, nowNanos);
    }

    /**
     * Where item {@code index} of a part that draws several stands: each
     * further item starts its beat the beat's stagger later. The index
     * may fall between items, so a long row can spread its stagger
     * across no more time than the motion allows it.
     */
    public float value(String part, MotionTrack track, float index,
                       long nowNanos) {
        PartState state = this.parts.get(part);
        if (state == null) {
            return track.neutral();
        }
        return sample(state, track.ordinal(), Math.max(0.0F, index),
                nowNanos);
    }

    /** Where a part's track is headed: the pose its beat goes to. */
    public float target(String part, MotionTrack track) {
        PartState state = this.parts.get(part);
        return state == null ? track.neutral() : state.to[track.ordinal()];
    }

    /** The pose a part rests in or is headed for, or null before any. */
    public String pose(String part) {
        PartState state = this.parts.get(part);
        return state == null ? null : state.pose;
    }

    /** The beat a part is playing, or null while it rests. */
    public String beat(String part) {
        PartState state = this.parts.get(part);
        return state == null ? null : state.beat;
    }

    /** Whether every part has arrived, the first item of each. */
    public boolean isSettled(long nowNanos) {
        return isSettled(nowNanos, 0);
    }

    /** Whether every part has arrived, up to its item {@code lastIndex}. */
    public boolean isSettled(long nowNanos, float lastIndex) {
        for (PartState state : this.parts.values()) {
            if (state.beat == null) {
                continue;
            }
            long elapsed = nowNanos - state.startNanos - state.delayNanos
                    - (long)(Math.max(0.0F, lastIndex) * state.staggerNanos);
            if (elapsed < state.durationNanos) {
                return false;
            }
        }
        return true;
    }

    private PartState state(String part) {
        PartState state = this.parts.get(part);
        if (state == null) {
            state = new PartState();
            this.parts.put(part, state);
        }
        return state;
    }

    private static float sample(PartState state, int track, float index,
                                long nowNanos) {
        if (state.beat == null) {
            return state.to[track];
        }
        float from = state.from[track];
        float to = state.to[track];
        long elapsed = nowNanos - state.startNanos - state.delayNanos
                - (long)(index * state.staggerNanos);
        if (state.jump[track] || state.durationNanos <= 0L
                || elapsed >= state.durationNanos) {
            return elapsed < 0L && !state.jump[track] ? from : to;
        }
        if (elapsed <= 0L) {
            return from;
        }
        float progress = elapsed / (float)state.durationNanos;
        MotionLayer[] layers = state.layers[track];
        float travel = Float.NaN;
        float offsets = 0.0F;
        if (layers != null) {
            for (MotionLayer layer : layers) {
                if (layer.kind() == MotionLayer.Kind.TRAVEL) {
                    travel = from + (to - from) * layer.travelShare(progress);
                } else {
                    offsets += layer.offset(progress);
                }
            }
        }
        if (Float.isNaN(travel)) {
            travel = from + (to - from) * state.curve.apply(progress);
        }
        return travel + offsets * state.factor[track];
    }

    /** What reduced motion keeps of a track's layers: the travel, on a plain curve. */
    private static MotionLayer[] reducedLayers(MotionLayer[] layers) {
        if (layers == null) {
            return null;
        }
        MotionLayer[] kept = new MotionLayer[layers.length];
        int count = 0;
        for (MotionLayer layer : layers) {
            if (layer.kind() == MotionLayer.Kind.TRAVEL) {
                kept[count++] = layer.withCurve(layer.curve().reduced());
            }
        }
        return Arrays.copyOf(kept, count);
    }
}
