package com.ninuna.losttales.party.sync;

/** Server-authoritative runtime availability of one party character. */
public enum PartyMemberAvailability {
    OFFLINE(0),
    INACTIVE_CHARACTER(1),
    UNAVAILABLE(2),
    ACTIVE(3),
    DEAD(4);

    private final int networkId;

    PartyMemberAvailability(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public boolean hasLiveEntityData() {
        return this == ACTIVE || this == DEAD;
    }

    public static PartyMemberAvailability fromNetworkId(int networkId) {
        for (PartyMemberAvailability value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return null;
    }
}
