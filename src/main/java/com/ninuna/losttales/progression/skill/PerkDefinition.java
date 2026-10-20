package com.ninuna.losttales.progression.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One perk of a skill's tree (PROG-06), as its file writes it: the skill
 * level each rank needs, the perk (and rank) before it if any, its
 * effect, the effect's numbers, and whether it is on. Each rank costs a
 * perk point.
 */
public final class PerkDefinition {
    private final String id;
    private final int[] levels;
    private final String needsPerk;
    private final int needsRank;
    private final String effect;
    private final double amount;
    private final Map<String, Double> params;
    private final List<String> with;
    private final boolean enabled;
    private final String name;
    private final String description;

    public PerkDefinition(String id, int[] levels, String needsPerk, int needsRank,
                          String effect, double amount, Map<String, Double> params,
                          List<String> with, boolean enabled, String name,
                          String description) {
        this.id = id == null ? "" : id;
        this.levels = levels == null ? new int[0] : levels.clone();
        this.needsPerk = needsPerk == null ? "" : needsPerk;
        this.needsRank = needsRank;
        this.effect = effect == null ? "" : effect;
        this.amount = amount;
        this.params = Collections.unmodifiableMap(params == null
                ? new LinkedHashMap<String, Double>()
                : new LinkedHashMap<String, Double>(params));
        this.with = Collections.unmodifiableList(with == null
                ? new ArrayList<String>() : new ArrayList<String>(with));
        this.enabled = enabled;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
    }

    public String getId() {
        return this.id;
    }

    public int getRanks() {
        return this.levels.length;
    }

    /** The skill level rank {@code rank} (from 1) needs. */
    public int levelFor(int rank) {
        return rank < 1 || rank > this.levels.length ? Integer.MAX_VALUE
                : this.levels[rank - 1];
    }

    public int[] getLevels() {
        return this.levels.clone();
    }

    /** The perk taken before this one, empty for none. */
    public String getNeedsPerk() {
        return this.needsPerk;
    }

    /** The rank {@link #getNeedsPerk} needs, at least 1 when it names one. */
    public int getNeedsRank() {
        return this.needsRank;
    }

    public String getEffect() {
        return this.effect;
    }

    /** The effect's number for each rank taken. */
    public double getAmount() {
        return this.amount;
    }

    public Map<String, Double> getParams() {
        return this.params;
    }

    /** A named number of the effect's, or {@code fallback} when the file writes none. */
    public double param(String key, double fallback) {
        Double value = this.params.get(key);
        return value == null ? fallback : value.doubleValue();
    }

    /**
     * What the effect works with: weapons or tools (empty for the skill's
     * own weapons), creatures, herbs or ingots, as the effect reads them.
     */
    public List<String> getWith() {
        return this.with;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /** The file's own name for the perk, standing in where no lang line names it. */
    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }
}
