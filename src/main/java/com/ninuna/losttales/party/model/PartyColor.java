package com.ninuna.losttales.party.model;

import com.ninuna.losttales.gui.style.LostTalesColors;

/**
 * Server-validated party indicator colors in deterministic assignment
 * order. Each carries the palette entry it is drawn in, so the HUD, the
 * chat and the party screens show one member in one colour.
 */
public enum PartyColor {
    GREEN(0, "green", LostTalesColors.MEADOW_GREEN),
    YELLOW(1, "yellow", LostTalesColors.HONEY),
    PURPLE(2, "purple", LostTalesColors.ORCHID),
    BLUE(3, "blue", LostTalesColors.SEAFOAM);

    private final int networkId;
    private final String id;
    private final int rgb;

    PartyColor(int networkId, String id, int paletteEntry) {
        this.networkId = networkId;
        this.id = id;
        this.rgb = LostTalesColors.rgb(paletteEntry);
    }

    /** What this colour is drawn in, without an alpha of its own. */
    public int getRgb() {
        return this.rgb;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public String getId() {
        return this.id;
    }

    public static PartyColor fromNetworkId(int networkId) {
        for (PartyColor color : values()) {
            if (color.networkId == networkId) {
                return color;
            }
        }
        return null;
    }

    public static PartyColor fromId(String id) {
        if (id != null) {
            for (PartyColor color : values()) {
                if (color.id.equalsIgnoreCase(id.trim())) {
                    return color;
                }
            }
        }
        return null;
    }
}
