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
public final class QuestDialogueModel {

    /** Where in a quest's life the conversation is happening. */
    public enum Mood {
        /** The quest has not been taken yet. */
        OFFER,
        /** It is under way and not yet finished. */
        PROGRESS,
        /** What it asked for is in hand and may be given over. */
        HAND_IN
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
        LEAVE
    }

    private final String speaker;
    private final String subtitle;
    private final String questTitle;
    private final String objective;
    private final Mood mood;
    private final String opening;
    private final String more;
    private final List<String> labels;
    private final boolean toldMore;

    private QuestDialogueModel(String speaker, String subtitle,
                               String questTitle, String objective,
                               Mood mood, String opening,
                               String more, List<String> labels,
                               boolean toldMore) {
        this.speaker = safe(speaker);
        this.subtitle = safe(subtitle);
        this.questTitle = safe(questTitle);
        this.objective = safe(objective);
        this.mood = mood == null ? Mood.OFFER : mood;
        this.opening = safe(opening);
        this.more = safe(more);
        this.labels = labels;
        this.toldMore = toldMore;
    }

    /**
     * A conversation. {@code labels} holds the words for each
     * {@link Reply} in that enum's order; a label left empty means the
     * reply is not offered, except that leaving is always offered, since
     * a conversation must always be endable.
     */
    public static QuestDialogueModel of(String speaker, String subtitle,
                                        String questTitle, String objective,
                                        Mood mood, String opening, String more,
                                        String[] labels) {
        List<String> words = new ArrayList<String>();
        Reply[] replies = Reply.values();
        for (int index = 0; index < replies.length; index++) {
            words.add(labels != null && index < labels.length
                    && labels[index] != null ? labels[index].trim() : "");
        }
        return new QuestDialogueModel(speaker, subtitle, questTitle,
                objective, mood, opening, more,
                Collections.unmodifiableList(words), false);
    }

    public String getSpeaker() { return this.speaker; }
    /** Who the speaker is — their faction, their trade; may be empty. */
    public String getSubtitle() { return this.subtitle; }
    public String getQuestTitle() { return this.questTitle; }
    public String getObjective() { return this.objective; }
    public Mood getMood() { return this.mood; }
    /** Whether the player has asked for more and been told. */
    public boolean isToldMore() { return this.toldMore; }

    /** What the speaker is saying right now. */
    public String getSaid() {
        return this.toldMore && this.more.length() > 0
                ? this.more : this.opening;
    }

    /**
     * The same conversation once the player has asked for more: the
     * speaker says the further line, and asking again is no longer
     * offered. A conversation with nothing further to say is unchanged,
     * so the reply simply does not appear.
     */
    public QuestDialogueModel told() {
        if (this.toldMore || this.more.length() == 0) {
            return this;
        }
        return new QuestDialogueModel(this.speaker, this.subtitle,
                this.questTitle, this.objective, this.mood,
                this.opening, this.more, this.labels, true);
    }

    /**
     * What the player may say, in the order it is offered: what there is
     * to do first, then asking for more, then leaving.
     */
    public List<Reply> getReplies() {
        List<Reply> offered = new ArrayList<Reply>();
        if (this.mood == Mood.OFFER && labelOf(Reply.ACCEPT).length() > 0) {
            offered.add(Reply.ACCEPT);
        }
        if (this.mood == Mood.HAND_IN && labelOf(Reply.HAND_OVER).length() > 0) {
            offered.add(Reply.HAND_OVER);
        }
        if (!this.toldMore && this.more.length() > 0
                && labelOf(Reply.MORE).length() > 0) {
            offered.add(Reply.MORE);
        }
        offered.add(this.mood == Mood.OFFER
                && labelOf(Reply.DECLINE).length() > 0
                ? Reply.DECLINE : Reply.LEAVE);
        return Collections.unmodifiableList(offered);
    }

    /** The words on a reply; empty where it was not written. */
    public String labelOf(Reply reply) {
        return reply == null ? ""
                : this.labels.get(reply.ordinal());
    }

    /**
     * Whether saying this ends the conversation. Asking for more does
     * not; everything else does, because taking a quest, refusing it or
     * handing it over is the end of what there was to talk about.
     */
    public static boolean ends(Reply reply) {
        return reply != Reply.MORE;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
