package com.ninuna.losttales.fellowship.model;

import java.util.UUID;

/**
 * A place marked on the map, of one of two kinds ({@link FellowshipMarkKind}).
 * A go-here mark is one character's own, one a character, nameless; its
 * owner sees it, and so does every character travelling with a fellowship
 * the owner is in. A fellowship's mark is placed by its leader or one of its
 * guides for every member, named: a meeting point, a target. It stands until
 * a leader or guide takes it away, or the fellowship ends; a fellowship
 * holds {@link #MAX_PER_FELLOWSHIP} at most. A mark keeps no height: it is
 * drawn on the ground where it stands.
 */
public final class FellowshipMark {

    public static final int CURRENT_DATA_VERSION = 1;
    /** The most marks one fellowship holds at once. */
    public static final int MAX_PER_FELLOWSHIP = 4;
    /** The longest name a mark may have, in characters: as long as a fellowship's. */
    public static final int MAX_NAME_LENGTH = Fellowship.MAX_NAME_LENGTH;
    /** The farthest from the world's middle a mark may stand, in blocks. */
    public static final double MAX_HORIZONTAL_COORDINATE = 30000000.0D;
    /** What a mark's marker id on the map and in a chat link begins with. */
    public static final String MARKER_ID_PREFIX = "fellowship_mark:";

    private final UUID markId;
    private final FellowshipMarkKind kind;
    private final UUID fellowshipId;
    private final String name;
    private final UUID placedBy;
    private final int dimensionId;
    private final double x;
    private final double z;
    private final long placedAt;

    private FellowshipMark(UUID markId, FellowshipMarkKind kind, UUID fellowshipId,
                           String name, UUID placedBy, int dimensionId, double x,
                           double z, long placedAt) {
        if (markId == null || kind == null || placedBy == null) {
            throw new IllegalArgumentException("a mark needs its id, kind and placer");
        }
        if (kind == FellowshipMarkKind.FELLOWSHIP
                ? fellowshipId == null || !isValidName(name)
                : fellowshipId != null || name == null || name.length() > 0) {
            throw new IllegalArgumentException(kind == FellowshipMarkKind.FELLOWSHIP
                    ? "a fellowship's mark needs its fellowship and a short name"
                    : "a go-here mark belongs to no fellowship and has no name");
        }
        if (!isValidPosition(x, z)) {
            throw new IllegalArgumentException("mark position is invalid");
        }
        this.markId = markId;
        this.kind = kind;
        this.fellowshipId = fellowshipId;
        this.name = name;
        this.placedBy = placedBy;
        this.dimensionId = dimensionId;
        this.x = x;
        this.z = z;
        this.placedAt = Math.max(0L, placedAt);
    }

    /** A character's go-here mark. */
    public static FellowshipMark goHere(UUID markId, UUID owner, int dimensionId,
                                        double x, double z, long placedAt) {
        return new FellowshipMark(markId, FellowshipMarkKind.GO_HERE, null, "", owner,
                dimensionId, x, z, placedAt);
    }

    /** A mark of a fellowship, named, placed by its leader or a guide. */
    public static FellowshipMark ofFellowship(UUID markId, UUID fellowshipId, String name,
                                              UUID placedBy, int dimensionId, double x,
                                              double z, long placedAt) {
        return new FellowshipMark(markId, FellowshipMarkKind.FELLOWSHIP, fellowshipId,
                name, placedBy, dimensionId, x, z, placedAt);
    }

    public UUID getMarkId() {
        return this.markId;
    }

    public FellowshipMarkKind getKind() {
        return this.kind;
    }

    public boolean isGoHere() {
        return this.kind == FellowshipMarkKind.GO_HERE;
    }

    /** The fellowship that holds it; null for a go-here mark. */
    public UUID getFellowshipId() {
        return this.fellowshipId;
    }

    /** Its name; empty for a go-here mark, which goes by its owner's. */
    public String getName() {
        return this.name;
    }

    /**
     * The identity that placed it: a go-here mark's owner, or the leader or
     * a guide of a fellowship's mark as it was placed or last moved.
     */
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

    /**
     * The same mark at another place, now: moved by {@code by}, the owner
     * of a go-here mark staying its owner.
     */
    public FellowshipMark movedTo(int dimension, double newX, double newZ,
                                  UUID by, long now) {
        return new FellowshipMark(this.markId, this.kind, this.fellowshipId, this.name,
                isGoHere() ? this.placedBy : by, dimension, newX, newZ, now);
    }

    /** Whether it stands at this place already. */
    public boolean standsAt(int dimension, double atX, double atZ) {
        return this.dimensionId == dimension
                && Double.doubleToLongBits(this.x) == Double.doubleToLongBits(atX)
                && Double.doubleToLongBits(this.z) == Double.doubleToLongBits(atZ);
    }

    /** The id the mark goes by on the map and in a chat link to it. */
    public String getMarkerId() {
        return MARKER_ID_PREFIX + this.markId;
    }

    /**
     * A name a fellowship's mark may carry: well formed as a fellowship's
     * name is ({@link Fellowship#isWellFormedName}), and with none of what
     * the chat reads in a line, {@code @} and square brackets, since the
     * fellowship's conversation names each mark.
     */
    public static boolean isValidName(String name) {
        return Fellowship.isWellFormedName(name)
                && name.indexOf('@') < 0 && name.indexOf('[') < 0
                && name.indexOf(']') < 0;
    }

    public static boolean isValidPosition(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z)
                && Math.abs(x) <= MAX_HORIZONTAL_COORDINATE
                && Math.abs(z) <= MAX_HORIZONTAL_COORDINATE;
    }
}
