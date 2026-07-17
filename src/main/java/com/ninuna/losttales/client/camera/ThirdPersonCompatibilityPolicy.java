package com.ninuna.losttales.client.camera;

/** Pure compatibility gates for fail-closed optional client hooks. */
public final class ThirdPersonCompatibilityPolicy {
    private ThirdPersonCompatibilityPolicy() {}

    public static boolean canActivateOverhaul(
            boolean configuredEnabled,
            boolean cameraTransformerActive) {
        return configuredEnabled && cameraTransformerActive;
    }

    public static boolean canUseAuthoritativeTargeting(
            boolean configuredEnabled,
            boolean targetingTransformerActive,
            boolean actionTransformerActive) {
        return configuredEnabled && targetingTransformerActive
                && actionTransformerActive;
    }
}
