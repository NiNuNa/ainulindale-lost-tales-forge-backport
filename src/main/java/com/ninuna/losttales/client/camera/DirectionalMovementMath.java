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
        if (!Float.isFinite(bodyYaw) || !Float.isFinite(viewYaw)
                || !Float.isFinite(trackingAngle)
                || trackingAngle < 0.0F || trackingAngle > 90.0F) {
            throw new IllegalArgumentException(
                    "head tracking angles must be finite and the limit must be between zero and ninety degrees");
        }
        float difference = wrapDegrees(viewYaw - bodyYaw);
        float absoluteDifference = Math.abs(difference);
        if (absoluteDifference <= trackingAngle) {
            return wrapDegrees(viewYaw);
        }

        /*
         * Crossing either shoulder immediately selects the opposite way of
         * looking at the camera. Clamp that reverse target to the same neck
         * limit so there is no body-forward dead zone and no excessive twist.
         */
        float reverseDifference = wrapDegrees(difference + 180.0F);
        float limitedReverseDifference = Math.max(
                -trackingAngle, Math.min(trackingAngle, reverseDifference));
        return wrapDegrees(bodyYaw + limitedReverseDifference);
    }

    public static boolean isReverseHeadTracking(
            float bodyYaw, float viewYaw, float trackingAngle) {
        if (!Float.isFinite(bodyYaw) || !Float.isFinite(viewYaw)
                || !Float.isFinite(trackingAngle)
                || trackingAngle < 0.0F || trackingAngle > 90.0F) {
            throw new IllegalArgumentException(
                    "head tracking angles must be finite and the limit must be between zero and ninety degrees");
        }
        return Math.abs(wrapDegrees(viewYaw - bodyYaw)) > trackingAngle;
    }

    public static float resolveHeadTrackingPitch(
            float viewPitch, boolean reverseTracking) {
        if (!Float.isFinite(viewPitch)) {
            throw new IllegalArgumentException(
                    "head pitch must be finite");
        }
        return reverseTracking ? -viewPitch : viewPitch;
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
}
