package com.ninuna.losttales.mapmarker;

public enum LostTalesWaystoneSettingsOperation {
    SAVE(0),
    SHARE_PLAYER(1),
    UNSHARE_PLAYER(2),
    SHARE_FELLOWSHIP(3),
    UNSHARE_FELLOWSHIP(4);

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
