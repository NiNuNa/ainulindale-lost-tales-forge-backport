package com.ninuna.losttales.party.model;

import java.util.UUID;

/** One persistent, server-owned personal marker for a party character. */
public final class PartyGoHereMarker {

    public static final int CURRENT_DATA_VERSION = 1;
    public static final double MAX_HORIZONTAL_COORDINATE = 30000000.0D;
    public static final double MAX_VERTICAL_COORDINATE = 4096.0D;

    private final UUID partyId;
    private final UUID ownerCharacterId;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final long updatedAt;

    public PartyGoHereMarker(UUID partyId,
                             UUID ownerCharacterId,
                             int dimensionId,
                             double x,
                             double y,
                             double z,
                             long updatedAt) {
        if (partyId == null) {
            throw new IllegalArgumentException("partyId must not be null");
        }
        if (ownerCharacterId == null) {
            throw new IllegalArgumentException("ownerCharacterId must not be null");
        }
        if (!isValidCoordinates(x, y, z)) {
            throw new IllegalArgumentException("marker coordinates are invalid");
        }
        this.partyId = partyId;
        this.ownerCharacterId = ownerCharacterId;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public UUID getOwnerCharacterId() {
        return this.ownerCharacterId;
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public long getUpdatedAt() {
        return this.updatedAt;
    }

    public static boolean isValidCoordinates(double x, double y, double z) {
        return isFinite(x) && isFinite(y) && isFinite(z)
                && Math.abs(x) <= MAX_HORIZONTAL_COORDINATE
                && Math.abs(z) <= MAX_HORIZONTAL_COORDINATE
                && Math.abs(y) <= MAX_VERTICAL_COORDINATE;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
