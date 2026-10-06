package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipGoHereMarker;

import java.util.UUID;

/** Immutable server-authoritative position for one currently trackable member. */
public final class FellowshipTrackedMemberSnapshot {

    private final UUID identityId;
    private final String characterName;
    private final FellowshipColor color;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;

    public FellowshipTrackedMemberSnapshot(UUID identityId,
                                      String characterName,
                                      FellowshipColor color,
                                      int dimensionId,
                                      double x,
                                      double y,
                                      double z) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId must not be null");
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        if (!FellowshipGoHereMarker.isValidCoordinates(x, y, z)) {
            throw new IllegalArgumentException("position is invalid");
        }
        this.identityId = identityId;
        this.characterName = normalizeName(characterName);
        this.color = color;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID getIdentityId() {
        return this.identityId;
    }

    public String getCharacterName() {
        return this.characterName;
    }

    public FellowshipColor getColor() {
        return this.color;
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

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof FellowshipTrackedMemberSnapshot)) {
            return false;
        }
        FellowshipTrackedMemberSnapshot other =
                (FellowshipTrackedMemberSnapshot) value;
        return this.identityId.equals(other.identityId)
                && this.characterName.equals(other.characterName)
                && this.color == other.color
                && this.dimensionId == other.dimensionId
                && Double.doubleToLongBits(this.x)
                == Double.doubleToLongBits(other.x)
                && Double.doubleToLongBits(this.y)
                == Double.doubleToLongBits(other.y)
                && Double.doubleToLongBits(this.z)
                == Double.doubleToLongBits(other.z);
    }

    @Override
    public int hashCode() {
        int result = this.identityId.hashCode();
        result = 31 * result + this.characterName.hashCode();
        result = 31 * result + this.color.hashCode();
        result = 31 * result + this.dimensionId;
        long bits = Double.doubleToLongBits(this.x);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        bits = Double.doubleToLongBits(this.y);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        bits = Double.doubleToLongBits(this.z);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        return result;
    }

    /** The name trimmed; empty where there is none, which the client words. */
    private static String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }
}
