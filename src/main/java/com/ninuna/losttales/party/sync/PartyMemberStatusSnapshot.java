package com.ninuna.losttales.party.sync;

import net.minecraft.item.ItemStack;

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
    private final ItemStack helmet;
    private final ItemStack heldItem;

    private PartyMemberStatusSnapshot(UUID characterId,
                                      PartyMemberAvailability availability,
                                      int dimensionId,
                                      float health,
                                      float maximumHealth,
                                      ItemStack helmet,
                                      ItemStack heldItem) {
        if (characterId == null || availability == null) {
            throw new IllegalArgumentException(
                    "party member status identity and availability are required");
        }
        this.characterId = characterId;
        this.availability = availability;
        if (!availability.hasLiveEntityData()) {
            if (dimensionId != NO_DIMENSION || health != 0.0F
                    || maximumHealth != 0.0F
                    || helmet != null || heldItem != null) {
                throw new IllegalArgumentException(
                        "unavailable party members must not expose entity state");
            }
            this.dimensionId = NO_DIMENSION;
            this.health = 0.0F;
            this.maximumHealth = 0.0F;
            this.helmet = null;
            this.heldItem = null;
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
        this.helmet = copy(helmet);
        this.heldItem = copy(heldItem);
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
        return online(characterId, dead, dimensionId, health,
                maximumHealth, null, null);
    }

    public static PartyMemberStatusSnapshot online(UUID characterId,
                                                    boolean dead,
                                                    int dimensionId,
                                                    float health,
                                                    float maximumHealth,
                                                    ItemStack helmet,
                                                    ItemStack heldItem) {
        return new PartyMemberStatusSnapshot(
                characterId,
                dead ? PartyMemberAvailability.DEAD
                        : PartyMemberAvailability.ACTIVE,
                dimensionId,
                health,
                maximumHealth,
                helmet,
                heldItem);
    }

    public static PartyMemberStatusSnapshot decoded(
            UUID characterId,
            PartyMemberAvailability availability,
            int dimensionId,
            float health,
            float maximumHealth) {
        return decoded(characterId, availability, dimensionId,
                health, maximumHealth, null, null);
    }

    public static PartyMemberStatusSnapshot decoded(
            UUID characterId,
            PartyMemberAvailability availability,
            int dimensionId,
            float health,
            float maximumHealth,
            ItemStack helmet,
            ItemStack heldItem) {
        if (availability == null) {
            throw new IllegalArgumentException("availability is required");
        }
        if (!availability.hasLiveEntityData()) {
            return unavailable(characterId, availability);
        }
        return new PartyMemberStatusSnapshot(
                characterId, availability, dimensionId,
                health, maximumHealth, helmet, heldItem);
    }

    private static PartyMemberStatusSnapshot unavailable(
            UUID characterId, PartyMemberAvailability availability) {
        return new PartyMemberStatusSnapshot(
                characterId, availability, NO_DIMENSION, 0.0F, 0.0F,
                null, null);
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

    /** Returns a defensive copy suitable for client-side item rendering. */
    public ItemStack getHelmet() {
        return copy(this.helmet);
    }

    /** Returns a defensive copy suitable for client-side item rendering. */
    public ItemStack getHeldItem() {
        return copy(this.heldItem);
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
                == Float.floatToIntBits(other.maximumHealth)
                && ItemStack.areItemStacksEqual(this.helmet, other.helmet)
                && ItemStack.areItemStacksEqual(this.heldItem, other.heldItem);
    }

    @Override
    public int hashCode() {
        int result = this.characterId.hashCode();
        result = 31 * result + this.availability.hashCode();
        result = 31 * result + this.dimensionId;
        result = 31 * result + Float.floatToIntBits(this.health);
        result = 31 * result + Float.floatToIntBits(this.maximumHealth);
        result = 31 * result + itemStackHash(this.helmet);
        result = 31 * result + itemStackHash(this.heldItem);
        return result;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static int itemStackHash(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        int result = stack.getItem() == null ? 0 : stack.getItem().hashCode();
        result = 31 * result + stack.stackSize;
        result = 31 * result + stack.getItemDamage();
        result = 31 * result + (stack.hasTagCompound()
                ? stack.getTagCompound().hashCode() : 0);
        return result;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
