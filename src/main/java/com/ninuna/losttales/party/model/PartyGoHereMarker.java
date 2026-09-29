package com.ninuna.losttales.party.model;

import java.util.UUID;

/** One persistent, server-owned go-here marker, filed under the identity that placed it. */
public final class PartyGoHereMarker {

    public static final int CURRENT_DATA_VERSION = 2;
    public static final double MAX_HORIZONTAL_COORDINATE = 30000000.0D;
    public static final double MAX_VERTICAL_COORDINATE = 4096.0D;

    private final UUID partyId;
    private final UUID ownerIdentityId;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final long updatedAt;

    public PartyGoHereMarker(UUID partyId,
                             UUID ownerIdentityId,
                             int dimensionId,
                             double x,
                             double y,
                             double z,
                             long updatedAt) {
        if (ownerIdentityId == null) {
            throw new IllegalArgumentException("ownerIdentityId must not be null");
        }
        if (!isValidCoordinates(x, y, z)) {
            throw new IllegalArgumentException("marker coordinates are invalid");
        }
        this.partyId = partyId;
        this.ownerIdentityId = ownerIdentityId;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public UUID getOwnerIdentityId() {
        return this.ownerIdentityId;
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
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= MAX_HORIZONTAL_COORDINATE
                && Math.abs(z) <= MAX_HORIZONTAL_COORDINATE
                && Math.abs(y) <= MAX_VERTICAL_COORDINATE;
    }
}
