package com.ninuna.losttales.quest.missive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One kind of notice a missive board posts (QUEST-42): a kill of a
 * creature kind or a group, or a gather of an item, how many it asks for,
 * the experience each one pays, and the words a notice is picked from: its
 * titles, issuers and flavour lines. A bundled template names its words by
 * id, each a {@code missive.losttales.*} line; a server's own template
 * ({@link #isWritten}) writes them, shown as written in every language.
 * Read by {@link MissiveTemplateFiles}.
 */
public final class MissiveTemplate {
    private final String id;
    private final String kind;
    private final String targetParam;
    private final String target;
    private final int leastCount;
    private final int mostCount;
    private final int experience;
    private final List<String> titles;
    private final List<String> issuers;
    private final List<String> flavours;
    private final boolean written;

    /**
     * A template. {@code targetParam} is {@code entity} or {@code group}
     * for a kill and {@code item} for a gather, and {@code target} what it
     * names; {@code experience} is paid for each creature or item.
     */
    public MissiveTemplate(String id, String kind, String targetParam, String target,
                           int leastCount, int mostCount, int experience,
                           List<String> titles, List<String> issuers,
                           List<String> flavours, boolean written) {
        this.id = id == null ? "" : id;
        this.kind = kind == null ? "" : kind;
        this.targetParam = targetParam == null ? "" : targetParam;
        this.target = target == null ? "" : target;
        this.leastCount = leastCount;
        this.mostCount = mostCount;
        this.experience = experience;
        this.titles = copy(titles);
        this.issuers = copy(issuers);
        this.flavours = copy(flavours);
        this.written = written;
    }

    public String getId() {
        return this.id;
    }

    /** {@code kill} or {@code gather}, as {@link LostTalesMissiveObjectiveData} names them. */
    public String getKind() {
        return this.kind;
    }

    public boolean isKill() {
        return LostTalesMissiveObjectiveData.TYPE_KILL.equals(this.kind);
    }

    /** The objective parameter the target goes under: {@code entity}, {@code group} or {@code item}. */
    public String getTargetParam() {
        return this.targetParam;
    }

    /** The creature kind, group or item id a notice asks for. */
    public String getTarget() {
        return this.target;
    }

    public int getLeastCount() {
        return this.leastCount;
    }

    public int getMostCount() {
        return this.mostCount;
    }

    /** The experience each creature or item asked for pays. */
    public int getExperience() {
        return this.experience;
    }

    public List<String> getTitles() {
        return this.titles;
    }

    /** Who may sign a notice; empty for nobody named. */
    public List<String> getIssuers() {
        return this.issuers;
    }

    /** The flavour lines a notice may carry; empty for none. */
    public List<String> getFlavours() {
        return this.flavours;
    }

    /** Whether the words are written, a server's own template's, rather than word ids. */
    public boolean isWritten() {
        return this.written;
    }

    private static List<String> copy(List<String> values) {
        return Collections.unmodifiableList(values == null ? new ArrayList<String>()
                : new ArrayList<String>(values));
    }
}
