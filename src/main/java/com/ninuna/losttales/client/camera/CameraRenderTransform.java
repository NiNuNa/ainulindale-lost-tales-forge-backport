package com.ninuna.losttales.client.camera;

/**
 * Converts world follow-lag and local motion into the OpenGL translation and
 * render-frame geometry used by collision, targeting, and the HUD.
 */
public final class CameraRenderTransform {
    private final CameraRenderFrame frame;
    private final double translateX;
    private final double translateY;
    private final double translateZ;

    private CameraRenderTransform(
            CameraRenderFrame frame,
            double translateX, double translateY, double translateZ) {
        this.frame = frame;
        this.translateX = translateX;
        this.translateY = translateY;
        this.translateZ = translateZ;
    }

    public static CameraRenderTransform resolve(
            double rawPivotX, double rawPivotY, double rawPivotZ,
            double yaw, double pitch, double actualDistance,
            CameraPose pose, CameraMotionOffset motion,
            double verticalFov) {
        if (pose == null || motion == null) {
            throw new IllegalArgumentException(
                    "pose and motion are required");
        }
        CameraMath.requireNonNegativeFinite(
                "actualDistance", actualDistance);
        Basis basis = new Basis(yaw, pitch);
        double scale = pose.getDistance() <= 0.000001D
                ? 0.0D : clamp01(actualDistance / pose.getDistance());
        double followX = (pose.getPositionX() - rawPivotX) * scale;
        double followY = (pose.getPositionY() - rawPivotY) * scale;
        double followZ = (pose.getPositionZ() - rawPivotZ) * scale;
        double forwardMotion = motion.getForward() * scale;
        double shoulder = (pose.getShoulderOffset()
                + motion.getSide()) * scale;
        double vertical = (pose.getVerticalOffset()
                + motion.getVertical()) * scale;

        double pivotX = rawPivotX + followX
                + basis.forwardX * forwardMotion;
        double pivotY = rawPivotY + followY
                + basis.forwardY * forwardMotion;
        double pivotZ = rawPivotZ + followZ
                + basis.forwardZ * forwardMotion;
        CameraRenderFrame frame = new CameraRenderFrame(
                pivotX, pivotY, pivotZ, yaw, pitch,
                actualDistance, shoulder, vertical, verticalFov);

        double cameraOffsetX = followX
                + basis.forwardX * forwardMotion
                + basis.rightX * shoulder + basis.upX * vertical;
        double cameraOffsetY = followY
                + basis.forwardY * forwardMotion
                + basis.rightY * shoulder + basis.upY * vertical;
        double cameraOffsetZ = followZ
                + basis.forwardZ * forwardMotion
                + basis.rightZ * shoulder + basis.upZ * vertical;
        return new CameraRenderTransform(
                frame,
                -dot(cameraOffsetX, cameraOffsetY, cameraOffsetZ,
                        basis.rightX, basis.rightY, basis.rightZ),
                -dot(cameraOffsetX, cameraOffsetY, cameraOffsetZ,
                        basis.upX, basis.upY, basis.upZ),
                dot(cameraOffsetX, cameraOffsetY, cameraOffsetZ,
                        basis.forwardX, basis.forwardY, basis.forwardZ));
    }

    public static LocalOffsets resolveDesiredOffsets(
            double rawPivotX, double rawPivotY, double rawPivotZ,
            double yaw, double pitch,
            CameraPose pose, CameraMotionOffset motion) {
        if (pose == null || motion == null) {
            throw new IllegalArgumentException(
                    "pose and motion are required");
        }
        Basis basis = new Basis(yaw, pitch);
        double followX = pose.getPositionX() - rawPivotX;
        double followY = pose.getPositionY() - rawPivotY;
        double followZ = pose.getPositionZ() - rawPivotZ;
        return new LocalOffsets(
                dot(followX, followY, followZ,
                        basis.rightX, basis.rightY, basis.rightZ)
                        + pose.getShoulderOffset() + motion.getSide(),
                dot(followX, followY, followZ,
                        basis.upX, basis.upY, basis.upZ)
                        + pose.getVerticalOffset() + motion.getVertical(),
                dot(followX, followY, followZ,
                        basis.forwardX, basis.forwardY, basis.forwardZ)
                        + motion.getForward());
    }

    public CameraRenderFrame getFrame() {
        return frame;
    }

    public double getTranslateX() {
        return translateX;
    }

    public double getTranslateY() {
        return translateY;
    }

    public double getTranslateZ() {
        return translateZ;
    }

    public static final class LocalOffsets {
        private final double side;
        private final double vertical;
        private final double forward;

        private LocalOffsets(
                double side, double vertical, double forward) {
            this.side = side;
            this.vertical = vertical;
            this.forward = forward;
        }

        public double getSide() {
            return side;
        }

        public double getVertical() {
            return vertical;
        }

        public double getForward() {
            return forward;
        }
    }

    private static final class Basis {
        private final double forwardX;
        private final double forwardY;
        private final double forwardZ;
        private final double rightX;
        private final double rightY;
        private final double rightZ;
        private final double upX;
        private final double upY;
        private final double upZ;

        private Basis(double yawDegrees, double pitchDegrees) {
            CameraMath.requireFinite("yaw", yawDegrees);
            CameraMath.requireFinite("pitch", pitchDegrees);
            double yaw = Math.toRadians(yawDegrees);
            double pitch = Math.toRadians(pitchDegrees);
            double sinYaw = Math.sin(yaw);
            double cosYaw = Math.cos(yaw);
            double sinPitch = Math.sin(pitch);
            double cosPitch = Math.cos(pitch);
            forwardX = -sinYaw * cosPitch;
            forwardY = -sinPitch;
            forwardZ = cosYaw * cosPitch;
            rightX = -cosYaw;
            rightY = 0.0D;
            rightZ = -sinYaw;
            upX = -sinYaw * sinPitch;
            upY = cosPitch;
            upZ = cosYaw * sinPitch;
        }
    }

    private static double dot(
            double leftX, double leftY, double leftZ,
            double rightX, double rightY, double rightZ) {
        return leftX * rightX + leftY * rightY + leftZ * rightZ;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
