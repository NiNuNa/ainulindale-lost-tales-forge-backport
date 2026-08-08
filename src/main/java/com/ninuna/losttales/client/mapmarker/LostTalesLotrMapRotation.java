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
    /** Furthest the map may be turned in either direction, in degrees. */
    public static final float MAX_DEGREES = 22.5F;
    /** Horizontal drag, in GUI pixels, that turns the map as far as it goes. */
    static final float FULL_TURN_DRAG_PIXELS = 900.0F;
    /**
     * How sharply the map stiffens as it leaves square. Both the turn and the
     * lean follow {@code 1 - (1 - t)^RESISTANCE_EXPONENT}: the first part of a
     * drag moves the map freely and the last of it costs an order of magnitude
     * more movement, so the extremes are places the player leans into rather
     * than falls through.
     */
    static final float RESISTANCE_EXPONENT = 7.0F;
    /**
     * Share of the drag spent at true north before the map begins to turn.
     * Square is the reading everything else is measured against, so it is
     * given a detent rather than left as one exact value among thousands.
     */
    static final float SNAP_INPUT_SHARE = 0.06F;
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
     * How far the map may lean away from flat.
     *
     * <p>The share by which the near edge grows. It is the same 22.5° the map
     * may turn through, read as a tilt of the eye rather than of the sheet:
     * {@code tan(22.5°) / 2}, which is how much nearer the bottom of the map
     * comes when the eye drops that far. Turning and leaning therefore reach
     * their limits together and by the same amount.</p>
     */
    static final float MAX_LEAN = 0.21F;
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
     * How far a lean drag has earned the map, through the same detent and the
     * same stiffening curve as the turn.
     */
    static float leanForInput(float input) {
        float clamped = Math.max(0.0F, Math.min(1.0F, input));
        if (clamped <= SNAP_INPUT_SHARE) {
            return 0.0F;
        }
        float advanced = (clamped - SNAP_INPUT_SHARE)
                / (1.0F - SNAP_INPUT_SHARE);
        return 1.0F - (float)Math.pow(
                1.0D - advanced, RESISTANCE_EXPONENT);
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
     * <p>Two things shape it. A detent around square: the first small part of
     * any drag turns nothing, so leaving true north takes a deliberate
     * movement and coming back to it lands exactly on it rather than near it.
     * And resistance that grows steeply with the angle: the last couple of
     * degrees cost roughly nine times the drag the first couple did, so the
     * limit is somewhere the player leans into rather than falls through.</p>
     */
    static float degreesForInput(float input) {
        float clamped = clampInput(input);
        float magnitude = Math.abs(clamped);
        if (magnitude <= SNAP_INPUT_SHARE) {
            return 0.0F;
        }
        float advanced = (magnitude - SNAP_INPUT_SHARE)
                / (1.0F - SNAP_INPUT_SHARE);
        float eased = 1.0F - (float)Math.pow(
                1.0D - advanced, RESISTANCE_EXPONENT);
        return (clamped < 0.0F ? -1.0F : 1.0F) * MAX_DEGREES * eased;
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
            multiplyLean(coefficient);
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
     * divided by slightly less than one and spreads outward; everything above
     * it by slightly more and draws in. Because the divide happens after
     * rasterisation sets up, the map texture follows it correctly rather than
     * shearing.</p>
     */
    private static void multiplyLean(float coefficient) {
        if (coefficient == 0.0F) {
            return;
        }
        FloatBuffer matrix = leanMatrix;
        matrix.clear();
        for (int index = 0; index < 16; index++) {
            matrix.put(index % 5 == 0 ? 1.0F : 0.0F);
        }
        // Column-major: column 1, row 3 is the w contributed by y.
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
            applyLean(point, centerX, centerY, leanCoefficient(
                    lean, mapHeightField.getInt(null)));
            return point;
        } catch (Throwable ignored) {
            return point;
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
     * The perspective coefficient a lean works out to.
     *
     * <p>Everything below the middle of the screen is divided by a number
     * slightly under one and everything above it by slightly over, which
     * spreads the near edge outward and draws the far edge in. That is the
     * whole of the effect: one number, applied the same way to the map image
     * and to every position on it.</p>
     */
    static float leanCoefficient(float lean, float viewportHeight) {
        float clamped = Math.max(0.0F, Math.min(1.0F, lean));
        if (clamped <= 0.0F || !(viewportHeight > 0.0F)) {
            return 0.0F;
        }
        return -MAX_LEAN * clamped / (viewportHeight * 0.5F);
    }

    /**
     * How much wider the sheet has to be cut to survive a lean.
     *
     * <p>The far edge is drawn smaller, so a sheet the size of the viewport
     * would pull its own top edge into view. Cutting it larger costs nothing
     * but a slightly bigger quad, and the scissor takes back the rest.</p>
     */
    static float leanCoverage(float lean) {
        float clamped = Math.max(0.0F, Math.min(1.0F, lean));
        return 1.0F + 2.0F * MAX_LEAN * clamped;
    }

    /** Leans a point that is already in screen space, in place. */
    static void applyLean(
            float[] point, float centerX, float centerY, float coefficient) {
        if (coefficient == 0.0F) {
            return;
        }
        float deltaY = point[1] - centerY;
        float divisor = Math.max(MIN_PERSPECTIVE_DIVISOR,
                1.0F + coefficient * deltaY);
        point[0] = centerX + (point[0] - centerX) / divisor;
        point[1] = centerY + deltaY / divisor;
    }

    /** Exact inverse of {@link #applyLean}. */
    static void removeLean(
            float[] point, float centerX, float centerY, float coefficient) {
        if (coefficient == 0.0F) {
            return;
        }
        float leanedY = point[1] - centerY;
        float denominator = Math.max(MIN_PERSPECTIVE_DIVISOR,
                1.0F - coefficient * leanedY);
        float deltaY = leanedY / denominator;
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
            removeLean(result, centerX, centerY, leanCoefficient(
                    leanOf(gui), mapHeightField.getInt(null)));
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
