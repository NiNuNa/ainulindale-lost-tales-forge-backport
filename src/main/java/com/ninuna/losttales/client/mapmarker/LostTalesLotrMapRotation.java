package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.core.LostTalesClassTransformer;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * The map's rotation, and the only place it is applied.
 *
 * <p>Rotation turns the coordinate system, not the artwork. LOTR converts every
 * position it draws — roads, waypoints, region labels, players, quest markers —
 * through one private method, and this rotates that method's answer, so all of
 * it lands on the turned map while the sprites and text it draws there stay
 * upright. The only thing that is really rotated is the map image itself, which
 * is one quad and is drawn by
 * {@link LostTalesLotrSmoothMapRenderer}.</p>
 *
 * <p>The same angle drives the inverse, which is what a click on the map is
 * resolved with, so what the player points at and what is drawn under the
 * pointer cannot drift apart. Nothing else in the mod is allowed to
 * compensate for rotation on its own.</p>
 */
public final class LostTalesLotrMapRotation {
    /**
     * Furthest the map may be tipped out of square, in degrees, on either
     * axis.
     *
     * <p>The one number the map's orientation is bounded by. Turning and
     * leaning are one gesture — the same drag, through the same stiffening
     * curve — so they are given the same limit and read as one movement
     * rather than as two axes with different ceilings. Everything that
     * constrains an angle derives from this rather than repeating it.</p>
     */
    public static final float MAX_ORIENTATION_DEGREES = 33.75F;
    /** Furthest the map may be turned in either direction, in degrees. */
    public static final float MAX_DEGREES = MAX_ORIENTATION_DEGREES;
    /** The ordinary camera range ends at one accumulated drag. */
    static final float NORMAL_INPUT_LIMIT = 1.0F;
    /** Extra input accepted only for the temporary elastic overshoot. */
    static final float MAX_INPUT = 1.36F;
    /** Rate at which additional drag buys less rotation. */
    static final float RESISTANCE_RATE = 5.0F;
    /** Non-zero gradient that carries motion smoothly through the soft cap. */
    static final float RESISTANCE_GRADIENT_FLOOR = 0.08F;
    private static final float RESISTANCE_NORMALIZER =
            (1.0F - (float)Math.exp(-RESISTANCE_RATE))
                    / RESISTANCE_RATE + RESISTANCE_GRADIENT_FLOOR;
    /** Strict visual bounds while the player pulls beyond the normal limit. */
    static final float MAX_VISUAL_LEAN = resistanceShare(MAX_INPUT);
    static final float MAX_VISUAL_DEGREES =
            MAX_DEGREES * MAX_VISUAL_LEAN;
    /** Time constant for an untouched overshoot to return to its normal limit. */
    static final float OVERSHOOT_RETURN_SECONDS = 0.12F;
    private static final float OVERSHOOT_RETURN_EPSILON = 0.0005F;
    /** Horizontal drag, in GUI pixels, that reaches the normal limit. */
    static final float FULL_TURN_DRAG_PIXELS = 520.0F;
    // The normal and elastic ranges deliberately share one curve. A separate
    // overshoot curve creates a zero-slope pause where the two meet.
    /**
     * How near square the map has to be drawn before it is drawn square.
     *
     * <p>Small enough that it is a place the map settles rather than a place
     * it is held: within it the angle eases to nothing, and anything past it
     * is left exactly as the drag asked for.</p>
     */
    static final float MAGNET_DEGREES = 2.0F;
    /** Inside this, the map is simply north-up. */
    static final float SETTLE_DEGREES = 0.35F;
    /**
     * Horizontal drag that has to accumulate before the map starts turning, so
     * a right click with an unsteady hand is still a right click.
     */
    static final float DRAG_THRESHOLD_PIXELS = 3.0F;
    /**
     * How quickly the drawn angle catches up with the one being dragged, as a
     * time constant in seconds. Mouse movement arrives in steps of whatever
     * the pointer did between two events; following it exactly is what made
     * the map look like it was shivering rather than turning.
     */
    static final float SMOOTHING_SECONDS = 0.045F;
    /** Below this, in degrees, the drawn angle simply becomes the target. */
    static final float SMOOTHING_EPSILON = 0.01F;
    /**
     * How far the eye may drop below straight down, in degrees.
     *
     * <p>The lean's half of {@link #MAX_ORIENTATION_DEGREES}. It was once
     * further out than the turn on the grounds that a low eye is just a low
     * eye, but a map that can be tipped a third further than it can be turned
     * reads as two separate controls; sharing the limit is what makes the
     * gesture one movement.</p>
     */
    static final float MAX_PITCH_DEGREES = MAX_ORIENTATION_DEGREES;
    /** Smallest safe orthographic scale when inverting the map pitch. */
    private static final float MIN_ORTHOGRAPHIC_SCALE = 0.05F;
    /**
     * Vertical drag, in GUI pixels, from flat to fully leaned. The same as a
     * full turn takes, and through the same curve, so leaning and turning
     * cost the player the same movement and feel like one gesture.
     */
    static final float FULL_LEAN_DRAG_PIXELS = FULL_TURN_DRAG_PIXELS;
    private static Field mapXMinField;
    private static Field mapYMinField;
    private static Field mapWidthField;
    private static Field mapHeightField;
    private static Field posXField;
    private static Field posYField;
    private static Field zoomScaleField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;
    /** Nesting depth of passes drawn as part of the sheet. */
    private static int unrotatedDepth;
    /** Matrices those passes have pushed and still owe the stack. */
    private static int pushedMatrices;
    /** Attribute sets they owe it, counted the same way and for the same reason. */
    private static int pushedAttributes;
    private static final FloatBuffer leanMatrix =
            BufferUtils.createFloatBuffer(16);

