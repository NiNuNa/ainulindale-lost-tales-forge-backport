package com.ninuna.losttales.client.camera;

/** Named, validated camera preset loaded from client configuration JSON. */
public final class CameraPresetDefinition {
    public static final int CURRENT_DATA_VERSION = 1;

    private final int dataVersion;
    private final String id;
    private final String name;
    private final CameraPreset preset;

    public CameraPresetDefinition(
            int dataVersion, String id, String name, CameraPreset preset) {
        if (dataVersion != CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported camera preset data version " + dataVersion);
        }
        if (id == null || id.length() == 0
                || name == null || name.length() == 0 || preset == null) {
            throw new IllegalArgumentException(
                    "id, name, and preset are required");
        }
        this.dataVersion = dataVersion;
        this.id = id;
        this.name = name;
        this.preset = preset;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CameraPreset getPreset() {
        return preset;
    }
}
