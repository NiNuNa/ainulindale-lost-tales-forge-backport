package com.ninuna.losttales.fellowship.model;

/** The two kinds of mark on the map: one character's go-here, and a fellowship's. */
public enum FellowshipMarkKind {
    /** Where one character asks the others to go; one a character, no name. */
    GO_HERE(0, "go_here"),
    /** A place a fellowship's leader or a guide marked for every member, named. */
    FELLOWSHIP(1, "fellowship");

    private final int networkId;
    private final String id;

    FellowshipMarkKind(int networkId, String id) {
        this.networkId = networkId;
        this.id = id;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    /** The word saves know the kind by. */
    public String getId() {
        return this.id;
    }

    public static FellowshipMarkKind fromNetworkId(int networkId) {
        for (FellowshipMarkKind kind : values()) {
            if (kind.networkId == networkId) {
                return kind;
            }
        }
        return null;
    }

    public static FellowshipMarkKind fromId(String id) {
        for (FellowshipMarkKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
