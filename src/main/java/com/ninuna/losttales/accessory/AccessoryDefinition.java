package com.ninuna.losttales.accessory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.Locale;

/** Immutable accessory compatibility and effect metadata. */
public final class AccessoryDefinition {

    private final String id;
    private final AccessorySlotType slotType;
    private final AccessoryStackMatcher matcher;
    private final AccessoryEligibility eligibility;
    private final String serverEffectId;
    private final String ownerVisualEffectId;
    private final String publicEffectId;

    public AccessoryDefinition(String id,
                               AccessorySlotType slotType,
                               AccessoryStackMatcher matcher,
                               AccessoryEligibility eligibility,
                               String serverEffectId,
                               String ownerVisualEffectId,
                               String publicEffectId) {
        this.id = requireId(id);
        if (slotType == null) {
            throw new IllegalArgumentException("slotType must not be null");
        }
        if (matcher == null) {
            throw new IllegalArgumentException("matcher must not be null");
        }
        this.slotType = slotType;
        this.matcher = matcher;
        this.eligibility = eligibility == null
                ? AccessoryEligibility.ALLOW_ALL : eligibility;
        this.serverEffectId = normalizeOptionalId(serverEffectId);
        this.ownerVisualEffectId = normalizeOptionalId(ownerVisualEffectId);
        this.publicEffectId = normalizeOptionalId(publicEffectId);
    }

    public String getId() {
        return this.id;
    }

    public AccessorySlotType getSlotType() {
        return this.slotType;
    }

    public String getServerEffectId() {
        return this.serverEffectId;
    }

    public String getOwnerVisualEffectId() {
        return this.ownerVisualEffectId;
    }

    public String getPublicEffectId() {
        return this.publicEffectId;
    }

    public boolean matches(ItemStack stack) {
        return stack != null && stack.getItem() != null
                && this.matcher.matches(stack);
    }

    public boolean canEquip(EntityPlayer player, ItemStack stack) {
        return matches(stack) && this.eligibility.canEquip(player, stack);
    }

    private static String requireId(String value) {
        String id = normalizeOptionalId(value);
        if (id.length() == 0 || id.length() > 128
                || id.indexOf(':') <= 0 || id.endsWith(":")) {
            throw new IllegalArgumentException("invalid accessory id " + value);
        }
        return id;
    }

    private static String normalizeOptionalId(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ENGLISH);
    }
}
