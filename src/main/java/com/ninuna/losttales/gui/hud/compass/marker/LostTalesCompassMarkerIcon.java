package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.Locale;
import net.minecraft.util.ResourceLocation;

public enum LostTalesCompassMarkerIcon {
    QUEST(0, 0),
    HOSTILE(18, 0),
    UNDISCOVERED(36, 0),
    N(0, 18),
    NE(18, 18),
    E(36, 18),
    SE(54, 18),
    S(72, 18),
    SW(90, 18),
    W(108, 18),
    NW(126, 18),
    TOWN(0, 36),
    GRAVEYARD(18, 36),
    FOREST(36, 36),
    FOUNTAIN(54, 36),
    PORT(72, 36),

    // Compatibility aliases for existing marker data and party providers.
    FORT(0, 36),
    TAVERN(0, 36),
    PARTY_PURPLE(0, 0),
    PARTY_YELLOW(0, 0),
    PARTY_BLUE(0, 0),
    PARTY_GREEN(0, 0);

    public static final ResourceLocation TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/map_markers.png");
    public static final int TEXTURE_WIDTH = 143;
    public static final int TEXTURE_HEIGHT = 53;
    public static final int WIDTH = 17;
    public static final int HEIGHT = 17;

    private final int u;
    private final int v;

    LostTalesCompassMarkerIcon(int u, int v) {
        this.u = u;
        this.v = v;
    }

    public int getU() {
        return u;
    }

    public int getV() {
        return v;
    }

    public static LostTalesCompassMarkerIcon fromName(String name) {
        if (name == null || name.length() == 0) {
            return UNDISCOVERED;
        }

        String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("NORTH".equals(normalized)) return N;
        if ("NORTH_EAST".equals(normalized) || "NORTHEAST".equals(normalized)) return NE;
        if ("EAST".equals(normalized)) return E;
        if ("SOUTH_EAST".equals(normalized) || "SOUTHEAST".equals(normalized)) return SE;
        if ("SOUTH".equals(normalized)) return S;
        if ("SOUTH_WEST".equals(normalized) || "SOUTHWEST".equals(normalized)) return SW;
        if ("WEST".equals(normalized)) return W;
        if ("NORTH_WEST".equals(normalized) || "NORTHWEST".equals(normalized)) return NW;
        if ("CITY".equals(normalized) || "SETTLEMENT".equals(normalized)) return TOWN;
        if ("GRAVE".equals(normalized) || "CEMETERY".equals(normalized)) return GRAVEYARD;
        if ("WOODS".equals(normalized)) return FOREST;
        if ("HARBOUR".equals(normalized) || "HARBOR".equals(normalized)
                || "DOCK".equals(normalized) || "DOCKS".equals(normalized)) return PORT;

        for (LostTalesCompassMarkerIcon icon : values()) {
            if (icon.name().equals(normalized)) {
                return icon;
            }
        }
        return UNDISCOVERED;
    }
}
