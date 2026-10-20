package com.ninuna.losttales.progression.deed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A deed as its data file writes it (PROG-11): what a character must do,
 * by its kind, the category its achievement stands in, the alignment it
 * gives and whether it brings a LOTR title. A bundled deed is a LOTR
 * achievement of its own; a server's own shows on the Skills page alone.
 */
public final class DeedDefinition {
    /** What a deed asks for. */
    public enum Kind {
        /** Reach every place it lists, LOTR waypoints or map markers. */
        PLACES,
        /** Slay {@code count} of the creatures it lists, or any creature someone named. */
        KILL,
        /** Finish every quest it lists, or any {@code count} quests. */
        QUESTS,
        /** Help win {@code count} world quests, of those it lists or any. */
        WORLD_QUEST,
        /** Reach a level in a skill, or in any skill. */
        SKILL_LEVEL,
        /** Reach a character level. */
        CHARACTER_LEVEL;

        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind fromWord(String word) {
            for (Kind kind : values()) {
                if (kind.word().equals(word)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** What any skill is, for a skill level deed. */
    public static final String ANY_SKILL = "any";

    private final String id;
    private final String category;
    private final String icon;
    private final Kind kind;
    private final List<String> places;
    private final double radius;
    private final List<String> creatures;
    private final boolean named;
    private final List<String> quests;
    private final int count;
    private final String skill;
    private final int level;
    private final boolean together;
    private final String alignment;
    private final boolean title;
    private final String name;
    private final String description;

    public DeedDefinition(String id, String category, String icon, Kind kind,
                          List<String> places, double radius, List<String> creatures,
                          boolean named, List<String> quests, int count, String skill,
                          int level, boolean together, String alignment, boolean title,
                          String name, String description) {
        this.id = id == null ? "" : id;
        this.category = category == null ? "" : category;
        this.icon = icon == null ? "" : icon;
        this.kind = kind;
        this.places = list(places);
        this.radius = radius;
        this.creatures = list(creatures);
        this.named = named;
        this.quests = list(quests);
        this.count = count;
        this.skill = skill == null ? "" : skill;
        this.level = level;
        this.together = together;
        this.alignment = alignment == null ? "" : alignment;
        this.title = title;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
    }

    private static List<String> list(List<String> values) {
        return Collections.unmodifiableList(values == null
                ? new ArrayList<String>() : new ArrayList<String>(values));
    }

    public String getId() {
        return this.id;
    }

    public String getCategory() {
        return this.category;
    }

    public String getIcon() {
        return this.icon;
    }

    public Kind getKind() {
        return this.kind;
    }

    public List<String> getPlaces() {
        return this.places;
    }

    /** How near a place counts as reached, in blocks. */
    public double getRadius() {
        return this.radius;
    }

    public List<String> getCreatures() {
        return this.creatures;
    }

    /** Whether only a creature someone gave a name counts. */
    public boolean isNamed() {
        return this.named;
    }

    public List<String> getQuests() {
        return this.quests;
    }

    public int getCount() {
        return this.count;
    }

    public String getSkill() {
        return this.skill;
    }

    public int getLevel() {
        return this.level;
    }

    /** Whether the companions who took part earn it too (PROG-11). */
    public boolean isTogether() {
        return this.together;
    }

    /** The alignment it gives, {@code faction*amount} parted by commas; empty for none. */
    public String getAlignment() {
        return this.alignment;
    }

    /** Whether a bundled deed brings the LOTR title its achievement unlocks. */
    public boolean hasTitle() {
        return this.title;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    /**
     * How far it stands at most: the places it lists, the creatures,
     * quests or world quests to count, the level to reach.
     */
    public long target() {
        switch (this.kind) {
            case PLACES:
                return this.places.size();
            case QUESTS:
                return this.quests.isEmpty() ? this.count : this.quests.size();
            case SKILL_LEVEL:
            case CHARACTER_LEVEL:
                return this.level;
            default:
                return this.count;
        }
    }
}
