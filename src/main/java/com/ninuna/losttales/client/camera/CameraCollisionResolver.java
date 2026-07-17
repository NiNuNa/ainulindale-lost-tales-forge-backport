package com.ninuna.losttales.client.camera;

/** Swept multi-ray collision math for an offset third-person camera. */
public final class CameraCollisionResolver {
    private static final double NO_HIT = -1.0D;

    private CameraCollisionResolver() {}

    public interface Raycaster {
        /** Returns distance from start to the hit, or a negative value for no hit. */
        double trace(
                double startX, double startY, double startZ,
                double endX, double endY, double endZ);
    }

    public static double resolveAllowedDistance(
            Raycaster raycaster,
            double pivotX, double pivotY, double pivotZ,
            double yawDegrees, double pitchDegrees,
            double desiredDistance, double shoulderOffset,
            double verticalOffset, double padding) {
        return resolveAllowedDistance(
                raycaster, pivotX, pivotY, pivotZ,
                yawDegrees, pitchDegrees, desiredDistance,
                shoulderOffset, verticalOffset, 0.0D, padding);
    }

    public static double resolveAllowedDistance(
            Raycaster raycaster,
            double pivotX, double pivotY, double pivotZ,
            double yawDegrees, double pitchDegrees,
            double desiredDistance, double shoulderOffset,
            double verticalOffset, double forwardOffset,
            double padding) {
        if (raycaster == null) {
            throw new IllegalArgumentException("raycaster is required");
        }
        CameraMath.requireNonNegativeFinite(
                "desiredDistance", desiredDistance);
        CameraMath.requireNonNegativeFinite("padding", padding);
        CameraMath.requireFinite("pivotX", pivotX);
        CameraMath.requireFinite("pivotY", pivotY);
        CameraMath.requireFinite("pivotZ", pivotZ);
        CameraMath.requireFinite("yawDegrees", yawDegrees);
        CameraMath.requireFinite("pitchDegrees", pitchDegrees);
        CameraMath.requireFinite("shoulderOffset", shoulderOffset);
        CameraMath.requireFinite("verticalOffset", verticalOffset);
        CameraMath.requireFinite("forwardOffset", forwardOffset);
        if (desiredDistance == 0.0D) {
            return 0.0D;
        }

        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);

        double forwardX = -sinYaw * cosPitch;
        double forwardY = -sinPitch;
        double forwardZ = cosYaw * cosPitch;
        double backX = -forwardX;
        double backY = -forwardY;
        double backZ = -forwardZ;
        double rightX = -cosYaw;
        double rightZ = -sinYaw;
        double upX = -sinYaw * sinPitch;
        double upY = cosPitch;
        double upZ = cosYaw * sinPitch;

        double endX = pivotX + backX * desiredDistance
                + rightX * shoulderOffset + upX * verticalOffset
                + forwardX * forwardOffset;
        double endY = pivotY + backY * desiredDistance
                + upY * verticalOffset + forwardY * forwardOffset;
        double endZ = pivotZ + backZ * desiredDistance
                + rightZ * shoulderOffset + upZ * verticalOffset
                + forwardZ * forwardOffset;

        double segmentX = endX - pivotX;
        double segmentY = endY - pivotY;
        double segmentZ = endZ - pivotZ;
        double segmentLength = Math.sqrt(
                segmentX * segmentX + segmentY * segmentY
                        + segmentZ * segmentZ);
        if (segmentLength <= 0.000001D) {
            return desiredDistance;
        }

        double allowedFraction = 1.0D;
        for (int corner = 0; corner < 8; corner++) {
            double offsetX = ((corner & 1) == 0 ? -padding : padding);
            double offsetY = ((corner & 2) == 0 ? -padding : padding);
            double offsetZ = ((corner & 4) == 0 ? -padding : padding);
            double hitDistance = raycaster.trace(
                    pivotX + offsetX, pivotY + offsetY,
                    pivotZ + offsetZ,
                    endX + offsetX, endY + offsetY, endZ + offsetZ);
            if (hitDistance == NO_HIT || hitDistance < 0.0D) {
                continue;
            }
            double hitFraction = Math.max(
                    0.0D, (hitDistance - padding) / segmentLength);
            allowedFraction = Math.min(allowedFraction, hitFraction);
        }
        return desiredDistance * allowedFraction;
    }
}
