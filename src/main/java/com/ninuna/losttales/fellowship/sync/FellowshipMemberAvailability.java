package com.ninuna.losttales.fellowship.sync;

/** Server-authoritative runtime availability of one fellowship member. */
public enum FellowshipMemberAvailability {
    OFFLINE(0),
    INACTIVE_CHARACTER(1),
    UNAVAILABLE(2),
    ACTIVE(3),
    DEAD(4);

    private final int networkId;

    FellowshipMemberAvailability(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public boolean hasLiveEntityData() {
        return this == ACTIVE || this == DEAD;
    }

    public static FellowshipMemberAvailability fromNetworkId(int networkId) {
        for (FellowshipMemberAvailability value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return null;
    }
}
