package com.ninuna.losttales.gui.hud.compass.marker;

public class LostTalesCompassMarker {
    private final String name;
    private final LostTalesCompassMarkerIcon icon;
    private final boolean bearingMarker;
    private final float bearingDegrees;
    private final double x;
    private final double y;
    private final double z;
    private final boolean scaleWithCenterFocus;
    private final boolean showDistanceLabel;
    private final double fadeInRadius;
    private final boolean activeQuestMarker;

    private LostTalesCompassMarker(String name, LostTalesCompassMarkerIcon icon, boolean bearingMarker, float bearingDegrees, double x, double y, double z, boolean scaleWithCenterFocus, boolean showDistanceLabel, double fadeInRadius, boolean activeQuestMarker) {
        this.name = name;
        this.icon = icon;
        this.bearingMarker = bearingMarker;
        this.bearingDegrees = bearingDegrees;
        this.x = x;
        this.y = y;
        this.z = z;
        this.scaleWithCenterFocus = scaleWithCenterFocus;
        this.showDistanceLabel = showDistanceLabel;
        this.fadeInRadius = fadeInRadius;
        this.activeQuestMarker = activeQuestMarker;
    }

    public static LostTalesCompassMarker bearing(String name, LostTalesCompassMarkerIcon icon, float bearingDegrees) {
        return new LostTalesCompassMarker(name, icon, true, bearingDegrees, 0.0D, 0.0D, 0.0D, false, false, 0.0D, false);
    }

    public static LostTalesCompassMarker position(String name, LostTalesCompassMarkerIcon icon, double x, double y, double z, boolean scaleWithCenterFocus, boolean showDistanceLabel, double fadeInRadius) {
        return new LostTalesCompassMarker(name, icon, false, 0.0F, x, y, z, scaleWithCenterFocus, showDistanceLabel, fadeInRadius, false);
    }

    public static LostTalesCompassMarker questPosition(String name, double x, double y, double z, boolean showDistanceLabel, double fadeInRadius) {
        return new LostTalesCompassMarker(name, LostTalesCompassMarkerIcon.QUEST, false, 0.0F, x, y, z, true, showDistanceLabel, fadeInRadius, true);
    }

    public String getName() {
        return name;
    }

    public LostTalesCompassMarkerIcon getIcon() {
        return icon;
    }

    public boolean isBearingMarker() {
        return bearingMarker;
    }

    public float getBearingDegrees() {
        return bearingDegrees;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean isScaleWithCenterFocus() {
        return scaleWithCenterFocus;
    }

    public boolean isShowDistanceLabel() {
        return showDistanceLabel;
    }

    public double getFadeInRadius() {
        return fadeInRadius;
    }

    public boolean isActiveQuestMarker() {
        return activeQuestMarker;
    }
}
