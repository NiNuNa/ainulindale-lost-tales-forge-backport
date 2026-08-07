package com.ninuna.losttales.client.mapmarker;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lotr.client.gui.LOTRGuiMap;

/**
 * Frames one marker stack: how far the map has to zoom for its members to
 * stand apart, and the eased camera that takes it there.
 *
 * <p>The zoom is not approximated. Icons are drawn at a fixed screen size, so
 * zooming only pushes their centres apart; that is enough to ask the real
 * clustering rule what a candidate zoom would decide, and the search stops at
 * the first level where every member is its own stack. A small tolerance is
 * added on top so the arrival zoom is not sitting exactly on the threshold it
 * just cleared.</p>
 *
 * <p>Panning and zooming are one movement, not two: a single eased progress
 * drives both, so the map never arrives at its destination while still
 * zooming, or the other way round. The path bows gently to one side rather
 * than sliding along a straight line, and the duration grows with the
 * distance so a short hop stays brisk and a long one still reads.</p>
 *
 * <p>A second focus restarts from wherever the camera has actually reached,
 * so repeated clicks retarget one movement instead of running two against
 * each other.</p>
 */
final class LostTalesMapGroupFocus {
    /** Zoom exponent granularity of the bounded search. */
    static final float ZOOM_SEARCH_STEP = 0.05F;
    /**
     * Extra zoom past the level that separates the stack. Arriving exactly on
     * the join threshold would let rounding regroup the markers again.
     */
    static final float ZOOM_TOLERANCE = 0.15F;
    /** Shortest a focus movement may take, in nanoseconds. */
    static final long MIN_DURATION_NANOS = 320000000L;
    /** Longest a focus movement may take, however far it has to go. */
    static final long MAX_DURATION_NANOS = 900000000L;
    /** Map-image pixels of travel that add one second of movement. */
    static final float NANOS_PER_MAP_PIXEL = 9000000.0F;
    /** Sideways bow of the path, as a share of its own length. */
    static final float PATH_ARC_RATIO = 0.16F;
    /** Ceiling on that bow, in map-image pixels. */
    static final float PATH_ARC_MAX = 24.0F;

    private static Field posXField;
    private static Field posYField;
    private static Field prevPosXField;
    private static Field prevPosYField;
    private static Field posXMoveField;
    private static Field posYMoveField;
    private static Field prevMouseXField;
    private static Field prevMouseYField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private boolean active;
    private float startPosX;
    private float startPosY;
    private float startZoomExp;
    private float targetPosX;
    private float targetPosY;
    private float targetZoomExp;
    private float currentZoomExp;
    private long startNanos;
    private long durationNanos;

    /**
     * Smallest zoom exponent above {@code currentZoomExp} at which every
     * member of the stack is its own group, plus {@link #ZOOM_TOLERANCE}.
     *
     * @param members  the stack's markers, as the last grouping decision saw
     *                 them on screen
     * @param centerX  screen point the zoom expands away from — the same
     *                 point the camera will centre on
     */
    static float separatingZoomExponent(
            List<LostTalesMapMarkerGrouping.Entry> members,
            float currentZoomExp, float maxZoomExp,
            float centerX, float centerY) {
        if (members == null || members.size() <= 1
                || !(maxZoomExp > currentZoomExp)) {
            return currentZoomExp;
        }
        int steps = (int)Math.ceil(
                (maxZoomExp - currentZoomExp) / ZOOM_SEARCH_STEP);
        for (int step = 1; step <= steps; step++) {
            float zoomExp = Math.min(maxZoomExp,
                    currentZoomExp + step * ZOOM_SEARCH_STEP);
            if (isFullySeparated(members, centerX, centerY,
                    (float)Math.pow(2.0D, zoomExp - currentZoomExp))) {
                return Math.min(maxZoomExp, zoomExp + ZOOM_TOLERANCE);
            }
        }
        return maxZoomExp;
    }

    /** Asks the real clustering rule what {@code factor} more zoom decides. */
    private static boolean isFullySeparated(
            List<LostTalesMapMarkerGrouping.Entry> members,
            float centerX, float centerY, float factor) {
        List<LostTalesMapMarkerGrouping.Entry> scaled =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>(
                        members.size());
        for (LostTalesMapMarkerGrouping.Entry member : members) {
            scaled.add(LostTalesMapMarkerGrouping.scaledAbout(
                    member, centerX, centerY, factor));
        }
        // No previous links: the stack is judged by the threshold that would
        // bring these markers together, not the looser one that keeps them.
        return LostTalesMapMarkerGrouping.group(scaled,
                Collections.<String, String>emptyMap())
                .getGroups().size() == scaled.size();
    }

