package com.ninuna.losttales.client.camera;

/** Pure camera math shared by state interpolation and future render hooks. */
public final class CameraMath {
    /**
     * Long pauses must not be integrated as one frame. A quarter second still
     * supports a genuine 4 FPS render interval without allowing pause menus or
     * debugger stops to snap every smoothed channel to its target.
     */
    public static final double MAX_FRAME_DELTA_SECONDS = 0.25D;

    private CameraMath() {}

    public static double sanitizeDeltaSeconds(double deltaSeconds) {
        if (!isFinite(deltaSeconds) || deltaSeconds <= 0.0D) {
            return 0.0D;
        }
        return Math.min(deltaSeconds, MAX_FRAME_DELTA_SECONDS);
    }

    public static double exponentialAlpha(
            double responseRate, double deltaSeconds) {
        requireNonNegativeFinite("responseRate", responseRate);
        double safeDelta = sanitizeDeltaSeconds(deltaSeconds);
        if (safeDelta == 0.0D) {
            return 0.0D;
        }
        if (responseRate == 0.0D) {
            return 1.0D;
        }
        return 1.0D - Math.exp(-responseRate * safeDelta);
    }

    public static double interpolate(
            double current, double target, double responseRate,
            double deltaSeconds) {
        requireFinite("current", current);
        requireFinite("target", target);
        double alpha = exponentialAlpha(responseRate, deltaSeconds);
        return current + (target - current) * alpha;
    }

    public static double interpolateAngle(
            double currentDegrees, double targetDegrees,
            double responseRate, double deltaSeconds) {
        requireFinite("currentDegrees", currentDegrees);
        requireFinite("targetDegrees", targetDegrees);
        double alpha = exponentialAlpha(responseRate, deltaSeconds);
        double difference = wrapDegrees(targetDegrees - currentDegrees);
        return wrapDegrees(currentDegrees + difference * alpha);
    }

    public static double wrapDegrees(double degrees) {
        requireFinite("degrees", degrees);
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        } else if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    static void requireFinite(String name, double value) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    static void requireNonNegativeFinite(String name, double value) {
        requireFinite(name, value);
        if (value < 0.0D) {
            throw new IllegalArgumentException(
                    name + " must be greater than or equal to zero");
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
