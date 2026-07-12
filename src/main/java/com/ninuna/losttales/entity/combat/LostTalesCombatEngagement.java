package com.ninuna.losttales.entity.combat;

/**
 * Player-specific combat evidence synchronized for transient enemy markers.
 */
public enum LostTalesCombatEngagement {
    NONE(0),
    TARGETING(1),
    ATTACKING(2),
    RECENTLY_ENGAGED(3);

    private final int networkId;

    LostTalesCombatEngagement(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public static LostTalesCombatEngagement fromNetworkId(int networkId) {
        for (LostTalesCombatEngagement value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return NONE;
    }
}
