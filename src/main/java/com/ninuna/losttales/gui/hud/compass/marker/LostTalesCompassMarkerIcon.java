package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.Locale;
import net.minecraft.util.ResourceLocation;

public enum LostTalesCompassMarkerIcon {
    QUEST(0, 0, 4, 13, 2, 15),
    HOSTILE(18, 0, 5, 12, 5, 12),
    UNDISCOVERED(36, 0, 4, 13, 2, 15),
    POINT_OF_INTEREST(54, 0, 4, 13, 2, 15),
    PERSONAL(72, 0, 4, 13, 2, 15),
    N(0, 18, 6, 11, 6, 11),
    NE(18, 18, 4, 13, 6, 11),
    E(36, 18, 7, 10, 6, 11),
    SE(54, 18, 5, 12, 6, 11),
    S(72, 18, 7, 10, 6, 11),
    SW(90, 18, 4, 13, 6, 11),
    W(108, 18, 6, 11, 6, 11),
    NW(126, 18, 3, 14, 6, 11),
    SHACK(0, 36, 2, 15, 3, 15),
    GRAVEYARD(18, 36, 3, 14, 3, 14),
    FOREST(36, 36, 3, 14, 1, 16),
    MOUNTAINS(54, 36, 1, 16, 2, 16),
    PORT(72, 36, 3, 14, 3, 15),
    BIG_PORT(90, 36, 3, 14, 2, 16),
    BRIDGE(108, 36, 1, 16, 3, 14),
    SMALL_BRIDGE(126, 36, 1, 16, 7, 14),
    CAMP(144, 36, 3, 14, 2, 15),

    // Compatibility aliases. Saved markers and waystones store the icon name,
    // so retired names must keep resolving rather than falling back to the
    // question mark.
    TOWN(0, 36, 2, 15, 3, 15),
    FORT(0, 36, 2, 15, 3, 15),
    TAVERN(0, 36, 2, 15, 3, 15),
    /** Retired from the palette; its cell now holds the mountains glyph. */
    FOUNTAIN(54, 36, 1, 16, 2, 16),
    PARTY_PURPLE(72, 0, 4, 13, 2, 15),
    PARTY_YELLOW(72, 0, 4, 13, 2, 15),
    PARTY_BLUE(72, 0, 4, 13, 2, 15),
    PARTY_GREEN(72, 0, 4, 13, 2, 15);

    public static final ResourceLocation TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/map_markers.png");
    public static final int TEXTURE_WIDTH = 197;
    public static final int TEXTURE_HEIGHT = 71;
    public static final int WIDTH = 17;
    public static final int HEIGHT = 17;

    /**
     * Every glyph is drawn symmetrically about the middle of its 17-pixel
     * cell. Markers, their labels and their mouse targets all anchor here, so
     * a redrawn atlas that shifts a glyph sideways must move this with it -
     * {@code LostTalesCompassMarkerIconArtBoundsTest} enforces the match.
     */
    public static final float ART_CENTER_X = 8.5F;

    private final int u;
    private final int v;
    private final int artLeft;
    private final int artRight;
    private final int artTop;
    private final int artBottom;

    /**
     * @param artLeft   first opaque column in the cell
     * @param artRight  one past the last opaque column
     * @param artTop    first opaque row in the cell
     * @param artBottom one past the last opaque row
     */
    LostTalesCompassMarkerIcon(int u, int v,
                               int artLeft, int artRight,
                               int artTop, int artBottom) {
        this.u = u;
        this.v = v;
        this.artLeft = artLeft;
        this.artRight = artRight;
        this.artTop = artTop;
        this.artBottom = artBottom;
    }

    public int getU() {
        return u;
    }

    public int getV() {
        return v;
    }

    /**
     * Opaque artwork bounds inside the {@link #WIDTH}x{@link #HEIGHT} cell, as
     * continuous edges rather than pixel indices.
     *
     * <p>Callers that anchor, hit-test, or label a marker must use these
     * instead of the cell: the cell carries several pixels of transparent
     * margin, most of it on the right, so treating it as the icon puts the
     * artwork off its coordinate and makes the mouse target far too wide.</p>
     */
    public float getArtLeft() {
        return artLeft;
    }

    public float getArtRight() {
        return artRight;
    }

    public float getArtTop() {
        return artTop;
    }

    public float getArtBottom() {
        return artBottom;
    }

    public float getArtCenterX() {
        return (artLeft + artRight) * 0.5F;
    }

    public float getArtCenterY() {
        return (artTop + artBottom) * 0.5F;
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
        if ("CITY".equals(normalized) || "SETTLEMENT".equals(normalized)
                || "HOUSE".equals(normalized) || "HUT".equals(normalized)) return SHACK;
        if ("GRAVE".equals(normalized) || "CEMETERY".equals(normalized)) return GRAVEYARD;
        if ("WOODS".equals(normalized)) return FOREST;
        if ("MOUNTAIN".equals(normalized) || "PEAK".equals(normalized)
                || "HILLS".equals(normalized)) return MOUNTAINS;
        if ("HARBOUR".equals(normalized) || "HARBOR".equals(normalized)
                || "DOCK".equals(normalized) || "DOCKS".equals(normalized)) return PORT;
        if ("LARGE_PORT".equals(normalized)
                || "HARBOUR_LARGE".equals(normalized)) return BIG_PORT;
        if ("POI".equals(normalized)
                || "INTEREST".equals(normalized)) return POINT_OF_INTEREST;
        if ("PLAYER".equals(normalized) || "CUSTOM".equals(normalized)
                || "WAYPOINT".equals(normalized)) return PERSONAL;
        if ("TENT".equals(normalized) || "ENCAMPMENT".equals(normalized)) return CAMP;

        for (LostTalesCompassMarkerIcon icon : values()) {
            if (icon.name().equals(normalized)) {
                return icon;
            }
        }
        return UNDISCOVERED;
    }
}