    private LostTalesLotrMapRotation() {}

    /** Whether the transformer that turns LOTR's own map space is in place. */
    public static boolean isSupported() {
        return Boolean.getBoolean(LostTalesClassTransformer
                .LOTR_MAP_ROTATION_ACTIVE_PROPERTY);
    }

    static float clampDegrees(float degrees) {
        if (Float.isNaN(degrees)) {
            return 0.0F;
        }
        return Math.max(-MAX_DEGREES, Math.min(MAX_DEGREES, degrees));
    }

    /**
     * How far a lean drag has earned the map, through the same stiffening
     * curve as the turn.
     *
     * <p>No detent, and none wanted: flat is one end of the lean's range
     * rather than the middle of it, so a detent there would only mean the
     * first part of every tilt did nothing.</p>
     */
    static float leanForInput(float input) {
        float clamped = Math.max(0.0F, Math.min(MAX_INPUT, input));
        return resist(clamped);
    }

    /**
     * The stiffening curve, shared by the turn and the lean.
     *
     * <p>Normalized to one at the normal limit but not clamped there. The
     * exponential continues into the overshoot with the same non-zero slope,
     * while every additional pixel buys less movement than the last.</p>
     */
    static float resist(float advanced) {
        float clamped = Math.max(0.0F, Math.min(MAX_INPUT, advanced));
        return resistanceShare(clamped);
    }

    private static float resistanceShare(float input) {
        return ((1.0F - (float)Math.exp(-RESISTANCE_RATE * input))
                        / RESISTANCE_RATE
                + RESISTANCE_GRADIENT_FLOOR * input)
                / RESISTANCE_NORMALIZER;
    }

