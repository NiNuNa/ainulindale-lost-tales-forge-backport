package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.config.LostTalesConfig;

/**
 * The close-map blend derived from the map's continuous zoom exponent.
 *
 * <p>There is deliberately no accumulated transition state. The smooth zoom
 * already supplies a stable fractional exponent, so deriving both opacities
 * from it makes fast wheel movement reversible and unable to leave the two
 * backgrounds out of sync.</p>
 */
final class LostTalesMapTerrainTransition {
    /** Starts bounded preparation shortly before terrain can become visible. */
    static final float PREPARE_MARGIN = 0.2F;

    private LostTalesMapTerrainTransition() {}

    static boolean shouldPrepareTerrain(float zoomExp) {
        return !Float.isNaN(zoomExp)
                && zoomExp >= mapOnlyZoomExp() - PREPARE_MARGIN;
    }

    /** Desired terrain opacity before availability is applied tile by tile. */
    static float terrainAlpha(float zoomExp) {
        float mapOnly = mapOnlyZoomExp();
        float span = terrainOnlyZoomExp() - mapOnly;
        if (!(span > 0.0F) || Float.isNaN(zoomExp)) {
            return 0.0F;
        }
        float progress = clamp((zoomExp - mapOnly) / span);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    static float mapOnlyZoomExp() {
        return (float)LostTalesConfig.closeMapTerrainTransitionStartZoom;
    }

    static float terrainOnlyZoomExp() {
        return (float)LostTalesConfig.closeMapTerrainTransitionEndZoom;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
