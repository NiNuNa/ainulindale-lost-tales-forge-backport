package com.ninuna.losttales.fellowship.sync;

import net.minecraft.item.ItemStack;

import java.util.UUID;

/** Immutable, bounded runtime projection for one authorized fellowship member. */
public final class FellowshipMemberStatusSnapshot {

    public static final int NO_DIMENSION = Integer.MIN_VALUE;
    public static final float MAX_SYNCHRONIZED_HEALTH = 1000000.0F;

    private final UUID identityId;
    private final FellowshipMemberAvailability availability;
    private final int dimensionId;
    private final float health;
    private final float maximumHealth;
    private final ItemStack helmet;
    private final ItemStack heldItem;

    private FellowshipMemberStatusSnapshot(UUID identityId,
                                      FellowshipMemberAvailability availability,
                                      int dimensionId,
                                      float health,
                                      float maximumHealth,
                                      ItemStack helmet,
                                      ItemStack heldItem) {
        if (identityId == null || availability == null) {
            throw new IllegalArgumentException(
                    "fellowship member status identity and availability are required");
        }
        this.identityId = identityId;
        this.availability = availability;
        if (!availability.hasLiveEntityData()) {
            if (dimensionId != NO_DIMENSION || health != 0.0F
                    || maximumHealth != 0.0F
                    || helmet != null || heldItem != null) {
                throw new IllegalArgumentException(
                        "unavailable fellowship members must not expose entity state");
            }
            this.dimensionId = NO_DIMENSION;
            this.health = 0.0F;
            this.maximumHealth = 0.0F;
            this.helmet = null;
            this.heldItem = null;
            return;
        }
        if (!Float.isFinite(health) || !Float.isFinite(maximumHealth)
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

    public static FellowshipMemberStatusSnapshot offline(UUID identityId) {
        return unavailable(identityId, FellowshipMemberAvailability.OFFLINE);
    }

    public static FellowshipMemberStatusSnapshot inactive(UUID identityId) {
        return unavailable(identityId,
                FellowshipMemberAvailability.INACTIVE_CHARACTER);
    }

    public static FellowshipMemberStatusSnapshot unavailable(UUID identityId) {
        return unavailable(identityId, FellowshipMemberAvailability.UNAVAILABLE);
    }

    public static FellowshipMemberStatusSnapshot online(UUID identityId,
                                                    boolean dead,
                                                    int dimensionId,
                                                    float health,
                                                    float maximumHealth,
                                                    ItemStack helmet,
                                                    ItemStack heldItem) {
        return new FellowshipMemberStatusSnapshot(
                identityId,
                dead ? FellowshipMemberAvailability.DEAD
                        : FellowshipMemberAvailability.ACTIVE,
                dimensionId,
                health,
                maximumHealth,
                helmet,
                heldItem);
    }

    public static FellowshipMemberStatusSnapshot decoded(
            UUID identityId,
            FellowshipMemberAvailability availability,
            int dimensionId,
            float health,
            float maximumHealth,
            ItemStack helmet,
            ItemStack heldItem) {
        if (availability == null) {
            throw new IllegalArgumentException("availability is required");
        }
        if (!availability.hasLiveEntityData()) {
            return unavailable(identityId, availability);
        }
        return new FellowshipMemberStatusSnapshot(
                identityId, availability, dimensionId,
                health, maximumHealth, helmet, heldItem);
    }

    private static FellowshipMemberStatusSnapshot unavailable(
            UUID identityId, FellowshipMemberAvailability availability) {
        return new FellowshipMemberStatusSnapshot(
                identityId, availability, NO_DIMENSION, 0.0F, 0.0F,
                null, null);
    }

    public UUID getIdentityId() {
        return this.identityId;
    }

    public FellowshipMemberAvailability getAvailability() {
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

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof FellowshipMemberStatusSnapshot)) {
            return false;
        }
        FellowshipMemberStatusSnapshot other =
                (FellowshipMemberStatusSnapshot) value;
        return this.identityId.equals(other.identityId)
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
        int result = this.identityId.hashCode();
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
}
