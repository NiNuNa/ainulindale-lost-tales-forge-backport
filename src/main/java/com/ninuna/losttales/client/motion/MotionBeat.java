package com.ninuna.losttales.client.motion;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing that can happen to a part — appearing, the pointer arriving
 * or leaving, a press, a release, a move — and how the part answers it:
 * which pose it goes to, how long that takes, when it starts, and how
 * each track gets there. A beat that begins while another is running
 * starts from wherever the part stands, so nothing ever jumps.
 *
 * <p>{@link #stagger()} delays each further item of a part that draws
 * several — the words of a line — by that much more, which is how a row
 * moves as overlapping action. A beat may also answer differently
 * depending on the pose it starts from ({@link #layers(MotionTrack,
 * String)}): a button's rise has its dip of anticipation only when it
 * rises from rest.</p>
 */
public final class MotionBeat {
    /** The longest a beat, its delay or a stagger may be, in milliseconds. */
    public static final int MAX_MILLIS = 5000;
    /** The most layers one track may hold. */
    public static final int MAX_LAYERS = 8;

    private final String name;
    private final String to;
    private final int durationMillis;
    private final int delayMillis;
    private final int staggerMillis;
    private final MotionCurve curve;
    private final Map<MotionTrack, MotionLayer[]> tracks;
    private final Map<String, Map<MotionTrack, MotionLayer[]>> variants;

    MotionBeat(String name, String to, int durationMillis, int delayMillis,
               int staggerMillis, MotionCurve curve,
               Map<MotionTrack, MotionLayer[]> tracks,
               Map<String, Map<MotionTrack, MotionLayer[]>> variants) {
        this.name = name;
        this.to = to == null ? "" : to;
        this.durationMillis = bounded(durationMillis);
        this.delayMillis = bounded(delayMillis);
        this.staggerMillis = bounded(staggerMillis);
        this.curve = curve == null ? MotionCurve.EASE_OUT : curve;
        this.tracks = tracks == null
                ? Collections.<MotionTrack, MotionLayer[]>emptyMap()
                : Collections.unmodifiableMap(
                        new EnumMap<MotionTrack, MotionLayer[]>(tracks));
        Map<String, Map<MotionTrack, MotionLayer[]>> kept =
                new LinkedHashMap<String, Map<MotionTrack, MotionLayer[]>>();
        if (variants != null) {
            for (Map.Entry<String, Map<MotionTrack, MotionLayer[]>> variant
                    : variants.entrySet()) {
                kept.put(variant.getKey(), Collections.unmodifiableMap(
                        new EnumMap<MotionTrack, MotionLayer[]>(
                                variant.getValue())));
            }
        }
        this.variants = Collections.unmodifiableMap(kept);
    }

    /** A beat of one curve and no layers of its own: a plain travel to {@code to}. */
    static MotionBeat plain(String name, String to, int durationMillis,
                            MotionCurve curve) {
        return new MotionBeat(name, to, durationMillis, 0, 0, curve, null,
                null);
    }

    public String name() {
        return this.name;
    }

    /** The pose the beat takes its part to; empty when the code names it. */
    public String to() {
        return this.to;
    }

    public int durationMillis() {
        return this.durationMillis;
    }

    public int delayMillis() {
        return this.delayMillis;
    }

    public int staggerMillis() {
        return this.staggerMillis;
    }

    /** The curve a track with no travel of its own travels on. */
    public MotionCurve curve() {
        return this.curve;
    }

    /** The layers the file gives the tracks, the variants' left out. */
    public Map<MotionTrack, MotionLayer[]> tracks() {
        return this.tracks;
    }

    /** The layers of each variant, by the pose it answers. */
    public Map<String, Map<MotionTrack, MotionLayer[]>> variants() {
        return this.variants;
    }

    /**
     * The layers a track runs when the beat starts from {@code fromPose}
     * (null when the part stands at no pose): the variant's for that
     * pose where it names the track, else the beat's own. Null means a
     * plain travel on the beat's curve.
     */
    MotionLayer[] layers(MotionTrack track, String fromPose) {
        if (fromPose != null) {
            Map<MotionTrack, MotionLayer[]> variant = this.variants.get(fromPose);
            if (variant != null && variant.containsKey(track)) {
                return variant.get(track);
            }
        }
        return this.tracks.get(track);
    }

    private static int bounded(int millis) {
        return Math.max(0, Math.min(MAX_MILLIS, millis));
    }
}
