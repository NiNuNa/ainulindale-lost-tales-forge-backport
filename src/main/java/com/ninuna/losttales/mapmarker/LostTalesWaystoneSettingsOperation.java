package com.ninuna.losttales.mapmarker;

/** What a request from a waystone's page asks for. */
public enum LostTalesWaystoneSettingsOperation {
    SAVE(0),
    SHARE_CHARACTER(1),
    UNSHARE_CHARACTER(2),
    SHARE_FELLOWSHIP(3),
    UNSHARE_FELLOWSHIP(4),
    /** A manager makes the character named the waystone's owner (WAY-08). */
    HAND_OVER(5),
    /**
     * Destinations pressed: the server opens the window in which the next
     * place picked on the map is travelled to from the waystone.
     */
    DESTINATIONS(6);

    private final int networkId;

    LostTalesWaystoneSettingsOperation(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() { return this.networkId; }

    public static LostTalesWaystoneSettingsOperation fromNetworkId(int id) {
        for (LostTalesWaystoneSettingsOperation operation : values()) {
            if (operation.networkId == id) {
                return operation;
            }
        }
        return null;
    }
}
