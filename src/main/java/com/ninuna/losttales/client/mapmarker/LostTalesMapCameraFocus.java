package com.ninuna.losttales.client.mapmarker;

import java.lang.reflect.Field;
import lotr.client.gui.LOTRGuiMap;

/**
 * The one camera movement the map screen owns: a single eased progress that
 * pans and zooms together and brings a map point to rest under a chosen point
 * on screen.
 *
 * <p>Framing a stack and framing a fast-travel destination are the same
 * movement with different arguments. The only difference is the screen anchor:
 * a stack is opened at the centre of the viewport, a travel destination above
 * the popup that describes it, so the marker being talked about is not hidden
 * behind the words. Because the anchor is part of the movement rather than
 * applied afterwards, the destination arrives where it was asked to whatever
 * the zoom does on the way.</p>
 *
 * <p>The path is a quadratic Bézier whose control point leads along the axis
 * the camera is mostly travelling on, so a movement downward sets off downward
 * and a movement to the right sets off to the right, instead of sliding along
 * a ruler or swinging out sideways. Its bow grows with the distance and with
 * how far off centre the destination currently sits, and is zero for a short
 * hop.</p>
 *
 * <p>A second focus restarts from the point the camera is visibly framing at
 * that moment, so repeated clicks retarget one movement instead of running two
 * against each other.</p>
 */
final class LostTalesMapCameraFocus {
    /** Shortest a focus movement may take, in nanoseconds. */
    static final long MIN_DURATION_NANOS = 320000000L;
    /** Longest a focus movement may take, however far it has to go. */
    static final long MAX_DURATION_NANOS = 900000000L;
    /** Map-image pixels of travel that add one second of movement. */
    static final float NANOS_PER_MAP_PIXEL = 9000000.0F;
    /**
     * Strongest bow toward the leading axis, as a share of the way from the
     * straight midpoint to the corner the path would turn at. Kept well below
     * one so the camera never travels noticeably away from its destination.
     */
    static final float MAX_CURVATURE = 0.34F;
    /** Travel shorter than this, in map-image pixels, is not worth curving. */
    static final float CURVATURE_MIN_DISTANCE = 12.0F;
    /** Travel at or beyond this, in map-image pixels, gets the full bow. */
    static final float CURVATURE_FULL_DISTANCE = 140.0F;
    /**
     * Share of the bow a destination already under the middle of the screen
     * keeps. One near an edge gets all of it.
     */
    static final float CENTRED_CURVATURE_SHARE = 0.55F;
    /** Floats one camera capture occupies. */
    static final int CAMERA_STATE_SIZE = 6;

    private static Field posXField;
    private static Field posYField;
    private static Field prevPosXField;
    private static Field prevPosYField;
    private static Field posXMoveField;
    private static Field posYMoveField;
    private static Field prevMouseXField;
    private static Field prevMouseYField;
    private static Field mapXMinField;
    private static Field mapYMinField;
    private static Field mapWidthField;
    private static Field mapHeightField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private boolean active;
    /** Map-image point being framed at the start of the movement. */
    private float startFocusX;
    private float startFocusY;
    private float targetFocusX;
    private float targetFocusY;
    private float startZoomExp;
    private float targetZoomExp;
    private float currentZoomExp;
    /** Screen point the framed map point comes to rest under. */
    private float anchorX;
    private float anchorY;
    private float curvature;
    private long startNanos;
    private long durationNanos;

