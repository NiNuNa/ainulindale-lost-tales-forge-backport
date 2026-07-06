package com.ninuna.losttales.gui.hud.compass.marker;

public class LostTalesCompassMarkerRenderItem {
    public final LostTalesCompassMarker marker;
    public final float x;
    public final float alpha;
    public final double distSq;
    public final double dy;
    public final float emphasis;

    public LostTalesCompassMarkerRenderItem(LostTalesCompassMarker marker, float x, float alpha, double distSq, double dy, float emphasis) {
        this.marker = marker;
        this.x = x;
        this.alpha = alpha;
        this.distSq = distSq;
        this.dy = dy;
        this.emphasis = emphasis;
    }
}
