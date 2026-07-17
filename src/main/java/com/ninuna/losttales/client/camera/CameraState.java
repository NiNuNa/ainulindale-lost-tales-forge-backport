package com.ninuna.losttales.client.camera;

/** Mutable owner for immutable current and target poses. */
public final class CameraState {
    private CameraPose current;
    private CameraPose target;

    public boolean isInitialized() {
        return current != null && target != null;
    }

    public CameraPose getCurrent() {
        return current;
    }

    public CameraPose getTarget() {
        return target;
    }

    public void reset(CameraPose pose) {
        if (pose == null) {
            throw new IllegalArgumentException("pose is required");
        }
        current = pose;
        target = pose;
    }

    public void clear() {
        current = null;
        target = null;
    }

    public void setTarget(CameraPose pose) {
        if (pose == null) {
            throw new IllegalArgumentException("pose is required");
        }
        if (!isInitialized()) {
            reset(pose);
            return;
        }
        target = pose;
    }

    void replaceCurrent(CameraPose pose) {
        if (pose == null || !isInitialized()) {
            throw new IllegalArgumentException(
                    "initialized state and pose are required");
        }
        current = pose;
    }

    public CameraPose advance(
            CameraSmoothing smoothing, double deltaSeconds) {
        if (!isInitialized()) {
            throw new IllegalStateException("camera state is not initialized");
        }
        current = CameraInterpolator.interpolate(
                current, target, smoothing, deltaSeconds);
        return current;
    }
}