    /**
     * Symmetric ease-in-out. Smootherstep rather than smoothstep: both its
     * first and second derivatives vanish at each end, so the map lifts off
     * and settles rather than starting and stopping abruptly.
     */
    static float ease(float linear) {
        float progress = Math.max(0.0F, Math.min(1.0F, linear));
        return progress * progress * progress
                * (progress * (progress * 6.0F - 15.0F) + 10.0F);
    }

    /** Longer journeys take longer, between a floor and a ceiling. */
    static long durationFor(float distance) {
        long scaled = MIN_DURATION_NANOS
                + (long)(Math.max(0.0F, distance) * NANOS_PER_MAP_PIXEL);
        return Math.max(MIN_DURATION_NANOS,
                Math.min(MAX_DURATION_NANOS, scaled));
    }

    /**
     * Position along the bowed path at an eased progress.
     *
     * <p>A quadratic Bézier whose control point sits beside the midpoint, so
     * the camera sweeps rather than sliding down a ruler. The bow is zero at
     * both ends, so neither endpoint moves.</p>
     *
     * @param result two floats written with the map-image position
     */
    static void pathPoint(
            float startX, float startY, float endX, float endY,
            float eased, float[] result) {
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float length = (float)Math.sqrt(
                deltaX * deltaX + deltaY * deltaY);
        float x = startX + deltaX * eased;
        float y = startY + deltaY * eased;
        if (length > 0.0F) {
            float bow = Math.min(length * PATH_ARC_RATIO, PATH_ARC_MAX)
                    * 4.0F * eased * (1.0F - eased);
            x += -deltaY / length * bow;
            y += deltaX / length * bow;
        }
        result[0] = x;
        result[1] = y;
    }

    /**
     * Sends the camera to a map-image position and a zoom, replacing any
     * focus already running rather than adding a second one.
     *
     * <p>The movement starts from where the camera is now, so retargeting
     * mid-flight continues from the visible position instead of jumping back
     * to where the previous one began.</p>
     */
    void focus(LOTRGuiMap gui, float mapImageX, float mapImageY,
               float currentZoomExp, float targetZoomExp,
               long nowNanos) {
        float[] camera = new float[CAMERA_STATE_SIZE];
        if (gui == null || !captureCamera(gui, camera)) {
            this.active = false;
            return;
        }
        this.startPosX = camera[0];
        this.startPosY = camera[1];
        this.startZoomExp = currentZoomExp;
        this.targetPosX = mapImageX;
        this.targetPosY = mapImageY;
        this.targetZoomExp = targetZoomExp;
        this.currentZoomExp = currentZoomExp;
        this.startNanos = nowNanos;
        float deltaX = mapImageX - this.startPosX;
        float deltaY = mapImageY - this.startPosY;
        this.durationNanos = durationFor((float)Math.sqrt(
                deltaX * deltaX + deltaY * deltaY));
        this.active = true;
    }

    /** The zoom exponent the movement is currently asking for. */
    float getCurrentZoomExp() {
        return this.currentZoomExp;
    }

    void cancel() {
        this.active = false;
    }

    boolean isActive() {
        return this.active;
    }

    /**
     * Steps the camera one frame.
     *
     * @return true while the camera is still moving
     */
    boolean advance(LOTRGuiMap gui, long nowNanos) {
        if (!this.active || gui == null || !ensureReflection()) {
            this.active = false;
            return false;
        }
        long elapsed = Math.max(0L, nowNanos - this.startNanos);
        float linear = this.durationNanos <= 0L ? 1.0F
                : Math.min(1.0F,
                        (float)elapsed / (float)this.durationNanos);
        float eased = ease(linear);
        float[] point = new float[2];
        pathPoint(this.startPosX, this.startPosY,
                this.targetPosX, this.targetPosY, eased, point);
        // One progress drives both, so the map stops moving and stops
        // zooming at the same moment.
        this.currentZoomExp = this.startZoomExp
                + (this.targetZoomExp - this.startZoomExp) * eased;
        if (linear >= 1.0F) {
            point[0] = this.targetPosX;
            point[1] = this.targetPosY;
            this.currentZoomExp = this.targetZoomExp;
            this.active = false;
        }
        try {
            // prevPosX/prevPosY are the camera of record. LOTR's own draw
            // opens by copying them over posX/posY, so writing only the
            // latter is thrown away before anything is rendered.
            prevPosXField.setFloat(gui, point[0]);
            prevPosYField.setFloat(gui, point[1]);
            posXField.setFloat(gui, point[0]);
            posYField.setFloat(gui, point[1]);
            // LOTR's keyboard momentum would otherwise keep pushing the map
            // while the focus is pulling it somewhere else.
            posXMoveField.setFloat(gui, 0.0F);
            posYMoveField.setFloat(gui, 0.0F);
            return true;
        } catch (IllegalAccessException exception) {
            markUnavailable();
            this.active = false;
            return false;
        }
    }

