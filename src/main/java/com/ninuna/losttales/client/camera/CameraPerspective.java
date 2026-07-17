package com.ninuna.losttales.client.camera;

/** Perspective states used while the optional overhaul is enabled. */
public enum CameraPerspective {
    FIRST_PERSON(0),
    THIRD_PERSON(1);

    private final int vanillaValue;

    CameraPerspective(int vanillaValue) {
        this.vanillaValue = vanillaValue;
    }

    public CameraPerspective next() {
        return this == FIRST_PERSON ? THIRD_PERSON : FIRST_PERSON;
    }

    public int getVanillaValue() {
        return vanillaValue;
    }

    public static CameraPerspective fromVanillaValue(int value) {
        return value == 0 ? FIRST_PERSON : THIRD_PERSON;
    }

    public static int normalizeVanillaValue(
            int value, boolean overhaulEnabled) {
        return overhaulEnabled && value > 1 ? 0 : value;
    }
}
