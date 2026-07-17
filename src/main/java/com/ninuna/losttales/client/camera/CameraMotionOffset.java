package com.ninuna.losttales.client.camera;

/** Immutable local-space visual motion added to profile framing. */
public final class CameraMotionOffset {
    public static final CameraMotionOffset ZERO =
            new CameraMotionOffset(0.0D, 0.0D, 0.0D);

    private final double side;
    private final double vertical;
    private final double forward;

    public CameraMotionOffset(double side, double vertical, double forward) {
        CameraMath.requireFinite("side", side);
        CameraMath.requireFinite("vertical", vertical);
        CameraMath.requireFinite("forward", forward);
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

    public CameraMotionOffset add(CameraMotionOffset other) {
        if (other == null) {
            throw new IllegalArgumentException("motion offset is required");
        }
        return new CameraMotionOffset(
                side + other.side,
                vertical + other.vertical,
                forward + other.forward);
    }
}
