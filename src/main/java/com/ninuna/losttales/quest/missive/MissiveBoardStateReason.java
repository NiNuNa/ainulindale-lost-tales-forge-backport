package com.ninuna.losttales.quest.missive;

/**
 * Why the server sends a missive board's notices: the player used the
 * board, the board changed while they stood at it, or the notices answer
 * a request from the board's page. The page opens on an opening, follows
 * a change quietly and says every answer in its status line; a refusal
 * is said in the chat as well, in the same words, unless the quest has
 * already said why in its own.
 */
public enum MissiveBoardStateReason {
    /** The player used the board: the page opens on it. */
    OPENED(0, "", false),
    /** Somebody else took, pinned or accepted a notice, or the board posted or took one down. */
    CHANGED(1, "", false),
    ACCEPTED(2, "gui.losttales.missive_board.said.accepted", false),
    TAKEN(3, "gui.losttales.missive_board.said.taken", false),
    PINNED(4, "gui.losttales.missive_board.said.pinned", false),
    /** The notice is no longer in the slot the page read it in. */
    GONE(5, "chat.losttales.missive.gone", true),
    /** Another letter stands in that slot now. */
    NOTICE_CHANGED(6, "chat.losttales.missive.changed", true),
    DAMAGED(7, "chat.losttales.missive.damaged", true),
    INVENTORY_FULL(8, "chat.losttales.missive.inventory_full", true),
    BOARD_FULL(9, "chat.losttales.missive.board_full", true),
    /** The letter to pin is no longer in the inventory slot the page read it in. */
    LETTER_GONE(10, "chat.losttales.missive.letter_gone", true),
    ALREADY_ACTIVE(11, "chat.losttales.missive.already_active", true),
    ALREADY_COMPLETED(12, "chat.losttales.missive.already_completed", true),
    /** No quest could be made of the letter. */
    REFUSED(13, "chat.losttales.missive.refused", true),
    /** The quest's requirements are not met; the quest's own line said which. */
    REQUIREMENTS(14, "gui.losttales.missive_board.said.requirements", false),
    /** Any other reason the quest would not start; the quest's own line says which. */
    NOT_NOW(15, "chat.losttales.missive.not_now", true);

    private final int networkId;
    private final String messageKey;
    private final boolean saidInChat;

    MissiveBoardStateReason(int networkId, String messageKey,
                            boolean saidInChat) {
        this.networkId = networkId;
        this.messageKey = messageKey;
        this.saidInChat = saidInChat;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    /** The lang key of the words that say it; empty for an opening or a change. */
    public String getMessageKey() {
        return this.messageKey;
    }

    /** Whether the server says it in the chat too: a refusal the quest has not said itself. */
    public boolean isSaidInChat() {
        return this.saidInChat;
    }

    /** Whether it answers a request of this player's page. */
    public boolean isAnswer() {
        return this != OPENED && this != CHANGED;
    }

    /** Whether the request it answers was turned down. */
    public boolean isRefusal() {
        return isAnswer() && this != ACCEPTED && this != TAKEN
                && this != PINNED;
    }

    /** The reason with this id; null for an id no reason has. */
    public static MissiveBoardStateReason fromNetworkId(int id) {
        for (MissiveBoardStateReason reason : values()) {
            if (reason.networkId == id) {
                return reason;
            }
        }
        return null;
    }
}
