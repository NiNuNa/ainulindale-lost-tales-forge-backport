package com.ninuna.losttales.client.motion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One named animation, such as {@code chat.line.hover} or
 * {@code ui.button.lift}: the parts it moves, their poses and beats, and
 * any numbers the drawing code reads from it by name ({@link #param}).
 * The code decides what moves and where it should end up; the motion
 * decides how it gets there.
 *
 * <p>Three shapes of motion share this one form. A <em>transition</em>
 * is a value going from off to on and back: one part, {@link #MAIN}, with
 * an {@link #ON} and an {@link #OFF} beat. A <em>follower</em> chases a
 * value that keeps changing, such as a scroll offset, covering most of
 * the way in {@link #followSeconds()}. Everything else is written out in
 * parts, poses and beats.</p>
 */
public final class Motion {
    /** The one part of a transition. */
    public static final String MAIN = "main";
    /** A transition's way on, and the beat the pointer arriving plays. */
    public static final String ON = "on";
    /** A transition's way off, and the beat the pointer leaving plays. */
    public static final String OFF = "off";
    /** Where every part rests. */
    public static final String REST = MotionPart.REST;

    private final String id;
    private final String about;
    private final Map<String, MotionPart> parts;
    private final Map<String, Float> params;
    private final float followSeconds;

    Motion(String id, String about, Map<String, MotionPart> parts,
           Map<String, Float> params, float followSeconds) {
        this.id = id;
        this.about = about == null ? "" : about;
        this.parts = parts == null ? Collections.<String, MotionPart>emptyMap()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<String, MotionPart>(parts));
        this.params = params == null ? Collections.<String, Float>emptyMap()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<String, Float>(params));
        this.followSeconds = followSeconds;
    }

    /** A motion that moves nothing: every beat lands at once. */
    static Motion instant(String id) {
        return new Motion(id, "", null, null, 0.0F);
    }

    public String id() {
        return this.id;
    }

    /** What the motion is for, in a sentence, as the Motion Lab shows it. */
    public String about() {
        return this.about;
    }

    public Map<String, MotionPart> parts() {
        return this.parts;
    }

    public MotionPart part(String name) {
        return this.parts.get(name);
    }

    public Map<String, Float> params() {
        return this.params;
    }

    /** A number the drawing code reads by name, or NaN when the motion has none. */
    public float param(String name) {
        Float value = this.params.get(name);
        return value == null ? Float.NaN : value.floatValue();
    }

    /** Whether this motion chases a value rather than playing beats. */
    public boolean isFollower() {
        return !Float.isNaN(this.followSeconds);
    }

    /** How long a follower takes to cover most of its way, in seconds. */
    public float followSeconds() {
        return Float.isNaN(this.followSeconds) ? 0.0F : this.followSeconds;
    }

    /**
     * A beat of the motion's leading part: {@link #MAIN}, or the first
     * part there is. What a transition and a duration read.
     */
    public MotionBeat beat(String name) {
        MotionPart part = this.parts.get(MAIN);
        if (part == null && !this.parts.isEmpty()) {
            part = this.parts.values().iterator().next();
        }
        return part == null ? null : part.beat(name);
    }
}
