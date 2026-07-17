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

    public static CameraPresetId fromConfigValue(String value) {
        if (value != null) {
            for (CameraPresetId id : values()) {
                if (id.configValue.equalsIgnoreCase(value.trim())) {
                    return id;
                }
            }
        }
        return MODERN_ACTION_RPG;
    }

    public static String[] getConfigValues() {
        CameraPresetId[] ids = values();
        String[] values = new String[ids.length];
        for (int index = 0; index < ids.length; index++) {
            values[index] = ids[index].configValue;
        }
        return values;
    }
}
