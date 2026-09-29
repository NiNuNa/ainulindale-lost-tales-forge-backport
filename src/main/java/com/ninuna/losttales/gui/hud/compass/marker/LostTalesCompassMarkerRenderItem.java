package com.ninuna.losttales.gui.hud.compass.marker;

public class LostTalesCompassMarkerRenderItem {
    public final LostTalesCompassMarker marker;
    public final float x;
    public final float alpha;
    public final double distSq;

    public LostTalesCompassMarkerRenderItem(LostTalesCompassMarker marker, float x, float alpha, double distSq) {
        this.marker = marker;
        this.x = x;
        this.alpha = alpha;
        this.distSq = distSq;
    }
}