    private static void markUnavailable() {
        reflectionFailed = true;
        reflectionReady = false;
    }

    /** Floats one camera capture occupies. */
    static final int CAMERA_STATE_SIZE = 6;

    /**
     * Reads the whole map camera into {@code result}: the position of
     * record, the interpolated copy drawn this frame, and the momentum
     * LOTR's keyboard movement carries.
     *
     * <p>Used to hold the map still while a popup owns the screen. LOTR pans
     * from inside its own draw and its own tick, so consuming the click is
     * not enough — the only reliable way to freeze it is to put back what it
     * was given.</p>
     *
     * @param result {@link #CAMERA_STATE_SIZE} floats
     * @return false when the camera could not be read
     */
    static boolean captureCamera(LOTRGuiMap gui, float[] result) {
        if (gui == null || result == null
                || result.length < CAMERA_STATE_SIZE
                || !ensureReflection()) {
            return false;
        }
        try {
            result[0] = prevPosXField.getFloat(gui);
            result[1] = prevPosYField.getFloat(gui);
            result[2] = posXField.getFloat(gui);
            result[3] = posYField.getFloat(gui);
            result[4] = posXMoveField.getFloat(gui);
            result[5] = posYMoveField.getFloat(gui);
            return true;
        } catch (IllegalAccessException exception) {
            markUnavailable();
            return false;
        }
    }

    static void restoreCamera(LOTRGuiMap gui, float[] captured) {
        if (gui == null || captured == null
                || captured.length < CAMERA_STATE_SIZE
                || !ensureReflection()) {
            return;
        }
        try {
            prevPosXField.setFloat(gui, captured[0]);
            prevPosYField.setFloat(gui, captured[1]);
            posXField.setFloat(gui, captured[2]);
            posYField.setFloat(gui, captured[3]);
            posXMoveField.setFloat(gui, captured[4]);
            posYMoveField.setFloat(gui, captured[5]);
        } catch (IllegalAccessException exception) {
            markUnavailable();
        }
    }

    /**
     * Holds the map still by telling LOTR the pointer has not moved.
     *
     * <p>LOTR pans from inside its own draw, by polling the mouse and
     * subtracting the previous frame's pointer position. Restoring the camera
     * afterwards only undoes the movement a frame later, which reads as a
     * snap; giving it a zero delta means the movement never happens. The
     * keyboard momentum is cleared for the same reason.</p>
     */
    static void holdPointer(LOTRGuiMap gui, int mouseX, int mouseY) {
        if (gui == null || !ensureReflection()) {
            return;
        }
        try {
            prevMouseXField.setInt(gui, mouseX);
            prevMouseYField.setInt(gui, mouseY);
            posXMoveField.setFloat(gui, 0.0F);
            posYMoveField.setFloat(gui, 0.0F);
        } catch (IllegalAccessException exception) {
            markUnavailable();
        }
    }

    private static synchronized boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            posXField = field("posX");
            posYField = field("posY");
            prevPosXField = field("prevPosX");
            prevPosYField = field("prevPosY");
            posXMoveField = field("posXMove");
            posYMoveField = field("posYMove");
            prevMouseXField = field("prevMouseX");
            prevMouseYField = field("prevMouseY");
            reflectionReady = true;
            return true;
        } catch (NoSuchFieldException exception) {
            reflectionFailed = true;
            return false;
        } catch (RuntimeException exception) {
            reflectionFailed = true;
            return false;
        } catch (LinkageError error) {
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
