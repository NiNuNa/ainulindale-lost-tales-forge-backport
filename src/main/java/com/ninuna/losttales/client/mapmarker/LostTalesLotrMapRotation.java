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
    /** Horizontal drag, in GUI pixels, that turns the map as far as it goes. */
    static final float FULL_TURN_DRAG_PIXELS = 520.0F;
    /**
     * How sharply the map stiffens as it leaves square. Both the turn and the
     * lean follow {@code 1 - (1 - t)^RESISTANCE_EXPONENT}, so the drag a
     * degree costs rises the whole way out: easy off square, noticeably
     * heavier half way, and heavy enough at the end that the limit is
     * somewhere the player leans into rather than falls through.
     *
     * <p>It was seven, which put nine tenths of the travel in the first
     * quarter of the drag and made the rest a wall — the map felt both
     * twitchy and immovable depending on where in the range it was.</p>
     */
    static final float RESISTANCE_EXPONENT = 3.0F;
    /**
     * Floor on how flat the resistance curve is allowed to read when a drag
     * is coming back towards square. Reached only in the last fraction of a
     * degree, where without it a single pixel would unwind the whole angle.
     */
    private static final float MIN_RELEASE_STEEPNESS = 0.05F;
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
    /**
     * The share by which the near edge grows at full lean.
     *
     * <p>Not an independent knob: with {@link #MAX_PITCH_DEGREES} it fixes how
     * far the eye is from the sheet, and that distance is what the whole
     * projection is built on. Raising it moves the eye closer and makes the
     * perspective stronger for the same angle.</p>
     */
    static final float MAX_LEAN = 0.30F;
    /** Smallest foreshortening the projection is allowed to divide by. */
    private static final float MIN_COVERAGE_DIVISOR = 0.05F;
    /**
     * Vertical drag, in GUI pixels, from flat to fully leaned. The same as a
     * full turn takes, and through the same curve, so leaning and turning
     * cost the player the same movement and feel like one gesture.
     */
    static final float FULL_LEAN_DRAG_PIXELS = FULL_TURN_DRAG_PIXELS;
    /**
     * Floor on the perspective divisor. Only reachable well outside the
     * viewport, and it keeps a point that far out from being turned inside
     * out by a division through zero.
     */
    private static final float MIN_PERSPECTIVE_DIVISOR = 0.2F;

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
        float clamped = Math.max(0.0F, Math.min(1.0F, input));
        return resist(clamped);
    }

    /**
     * The stiffening curve, shared by the turn and the lean.
     *
     * <p>Returns how far along its range an input has earned. The gradient is
     * {@code RESISTANCE_EXPONENT} at the start and nothing at all at the end,
     * so the resistance the player feels rises smoothly the whole way out
     * instead of being one number over the range.</p>
     */
    static float resist(float advanced) {
        float clamped = Math.max(0.0F, Math.min(1.0F, advanced));
        return 1.0F - (float)Math.pow(
                1.0D - clamped, RESISTANCE_EXPONENT);
    }

    /**
     * Adds a drag to an axis, with the resistance depending on which way it
     * is going.
     *
     * <p>Moving away from square costs exactly what it always did: the drag
     * accumulates unchanged and {@link #degreesForInput(float)} turns it into
     * an angle through a curve that stiffens the whole way out, so the limit
     * is still somewhere the player leans into.</p>
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
     * @param current accumulated drag, {@code -1} to {@code 1}
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
     * <p>Floored, because at the very limit the curve is flat and dividing by
     * that would let a single pixel throw the map back to square.</p>
     */
    private static float releaseSteepness(float advanced) {
        float clamped = Math.max(0.0F, Math.min(1.0F, advanced));
        return Math.max(MIN_RELEASE_STEEPNESS, (float)Math.pow(
                1.0D - clamped, RESISTANCE_EXPONENT - 1.0F));
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
        return Math.max(-1.0F, Math.min(1.0F, input));
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
        float eased = resist(Math.abs(clamped));
        return magnetiseDegrees(
                (clamped < 0.0F ? -1.0F : 1.0F) * MAX_DEGREES * eased);
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
            float coefficient = leanCoefficient(
                    lean, mapHeightField.getInt(null));
            GL11.glPushMatrix();
            GL11.glTranslatef(centerX, centerY, 0.0F);
            multiplyLean(coefficient, leanScaleY(lean));
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
     * <p>Perspective, done the only way a fixed pipeline offers it: the
     * matrix writes a {@code w} that varies with the vertical, and the
     * hardware divides by it. Everything below the middle of the screen is
     * divided by less than one and spreads outward; everything above it by
     * more and draws in. Because the divide happens after rasterisation sets
     * up, the map texture follows it correctly rather than shearing.</p>
     *
     * <p>The same matrix carries the foreshortening, so the image and every
     * position on it are laid down and divided by one operation and cannot
     * disagree about where the sheet is.</p>
     */
    private static void multiplyLean(float coefficient, float scaleY) {
        if (coefficient == 0.0F) {
            return;
        }
        FloatBuffer matrix = leanMatrix;
        matrix.clear();
        for (int index = 0; index < 16; index++) {
            matrix.put(index % 5 == 0 ? 1.0F : 0.0F);
        }
        // Column-major: column 1, row 1 is the y contributed by y, and
        // column 1, row 3 the w it contributes.
        matrix.put(5, scaleY);
        matrix.put(7, coefficient);
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
            applyLean(point, centerX, centerY,
                    leanCoefficient(lean, mapHeightField.getInt(null)),
                    leanScaleY(lean));
            return point;
        } catch (Throwable ignored) {
            return point;
        }
    }

    /**
     * Projects a point on the sheet and reports how much something standing
     * there is shrunk by the distance to it.
     *
     * <p>The same path as {@link #rotate}, and the answer is the perspective
     * divide that path already performs. A billboard standing on the far half
     * of a leaning map is further from the eye than one on the near half, so
     * it has to be drawn smaller; without this a tilted map reads as an
     * evenly-lit wall of sprites and loses the depth the projection is there
     * to give it.</p>
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
            float coefficient = leanCoefficient(
                    lean, mapHeightField.getInt(null));
            // Read before the lean moves the point, because the divide is a
            // function of where the point is on the sheet, not of where the
            // projection has put it.
            float scale = perspectiveScale(point[1] - centerY, coefficient);
            applyLean(point, centerX, centerY, coefficient, leanScaleY(lean));
            return scale;
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
            float coefficient = leanCoefficient(
                    lean, mapHeightField.getInt(null));
            float scale = perspectiveScale(point[1] - centerY, coefficient);
            applyLean(point, centerX, centerY, coefficient, leanScaleY(lean));
            return scale;
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    /** How far away a point on the sheet has ended up, as a drawn scale. */
    static float perspectiveScale(float deltaY, float coefficient) {
        if (coefficient == 0.0F) {
            return 1.0F;
        }
        return 1.0F / Math.max(MIN_PERSPECTIVE_DIVISOR,
                1.0F + coefficient * deltaY);
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

    /** How far the eye has dropped, in degrees, for a lean of 0 to 1. */
    static float pitchDegrees(float lean) {
        return MAX_PITCH_DEGREES * Math.max(0.0F, Math.min(1.0F, lean));
    }

    /**
     * How far the eye is from the sheet, in screen pixels.
     *
     * <p>Fixed by {@link #MAX_LEAN}: at full pitch the near edge of the
     * viewport has to grow by exactly that share, and only one distance does
     * that. Tied to the viewport rather than to a constant so the same lean
     * looks the same on every screen.</p>
     */
    private static float eyeDistance(float viewportHeight) {
        return viewportHeight * 0.5F
                * (float)Math.sin(Math.toRadians(MAX_PITCH_DEGREES))
                / MAX_LEAN;
    }

    /**
     * The perspective coefficient a lean works out to: the {@code w} written
     * per pixel of screen height.
     *
     * <p>Everything below the middle of the screen is divided by a number
     * under one and everything above it by one over, which spreads the near
     * edge outward and draws the far edge in.</p>
     */
    static float leanCoefficient(float lean, float viewportHeight) {
        float clamped = Math.max(0.0F, Math.min(1.0F, lean));
        if (clamped <= 0.0F || !(viewportHeight > 0.0F)) {
            return 0.0F;
        }
        return -(float)Math.sin(Math.toRadians(pitchDegrees(clamped)))
                / eyeDistance(viewportHeight);
    }

    /**
     * How much the sheet is squashed towards the horizon by a lean.
     *
     * <p>The half of the tilt the map was missing. A perspective divide on its
     * own keystones the sheet without ever letting it recede, so turning a map
     * that was leaning read as the reader tilting their head rather than as a
     * camera moving round an angled surface. Foreshortening by the cosine of
     * the same angle the divide is built from is what makes the two one
     * camera: the sheet lies down as the eye drops, and the divide then puts
     * the near edge nearer.</p>
     */
    static float leanScaleY(float lean) {
        float clamped = Math.max(0.0F, Math.min(1.0F, lean));
        if (clamped <= 0.0F) {
            return 1.0F;
        }
        return (float)Math.cos(Math.toRadians(pitchDegrees(clamped)));
    }

    /**
     * The largest the sheet is ever cut, whatever it is doing.
     *
     * <p>What the paper grain is sized to. The grain is one stretched copy of
     * a texture, so anything that changes the size of what it is stretched
     * over changes the grain — and the sheet changes size as the map leans.
     * Cutting the grain to the size the sheet reaches at full lean gives it
     * one size it keeps at every angle, and one that always covers.</p>
     */
    static float maxCoverage(float width, float height) {
        return (float)Math.sqrt(width * width + height * height)
                * leanCoverage(1.0F);
    }

    /**
     * How much wider the sheet has to be cut to survive a lean.
     *
     * <p>The far edge is both squashed and divided down, so a sheet the size
     * of the viewport would pull its own top edge into view. This is the
     * projection read backwards: how far the sheet has to reach for its far
     * edge to still land on the edge of the screen. Cutting it larger costs
     * nothing but a slightly bigger quad, and the scissor takes back the
     * rest.</p>
     */
    static float leanCoverage(float lean) {
        float clamped = Math.max(0.0F, Math.min(1.0F, lean));
        if (clamped <= 0.0F) {
            return 1.0F;
        }
        float squash = leanScaleY(clamped);
        float spread = MAX_LEAN
                * (float)Math.sin(Math.toRadians(pitchDegrees(clamped)))
                / (float)Math.sin(Math.toRadians(MAX_PITCH_DEGREES));
        return 1.0F / Math.max(MIN_COVERAGE_DIVISOR, squash - spread);
    }

    /**
     * Projects a point that is already in screen space, in place.
     *
     * <p>One camera: the sheet is laid down by {@code scaleY} and then divided
     * through by how far each part of it has ended up from the eye.</p>
     */
    static void applyLean(float[] point, float centerX, float centerY,
                          float coefficient, float scaleY) {
        if (coefficient == 0.0F) {
            return;
        }
        float deltaY = point[1] - centerY;
        float divisor = Math.max(MIN_PERSPECTIVE_DIVISOR,
                1.0F + coefficient * deltaY);
        point[0] = centerX + (point[0] - centerX) / divisor;
        point[1] = centerY + deltaY * scaleY / divisor;
    }

    /**
     * Exact inverse of {@link #applyLean}.
     *
     * <p>Solved rather than iterated: the forward path is a ratio of two
     * linear functions of the same unknown, so undoing it is one division.
     * A point past the horizon has no answer at all, which is what the floor
     * on the denominator stands in for.</p>
     */
    static void removeLean(float[] point, float centerX, float centerY,
                           float coefficient, float scaleY) {
        if (coefficient == 0.0F) {
            return;
        }
        float projectedY = point[1] - centerY;
        float denominator = scaleY - coefficient * projectedY;
        if (Math.abs(denominator) < MIN_PERSPECTIVE_DIVISOR) {
            denominator = denominator < 0.0F
                    ? -MIN_PERSPECTIVE_DIVISOR : MIN_PERSPECTIVE_DIVISOR;
        }
        float deltaY = projectedY / denominator;
        float divisor = Math.max(MIN_PERSPECTIVE_DIVISOR,
                1.0F + coefficient * deltaY);
        point[0] = centerX + (point[0] - centerX) * divisor;
        point[1] = centerY + deltaY;
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
            removeLean(result, centerX, centerY,
                    leanCoefficient(lean, mapHeightField.getInt(null)),
                    leanScaleY(lean));
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
        // Sizing the sheet to each angle in turn changed its proportions as
        // it went round, and the paper grain printed on it stretched and
        // settled with them; a sheet that is always the same shape cannot.
        float diagonal = (float)Math.sqrt(
                width * width + height * height) * leanCoverage(lean);
        result[0] = diagonal;
        result[1] = diagonal;
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
