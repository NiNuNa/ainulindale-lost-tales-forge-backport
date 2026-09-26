package com.ninuna.losttales.character.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What a character says about itself, laid out as Total RP 3 lays a
 * profile out: three texts under About — how it looks, who it is and what
 * it has lived through — six short facts, and up to five glances, each one
 * of the chat's emoji with a title and a line. All of it is the
 * character's own words; {@code CharacterValidator} holds them to their
 * bounds and to the profanity list.
 */
public final class CharacterProfile {
    /** An About text, in characters. */
    public static final int MAX_SECTION_LENGTH = 512;
    /** A fact's value, in characters. */
    public static final int MAX_FACT_LENGTH = 24;
    public static final int MAX_GLANCES = 5;
    /** A glance's title, in characters; a glance always has one. */
    public static final int MAX_GLANCE_TITLE_LENGTH = 24;
    /** A glance's line, in characters; it may have none. */
    public static final int MAX_GLANCE_LINE_LENGTH = 120;

    /** The About texts, in the order the profile shows them. */
    public enum Section {
        APPEARANCE("appearance"),
        PERSONALITY("personality"),
        HISTORY("history");

        private final String id;

        Section(String id) {
            this.id = id;
        }

        public String getId() {
            return this.id;
        }
    }

    /** The one-line facts, in the order the profile shows them. */
    public enum Fact {
        HEIGHT("height"),
        BUILD("build"),
        EYES("eyes"),
        HAIR("hair"),
        BIRTHPLACE("birthplace"),
        HOME("home");

        private final String id;

        Fact(String id) {
            this.id = id;
        }

        public String getId() {
            return this.id;
        }
    }

    /**
     * Something a character shows at a glance, as Total RP 3's glances
     * are: one of the chat's emoji by its name, a title and a line.
     */
    public static final class Glance {
        private final String emoji;
        private final String title;
        private final String line;

        public Glance(String emoji, String title, String line) {
            this.emoji = emoji == null ? "" : emoji;
            this.title = title == null ? "" : title;
            this.line = line == null ? "" : line;
        }

        /** The chat emoji's name, as its shortcode names it without the colons. */
        public String getEmoji() {
            return this.emoji;
        }

        public String getTitle() {
            return this.title;
        }

        public String getLine() {
            return this.line;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Glance)) {
                return false;
            }
            Glance glance = (Glance)other;
            return this.emoji.equals(glance.emoji)
                    && this.title.equals(glance.title)
                    && this.line.equals(glance.line);
        }

        @Override
        public int hashCode() {
            return (this.emoji.hashCode() * 31 + this.title.hashCode()) * 31
                    + this.line.hashCode();
        }
    }

    /** A character that says nothing about itself yet. */
    public static final CharacterProfile EMPTY = new CharacterProfile(
            new String[Section.values().length],
            new String[Fact.values().length],
            Collections.<Glance>emptyList());

    private final String[] sections;
    private final String[] facts;
    private final List<Glance> glances;

    private CharacterProfile(String[] sections, String[] facts,
                             List<Glance> glances) {
        this.sections = new String[Section.values().length];
        for (int index = 0; index < this.sections.length; index++) {
            this.sections[index] = sections[index] == null ? ""
                    : sections[index];
        }
        this.facts = new String[Fact.values().length];
        for (int index = 0; index < this.facts.length; index++) {
            this.facts[index] = facts[index] == null ? "" : facts[index];
        }
        List<Glance> kept = new ArrayList<Glance>();
        if (glances != null) {
            for (Glance glance : glances) {
                if (glance != null) {
                    kept.add(glance);
                }
            }
        }
        this.glances = Collections.unmodifiableList(kept);
    }

    /** An About text; empty for one not written. */
    public String section(Section section) {
        return this.sections[section.ordinal()];
    }

    /** A fact's value; empty for one not given. */
    public String fact(Fact fact) {
        return this.facts[fact.ordinal()];
    }

    /** The glances, in the order they are shown. */
    public List<Glance> glances() {
        return this.glances;
    }

    public CharacterProfile withSection(Section section, String text) {
        String[] changed = this.sections.clone();
        changed[section.ordinal()] = text;
        return new CharacterProfile(changed, this.facts, this.glances);
    }

    public CharacterProfile withFact(Fact fact, String value) {
        String[] changed = this.facts.clone();
        changed[fact.ordinal()] = value;
        return new CharacterProfile(this.sections, changed, this.glances);
    }

    public CharacterProfile withGlances(List<Glance> glances) {
        return new CharacterProfile(this.sections, this.facts, glances);
    }

    /** Whether the character says nothing at all about itself. */
    public boolean isEmpty() {
        return equals(EMPTY);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CharacterProfile)) {
            return false;
        }
        CharacterProfile profile = (CharacterProfile)other;
        return Arrays.equals(this.sections, profile.sections)
                && Arrays.equals(this.facts, profile.facts)
                && this.glances.equals(profile.glances);
    }

    @Override
    public int hashCode() {
        return (Arrays.hashCode(this.sections) * 31
                + Arrays.hashCode(this.facts)) * 31 + this.glances.hashCode();
    }
}
