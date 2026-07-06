package com.ninuna.losttales.mapmarker;

/** Server-safe metadata for bundled/static map markers. */
public final class LostTalesMapMarkerDefinition {
    private final String id;
    private final String name;
    private final String iconName;
    private final String colorName;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final boolean hiddenUntilDiscovered;

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, boolean hiddenUntilDiscovered) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.colorName = colorName;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hiddenUntilDiscovered = hiddenUntilDiscovered;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIconName() {
        return iconName;
    }

    public String getColorName() {
        return colorName;
    }

    public int getDimensionId() {
        return dimensionId;
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

    public boolean isHiddenUntilDiscovered() {
        return hiddenUntilDiscovered;
    }

    public String getShortDescription() {
        return id + " (" + name + ", dim " + dimensionId + " @ " + format(x) + ", " + format(y) + ", " + format(z) + ")";
    }

    private static String format(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01D ? String.valueOf(rounded) : String.valueOf(value);
    }
}
