package com.ninuna.losttales.client.motion;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One piece of what a motion moves — a chat line's chevron, its words, a
 * button's glyph — with the poses it can rest in and the beats that take
 * it from one to another. A pose names a value for some tracks; a track
 * it leaves out keeps whatever value it had.
 */
public final class MotionPart {
    /** The pose every part rests in until something happens to it. */
    public static final String REST = "rest";
    /** How near a pose a part must stand to count as standing in it. */
    private static final float AT_POSE = 0.01F;

    private final String name;
    private final Map<String, float[]> poses;
    private final Map<String, MotionBeat> beats;

    MotionPart(String name, Map<String, float[]> poses,
               Map<String, MotionBeat> beats) {
        this.name = name;
        Map<String, float[]> keptPoses = new LinkedHashMap<String, float[]>();
        if (poses != null) {
            for (Map.Entry<String, float[]> pose : poses.entrySet()) {
                keptPoses.put(pose.getKey(), Arrays.copyOf(pose.getValue(),
                        MotionTrack.values().length));
            }
        }
        this.poses = Collections.unmodifiableMap(keptPoses);
        this.beats = beats == null ? Collections.<String, MotionBeat>emptyMap()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<String, MotionBeat>(beats));
    }

    /** A pose of nothing but {@code values}, the other tracks left untouched. */
    static float[] pose(Map<MotionTrack, Float> values) {
        float[] pose = new float[MotionTrack.values().length];
        Arrays.fill(pose, Float.NaN);
        if (values != null) {
            for (Map.Entry<MotionTrack, Float> value : values.entrySet()) {
                pose[value.getKey().ordinal()] = value.getKey()
                        .bounded(value.getValue().floatValue());
            }
        }
        return pose;
    }

    public String name() {
        return this.name;
    }

    /** The poses by name, each a value per track, NaN where it names none. */
    public Map<String, float[]> poses() {
        return this.poses;
    }

    public Map<String, MotionBeat> beats() {
        return this.beats;
    }

    public MotionBeat beat(String beat) {
        return this.beats.get(beat);
    }

    /** A pose's value for a track, or NaN where the pose names none or is not there. */
    public float poseValue(String pose, MotionTrack track) {
        float[] values = this.poses.get(pose);
        return values == null ? Float.NaN : values[track.ordinal()];
    }

    /** Where a track stands at rest: the rest pose's value, or the track's neutral. */
    float restValue(MotionTrack track) {
        float value = poseValue(REST, track);
        return Float.isNaN(value) ? track.neutral() : value;
    }

    /** The pose the values stand in, every track it names within a hair, or null. */
    String poseAt(float[] values) {
        for (Map.Entry<String, float[]> pose : this.poses.entrySet()) {
            float[] wanted = pose.getValue();
            boolean named = false;
            boolean matches = true;
            for (int track = 0; track < wanted.length && matches; track++) {
                if (!Float.isNaN(wanted[track])) {
                    named = true;
                    matches = Math.abs(values[track] - wanted[track]) < AT_POSE;
                }
            }
            if (named && matches) {
                return pose.getKey();
            }
        }
        return null;
    }
}
