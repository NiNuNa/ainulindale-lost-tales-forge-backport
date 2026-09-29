package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyGoHereMarker;

import java.util.UUID;

/** Authorized client projection of one personal Go Here marker. */
public final class PartyGoHereMarkerSnapshot {

    private final UUID ownerIdentityId;
    private final String ownerCharacterName;
    private final PartyColor ownerColor;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final long updatedAt;

    public PartyGoHereMarkerSnapshot(UUID ownerIdentityId,
                                     String ownerCharacterName,
                                     PartyColor ownerColor,
                                     int dimensionId,
                                     double x,
                                     double y,
                                     double z,
                                     long updatedAt) {
        if (ownerIdentityId == null) {
            throw new IllegalArgumentException(
                    "ownerIdentityId must not be null");
        }
        if (ownerColor == null) {
            throw new IllegalArgumentException("ownerColor must not be null");
        }
        if (!PartyGoHereMarker.isValidCoordinates(x, y, z)) {
            throw new IllegalArgumentException("marker coordinates are invalid");
        }
        this.ownerIdentityId = ownerIdentityId;
        this.ownerCharacterName = normalizeName(ownerCharacterName);
        this.ownerColor = ownerColor;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public UUID getOwnerIdentityId() {
        return this.ownerIdentityId;
    }

    public String getOwnerCharacterName() {
        return this.ownerCharacterName;
    }

    public PartyColor getOwnerColor() {
        return this.ownerColor;
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

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof PartyGoHereMarkerSnapshot)) {
            return false;
        }
        PartyGoHereMarkerSnapshot other =
                (PartyGoHereMarkerSnapshot) value;
        return this.ownerIdentityId.equals(other.ownerIdentityId)
                && this.ownerCharacterName.equals(other.ownerCharacterName)
                && this.ownerColor == other.ownerColor
                && this.dimensionId == other.dimensionId
                && Double.doubleToLongBits(this.x)
                == Double.doubleToLongBits(other.x)
                && Double.doubleToLongBits(this.y)
                == Double.doubleToLongBits(other.y)
                && Double.doubleToLongBits(this.z)
                == Double.doubleToLongBits(other.z)
                && this.updatedAt == other.updatedAt;
    }

    @Override
    public int hashCode() {
        int result = this.ownerIdentityId.hashCode();
        result = 31 * result + this.ownerCharacterName.hashCode();
        result = 31 * result + this.ownerColor.hashCode();
        result = 31 * result + this.dimensionId;
        long bits = Double.doubleToLongBits(this.x);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        bits = Double.doubleToLongBits(this.y);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        bits = Double.doubleToLongBits(this.z);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        result = 31 * result + (int) (this.updatedAt
                ^ (this.updatedAt >>> 32));
        return result;
    }

    private static String normalizeName(String value) {
        String name = value == null ? "Unknown" : value.trim();
        return name.length() == 0 ? "Unknown" : name;
    }
}
