package com.ninuna.losttales.client.camera;

/**
 * Where a camera has to stand to frame a subject at a chosen place and size
 * on the screen.
 *
 * <p>The creator wants the character to sit in the stage right of its
 * column, filling most of the stage's height, whatever the window's size
 * and field of view. That is three numbers: how far back the camera stands
 * for the subject to be that tall, and how far it steps aside and up so
 * the subject lands where the stage is rather than in the middle of the
 * screen. All three fall out of the field of view, so the framing holds
 * when the player changes theirs.</p>
 *
 * <p>Screen positions are normalised device coordinates: minus one to one
 * across, positive to the viewer's right, and minus one to one down,
 * positive upward. Offsets are along the camera's own right and up
 * vectors; a positive side offset moves the camera to its right, which
 * moves the subject to the left of the screen.</p>
 */
public final class InspectionCameraMath {

    /** Nearer than this and the camera is inside the subject. */
    public static final double MIN_DISTANCE = 0.6D;
    /** Farther than this and the subject is a speck; also bounds collision work. */
    public static final double MAX_DISTANCE = 16.0D;

    private InspectionCameraMath() {}

    /**
     * How far back the camera stands for a subject that tall to fill that
     * share of the screen's height, brought nearer by the zoom.
     */
    public static double distanceFor(double subjectHeight,
                                     double heightFraction,
                                     double verticalFovDegrees, double zoom) {
        double halfTangent = halfTangent(verticalFovDegrees);
        double fraction = heightFraction <= 0.0D ? 0.5D : heightFraction;
        double height = subjectHeight <= 0.0D ? 1.8D : subjectHeight;
        double magnification = zoom <= 0.0D ? 1.0D : zoom;
        double distance = height / (fraction * 2.0D * halfTangent) / magnification;
        return clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
    }

    /**
     * How far the camera steps to its side so the subject appears at that
     * horizontal screen position instead of the centre.
     */
    public static double sideOffsetFor(double distance,
                                       double verticalFovDegrees,
                                       double aspectRatio,
                                       double screenX) {
        double aspect = aspectRatio <= 0.0D ? 16.0D / 9.0D : aspectRatio;
        return -clampUnit(screenX) * distance
                * halfTangent(verticalFovDegrees) * aspect;
    }

    /**
     * How far the camera rises so the subject appears at that vertical
     * screen position instead of the centre.
     */
    public static double verticalOffsetFor(double distance,
                                           double verticalFovDegrees,
                                           double screenY) {
        return -clampUnit(screenY) * distance * halfTangent(verticalFovDegrees);
    }

    /**
     * How far below the eye the middle of the body is: where the camera
     * looks so the whole figure, not the head, sits where it is framed.
     */
    public static double pivotDropBelowEye(double subjectHeight,
                                           double eyeHeightAboveFeet) {
        return pivotDropBelowEye(subjectHeight, eyeHeightAboveFeet, 0.5D);
    }

    /**
     * How far below the eye the point the camera looks at is, for a point
     * that far up the body: zero at the feet, one at the top of the head.
     * A negative drop is above the eye.
     */
    public static double pivotDropBelowEye(double subjectHeight,
                                           double eyeHeightAboveFeet,
                                           double focus) {
        double height = subjectHeight <= 0.0D ? 1.8D : subjectHeight;
        double share = clamp(focus, 0.0D, 1.0D);
        return eyeHeightAboveFeet - height * share;
    }

    /** The screen position, from minus one to one, of a pixel across a width. */
    public static double screenX(double pixelX, double screenWidth) {
        return screenWidth <= 0.0D ? 0.0D : clampUnit(pixelX / screenWidth * 2.0D - 1.0D);
    }

    /** The screen position, from minus one to one, of a pixel down a height. */
    public static double screenY(double pixelY, double screenHeight) {
        return screenHeight <= 0.0D ? 0.0D : clampUnit(1.0D - pixelY / screenHeight * 2.0D);
    }

    private static double halfTangent(double verticalFovDegrees) {
        double fov = verticalFovDegrees;
        if (Double.isNaN(fov) || fov < 1.0D || fov > 179.0D) {
            fov = 70.0D;
        }
        return Math.tan(Math.toRadians(fov / 2.0D));
    }

    private static double clampUnit(double value) {
        return clamp(value, -1.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return value < min ? min : value > max ? max : value;
    }
}
