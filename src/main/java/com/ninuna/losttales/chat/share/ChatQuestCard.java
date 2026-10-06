package com.ninuna.losttales.chat.share;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a shared quest's card says, as data each reader's game words in its
 * own language: where the quest comes from, the word its category is filed
 * under, each current objective by its kind, target, count and progress,
 * and the reward as the quest writes it. Words travel only where no game
 * could find them in its lang file: the title and objective lines a server
 * quest's operator wrote, and the words LOTR gives a mini-quest. A missive
 * travels as its title's template id. Every field is bounded; a card past
 * a bound is never made.
 */
public final class ChatQuestCard {
    /** Where the quest comes from, which says how a reader's game words it. */
    public enum Source {
        /** A quest bundled with the mod: worded by the lang lines its id names. */
        BUNDLED(0),
        /** A server's own quest file: its operator's words, as written. */
        SERVER(1),
        /** A missive: its title by template id, its work by kind and target. */
        MISSIVE(2),
        /** A LOTR mini-quest: the words LOTR gave it on the server. */
        LOTR(3),
        /** LOTR's welcome quest, the Grey Wanderer's: worded by the mod's lang lines. */
        LOTR_WELCOME(4);

        private final int code;

        Source(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }

        /** The source a code stands for; null for none. */
        public static Source fromCode(int code) {
            for (Source source : values()) {
                if (source.code == code) {
                    return source;
                }
            }
            return null;
        }
    }

    /**
     * The parameters an objective names its targets by, as a quest file
     * writes them: what to gather, craft or bring, and whom to defeat,
     * speak to or bring it to.
     */
    public static final List<String> TARGET_PARAMS = Collections.unmodifiableList(
            Arrays.asList("item", "ore", "entity", "group"));

    public static final int MAX_TITLE_BYTES = 256;
    public static final int MAX_CATEGORY_BYTES = 128;
    public static final int MAX_OBJECTIVES = 8;
    public static final int MAX_OBJECTIVE_ID_BYTES = 64;
    public static final int MAX_OBJECTIVE_TYPE_BYTES = 16;
    public static final int MAX_TARGET_BYTES = 128;
    public static final int MAX_OBJECTIVE_TEXT_BYTES = 256;
    public static final int MAX_REWARDS = 6;
    public static final int MAX_REWARD_KEY_BYTES = 32;
    public static final int MAX_REWARD_VALUE_BYTES = 256;
    /** The largest count or progress an objective may show. */
    public static final int MAX_COUNT = 1000000;
    /** The reward entry a LOTR card names the faction that pays by, by LOTR's code; empty for the giver's. */
    public static final String LOTR_PAYMENT = "lotrPayment";

    private final Source source;
    private final String title;
    private final String category;
    private final List<Objective> objectives;
    private final Map<String, String> rewards;

    /**
     * A card. {@code title} is the operator's or LOTR's words for a server
     * quest or a mini-quest, a missive's title id, empty for a bundled
     * quest and the welcome quest.
     */
    public ChatQuestCard(Source source, String title, String category,
                         List<Objective> objectives, Map<String, String> rewards) {
        if (source == null || !fits(title, MAX_TITLE_BYTES)
                || !fits(category, MAX_CATEGORY_BYTES)
                || objectives == null || objectives.size() > MAX_OBJECTIVES
                || rewards == null || rewards.size() > MAX_REWARDS) {
            throw new IllegalArgumentException("invalid quest card");
        }
        for (Objective objective : objectives) {
            if (objective == null) {
                throw new IllegalArgumentException("invalid quest card objective");
            }
        }
        for (Map.Entry<String, String> entry : rewards.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0
                    || !fits(entry.getKey(), MAX_REWARD_KEY_BYTES)
                    || !fits(entry.getValue(), MAX_REWARD_VALUE_BYTES)) {
                throw new IllegalArgumentException("invalid quest card reward");
            }
        }
        this.source = source;
        this.title = title == null ? "" : title;
        this.category = category == null ? "" : category;
        this.objectives = Collections.unmodifiableList(
                new ArrayList<Objective>(objectives));
        this.rewards = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(rewards));
    }

    public Source getSource() { return this.source; }
    public String getTitle() { return this.title; }
    public String getCategory() { return this.category; }
    public List<Objective> getObjectives() { return this.objectives; }
    public Map<String, String> getRewards() { return this.rewards; }

    /** What the card costs on the wire, its framing included. */
    public int serializedBytes() {
        int bytes = 1 + 2 + utf8Length(this.title) + 2
                + utf8Length(this.category) + 1 + 1;
        for (Objective objective : this.objectives) {
            bytes += objective.serializedBytes();
        }
        for (Map.Entry<String, String> entry : this.rewards.entrySet()) {
            bytes += 4 + utf8Length(entry.getKey())
                    + utf8Length(entry.getValue());
        }
        return bytes;
    }

    /** One current objective: its kind, targets, count and progress. */
    public static final class Objective {
        private final String id;
        private final String type;
        private final Map<String, String> targets;
        private final int count;
        private final int progress;
        private final boolean optional;
        private final String text;

        /**
         * {@code targets} are the objective's {@link #TARGET_PARAMS} as
         * written; {@code text} is the operator's or LOTR's words for it,
         * empty for one each reader's game words from its kind and targets.
         */
        public Objective(String id, String type, Map<String, String> targets,
                         int count, int progress, boolean optional,
                         String text) {
            if (!fits(id, MAX_OBJECTIVE_ID_BYTES)
                    || !fits(type, MAX_OBJECTIVE_TYPE_BYTES)
                    || targets == null
                    || targets.size() > TARGET_PARAMS.size()
                    || count < 0 || count > MAX_COUNT
                    || progress < 0 || progress > count
                    || !fits(text, MAX_OBJECTIVE_TEXT_BYTES)) {
                throw new IllegalArgumentException("invalid quest card objective");
            }
            for (Map.Entry<String, String> target : targets.entrySet()) {
                if (!TARGET_PARAMS.contains(target.getKey())
                        || target.getValue() == null
                        || target.getValue().length() == 0
                        || !fits(target.getValue(), MAX_TARGET_BYTES)) {
                    throw new IllegalArgumentException("invalid quest card target");
                }
            }
            this.id = id == null ? "" : id;
            this.type = type == null ? "" : type;
            this.targets = Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(targets));
            this.count = count;
            this.progress = progress;
            this.optional = optional;
            this.text = text == null ? "" : text;
        }

        public String getId() { return this.id; }
        public String getType() { return this.type; }
        public Map<String, String> getTargets() { return this.targets; }
        public int getCount() { return this.count; }
        public int getProgress() { return this.progress; }
        public boolean isOptional() { return this.optional; }
        public String getText() { return this.text; }

        int serializedBytes() {
            int bytes = 2 + utf8Length(this.id) + 2 + utf8Length(this.type)
                    + 1 + 4 + 4 + 1 + 2 + utf8Length(this.text);
            for (Map.Entry<String, String> target : this.targets.entrySet()) {
                bytes += 4 + utf8Length(target.getKey())
                        + utf8Length(target.getValue());
            }
            return bytes;
        }
    }

    static boolean fits(String value, int maximumBytes) {
        return utf8Length(value) <= maximumBytes;
    }

    static int utf8Length(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return value.getBytes("UTF-8").length;
        } catch (java.io.UnsupportedEncodingException exception) {
            return value.length() * 3;
        }
    }
}
