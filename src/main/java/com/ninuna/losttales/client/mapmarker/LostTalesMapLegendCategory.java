package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Immutable presentation metadata for one map-only legend filter. */
@SideOnly(Side.CLIENT)
public final class LostTalesMapLegendCategory {
    private final String id;
    private final String translationKey;
    private final LostTalesCompassMarkerIcon icon;
    private final String colorName;

    public LostTalesMapLegendCategory(
            String id, String translationKey,
            LostTalesCompassMarkerIcon icon, String colorName) {
        this.id = id == null ? "" : id;
        this.translationKey = translationKey == null
                || translationKey.length() == 0
                ? this.id : translationKey;
        this.icon = icon == null
                ? LostTalesCompassMarkerIcon.UNDISCOVERED : icon;
        this.colorName = colorName == null || colorName.length() == 0
                ? "white" : colorName;
    }

    public String getId() {
        return this.id;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public LostTalesCompassMarkerIcon getIcon() {
        return this.icon;
    }

    public String getColorName() {
        return this.colorName;
    }
}
