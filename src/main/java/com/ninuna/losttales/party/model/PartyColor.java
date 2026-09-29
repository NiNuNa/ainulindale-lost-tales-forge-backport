package com.ninuna.losttales.party.model;

import com.ninuna.losttales.gui.style.LostTalesColors;

/**
 * The colours party members wear, one each, in the order a joining member
 * is given the first one free: as many as a party can hold members. Each
 * carries the palette entry it is drawn in, so the HUD, the chat and the
 * Party page show one member in one colour.
 */
public enum PartyColor {
    GREEN(0, "green", LostTalesColors.MEADOW_GREEN),
    YELLOW(1, "yellow", LostTalesColors.HONEY),
    PURPLE(2, "purple", LostTalesColors.ORCHID),
    BLUE(3, "blue", LostTalesColors.SEAFOAM),
    ORANGE(4, "orange", LostTalesColors.APRICOT),
    RED(5, "red", LostTalesColors.SALMON),
    TEAL(6, "teal", LostTalesColors.TEAL),
    ROSE(7, "rose", LostTalesColors.ROSE_BEIGE);

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

    /** The colour as the compass and the map read a colour's name: #RRGGBB. */
    public String getTint() {
        return String.format("#%06X", Integer.valueOf(this.rgb));
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
