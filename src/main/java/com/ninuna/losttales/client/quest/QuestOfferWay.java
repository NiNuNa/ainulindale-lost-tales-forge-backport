package com.ninuna.losttales.client.quest;

/**
 * How a giver's offer reaches the player (QUEST-41): in the conversation,
 * or as the giver's line in the chat ending on *Accept* and *Decline*. The
 * chat takes it where the conversation is switched off, and where another
 * screen is open, which stays open; nothing opens over it.
 */
public enum QuestOfferWay {
    CONVERSATION,
    CHAT;

    /** The way an offer goes, by the conversation setting and whether a screen is open. */
    public static QuestOfferWay of(boolean conversationOn, boolean screenOpen) {
        return conversationOn && !screenOpen ? CONVERSATION : CHAT;
    }
}
