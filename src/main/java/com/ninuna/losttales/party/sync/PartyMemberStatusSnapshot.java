package com.ninuna.losttales.party.sync;

import java.util.UUID;

/** Immutable, bounded runtime projection for one authorized party member. */
public final class PartyMemberStatusSnapshot {

    public static final int NO_DIMENSION = Integer.MIN_VALUE;
    public static final float MAX_SYNCHRONIZED_HEALTH = 1000000.0F;

    private final UUID characterId;
    private final PartyMemberAvailability availability;
    private final int dimensionId;
    private final float health;
    private final float maximumHealth;

    private PartyMemberStatusSnapshot(UUID characterId,
                                      PartyMemberAvailability availability,
                                      int dimensionId,
                                      float health,
                                      float maximumHealth) {
        if (characterId == null || availability == null) {
            throw new IllegalArgumentException(
                    "party member status identity and availability are required");
        }
        this.characterId = characterId;
        this.availability = availability;
        if (!availability.hasLiveEntityData()) {
            if (dimensionId != NO_DIMENSION || health != 0.0F
                    || maximumHealth != 0.0F) {
                throw new IllegalArgumentException(
                        "unavailable party members must not expose entity state");
            }
            this.dimensionId = NO_DIMENSION;
            this.health = 0.0F;
            this.maximumHealth = 0.0F;
            return;
        }
        if (!isFinite(health) || !isFinite(maximumHealth)
                || maximumHealth <= 0.0F
                || maximumHealth > MAX_SYNCHRONIZED_HEALTH) {
            throw new IllegalArgumentException("invalid synchronized health");
        }
        this.dimensionId = dimensionId;
        this.maximumHealth = maximumHealth;
        this.health = Math.max(0.0F, Math.min(health, maximumHealth));
    }

    public static PartyMemberStatusSnapshot offline(UUID characterId) {
        return unavailable(characterId, PartyMemberAvailability.OFFLINE);
    }

    public static PartyMemberStatusSnapshot inactive(UUID characterId) {
        return unavailable(characterId,
                PartyMemberAvailability.INACTIVE_CHARACTER);
    }

    public static PartyMemberStatusSnapshot unavailable(UUID characterId) {
        return unavailable(characterId, PartyMemberAvailability.UNAVAILABLE);
    }

    public static PartyMemberStatusSnapshot online(UUID characterId,
                                                    boolean dead,
                                                    int dimensionId,
                                                    float health,
                                                    float maximumHealth) {
        return new PartyMemberStatusSnapshot(
                characterId,
                dead ? PartyMemberAvailability.DEAD
                        : PartyMemberAvailability.ACTIVE,
                dimensionId,
                health,
                maximumHealth);
    }

    public static PartyMemberStatusSnapshot decoded(
            UUID characterId,
            PartyMemberAvailability availability,
            int dimensionId,
            float health,
            float maximumHealth) {
        if (availability == null) {
            throw new IllegalArgumentException("availability is required");
        }
        if (!availability.hasLiveEntityData()) {
            return unavailable(characterId, availability);
        }
        return new PartyMemberStatusSnapshot(
                characterId, availability, dimensionId,
                health, maximumHealth);
    }

    private static PartyMemberStatusSnapshot unavailable(
            UUID characterId, PartyMemberAvailability availability) {
        return new PartyMemberStatusSnapshot(
                characterId, availability, NO_DIMENSION, 0.0F, 0.0F);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public PartyMemberAvailability getAvailability() {
        return this.availability;
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public float getHealth() {
        return this.health;
    }

    public float getMaximumHealth() {
        return this.maximumHealth;
    }

    public boolean isOnlineActive() {
        return this.availability.hasLiveEntityData();
    }

    public boolean isDead() {
        return this.availability == PartyMemberAvailability.DEAD;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof PartyMemberStatusSnapshot)) {
            return false;
        }
        PartyMemberStatusSnapshot other =
                (PartyMemberStatusSnapshot) value;
        return this.characterId.equals(other.characterId)
                && this.availability == other.availability
                && this.dimensionId == other.dimensionId
                && Float.floatToIntBits(this.health)
                == Float.floatToIntBits(other.health)
                && Float.floatToIntBits(this.maximumHealth)
                == Float.floatToIntBits(other.maximumHealth);
    }

    @Override
    public int hashCode() {
        int result = this.characterId.hashCode();
        result = 31 * result + this.availability.hashCode();
        result = 31 * result + this.dimensionId;
        result = 31 * result + Float.floatToIntBits(this.health);
        result = 31 * result + Float.floatToIntBits(this.maximumHealth);
        return result;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
