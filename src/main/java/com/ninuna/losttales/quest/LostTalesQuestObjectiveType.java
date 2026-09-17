package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What an objective asks of the player, and the one place a JSON
 * {@code type} is read as one of them.
 *
 * <p>A definition names its type as a word, and several words mean the
 * same thing — {@code gather}, {@code collect} and {@code pickup} are
 * one objective. Every part of the quest system asks here rather than
 * spelling those words out again, so the journal, the tracker, the
 * validator and the server's progress path always agree on what an
 * objective is. The first name of each kind is the one the
 * documentation and the bundled files use; the rest are accepted
 * spellings.</p>
 */
public enum LostTalesQuestObjectiveType {
    /** Have a number of items in hand, however they were come by. */
    GATHER("gather", "gather_item", "collect", "pickup", "pickup_item"),
    /** Make a number of items at a crafting surface. */
    CRAFT("craft"),
    /** Defeat a number of creatures. */
    KILL("kill"),
    /** Reach a place: coordinates, or a map marker's. */
    GOTO("goto", "go_to", "travel", "location"),
    /** Go and speak to somebody. */
    TALK("talk", "talk_to", "speak", "speak_to", "visit"),
    /** Hand items over to somebody, who keeps them. */
    DELIVER("deliver", "hand_in", "handin", "turn_in", "turnin", "give"),
    /** A type this build does not know; it makes no progress. */
    UNKNOWN();

    private static final Map<String, LostTalesQuestObjectiveType> BY_NAME = byName();

    private final String[] names;

    private LostTalesQuestObjectiveType(String... names) {
        this.names = names;
    }

    private static Map<String, LostTalesQuestObjectiveType> byName() {
        Map<String, LostTalesQuestObjectiveType> map =
                new LinkedHashMap<String, LostTalesQuestObjectiveType>();
        for (LostTalesQuestObjectiveType type : values()) {
            for (String name : type.names) {
                map.put(name, type);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /** The kind that word names, {@link #UNKNOWN} for anything else. */
    public static LostTalesQuestObjectiveType of(String type) {
        if (type == null) {
            return UNKNOWN;
        }
        LostTalesQuestObjectiveType known =
                BY_NAME.get(type.trim().toLowerCase(Locale.ROOT));
        return known == null ? UNKNOWN : known;
    }

    /** The kind the objective is, {@link #UNKNOWN} for none at all. */
    public static LostTalesQuestObjectiveType of(
            LostTalesQuestObjectiveDefinition objective) {
        return objective == null ? UNKNOWN : of(objective.getType());
    }

    /** Whether the objective is of this kind. */
    public boolean is(LostTalesQuestObjectiveDefinition objective) {
        return of(objective) == this;
    }

    /** The name this kind is written as; empty for {@link #UNKNOWN}. */
    public String canonicalName() {
        return this.names.length == 0 ? "" : this.names[0];
    }

    /** Whether the kind asks the player to go to somebody in particular. */
    public boolean isNpcVisit() {
        return this == TALK || this == DELIVER;
    }

    /**
     * Whether one act finishes the objective, so its target is always
     * one: arriving somewhere, or reaching the person asked for. A
     * delivery counts the items handed over instead.
     */
    public boolean countsToOne() {
        return this == GOTO || this == TALK;
    }
}
