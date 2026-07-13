package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.Locale;
import net.minecraft.util.ResourceLocation;

public enum LostTalesCompassMarkerIcon {
    N(0, 19),
    NE(19, 19),
    E(38, 19),
    SE(57, 19),
    S(76, 19),
    SW(95, 19),
    W(114, 19),
    NW(133, 19),
    QUEST(0, 0),
    HOSTILE(19, 0),
    FORT(0, 38),
    UNDISCOVERED(38, 0),
    TAVERN(19, 38),
    PARTY_PURPLE(133, 0),
    PARTY_YELLOW(152, 0),
    PARTY_BLUE(171, 0),
    PARTY_GREEN(190, 0);

    public static final ResourceLocation TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/map_marker_icons.png");
    public static final int TEXTURE_WIDTH = 207;
    public static final int TEXTURE_HEIGHT = 64;
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

        for (LostTalesCompassMarkerIcon icon : values()) {
            if (icon.name().equals(normalized)) {
                return icon;
            }
        }
        return UNDISCOVERED;
    }
}

