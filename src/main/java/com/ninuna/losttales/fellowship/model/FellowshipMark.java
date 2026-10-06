package com.ninuna.losttales.fellowship.model;

import java.util.UUID;

/**
 * A place a fellowship's leader or one of its guides marked on the map for
 * every member: a meeting point, a target. It stands until a leader or
 * guide takes it away, or the fellowship ends. A fellowship holds
 * {@link #MAX_PER_FELLOWSHIP} at most.
 */
public final class FellowshipMark {

    public static final int CURRENT_DATA_VERSION = 1;
    /** The most marks one fellowship holds at once. */
    public static final int MAX_PER_FELLOWSHIP = 4;
    /** The longest name a mark may have, in characters. */
    public static final int MAX_NAME_LENGTH = 32;
    /** What a mark's marker id on the map and in a chat link begins with. */
    public static final String MARKER_ID_PREFIX = "fellowship_mark:";

    private final UUID markId;
    private final UUID fellowshipId;
    private final String name;
    private final UUID placedBy;
    private final int dimensionId;
    private final double x;
    private final double z;
    private final long placedAt;

    public FellowshipMark(UUID markId, UUID fellowshipId, String name,
                          UUID placedBy, int dimensionId, double x, double z,
                          long placedAt) {
        if (markId == null || fellowshipId == null || placedBy == null) {
            throw new IllegalArgumentException("a mark needs its id, fellowship and placer");
        }
        if (!isValidName(name)) {
            throw new IllegalArgumentException("a mark needs a short name");
        }
        if (!isValidPosition(x, z)) {
            throw new IllegalArgumentException("mark position is invalid");
        }
        this.markId = markId;
        this.fellowshipId = fellowshipId;
        this.name = name;
        this.placedBy = placedBy;
        this.dimensionId = dimensionId;
        this.x = x;
        this.z = z;
        this.placedAt = Math.max(0L, placedAt);
    }

    public UUID getMarkId() {
        return this.markId;
    }

    public UUID getFellowshipId() {
        return this.fellowshipId;
    }

    public String getName() {
        return this.name;
    }

    /** The identity that placed it, the leader or a guide then. */
    public UUID getPlacedBy() {
        return this.placedBy;
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

    public long getPlacedAt() {
        return this.placedAt;
    }

    /** The same mark at another place, by {@code placedBy}, now. */
    public FellowshipMark movedTo(int dimension, double newX, double newZ,
                                  UUID by, long now) {
        return new FellowshipMark(this.markId, this.fellowshipId, this.name,
                by, dimension, newX, newZ, now);
    }

    /** The id the mark goes by on the map and in a chat link to it. */
    public String getMarkerId() {
        return MARKER_ID_PREFIX + this.markId;
    }

    /**
     * A name a mark may carry: some words, no longer than
     * {@link #MAX_NAME_LENGTH}, with no formatting code and none of what the
     * chat reads in a line, {@code @} and square brackets, since the
     * fellowship's conversation names each mark.
     */
    public static boolean isValidName(String name) {
        if (name == null || name.trim().length() == 0
                || !name.equals(name.trim())
                || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character == '§' || character == '@' || character == '['
                    || character == ']' || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidPosition(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z)
                && Math.abs(x) <= FellowshipGoHereMarker.MAX_HORIZONTAL_COORDINATE
                && Math.abs(z) <= FellowshipGoHereMarker.MAX_HORIZONTAL_COORDINATE;
    }
}
