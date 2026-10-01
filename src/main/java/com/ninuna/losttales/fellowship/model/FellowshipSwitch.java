package com.ninuna.losttales.fellowship.model;

import java.util.Locale;

/**
 * The three things a fellowship can switch on for its members, as LOTR's
 * fellowships can, all on for a new one: members cannot hurt each other,
 * nor each other's hired units, and they show to each other on the map even
 * while hiding. LOTR's fellowship behind each of ours carries them out.
 */
public enum FellowshipSwitch {
    /** Members cannot hurt each other. Leader and guides set it. */
    NO_FIGHTING(0, "no_fighting", false),
    /** Members cannot hurt each other's hired units. Leader and guides set it. */
    NO_HIRED_HARM(1, "no_hired_harm", false),
    /** Members show to each other on the map even while hiding. The leader sets it. */
    SHOWN_ON_MAP(2, "shown_on_map", true);

    private final int networkId;
    private final String id;
    private final boolean leaderOnly;

    FellowshipSwitch(int networkId, String id, boolean leaderOnly) {
        this.networkId = networkId;
        this.id = id;
        this.leaderOnly = leaderOnly;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    /** The word saves and lang keys know the switch by. */
    public String getId() {
        return this.id;
    }

    /** Whether only the leader sets it; the guides set the others too. */
    public boolean isLeaderOnly() {
        return this.leaderOnly;
    }

    public static FellowshipSwitch fromNetworkId(int networkId) {
        for (FellowshipSwitch value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return null;
    }

    public static FellowshipSwitch fromId(String id) {
        String word = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (FellowshipSwitch value : values()) {
            if (value.id.equals(word)) {
                return value;
            }
        }
        return null;
    }
}
