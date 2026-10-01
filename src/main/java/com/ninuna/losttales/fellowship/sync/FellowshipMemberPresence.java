package com.ninuna.losttales.fellowship.sync;

/**
 * Where a member stands, as the Fellowship page shows it: away (their
 * account is offline), here (online as the character who is the member), or
 * elsewhere (online as another of their characters).
 */
public enum FellowshipMemberPresence {
    AWAY(0),
    HERE(1),
    ELSEWHERE(2);

    private final int networkId;

    FellowshipMemberPresence(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    /** The presence a network id names; null for one that names none. */
    public static FellowshipMemberPresence fromNetworkId(int networkId) {
        for (FellowshipMemberPresence value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return null;
    }
}
