package com.ninuna.losttales.character.sync;

/** Stable network identifiers for character-management operations. */
public enum CharacterOperationType {
    REQUEST_ROSTER(0, "request_roster"),
    CREATE(1, "create"),
    SELECT(2, "select"),
    DELETE(3, "delete"),
    CAPE_UPDATE(4, "cape_update"),
    UNKNOWN(255, "unknown");

    private final int networkId;
    private final String id;

    CharacterOperationType(int networkId, String id) {
        this.networkId = networkId;
        this.id = id;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public String getId() {
        return this.id;
    }

    public static CharacterOperationType fromNetworkId(int networkId) {
        for (CharacterOperationType value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
