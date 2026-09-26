package com.ninuna.losttales.mapmarker;

/**
 * Why the server sends a waystone's state: the player used the waystone,
 * or the state answers a request from the waystone's page. The page opens
 * on an opening and says every answer in its status line; the server
 * says a refusal in the chat as well. Each answer's words are its chat
 * line's.
 */
public enum LostTalesWaystoneStateReason {
    /** The player used the waystone: the page opens on it. */
    OPENED(0, ""),
    SAVED(1, "chat.losttales.waystone.saved"),
    /** The waystone changed since the page read it: what stands now comes back instead. */
    STALE(2, "chat.losttales.waystone.stale"),
    DENIED(3, "chat.losttales.waystone.denied"),
    INVALID_SETTINGS(4, "chat.losttales.waystone.invalid_settings"),
    PUBLIC_DENIED(5, "chat.losttales.waystone.public_denied"),
    PLAYER_NOT_FOUND(6, "chat.losttales.waystone.player_not_found"),
    INVALID_SHARE(7, "chat.losttales.waystone.invalid_share"),
    SHARE_LIMIT(8, "chat.losttales.waystone.share_limit"),
    FELLOWSHIP_NOT_FOUND(9,
            "chat.losttales.waystone.fellowship_not_found"),
    SAVE_FAILED(10, "chat.losttales.waystone.save_failed");

    private final int networkId;
    private final String messageKey;

    LostTalesWaystoneStateReason(int networkId, String messageKey) {
        this.networkId = networkId;
        this.messageKey = messageKey;
    }

    public int getNetworkId() { return this.networkId; }

    /** The lang key of the words that say it; empty for an opening. */
    public String getMessageKey() { return this.messageKey; }

    /** Whether the request it answers was turned down. */
    public boolean isRefusal() {
        return this != OPENED && this != SAVED;
    }

    /** The reason with this id; null for an id no reason has. */
    public static LostTalesWaystoneStateReason fromNetworkId(int id) {
        for (LostTalesWaystoneStateReason reason : values()) {
            if (reason.networkId == id) {
                return reason;
            }
        }
        return null;
    }
}
