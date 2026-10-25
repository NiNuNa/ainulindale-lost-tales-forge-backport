package com.ninuna.losttales.quest;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNames;

/**
 * The place a rumour points to (QUEST-20), as the player's game is sent
 * it: the marker the rumour's file names, where that marker stands and
 * what it is called. The journal names it and the map pins it; nothing of
 * it is discovered until the player goes there.
 */
public final class QuestRumourPlace {
    private final String questId;
    private final String markerId;
    private final String name;
    private final String namedAfter;
    private final int dimensionId;
    private final double x;
    private final double z;

    public QuestRumourPlace(String questId, String markerId, String name,
                            String namedAfter, int dimensionId, double x,
                            double z) {
        this.questId = questId == null ? "" : questId;
        this.markerId = markerId == null ? "" : markerId;
        this.name = name == null ? "" : name;
        this.namedAfter = namedAfter == null ? "" : namedAfter;
        this.dimensionId = dimensionId;
        this.x = x;
        this.z = z;
    }

    /** The quest whose rumour points here. */
    public String getQuestId() {
        return this.questId;
    }

    /** The marker standing at the place. */
    public String getMarkerId() {
        return this.markerId;
    }

    /** The marker's own words for its name; empty for none. */
    public String getName() {
        return this.name;
    }

    /** What the marker is called after while it has no name ({@code LostTalesMapMarkerNamedAfter}). */
    public String getNamedAfter() {
        return this.namedAfter;
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public double getX() {
        return this.x;
    }

    public double getZ() {
        return this.z;
    }

    /** Whether the place stands where a map can draw it: a marker id and finite coordinates. */
    public boolean isValid() {
        return this.questId.length() > 0 && this.markerId.length() > 0
                && Double.isFinite(this.x) && Double.isFinite(this.z);
    }

    /** The place's name as the reading side words it, as every marker's is ({@link LostTalesMapMarkerNames}). */
    public String shownName() {
        return LostTalesMapMarkerNames.component(this.markerId, this.name,
                this.namedAfter).getUnformattedText();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof QuestRumourPlace)) {
            return false;
        }
        QuestRumourPlace that = (QuestRumourPlace) other;
        return this.questId.equals(that.questId)
                && this.markerId.equals(that.markerId)
                && this.name.equals(that.name)
                && this.namedAfter.equals(that.namedAfter)
                && this.dimensionId == that.dimensionId
                && Double.compare(this.x, that.x) == 0
                && Double.compare(this.z, that.z) == 0;
    }

    @Override
    public int hashCode() {
        int hash = this.questId.hashCode();
        hash = hash * 31 + this.markerId.hashCode();
        hash = hash * 31 + this.name.hashCode();
        hash = hash * 31 + this.namedAfter.hashCode();
        hash = hash * 31 + this.dimensionId;
        long xBits = Double.doubleToLongBits(this.x);
        long zBits = Double.doubleToLongBits(this.z);
        hash = hash * 31 + (int) (xBits ^ (xBits >>> 32));
        return hash * 31 + (int) (zBits ^ (zBits >>> 32));
    }
}
