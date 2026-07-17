package com.ninuna.losttales.client.camera;

/** Pure angle math for camera-relative body facing. */
public final class DirectionalMovementMath {
    private DirectionalMovementMath() {}

    public static float resolveMovementYaw(
            float cameraYaw, float moveForward, float moveStrafe) {
        if (!Float.isFinite(cameraYaw)
                || !Float.isFinite(moveForward)
                || !Float.isFinite(moveStrafe)) {
            throw new IllegalArgumentException("movement angles must be finite");
        }
        if (Math.abs(moveForward) < 0.000001F
                && Math.abs(moveStrafe) < 0.000001F) {
            return wrapDegrees(cameraYaw);
        }
        double inputAngle = Math.toDegrees(Math.atan2(
                -(double)moveStrafe, (double)moveForward));
        return wrapDegrees((float)(cameraYaw + inputAngle));
    }

    public static float approachDegrees(
            float current, float target, float maximumStep) {
        if (!Float.isFinite(current) || !Float.isFinite(target)
                || !Float.isFinite(maximumStep) || maximumStep < 0.0F) {
            throw new IllegalArgumentException(
                    "angles and non-negative step must be finite");
        }
        float difference = wrapDegrees(target - current);
        if (Math.abs(difference) <= maximumStep) {
            return wrapDegrees(target);
        }
        return wrapDegrees(current + Math.copySign(maximumStep, difference));
    }

    public static float resolveHeadTrackingYaw(
            float bodyYaw, float viewYaw, float trackingAngle) {
        return resolveHeadTrackingYaw(
                bodyYaw, viewYaw, trackingAngle,
                isReverseHeadTracking(bodyYaw, viewYaw, trackingAngle)
                        ? 1.0F : 0.0F);
    }

    public static float resolveHeadTrackingYaw(
            float bodyYaw, float viewYaw, float trackingAngle,
            float reverseBlend) {
        if (!Float.isFinite(bodyYaw) || !Float.isFinite(viewYaw)
                || !Float.isFinite(trackingAngle)
                || !Float.isFinite(reverseBlend)
                || trackingAngle < 0.0F || trackingAngle > 120.0F
                || reverseBlend < 0.0F || reverseBlend > 1.0F) {
            throw new IllegalArgumentException(
                    "head tracking angles and blend must be finite and within their limits");
        }
        float difference = wrapDegrees(viewYaw - bodyYaw);
        float normalDifference = clamp(
                difference, -trackingAngle, trackingAngle);
        float reverseDifference = wrapDegrees(difference + 180.0F);
        float limitedReverseDifference = clamp(
                reverseDifference, -trackingAngle, trackingAngle);
        float easedBlend = smoothStep(reverseBlend);
        float blendedDifference = normalDifference + wrapDegrees(
                limitedReverseDifference - normalDifference) * easedBlend;
        return wrapDegrees(bodyYaw + blendedDifference);
    }

    public static boolean isReverseHeadTracking(
            float bodyYaw, float viewYaw, float trackingAngle) {
        if (!Float.isFinite(bodyYaw) || !Float.isFinite(viewYaw)
                || !Float.isFinite(trackingAngle)
                || trackingAngle < 0.0F || trackingAngle > 120.0F) {
            throw new IllegalArgumentException(
                    "head tracking angles must be finite and the limit must be between zero and one hundred twenty degrees");
        }
        return Math.abs(wrapDegrees(viewYaw - bodyYaw)) > trackingAngle;
    }

    public static boolean updateReverseHeadTracking(
            boolean reverseTracking, float bodyYaw, float viewYaw,
            float trackingAngle, float hysteresisAngle) {
        if (!Float.isFinite(bodyYaw) || !Float.isFinite(viewYaw)
                || !Float.isFinite(trackingAngle)
                || !Float.isFinite(hysteresisAngle)
                || trackingAngle < 0.0F || trackingAngle > 120.0F
                || hysteresisAngle < 0.0F
                || hysteresisAngle > 45.0F) {
            throw new IllegalArgumentException(
                    "head tracking hysteresis must be finite and within its limits");
        }
        float difference = Math.abs(wrapDegrees(viewYaw - bodyYaw));
        float enterAngle = Math.min(
                180.0F, trackingAngle + hysteresisAngle);
        float exitAngle = Math.max(
                0.0F, trackingAngle - hysteresisAngle);
        return reverseTracking
                ? difference > exitAngle
                : difference >= enterAngle;
    }

    public static float resolveHeadTrackingPitch(
            float viewPitch, boolean reverseTracking) {
        return resolveHeadTrackingPitch(
                viewPitch, reverseTracking ? 1.0F : 0.0F);
    }

    public static float resolveHeadTrackingPitch(
            float viewPitch, float reverseBlend) {
        if (!Float.isFinite(viewPitch) || !Float.isFinite(reverseBlend)
                || reverseBlend < 0.0F || reverseBlend > 1.0F) {
            throw new IllegalArgumentException(
                    "head pitch and reverse blend must be finite and within their limits");
        }
        return viewPitch * (1.0F - 2.0F * smoothStep(reverseBlend));
    }

    public static float approachValue(
            float current, float target, float maximumStep) {
        if (!Float.isFinite(current) || !Float.isFinite(target)
                || !Float.isFinite(maximumStep) || maximumStep < 0.0F) {
            throw new IllegalArgumentException(
                    "values and non-negative step must be finite");
        }
        float difference = target - current;
        if (Math.abs(difference) <= maximumStep) {
            return target;
        }
        return current + Math.copySign(maximumStep, difference);
    }

    public static float wrapDegrees(float degrees) {
        return (float)CameraMath.wrapDegrees(degrees);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
