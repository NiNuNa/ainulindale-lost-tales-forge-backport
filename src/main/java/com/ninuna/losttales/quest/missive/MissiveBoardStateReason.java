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
    /** The slot the page read holds no notice. */
    GONE(5, "chat.losttales.missive.gone", true),
    /** Another letter stands in that slot now. */
    NOTICE_CHANGED(6, "chat.losttales.missive.changed", true),
    DAMAGED(7, "chat.losttales.missive.damaged", true),
    INVENTORY_FULL(8, "chat.losttales.missive.inventory_full", true),
    BOARD_FULL(9, "chat.losttales.missive.board_full", true),
    /** The inventory slot the page read holds no letter to pin. */
    LETTER_GONE(10, "chat.losttales.missive.letter_gone", true),
    /** The player runs the quest already; the start gate said so. */
    ALREADY_ACTIVE(11, "chat.losttales.missive.already_active", false),
    /** The player finished the quest already; the start gate said so. */
    ALREADY_COMPLETED(12, "chat.losttales.missive.already_completed", false),
    /** The start gate refused the letter's quest and said why in its own line. */
    REFUSED(13, "chat.losttales.missive.refused", false),
    /** The quest's requirements are not met; the quest's own line said which. */
    REQUIREMENTS(14, "gui.losttales.missive_board.said.requirements", false),
    /**
     * The world's record of missives taken cannot be read, or could not
     * record this one (QUEST-34); nothing else says so.
     */
    NOT_NOW(15, "chat.losttales.missive.not_now", true),
    /** The notice's time on a board is past, so its letter starts nothing. */
    EXPIRED(16, "chat.losttales.missive.expired", true),
    /** Somebody took this missive already; a copy of its letter starts nothing (QUEST-34). */
    TAKEN_BEFORE(17, "chat.losttales.missive.taken_before", true),
    /** The player's finished missives were handed in at the board (QUEST-31). */
    HANDED_IN(18, "gui.losttales.missive_board.said.handed_in", false),
    /** None of the player's missives waits for a board. */
    NOTHING_TO_HAND_IN(19, "chat.losttales.missive.nothing_to_hand_in", true),
    /** The world's record of missives taken cannot be read, so no letter goes up (QUEST-34). */
    CANNOT_PIN(20, "chat.losttales.missive.cannot_pin", true);

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
                && this != PINNED && this != HANDED_IN;
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
