package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyGoHereMarker;

import java.util.UUID;

/** Immutable server-authoritative position for one currently trackable member. */
public final class PartyTrackedMemberSnapshot {

    private final UUID characterId;
    private final String characterName;
    private final PartyColor color;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;

    public PartyTrackedMemberSnapshot(UUID characterId,
                                      String characterName,
                                      PartyColor color,
                                      int dimensionId,
                                      double x,
                                      double y,
                                      double z) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        if (!PartyGoHereMarker.isValidCoordinates(x, y, z)) {
            throw new IllegalArgumentException("position is invalid");
        }
        this.characterId = characterId;
        this.characterName = normalizeName(characterName);
        this.color = color;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public String getCharacterName() {
        return this.characterName;
    }

    public PartyColor getColor() {
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
        if (!(value instanceof PartyTrackedMemberSnapshot)) {
            return false;
        }
        PartyTrackedMemberSnapshot other =
                (PartyTrackedMemberSnapshot) value;
        return this.characterId.equals(other.characterId)
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
        int result = this.characterId.hashCode();
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

    private static String normalizeName(String value) {
        String name = value == null ? "Unknown" : value.trim();
        return name.length() == 0 ? "Unknown" : name;
    }
}
