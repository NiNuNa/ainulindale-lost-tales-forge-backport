package com.ninuna.losttales.character.physics;

/** Pure nameplate-anchor math shared by all race-aware player renderers. */
public final class CharacterNameplateHeightHelper {

    public static final float VANILLA_MODEL_HEIGHT = 1.8F;

    private CharacterNameplateHeightHelper() {}

    /**
     * Vanilla already anchors labels to entity.height. Only return the visual
     * amount extending above that synchronized physical height.
     */
    public static float resolveExtraHeight(
            float physicalHeight, float rendererScale) {
        if (!isPositiveFinite(physicalHeight)
                || !isPositiveFinite(rendererScale)) {
            return 0.0F;
        }
        float visualHeight = VANILLA_MODEL_HEIGHT * rendererScale;
        return Math.max(0.0F, visualHeight - physicalHeight);
    }

    private static boolean isPositiveFinite(float value) {
        return value > 0.0F && !Float.isNaN(value)
                && !Float.isInfinite(value);
    }
}
