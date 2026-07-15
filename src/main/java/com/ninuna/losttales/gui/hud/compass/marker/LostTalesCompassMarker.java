package com.ninuna.losttales.gui.hud.compass.marker;

import java.util.Locale;

public class LostTalesCompassMarker {
    private final String stateKey;
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
    private final boolean retainBeyondFadeRadius;
    private final boolean forceFullOpacity;
    private final float red;
    private final float green;
    private final float blue;

    private LostTalesCompassMarker(String stateKey, String name, LostTalesCompassMarkerIcon icon, boolean bearingMarker, float bearingDegrees, double x, double y, double z, boolean scaleWithCenterFocus, boolean showDistanceLabel, double fadeInRadius, boolean activeQuestMarker, boolean retainBeyondFadeRadius, boolean forceFullOpacity, float red, float green, float blue) {
        this.stateKey = stateKey;
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
        this.retainBeyondFadeRadius = retainBeyondFadeRadius;
        this.forceFullOpacity = forceFullOpacity;
        this.red = clampColor(red);
        this.green = clampColor(green);
        this.blue = clampColor(blue);
    }

    public static LostTalesCompassMarker bearing(String name, LostTalesCompassMarkerIcon icon, float bearingDegrees) {
        return new LostTalesCompassMarker(null, name, icon, true, bearingDegrees, 0.0D, 0.0D, 0.0D, false, false, 0.0D, false, false, false, 1.0F, 1.0F, 1.0F);
    }

    public static LostTalesCompassMarker position(String name, LostTalesCompassMarkerIcon icon, double x, double y, double z, boolean scaleWithCenterFocus, boolean showDistanceLabel, double fadeInRadius) {
        return position(name, icon, x, y, z, scaleWithCenterFocus, showDistanceLabel, fadeInRadius, "white");
    }

    public static LostTalesCompassMarker position(String name, LostTalesCompassMarkerIcon icon, double x, double y, double z, boolean scaleWithCenterFocus, boolean showDistanceLabel, double fadeInRadius, String colorName) {
        float[] color = parseColor(colorName);
        return new LostTalesCompassMarker(null, name, icon, false, 0.0F, x, y, z, scaleWithCenterFocus, showDistanceLabel, fadeInRadius, false, false, false, color[0], color[1], color[2]);
    }

    public static LostTalesCompassMarker questPosition(String name, double x, double y, double z, boolean showDistanceLabel, double fadeInRadius) {
        return new LostTalesCompassMarker(null, name, LostTalesCompassMarkerIcon.QUEST, false, 0.0F, x, y, z, true, showDistanceLabel, fadeInRadius, true, false, false, 1.0F, 1.0F, 1.0F);
    }

    public static LostTalesCompassMarker positionWithStateKey(String stateKey, String name, LostTalesCompassMarkerIcon icon, double x, double y, double z, boolean scaleWithCenterFocus, boolean showDistanceLabel, double fadeInRadius) {
        return positionWithStateKey(stateKey, name, icon, x, y, z,
                scaleWithCenterFocus, showDistanceLabel, fadeInRadius, "white");
    }

    public static LostTalesCompassMarker positionWithStateKey(String stateKey, String name, LostTalesCompassMarkerIcon icon, double x, double y, double z, boolean scaleWithCenterFocus, boolean showDistanceLabel, double fadeInRadius, String colorName) {
        float[] color = parseColor(colorName);
        return new LostTalesCompassMarker(stateKey, name, icon, false, 0.0F,
                x, y, z, scaleWithCenterFocus, showDistanceLabel,
                fadeInRadius, false, false, false,
                color[0], color[1], color[2]);
    }

    public static LostTalesCompassMarker persistentPositionWithStateKey(
            String stateKey, String name, LostTalesCompassMarkerIcon icon,
            double x, double y, double z, boolean scaleWithCenterFocus,
            boolean showDistanceLabel, double fadeInRadius, String colorName) {
        float[] color = parseColor(colorName);
        return new LostTalesCompassMarker(stateKey, name, icon, false, 0.0F,
                x, y, z, scaleWithCenterFocus, showDistanceLabel,
                fadeInRadius, false, true, false,
                color[0], color[1], color[2]);
    }

    /** A directional marker that is clamped on-screen and never fades. */
    public static LostTalesCompassMarker alwaysVisiblePositionWithStateKey(
            String stateKey, String name, LostTalesCompassMarkerIcon icon,
            double x, double y, double z, boolean scaleWithCenterFocus,
            boolean showDistanceLabel, String colorName) {
        float[] color = parseColor(colorName);
        return new LostTalesCompassMarker(stateKey, name, icon, false, 0.0F,
                x, y, z, scaleWithCenterFocus, showDistanceLabel,
                0.0D, false, true, true,
                color[0], color[1], color[2]);
    }

    public String getStateKey() {
        return this.stateKey;
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

    public boolean isRetainedBeyondFadeRadius() {
        return this.retainBeyondFadeRadius;
    }

    public boolean isForceFullOpacity() {
        return this.forceFullOpacity;
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    private static float clampColor(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    public static float[] parseColor(String colorName) {
        if (colorName == null || colorName.trim().length() == 0) {
            return new float[] {1.0F, 1.0F, 1.0F};
        }

        String value = colorName.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (value.startsWith("#")) {
            return parseHexColor(value.substring(1));
        }
        if (value.startsWith("0x")) {
            return parseHexColor(value.substring(2));
        }
        if ("white".equals(value)) return new float[] {1.0F, 1.0F, 1.0F};
        if ("red".equals(value)) return new float[] {1.0F, 0.25F, 0.25F};
        if ("green".equals(value)) return new float[] {0.35F, 1.0F, 0.35F};
        if ("blue".equals(value)) return new float[] {0.35F, 0.55F, 1.0F};
        if ("yellow".equals(value) || "gold".equals(value)) return new float[] {1.0F, 0.85F, 0.25F};
        if ("orange".equals(value)) return new float[] {1.0F, 0.55F, 0.2F};
        if ("purple".equals(value) || "violet".equals(value)) return new float[] {0.75F, 0.45F, 1.0F};
        if ("gray".equals(value) || "grey".equals(value)) return new float[] {0.65F, 0.65F, 0.65F};
        if ("dark_gray".equals(value) || "dark_grey".equals(value)) return new float[] {0.35F, 0.35F, 0.35F};
        if ("black".equals(value)) return new float[] {0.15F, 0.15F, 0.15F};
        return new float[] {1.0F, 1.0F, 1.0F};
    }

    private static float[] parseHexColor(String hex) {
        if (hex == null || hex.length() != 6) {
            return new float[] {1.0F, 1.0F, 1.0F};
        }
        try {
            int value = Integer.parseInt(hex, 16);
            float red = ((value >> 16) & 255) / 255.0F;
            float green = ((value >> 8) & 255) / 255.0F;
            float blue = (value & 255) / 255.0F;
            return new float[] {red, green, blue};
        } catch (NumberFormatException ignored) {
            return new float[] {1.0F, 1.0F, 1.0F};
        }
    }
}
