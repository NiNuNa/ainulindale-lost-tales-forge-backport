package com.ninuna.losttales.client.camera;

/**
 * The vanilla perspective setting while the optional overhaul is enabled:
 * first person and the camera behind, with no front-facing mode.
 */
public final class CameraPerspective {
    private CameraPerspective() {}

    public static int normalizeVanillaValue(
            int value, boolean overhaulEnabled) {
        return overhaulEnabled && value > 1 ? 0 : value;
    }
}
