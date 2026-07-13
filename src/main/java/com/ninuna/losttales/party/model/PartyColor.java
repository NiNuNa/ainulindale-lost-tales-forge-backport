package com.ninuna.losttales.party.model;

/** Server-validated party indicator colors in deterministic assignment order. */
public enum PartyColor {
    GREEN(0, "green"),
    YELLOW(1, "yellow"),
    PURPLE(2, "purple"),
    BLUE(3, "blue");

    private final int networkId;
    private final String id;

    PartyColor(int networkId, String id) {
        this.networkId = networkId;
        this.id = id;
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