    /**
     * Adds a drag to an axis, with the resistance depending on which way it
     * is going.
     *
     * <p>Moving away from square accumulates drag unchanged and
     * {@link #degreesForInput(float)} maps it through one exponential curve.
     * The normal limit is only a point on that curve, not a handoff.</p>
     *
     * <p>Coming back is not a second helping of that. The stiffening is in
     * the shape of that curve, which means that near the limit an inch of
     * drag is worth a fraction of a degree — outward, where that is the
     * point, and inward, where it only reads as the map refusing to
     * straighten. So an inward drag is divided by the same steepness the
     * curve has where the map currently is, which cancels it exactly: the map
     * comes back at one steady rate, the rate it left square at, however far
     * out it was. The player still feels the wall going out and never feels
     * it coming back.</p>
     *
     * @param current accumulated drag, bounded by {@link #MAX_INPUT}
     * @param delta   this frame's drag, in the same units
     */
    static float advanceInput(float current, float delta) {
        float clamped = clampInput(current);
        if (delta == 0.0F || Float.isNaN(delta)) {
            return clamped;
        }
        boolean outward = clamped == 0.0F
                || (delta > 0.0F) == (clamped > 0.0F);
        if (outward) {
            return clampInput(clamped + delta);
        }
        float magnitude = Math.abs(clamped);
        if (magnitude > NORMAL_INPUT_LIMIT) {
            // The elastic part gives drag back at the same rate it accepted
            // it. Any remainder continues through the ordinary release curve.
            float distanceToLimit = magnitude - NORMAL_INPUT_LIMIT;
            float inward = Math.abs(delta);
            if (inward <= distanceToLimit) {
                return clampInput(clamped + delta);
            }
            float direction = clamped < 0.0F ? -1.0F : 1.0F;
            float remainder = inward - distanceToLimit;
            return advanceInput(direction * NORMAL_INPUT_LIMIT,
                    -direction * remainder);
        }
        float released = delta / releaseSteepness(Math.abs(clamped));
        float advanced = clamped + released;
        if ((clamped > 0.0F) != (advanced > 0.0F) && advanced != 0.0F) {
            // Crossing square in one frame: the far side of the crossing is
            // outward travel and must not keep the release's easier rate.
            float toSquare = -clamped;
            float spent = toSquare * releaseSteepness(Math.abs(clamped));
            return clampInput(delta - spent);
        }
        return clampInput(advanced);
    }

    /**
     * How steeply {@link #degreesForInput(float)} is rising at a given amount
     * of accumulated drag, relative to its rate at square.
     *
     * <p>An exponential's relative gradient is another exponential. Dividing
     * by it cancels the outward resistance when the player deliberately
     * drags back towards square.</p>
     */
    private static float releaseSteepness(float advanced) {
        float clamped = Math.max(0.0F,
                Math.min(NORMAL_INPUT_LIMIT, advanced));
        return ((float)Math.exp(-RESISTANCE_RATE * clamped)
                + RESISTANCE_GRADIENT_FLOOR)
                / (1.0F + RESISTANCE_GRADIENT_FLOOR);
    }

    /**
     * Settles an angle that is nearly square onto square.
     *
     * <p>A magnet rather than a detent. The angle it gives back rises the
     * whole way through the zone, so any movement the player makes moves the
     * map and leaving is as immediate as arriving; what the zone does is bend
     * that rise so the last fraction of a degree costs more than the first,
     * and give up entirely once the map is within
     * {@link #SETTLE_DEGREES} of north.</p>
     *
     * <p>Continuous at both ends: it hands back exactly
     * {@link #MAGNET_DEGREES} at the top of the zone and exactly zero at the
     * bottom, so nothing jumps as the map crosses either.</p>
     */
    static float magnetiseDegrees(float degrees) {
        float magnitude = Math.abs(degrees);
        if (magnitude <= SETTLE_DEGREES) {
            return 0.0F;
        }
        if (magnitude >= MAGNET_DEGREES) {
            return degrees;
        }
        float advanced = (magnitude - SETTLE_DEGREES)
                / (MAGNET_DEGREES - SETTLE_DEGREES);
        return (degrees < 0.0F ? -1.0F : 1.0F)
                * MAGNET_DEGREES * advanced * advanced;
    }

    static float leanInputPerPixel() {
        return 1.0F / FULL_LEAN_DRAG_PIXELS;
    }

    /** Accumulated drag, as a share of the travel a full turn takes. */
    static float clampInput(float input) {
        if (Float.isNaN(input)) {
            return 0.0F;
        }
        return Math.max(-MAX_INPUT, Math.min(MAX_INPUT, input));
    }

    /** Removes the temporary part of an input without changing its direction. */
    static float releasedInput(float input) {
        float clamped = clampInput(input);
        if (Math.abs(clamped) <= NORMAL_INPUT_LIMIT) {
            return clamped;
        }
        return clamped < 0.0F
                ? -NORMAL_INPUT_LIMIT : NORMAL_INPUT_LIMIT;
    }

