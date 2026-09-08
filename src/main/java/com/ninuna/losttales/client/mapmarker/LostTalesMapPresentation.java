package com.ninuna.losttales.client.mapmarker;

/** Appearance choices for views of the same map and resource-backed layers. */
public enum LostTalesMapPresentation {
    GAMEPLAY(true, true, true, 0.0F),
    MAIN_MENU(false, true, true, 0.6F),
    LOADING(false, true, false, 0.35F),
    INSET(false, true, false, 0.0F);

    public final boolean knownTerrain;
    public final boolean scenery;
    public final boolean weather;
    public final float lean;

    LostTalesMapPresentation(boolean knownTerrain, boolean scenery,
                             boolean weather, float lean) {
        this.knownTerrain = knownTerrain;
        this.scenery = scenery;
        this.weather = weather;
        this.lean = lean;
    }
}
