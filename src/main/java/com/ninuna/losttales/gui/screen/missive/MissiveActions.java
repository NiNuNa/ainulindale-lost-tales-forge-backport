package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.quest.missive.MissiveNotice;

/**
 * Why each action on a missive page's bar cannot be taken now, as the
 * lang key of the words its greyed button says; empty where it can. The
 * page only greys what it can see from the client; the server checks
 * every request again. Free of Minecraft, so a test can ask it every
 * case.
 */
final class MissiveActions {
    /** Where the reasons live in the lang file. */
    static final String WHY = "gui.losttales.missive.why.";

    private MissiveActions() {}

    /**
     * Accept on the board: a notice picked, one that can be read, whose
     * quest the player is not already on, and no answer awaited.
     */
    static String whyNotAccept(MissiveNotice picked, boolean alreadyActive,
                               boolean waiting) {
        if (picked == null) {
            return WHY + "pick";
        }
        return whyNotAcceptLetter(picked.isReadable(), alreadyActive, waiting);
    }

    /** Take Letter: a notice picked, room in the inventory, and no answer awaited. */
    static String whyNotTake(MissiveNotice picked, boolean inventoryFull,
                             boolean waiting) {
        if (picked == null) {
            return WHY + "pick";
        }
        if (inventoryFull) {
            return WHY + "inventory_full";
        }
        return waiting ? WHY + "waiting" : "";
    }

    /**
     * Pin Letter: a missive letter carried, room on the board — fewer
     * notices than it posts, and a slot free — and no answer awaited.
     */
    static String whyNotPin(int lettersCarried, int notices, int maxNotices,
                            int slots, boolean waiting) {
        if (lettersCarried <= 0) {
            return WHY + "no_letters";
        }
        if (notices >= Math.min(maxNotices, slots)) {
            return WHY + "board_full";
        }
        return waiting ? WHY + "waiting" : "";
    }

    /** Accept on a letter: one that can be read, a quest the player is not on, and no answer awaited. */
    static String whyNotAcceptLetter(boolean readable, boolean alreadyActive,
                                     boolean waiting) {
        if (!readable) {
            return WHY + "unreadable";
        }
        if (alreadyActive) {
            return WHY + "already_active";
        }
        return waiting ? WHY + "waiting" : "";
    }

    /**
     * Whether the inventory slot a letter's page was opened on still holds
     * that letter: {@code questIdNow} is the quest id of the letter there
     * now, empty for one that cannot be read and null for no letter at
     * all. A letter that could not be read stays open while one that
     * cannot be read is still there.
     */
    static boolean stillHolds(String openedQuestId, String questIdNow) {
        return questIdNow != null && openedQuestId != null
                && openedQuestId.equals(questIdNow);
    }

    /** The lang keys of the server's lines about a missive. */
    static final String MISSIVE_LINES = "chat.losttales.missive.";
    /** The lang keys of a quest's lines about a requirement not met. */
    static final String REQUIREMENT_LINES = "chat.losttales.quest.requires.";

    /**
     * Whether a line the server says in the chat, by its lang key, answers
     * a request of a missive page: its own lines about a missive, and a
     * quest's about a requirement an Accept does not meet.
     */
    static boolean answersRequest(String key) {
        return key != null && (key.startsWith(MISSIVE_LINES)
                || saysRequirement(key));
    }

    /** Whether the line is a quest's about a requirement not met. */
    static boolean saysRequirement(String key) {
        return key != null && key.startsWith(REQUIREMENT_LINES);
    }
}
