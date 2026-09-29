package com.ninuna.losttales.client.camera;

/** Stable user-facing identifiers for the built-in camera presets. */
public enum CameraPresetId {
    MODERN_ACTION_RPG("modern_action_rpg");

    private final String configValue;

    CameraPresetId(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigValue() {
        return configValue;
    }
}