    /**
     * Eases an untouched overshoot back to the normal camera range.
     * Elapsed time makes the return independent of render frame rate.
     */
    static float releaseOvershootInput(float input, float elapsedSeconds) {
        float clamped = clampInput(input);
        float limit = releasedInput(clamped);
        if (clamped == limit || !(elapsedSeconds > 0.0F)) {
            return clamped;
        }
        float factor = 1.0F - (float)Math.exp(
                -elapsedSeconds / OVERSHOOT_RETURN_SECONDS);
        float released = clamped + (limit - clamped)
                * Math.max(0.0F, Math.min(1.0F, factor));
        return Math.abs(released - limit) <= OVERSHOOT_RETURN_EPSILON
                ? limit : released;
    }

    static float inputPerPixel() {
        return 1.0F / FULL_TURN_DRAG_PIXELS;
    }

    /**
     * The angle a given amount of drag has earned.
     *
     * <p>Two things shape it. Resistance that grows with the angle, so the
     * map comes off square easily and the last few degrees have to be worked
     * for. And a magnet at square, small enough that it is somewhere the map
     * settles rather than somewhere it is caught: the first pixel of any drag
     * turns the map, and the map turns back to exactly north on its own only
     * once it is already all but there.</p>
     */
    static float degreesForInput(float input) {
        float clamped = clampInput(input);
        float direction = clamped < 0.0F ? -1.0F : 1.0F;
        float magnitude = Math.abs(clamped);
        return magnetiseDegrees(
                direction * MAX_DEGREES * resist(magnitude));
    }

    /**
     * Moves the drawn angle towards the one being dragged.
     *
     * <p>Exponential, off elapsed time rather than off frames, so the map
     * turns at the same rate whatever the frame rate is doing.</p>
     */
    static float approachDegrees(
            float current, float target, float elapsedSeconds) {
        if (Math.abs(target - current) <= SMOOTHING_EPSILON
                || !(elapsedSeconds > 0.0F)) {
            return Math.abs(target - current) <= SMOOTHING_EPSILON
                    ? target : current;
        }
        float factor = 1.0F - (float)Math.exp(
                -elapsedSeconds / SMOOTHING_SECONDS);
        return current + (target - current)
                * Math.max(0.0F, Math.min(1.0F, factor));
    }

    /**
     * How far the map is turned for this screen.
     *
     * <p>Only the Lost Tales map screen can be turned. LOTR's menu background
     * and any other map this code is reached from stay square.</p>
     */
    static float degreesOf(LOTRGuiMap gui) {
        return gui instanceof LostTalesLotrMapGui
                ? ((LostTalesLotrMapGui)gui).getMapRotationDegrees()
                : 0.0F;
    }

    /**
     * Turns a point LOTR has just converted into screen space.
     *
     * <p>Called from the tail of {@code LOTRGuiMap.transformMapCoords}. It must
     * survive anything: a failure here would misplace every icon on the map,
     * so an unusable point is returned exactly as it arrived.</p>
     */
    /**
     * Draws one of LOTR's passes as part of the sheet rather than on top of
     * it.
     *
     * <p>The region names written across the map — Bree-land, the South Downs
     * — are painted on the paper, so they turn and lean with it, letters and
     * all, instead of pivoting to face the reader as a marker's label does.
     * That is the whole difference between the two: a marker is a pin stuck
     * in the map and always readable; a region name is ink on it.</p>
     *
     * <p>So the pass is drawn under the same matrix as the map image, and the
     * coordinate transform stands down for its duration — the matrix is
     * already moving those positions, and doing both would turn them
     * twice.</p>
     *
     * <p>A count rather than a flag, so nested passes cannot end it early,
     * and the map screen clears it once a frame so a pass that throws part
     * way through cannot leave the map stuck.</p>
     */
    public static void beginSheetPass(LOTRGuiMap gui) {
        unrotatedDepth++;
        if (pushDepthNeutral()) {
            pushedAttributes++;
        }
        if (pushSheetTransform(gui)) {
            pushedMatrices++;
        }
    }

    public static void endSheetPass() {
        if (unrotatedDepth <= 0) {
            return;
        }
        unrotatedDepth--;
        popSheetMatrix();
        popSheetAttributes();
    }

