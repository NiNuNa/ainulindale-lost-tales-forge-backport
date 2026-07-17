package com.ninuna.losttales.client.camera;

/** Response rates, in inverse seconds, for each independently smoothed channel. */
public final class CameraSmoothing {
    private final double positionRate;
    private final double verticalPositionRate;
    private final double rotationRate;
    private final double zoomRate;
    private final double shoulderRate;
    private final double verticalRate;
    private final double fovRate;

    public CameraSmoothing(
            double positionRate, double rotationRate, double zoomRate,
            double shoulderRate, double verticalRate, double fovRate) {
        this(positionRate, positionRate, rotationRate, zoomRate,
                shoulderRate, verticalRate, fovRate);
    }

    public CameraSmoothing(
            double positionRate, double verticalPositionRate,
            double rotationRate, double zoomRate,
            double shoulderRate, double verticalRate, double fovRate) {
        CameraMath.requireNonNegativeFinite("positionRate", positionRate);
        CameraMath.requireNonNegativeFinite(
                "verticalPositionRate", verticalPositionRate);
        CameraMath.requireNonNegativeFinite("rotationRate", rotationRate);
        CameraMath.requireNonNegativeFinite("zoomRate", zoomRate);
        CameraMath.requireNonNegativeFinite("shoulderRate", shoulderRate);
        CameraMath.requireNonNegativeFinite("verticalRate", verticalRate);
        CameraMath.requireNonNegativeFinite("fovRate", fovRate);
        this.positionRate = positionRate;
        this.verticalPositionRate = verticalPositionRate;
        this.rotationRate = rotationRate;
        this.zoomRate = zoomRate;
        this.shoulderRate = shoulderRate;
        this.verticalRate = verticalRate;
        this.fovRate = fovRate;
    }

    public double getPositionRate() {
        return positionRate;
    }

    public double getVerticalPositionRate() {
        return verticalPositionRate;
    }

    public double getRotationRate() {
        return rotationRate;
    }

    public double getZoomRate() {
        return zoomRate;
    }

    public double getShoulderRate() {
        return shoulderRate;
    }

    public double getVerticalRate() {
        return verticalRate;
    }

    public double getFovRate() {
        return fovRate;
    }

    public CameraSmoothing scaled(double multiplier) {
        CameraMath.requireNonNegativeFinite("multiplier", multiplier);
        if (multiplier == 1.0D) {
            return this;
        }
        return new CameraSmoothing(
                positionRate * multiplier,
                verticalPositionRate * multiplier,
                rotationRate * multiplier,
                zoomRate * multiplier,
                shoulderRate * multiplier,
                verticalRate * multiplier,
                fovRate * multiplier);
    }
}