    /**
     * Symmetric ease-in-out.
     *
     * <p>Smoothstep rather than smootherstep: it still leaves and arrives at
     * rest, but its peak speed is only half again the average, so the movement
     * stays visible from the first frame to the last instead of creeping,
     * lurching and then crawling to a halt.</p>
     */
    static float ease(float linear) {
        float progress = clamp01(linear);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    /** Longer journeys take longer, between a floor and a ceiling. */
    static long durationFor(float distance) {
        long scaled = MIN_DURATION_NANOS
                + (long)(Math.max(0.0F, distance) * NANOS_PER_MAP_PIXEL);
        return Math.max(MIN_DURATION_NANOS,
                Math.min(MAX_DURATION_NANOS, scaled));
    }

    /**
     * How much the path bows, from the length of the journey and where its
     * destination currently sits on screen.
     *
     * @param distance         map-image pixels the camera has to travel
     * @param screenOffsetRatio how far the destination is from the middle of
     *                          the viewport, as a share of the way to its edge
     */
    static float curvature(float distance, float screenOffsetRatio) {
        if (!(distance > CURVATURE_MIN_DISTANCE)) {
            return 0.0F;
        }
        float travelled = clamp01(
                (distance - CURVATURE_MIN_DISTANCE)
                        / (CURVATURE_FULL_DISTANCE - CURVATURE_MIN_DISTANCE));
        float offset = CENTRED_CURVATURE_SHARE
                + (1.0F - CENTRED_CURVATURE_SHARE)
                        * clamp01(screenOffsetRatio);
        return MAX_CURVATURE * travelled * offset;
    }

    /**
     * Position along the path at an eased progress.
     *
     * <p>A quadratic Bézier whose control point sits between the straight
     * midpoint and the corner an axis-first path would turn at: the axis the
     * camera travels furthest along leads, and the other follows. A curvature
     * of zero puts the control point exactly on the midpoint, which is the
     * straight line at a uniform speed.</p>
     *
     * @param result two floats written with the map-image position
     */
    static void pathPoint(
            float startX, float startY, float endX, float endY,
            float curvature, float eased, float[] result) {
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float midX = startX + deltaX * 0.5F;
        float midY = startY + deltaY * 0.5F;
        boolean verticalLead = Math.abs(deltaY) > Math.abs(deltaX);
        float leadX = verticalLead ? startX : endX;
        float leadY = verticalLead ? endY : startY;
        float bow = Math.max(0.0F, Math.min(1.0F, curvature));
        float controlX = midX + (leadX - midX) * bow;
        float controlY = midY + (leadY - midY) * bow;
        float progress = clamp01(eased);
        float remaining = 1.0F - progress;
        result[0] = remaining * remaining * startX
                + 2.0F * remaining * progress * controlX
                + progress * progress * endX;
        result[1] = remaining * remaining * startY
                + 2.0F * remaining * progress * controlY
                + progress * progress * endY;
    }

    /**
     * Map-image point that has to sit at the camera centre for
     * {@code focusX/focusY} to appear under the screen anchor.
     */
    static float cameraPositionFor(
            float focusPosition, float anchor, float viewportCentre,
            float zoomScale) {
        return zoomScale <= 0.0F ? focusPosition
                : focusPosition - (anchor - viewportCentre) / zoomScale;
    }

    /** Inverse of {@link #cameraPositionFor}: what the anchor is framing now. */
    static float framedPositionAt(
            float cameraPosition, float anchor, float viewportCentre,
            float zoomScale) {
        return zoomScale <= 0.0F ? cameraPosition
                : cameraPosition + (anchor - viewportCentre) / zoomScale;
    }

    /**
     * Sends the camera to a map-image point and a zoom, replacing any movement
     * already running rather than adding a second one.
     *
     * @param anchorX screen point the map point comes to rest under, or
     *                {@link Float#NaN} for the middle of the viewport
     */
    void focus(LOTRGuiMap gui, float mapImageX, float mapImageY,
               float currentZoomExp, float targetZoomExp,
               float anchorX, float anchorY, long nowNanos) {
        float[] viewport = new float[4];
        float[] camera = new float[CAMERA_STATE_SIZE];
        if (gui == null || !readViewport(viewport)
                || !captureCamera(gui, camera)) {
            this.active = false;
            return;
        }
        float centreX = viewportCentreX(viewport);
        float centreY = viewportCentreY(viewport);
        this.anchorX = Float.isNaN(anchorX) ? centreX : anchorX;
        this.anchorY = Float.isNaN(anchorY) ? centreY : anchorY;
        float[] anchor = mapAlignedAnchor(gui, viewport);
        float startScale = (float)Math.pow(2.0D, currentZoomExp);
        // Starting from the point the anchor is already framing is what makes
        // a second click continue the movement instead of restarting it.
        this.startFocusX = framedPositionAt(
                camera[0], anchor[0], centreX, startScale);
        this.startFocusY = framedPositionAt(
                camera[1], anchor[1], centreY, startScale);
        this.targetFocusX = mapImageX;
        this.targetFocusY = mapImageY;
        this.startZoomExp = currentZoomExp;
        this.targetZoomExp = targetZoomExp;
        this.currentZoomExp = currentZoomExp;
        this.startNanos = nowNanos;
        float deltaX = mapImageX - this.startFocusX;
        float deltaY = mapImageY - this.startFocusY;
        float distance = (float)Math.sqrt(
                deltaX * deltaX + deltaY * deltaY);
        this.durationNanos = durationFor(distance);
        this.curvature = curvature(distance, screenOffsetRatio(
                deltaX * startScale, deltaY * startScale, viewport));
        this.active = true;
    }

    /**
     * The screen anchor as the camera has to see it.
     *
     * <p>The camera works along the map's own axes, which stop being the
     * screen's axes once the map is turned. Undoing the turn on the anchor
     * before solving for the camera is what puts the framed marker under the
     * point on screen that was actually asked for. It is re-derived every
     * frame, so turning the map mid-movement is followed rather than
     * ignored.</p>
     *
     * @return two floats, the anchor in the camera's own frame
     */
    private float[] mapAlignedAnchor(LOTRGuiMap gui, float[] viewport) {
        float[] anchor = new float[] { this.anchorX, this.anchorY };
        LostTalesLotrMapRotation.rotateAbout(anchor,
                viewportCentreX(viewport), viewportCentreY(viewport),
                -LostTalesLotrMapRotation.degreesOf(gui));
        return anchor;
    }

    /**
     * How far off centre the destination currently appears, as a share of the
     * way to the viewport edge, clamped to one at the edge itself.
     */
    private static float screenOffsetRatio(
            float screenDeltaX, float screenDeltaY, float[] viewport) {
        float halfWidth = viewport[2] * 0.5F;
        float halfHeight = viewport[3] * 0.5F;
        float horizontal = halfWidth <= 0.0F
                ? 0.0F : Math.abs(screenDeltaX) / halfWidth;
        float vertical = halfHeight <= 0.0F
                ? 0.0F : Math.abs(screenDeltaY) / halfHeight;
        return Math.min(1.0F, Math.max(horizontal, vertical));
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
        float[] viewport = new float[4];
        if (!this.active || gui == null || !readViewport(viewport)) {
            this.active = false;
            return false;
        }
        long elapsed = Math.max(0L, nowNanos - this.startNanos);
        float linear = this.durationNanos <= 0L ? 1.0F
                : Math.min(1.0F,
                        (float)elapsed / (float)this.durationNanos);
        float eased = ease(linear);
        float[] point = new float[2];
        pathPoint(this.startFocusX, this.startFocusY,
                this.targetFocusX, this.targetFocusY,
                this.curvature, eased, point);
        // One progress drives both, so the map stops moving and stops zooming
        // at the same moment.
        this.currentZoomExp = this.startZoomExp
                + (this.targetZoomExp - this.startZoomExp) * eased;
        if (linear >= 1.0F) {
            point[0] = this.targetFocusX;
            point[1] = this.targetFocusY;
            this.currentZoomExp = this.targetZoomExp;
            this.active = false;
        }
        float scale = (float)Math.pow(2.0D, this.currentZoomExp);
        float[] anchor = mapAlignedAnchor(gui, viewport);
        // Kept on the map. An anchor off the centre of the screen is worth a
        // fixed number of screen pixels but a growing number of map ones as
        // the map zooms out — far enough out, framing a marker beside a popup
        // asks for a camera hundreds of map pixels into open sea, and the map
        // image is simply not there any more.
        float posX = LostTalesLotrMapRotation.clampToMapImage(
                cameraPositionFor(point[0], anchor[0],
                        viewportCentreX(viewport), scale),
                LostTalesLotrMapRotation.mapImageWidth());
        float posY = LostTalesLotrMapRotation.clampToMapImage(
                cameraPositionFor(point[1], anchor[1],
                        viewportCentreY(viewport), scale),
                LostTalesLotrMapRotation.mapImageHeight());
        try {
            // prevPosX/prevPosY are the camera of record. LOTR's own draw
            // opens by copying them over posX/posY, so writing only the latter
            // is thrown away before anything is rendered.
            prevPosXField.setFloat(gui, posX);
            prevPosYField.setFloat(gui, posY);
            posXField.setFloat(gui, posX);
            posYField.setFloat(gui, posY);
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

    /**
     * Reads the map viewport as {@code {xMin, yMin, width, height}}. It is
     * re-read every frame because a window resize changes it mid-movement.
     */
    private static boolean readViewport(float[] result) {
        if (result == null || result.length < 4 || !ensureReflection()) {
            return false;
        }
        try {
            result[0] = mapXMinField.getInt(null);
            result[1] = mapYMinField.getInt(null);
            result[2] = mapWidthField.getInt(null);
            result[3] = mapHeightField.getInt(null);
            return true;
        } catch (IllegalAccessException exception) {
            markUnavailable();
            return false;
        }
    }

    /**
     * The point the camera is centred on, exactly as LOTR computes it.
     *
     * <p>Its own projection adds {@code mapXMin + mapWidth / 2} in integer
     * arithmetic. Solving the camera against a centre half a pixel from that
     * one leaves the framed marker half a pixel off at every zoom, growing
     * with it.</p>
     */
    private static float viewportCentreX(float[] viewport) {
        return viewport[0] + (int)viewport[2] / 2;
    }

    private static float viewportCentreY(float[] viewport) {
        return viewport[1] + (int)viewport[3] / 2;
    }

    private static void markUnavailable() {
        reflectionFailed = true;
        reflectionReady = false;
    }

    /**
     * Reads the whole map camera into {@code result}: the position of record,
     * the interpolated copy drawn this frame, and the momentum LOTR's keyboard
     * movement carries.
     *
     * <p>Used to hold the map still while a popup owns the screen. LOTR pans
     * from inside its own draw and its own tick, so consuming the click is not
     * enough — the only reliable way to freeze it is to put back what it was
     * given.</p>
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

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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
            mapXMinField = field("mapXMin");
            mapYMinField = field("mapYMin");
            mapWidthField = field("mapWidth");
            mapHeightField = field("mapHeight");
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