    /**
     * Takes the pass out of the depth buffer for as long as it runs.
     *
     * <p>The map is drawn back to front by the order the calls are made in,
     * and it shares a depth buffer with everything else on the screen, so a
     * depth test during it can only throw away layers the order has already
     * placed correctly. That is what was dropping the region names in and out
     * as the map moved. The same treatment the map image and the marker
     * passes already get.</p>
     *
     * @return true when the attributes were pushed and must be popped
     */
    private static boolean pushDepthNeutral() {
        try {
            GL11.glPushAttrib(
                    GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Puts the matrix stack back where it was found, at the start of a frame.
     *
     * <p>These passes bracket code inside the base mod. If one of them ever
     * fails to come back — an exception on the way through, a version that
     * returns by a path the transformer did not reach — a matrix is left on
     * the stack, and a handful of frames later the stack is full and every
     * push after it fails: the map flickers, panels vanish and nothing on
     * screen can be trusted. Forgetting the leak is what makes it permanent,
     * so it is unwound instead.</p>
     */
    static void clearSheetPasses() {
        unrotatedDepth = 0;
        while (pushedMatrices > 0) {
            popSheetMatrix();
        }
        while (pushedAttributes > 0) {
            popSheetAttributes();
        }
    }

    private static void popSheetMatrix() {
        if (pushedMatrices <= 0) {
            return;
        }
        pushedMatrices--;
        try {
            GL11.glPopMatrix();
        } catch (Throwable ignored) {
            // Nothing useful is left to do about a matrix stack this broken.
        }
    }

    private static void popSheetAttributes() {
        if (pushedAttributes <= 0) {
            return;
        }
        pushedAttributes--;
        try {
            GL11.glPopAttrib();
        } catch (Throwable ignored) {
            // As with the matrix stack: leaving it counted is what would
            // make a single failure permanent.
        }
    }

    /**
     * Pushes the matrix that turns and leans the sheet the map is drawn on.
     *
     * <p>The map image and everything painted on it go through this one
     * matrix, so they cannot disagree about where the paper is. It matches
     * the coordinate transform exactly: the turn about the middle of the
     * viewport, and then the lean, applied last so it acts on the screen.</p>
     *
     * @return true when a matrix was pushed and must be popped
     */
    public static boolean pushSheetTransform(LOTRGuiMap gui) {
        float degrees = degreesOf(gui);
        float lean = leanOf(gui);
        if ((degrees == 0.0F && lean <= 0.0F) || !ensureReflection()) {
            return false;
        }
        try {
            float centerX = centerX();
            float centerY = centerY();
            GL11.glPushMatrix();
            GL11.glTranslatef(centerX, centerY, 0.0F);
            multiplyLean(lean);
            GL11.glRotatef(degrees, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-centerX, -centerY, 0.0F);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Multiplies in the lean.
     *
     * <p>This is an orthographic pitch around the screen's horizontal axis.
     * Parallel features therefore remain parallel instead of leaning towards
     * a central vanishing point. The matrix carries both ground
     * foreshortening and elevation: terrain height moves upward on screen and
     * into depth, so the same lean that lays down the printed map exposes the
     * sides of close terrain.</p>
     */
    private static void multiplyLean(float lean) {
        if (!(lean > 0.0F)) {
            return;
        }
        float radians = (float)Math.toRadians(pitchDegrees(lean));
        float cosine = (float)Math.cos(radians);
        float sine = (float)Math.sin(radians);
        FloatBuffer matrix = leanMatrix;
        matrix.clear();
        for (int index = 0; index < 16; index++) {
            matrix.put(index % 5 == 0 ? 1.0F : 0.0F);
        }
        // Column-major rotation in the GUI's y/z plane. Positive terrain z
        // is height, so it moves toward the top of the screen as the eye
        // drops while ground farther down the map moves deeper.
        matrix.put(5, cosine);
        matrix.put(6, sine);
        matrix.put(9, -sine);
        matrix.put(10, cosine);
        matrix.flip();
        GL11.glMultMatrix(matrix);
    }

    public static float[] rotate(float[] point, LOTRGuiMap gui) {
        if (point == null || point.length < 2 || unrotatedDepth > 0) {
            return point;
        }
        try {
            float degrees = degreesOf(gui);
            float lean = leanOf(gui);
            if ((degrees == 0.0F && lean <= 0.0F) || !ensureReflection()) {
                return point;
            }
            float centerX = centerX();
            float centerY = centerY();
            rotateAbout(point, centerX, centerY, degrees);
            // The lean is applied last, so it acts on the screen the player
            // is looking at rather than on the map's own axes.
            applyLean(point, centerX, centerY, leanScaleY(lean));
            return point;
        } catch (Throwable ignored) {
            return point;
        }
    }

    /**
     * Projects a point on the sheet through the orthographic map camera.
     *
     * <p>The returned scale remains one: distance does not resize objects in
     * an orthographic view. This keeps trees and other vertical features
     * parallel across the screen.</p>
     *
     * @return the factor a sprite standing at this point is drawn at
     */
    static float rotateAndProject(float[] point, LOTRGuiMap gui) {
        if (point == null || point.length < 2 || unrotatedDepth > 0) {
            return 1.0F;
        }
        try {
            float degrees = degreesOf(gui);
            float lean = leanOf(gui);
            if ((degrees == 0.0F && lean <= 0.0F) || !ensureReflection()) {
                return 1.0F;
            }
            float centerX = centerX();
            float centerY = centerY();
            rotateAbout(point, centerX, centerY, degrees);
            applyLean(point, centerX, centerY, leanScaleY(lean));
            return 1.0F;
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    /**
     * Turns a point onto the sheet without laying the sheet down.
     *
     * <p>The half-way space, and the only place a shape lying <em>on</em> the
     * map can be laid out: the map's own turn has been applied, so the sheet's
     * axes are the screen's, but the lean has not, so a length along the sheet
     * is still just a length. Offsets are measured here and
     * {@link #leanOnly} then carries the result the rest of the way.</p>
     */
    static void rotateOnly(float[] point, LOTRGuiMap gui) {
        if (point == null || point.length < 2 || unrotatedDepth > 0) {
            return;
        }
        try {
            float degrees = degreesOf(gui);
            if (degrees == 0.0F || !ensureReflection()) {
                return;
            }
            rotateAbout(point, centerX(), centerY(), degrees);
        } catch (Throwable ignored) {
            // A point that cannot be turned is left where it was found.
        }
    }

    /** Lays the sheet down under a point already turned onto it. */
    static float leanOnly(float[] point, LOTRGuiMap gui) {
        if (point == null || point.length < 2 || unrotatedDepth > 0) {
            return 1.0F;
        }
        try {
            float lean = leanOf(gui);
            if (lean <= 0.0F || !ensureReflection()) {
                return 1.0F;
            }
            float centerX = centerX();
            float centerY = centerY();
            applyLean(point, centerX, centerY, leanScaleY(lean));
            return 1.0F;
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    /**
     * The point the map turns about: exactly the one LOTR projects through.
     *
     * <p>Its own conversion adds {@code mapXMin + mapWidth / 2} in integer
     * arithmetic, so this has to as well. Half a pixel of disagreement leaves
     * every marker swinging on a slightly different circle from the ground
     * beneath it, which reads as the map shivering as it turns.</p>
     *
     * <p>Private on purpose: it reads fields this class resolves lazily, so
     * callers outside it go through {@link #readCenter(float[])}, which makes
     * sure they have been resolved first.</p>
     */
    private static float centerX() throws IllegalAccessException {
        return mapXMinField.getInt(null) + mapWidthField.getInt(null) / 2;
    }

    private static float centerY() throws IllegalAccessException {
        return mapYMinField.getInt(null) + mapHeightField.getInt(null) / 2;
    }

    /**
     * Writes the turn centre into {@code result}, or leaves it untouched and
     * returns false when the map camera cannot be read.
     */
    static boolean readCenter(float[] result) {
        if (result == null || result.length < 2 || !ensureReflection()) {
            return false;
        }
        try {
            result[0] = centerX();
            result[1] = centerY();
            return true;
        } catch (IllegalAccessException exception) {
            return false;
        }
    }

    /**
     * How far the map is leaning for this screen, from 0 flat to 1.
     */
    static float leanOf(LOTRGuiMap gui) {
        return gui instanceof LostTalesLotrMapGui
                ? ((LostTalesLotrMapGui)gui).getMapLean() : 0.0F;
    }

    /**
     * How far the eye has dropped, as a sine.
     *
     * <p>What a layer with height over the sheet is lifted by: a thing
     * standing above the ground stands further up the screen the lower the eye
     * gets, and is not lifted at all while the map is flat.</p>
     */
    static float leanSine(LOTRGuiMap gui) {
        return (float)Math.sin(Math.toRadians(pitchDegrees(leanOf(gui))));
    }

    /** How far the eye has dropped, including the bounded elastic overshoot. */
    static float pitchDegrees(float lean) {
        return MAX_PITCH_DEGREES
                * Math.max(0.0F, Math.min(MAX_VISUAL_LEAN, lean));
    }

    /**
     * How much the sheet is squashed towards the horizon by a lean.
     *
     * <p>The cosine of the same pitch used by the y/z matrix. Because this is
     * orthographic there is no position-dependent scale: the whole sheet
     * recedes evenly and parallel features stay parallel.</p>
     */
    static float leanScaleY(float lean) {
        float clamped = Math.max(0.0F,
                Math.min(MAX_VISUAL_LEAN, lean));
        if (clamped <= 0.0F) {
            return 1.0F;
        }
        return (float)Math.cos(Math.toRadians(pitchDegrees(clamped)));
    }

    /**
     * The largest the sheet is ever cut, whatever it is doing.
     *
     * <p>Used by conservative screen-space culling for sheet-attached labels.
     * Procedural noise uses the map texture's own coordinates and no longer
     * needs an independent coverage quad.</p>
     */
    static float maxCoverage(float width, float height) {
        return (float)Math.sqrt(width * width + height * height)
                * leanCoverage(MAX_VISUAL_LEAN);
    }

    /**
     * How much taller the source sheet has to be cut to survive a lean.
     *
     * <p>Orthographic pitch only foreshortens the vertical axis, so its exact
     * inverse is enough. The surrounding scissor takes back the extra source
     * area after rotation.</p>
     */
    static float leanCoverage(float lean) {
        float clamped = Math.max(0.0F,
                Math.min(MAX_VISUAL_LEAN, lean));
        if (clamped <= 0.0F) {
            return 1.0F;
        }
        return 1.0F / Math.max(MIN_ORTHOGRAPHIC_SCALE,
                leanScaleY(clamped));
    }

    /**
     * Projects a point that is already in screen space, in place.
     *
     * <p>One orthographic camera: the sheet is laid down by {@code scaleY}
     * without moving either horizontal edge toward a vanishing point.</p>
     */
    static void applyLean(float[] point, float centerX, float centerY,
                          float scaleY) {
        float deltaY = point[1] - centerY;
        point[1] = centerY + deltaY * scaleY;
    }

    /**
     * Exact inverse of {@link #applyLean}.
     *
     * <p>The orthographic forward path is one bounded multiplication, so its
     * inverse is one division.</p>
     */
    static void removeLean(float[] point, float centerX, float centerY,
                           float scaleY) {
        float projectedY = point[1] - centerY;
        point[1] = centerY + projectedY
                / Math.max(MIN_ORTHOGRAPHIC_SCALE, scaleY);
    }

    /** Rotates {@code point} about a centre, in place, by degrees. */
    static void rotateAbout(
            float[] point, float centerX, float centerY, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float deltaX = point[0] - centerX;
        float deltaY = point[1] - centerY;
        point[0] = centerX + deltaX * cos - deltaY * sin;
        point[1] = centerY + deltaX * sin + deltaY * cos;
    }

    /**
     * The map-image point under a screen position.
     *
     * <p>The exact inverse of the forward path: the rotation is undone about
     * the same centre, and LOTR's own pan and zoom are then undone with the
     * fields it computed them from. Deriving it rather than reading LOTR's
     * unrotated mouse coordinates is what keeps placement, hit testing and
     * rendering answering with the same point.</p>
     *
     * @param result two floats written with the map-image position
     * @return false when the map camera could not be read
     */
    static boolean screenToMapImage(
            LOTRGuiMap gui, float screenX, float screenY, float[] result) {
        if (gui == null || result == null || result.length < 2
                || !ensureReflection()) {
            return false;
        }
        try {
            float centerX = centerX();
            float centerY = centerY();
            result[0] = screenX;
            result[1] = screenY;
            // Undone in the order it was applied: the lean first, then the
            // turn, so the two stay exact inverses of the forward path.
            float lean = leanOf(gui);
            removeLean(result, centerX, centerY, leanScaleY(lean));
            rotateAbout(result, centerX, centerY, -degreesOf(gui));
            float zoomScale = zoomScaleField.getFloat(gui);
            if (!(zoomScale > 0.0F)) {
                return false;
            }
            result[0] = posXField.getFloat(gui)
                    + (result[0] - centerX) / zoomScale;
            result[1] = posYField.getFloat(gui)
                    + (result[1] - centerY) / zoomScale;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Turns a camera movement so dragging follows the pointer on a turned map.
     *
     * <p>LOTR moves the camera along the map's own axes. Once the map is
     * turned those are no longer the screen's axes, so the movement it worked
     * out from a horizontal drag has to be turned by the same angle before it
     * is applied, or the map slides away at a slant.</p>
     *
     * @param delta two floats, replaced with the turned movement
     */
    static void rotateCameraDelta(float[] delta, float degrees) {
        if (delta == null || delta.length < 2 || degrees == 0.0F) {
            return;
        }
        float[] point = new float[] { delta[0], delta[1] };
        rotateAbout(point, 0.0F, 0.0F, -degrees);
        delta[0] = point[0];
        delta[1] = point[1];
    }

    /** Keeps a camera position on the map image, as LOTR's own panning does. */
    static float clampToMapImage(float position, int imageSize) {
        if (imageSize <= 0) {
            // The map image has not been measured yet. Clamping to a size of
            // nothing would pin the camera to the corner of a map that is
            // about to exist; leaving it alone costs a frame at worst.
            return position;
        }
        return Math.max(0.0F, Math.min((float)imageSize, position));
    }

    static int mapImageWidth() {
        return LOTRGenLayerWorld.imageWidth;
    }

    static int mapImageHeight() {
        return LOTRGenLayerWorld.imageHeight;
    }

    /**
     * How far a rotated viewport reaches along each screen axis.
     *
     * <p>Turning the map leaves the corners of the screen looking at ground
     * that was outside it before, so the image has to be sampled and drawn
     * over the bounding box of the turned viewport rather than the viewport
     * itself.</p>
     *
     * @param result two floats written with the covered width and height
     */
    static void rotatedCoverage(
            float width, float height, float degrees, float lean,
            float[] result) {
        if (degrees == 0.0F && lean <= 0.0F) {
            result[0] = width;
            result[1] = height;
            return;
        }
        // One square, cut to the viewport's diagonal, whatever the angle.
        // Sizing the sheet to each angle in turn changes its proportions as
        // it turns; keeping one shape prevents visible coverage shifts.
        float diagonal = (float)Math.sqrt(
                width * width + height * height) * leanCoverage(lean);
        result[0] = diagonal;
        result[1] = diagonal;
    }

    /**
     * Tight conservative bounds for querying objects that will later be
     * projected individually. Unlike the square sheet allocation, these
     * bounds need not preserve a texture's aspect ratio.
     */
    static void visibleCoverage(
            float width, float height, float degrees, float lean,
            float[] result) {
        if (result == null || result.length < 2) {
            return;
        }
        double radians = Math.toRadians(degrees);
        float cosine = Math.abs((float)Math.cos(radians));
        float sine = Math.abs((float)Math.sin(radians));
        float expansion = leanCoverage(lean);
        result[0] = (cosine * width + sine * height) * expansion;
        result[1] = (sine * width + cosine * height) * expansion;
    }

    private static synchronized boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            mapXMinField = field("mapXMin");
            mapYMinField = field("mapYMin");
            mapWidthField = field("mapWidth");
            mapHeightField = field("mapHeight");
            posXField = field("posX");
            posYField = field("posY");
            zoomScaleField = field("zoomScale");
            reflectionReady = true;
            return true;
        } catch (Throwable ignored) {
            reflectionFailed = true;
            return false;
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = LOTRGuiMap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
