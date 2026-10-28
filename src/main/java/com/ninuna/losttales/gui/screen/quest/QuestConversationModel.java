package com.ninuna.losttales.gui.screen.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What is being said, and what the player may say back.
 *
 * <p>One conversation, whichever mod owns the quest: a Lost Tales quest
 * speaks the lines its JSON wrote, a Middle-earth quest the ones LOTR
 * already holds in its speech banks. The screen asks this what to draw
 * and what a reply does; nothing here draws, and nothing here decides
 * whether a reply is allowed — the server does that when it is told.</p>
 *
 * <p>Free of Minecraft, so a test can hold the whole conversation.</p>
 */
public final class QuestConversationModel {

    /** Where in a quest's life the conversation is happening. */
    public enum Mood {
        /** The quest has not been taken yet. */
        OFFER,
        /** It is under way and not yet finished. */
        PROGRESS,
        /** What it asked for is in hand and may be given over. */
        HAND_IN,
        /** Its stage is done and waits for the player's choice (QUEST-32). */
        CHOICE
    }

    /** What the player may say. */
    public enum Reply {
        /** Take the quest. */
        ACCEPT,
        /** Ask for more, which is said without leaving the conversation. */
        MORE,
        /** Hand over what was asked for. */
        HAND_OVER,
        /** Refuse the quest. */
        DECLINE,
        /** End the conversation, having taken it or being part way through. */
        LEAVE,
        /** Make one of a stage's choices. */
        CHOOSE
    }

    private final String speaker;
    private final String subtitle;
    private final String questTitle;
    private final String objective;
    private final Mood mood;
    private final String opening;
    private final String more;
    private final List<String> labels;
    private final List<String> choices;
    private final boolean toldMore;

    /** One thing the player may say: its kind, the choice it names (-1 for none) and its words. */
    public static final class Option {
        private final Reply reply;
        private final int choice;
        private final String label;

        Option(Reply reply, int choice, String label) {
            this.reply = reply;
            this.choice = choice;
            this.label = label;
        }

        public Reply getReply() { return this.reply; }
        /** Which of the hand-in's choices it takes; -1 for a reply that names none. */
        public int getChoice() { return this.choice; }
        public String getLabel() { return this.label; }
    }

    private QuestConversationModel(String speaker, String subtitle,
                               String questTitle, String objective,
                               Mood mood, String opening,
                               String more, List<String> labels,
                               List<String> choices, boolean toldMore) {
        this.speaker = safe(speaker);
        this.subtitle = safe(subtitle);
        this.questTitle = safe(questTitle);
        this.objective = safe(objective);
        this.mood = mood == null ? Mood.OFFER : mood;
        this.opening = safe(opening);
        this.more = safe(more);
        this.labels = labels;
        this.choices = choices;
        this.toldMore = toldMore;
    }

    /**
     * A conversation. {@code labels} holds the words for each
     * {@link Reply} in that enum's order; a label left empty means the
     * reply is not offered, except that leaving is always offered, since
     * a conversation must always be endable.
     */
    public static QuestConversationModel of(String speaker, String subtitle,
                                        String questTitle, String objective,
                                        Mood mood, String opening, String more,
                                        String[] labels) {
        List<String> words = new ArrayList<String>();
        Reply[] replies = Reply.values();
        for (int index = 0; index < replies.length - 1; index++) {
            words.add(labels != null && index < labels.length
                    && labels[index] != null ? labels[index].trim() : "");
        }
        return new QuestConversationModel(speaker, subtitle, questTitle,
                objective, mood, opening, more,
                Collections.unmodifiableList(words),
                Collections.<String>emptyList(), false);
    }

    /**
     * The same conversation whose hand-in offers a choice: one reply per
     * entry of {@code choices}, each handing over and taking that one, in
     * place of the single hand-over (a quest's pick of rewards, QUEST-33).
     */
    public QuestConversationModel withChoices(List<String> choices) {
        List<String> words = new ArrayList<String>();
        if (choices != null) {
            for (String choice : choices) {
                words.add(choice == null ? "" : choice.trim());
            }
        }
        return new QuestConversationModel(this.speaker, this.subtitle,
                this.questTitle, this.objective, this.mood, this.opening,
                this.more, this.labels, Collections.unmodifiableList(words),
                this.toldMore);
    }

    public String getSpeaker() { return this.speaker; }
    /** Who the speaker is — their faction, their trade; may be empty. */
    public String getSubtitle() { return this.subtitle; }
    public String getQuestTitle() { return this.questTitle; }
    public String getObjective() { return this.objective; }

    /** What the speaker is saying right now. */
    public String getSaid() {
        return this.toldMore && this.more.length() > 0
                ? this.more : this.opening;
    }

    /**
     * The same conversation once the player has asked for more: the
     * speaker says the further line, and asking again is no longer
     * offered. A conversation with nothing further to say is unchanged,
     * so the reply is not offered.
     */
    public QuestConversationModel told() {
        if (this.toldMore || this.more.length() == 0) {
            return this;
        }
        return new QuestConversationModel(this.speaker, this.subtitle,
                this.questTitle, this.objective, this.mood,
                this.opening, this.more, this.labels, this.choices, true);
    }

    /**
     * What the player may say, in the order it is offered: what there is
     * to do first (a hand-in with a choice offers each), then asking for
     * more, then leaving.
     */
    public List<Option> getReplies() {
        List<Option> offered = new ArrayList<Option>();
        if (this.mood == Mood.OFFER && labelOf(Reply.ACCEPT).length() > 0) {
            offered.add(option(Reply.ACCEPT));
        }
        if (this.mood == Mood.CHOICE) {
            for (int index = 0; index < this.choices.size(); index++) {
                offered.add(new Option(Reply.CHOOSE, index, this.choices.get(index)));
            }
        } else if (this.mood == Mood.HAND_IN && !this.choices.isEmpty()) {
            for (int index = 0; index < this.choices.size(); index++) {
                offered.add(new Option(Reply.HAND_OVER, index,
                        this.choices.get(index)));
            }
        } else if (this.mood == Mood.HAND_IN
                && labelOf(Reply.HAND_OVER).length() > 0) {
            offered.add(option(Reply.HAND_OVER));
        }
        if (!this.toldMore && this.more.length() > 0
                && labelOf(Reply.MORE).length() > 0) {
            offered.add(option(Reply.MORE));
        }
        offered.add(option(this.mood == Mood.OFFER
                && labelOf(Reply.DECLINE).length() > 0
                ? Reply.DECLINE : Reply.LEAVE));
        return Collections.unmodifiableList(offered);
    }

    private Option option(Reply reply) {
        return new Option(reply, -1, labelOf(reply));
    }

    /** The words on a reply; empty where it was not written. */
    public String labelOf(Reply reply) {
        return reply == null || reply.ordinal() >= this.labels.size() ? ""
                : this.labels.get(reply.ordinal());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
