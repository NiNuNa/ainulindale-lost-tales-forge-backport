package com.ninuna.losttales.progression.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each thing is worth to a skill (PROG-02), as a file writes it: an
 * item, block or creature by its id, {@code id@meta} for one kind of a
 * block or item, {@code ore:Name} for an ore-dictionary name,
 * {@code food} for food by each point of hunger it fills, and {@code *}
 * for everything else. Nothing the table names is worth nothing.
 */
public final class WorthTable {
    public static final String ANY = "*";
    public static final String FOOD = "food";
    public static final String ORE_PREFIX = "ore:";
    public static final WorthTable EMPTY =
            new WorthTable(new LinkedHashMap<String, Double>());

    private final Map<String, Double> entries;

    public WorthTable(Map<String, Double> entries) {
        this.entries = Collections.unmodifiableMap(
                new LinkedHashMap<String, Double>(entries));
    }

    public Map<String, Double> entries() {
        return this.entries;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** What the table gives each point of food, or null when it names no food. */
    public Double perFood() {
        return this.entries.get(FOOD);
    }
}
