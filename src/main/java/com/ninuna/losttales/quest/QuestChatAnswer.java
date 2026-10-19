package com.ninuna.losttales.quest;

/**
 * An answer to a quest giver clicked in the chat, where the quest
 * conversation is switched off (QUEST-37): *Accept* or *Decline* on an
 * offer, or the item taken from a hand-in's pick. The answer rides in the
 * click of the word that says it; the client reads it back and asks the
 * server as the conversation's replies do, and the server checks it all
 * again, so a forged answer does nothing.
 */
public final class QuestChatAnswer {
    private static final String PREFIX = "losttales-quest:";
    public static final String ACCEPT = "accept";
    public static final String DECLINE = "decline";
    public static final String HAND_IN = "hand_in";
    /** A stage's choice made in the chat (QUEST-32). */
    public static final String CHOOSE = "choose";

    public final String action;
    /** The item taken from a pick; -1 for none. */
    public final int choice;
    public final String questId;

    private QuestChatAnswer(String action, int choice, String questId) {
        this.action = action;
        this.choice = choice;
        this.questId = questId;
    }

    /** The click value that carries the answer. */
    public static String encode(String action, int choice, String questId) {
        return PREFIX + action + ":" + choice + ":" + questId;
    }

    /** The answer a click value carries, or null for any other value. */
    public static QuestChatAnswer parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        String[] fields = value.substring(PREFIX.length()).split(":", 3);
        if (fields.length != 3 || !(ACCEPT.equals(fields[0])
                || DECLINE.equals(fields[0]) || HAND_IN.equals(fields[0])
                || CHOOSE.equals(fields[0]))) {
            return null;
        }
        int choice;
        try {
            choice = Integer.parseInt(fields[1]);
        } catch (NumberFormatException notANumber) {
            return null;
        }
        String questId = fields[2].trim();
        if (choice < -1 || choice >= LostTalesQuestRewards.MAX_PICKS
                || !LostTalesQuestIds.fits(questId) || questId.length() == 0) {
            return null;
        }
        return new QuestChatAnswer(fields[0], choice, questId);
    }
}
