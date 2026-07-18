package com.ninuna.losttales.accessory;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic registry for items allowed in Lost Tales accessory slots.
 * Ambiguous matches fail closed instead of depending on registration order.
 */
public final class AccessoryCompatibilityRegistry {

    private static final AccessoryCompatibilityRegistry INSTANCE =
            new AccessoryCompatibilityRegistry();

    private final Map<String, AccessoryDefinition> definitionsById =
            new LinkedHashMap<String, AccessoryDefinition>();
    private List<AccessoryDefinition> frozenDefinitions =
            Collections.emptyList();
    private boolean frozen;

    private AccessoryCompatibilityRegistry() {}

    public static AccessoryCompatibilityRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void register(AccessoryDefinition definition) {
        if (this.frozen) {
            throw new IllegalStateException(
                    "Accessory compatibility registry is already frozen");
        }
        if (definition == null
                || this.definitionsById.containsKey(definition.getId())) {
            throw new IllegalArgumentException(
                    "Duplicate or null accessory definition");
        }
        this.definitionsById.put(definition.getId(), definition);
    }

    public synchronized void registerExactItem(
            String id,
            AccessorySlotType slotType,
            final Item item,
            AccessoryEligibility eligibility,
            String serverEffectId,
            String ownerVisualEffectId,
            String publicEffectId) {
        if (item == null) {
            throw new IllegalArgumentException(
                    "Accessory item must not be null for " + id);
        }
        register(new AccessoryDefinition(
                id, slotType,
                new AccessoryStackMatcher() {
                    @Override
                    public boolean matches(ItemStack stack) {
                        return stack != null && stack.getItem() == item;
                    }
                },
                eligibility,
                serverEffectId,
                ownerVisualEffectId,
                publicEffectId));
    }

    public synchronized void freeze() {
        if (this.frozen) {
            return;
        }
        this.frozenDefinitions = Collections.unmodifiableList(
                new ArrayList<AccessoryDefinition>(
                        this.definitionsById.values()));
        this.frozen = true;
    }

    public synchronized boolean isFrozen() {
        return this.frozen;
    }

    public AccessoryDefinition find(
            AccessorySlotType slotType, ItemStack stack) {
        if (slotType == null || stack == null || stack.getItem() == null) {
            return null;
        }
        List<AccessoryDefinition> definitions = snapshotDefinitions();
        AccessoryDefinition match = null;
        for (AccessoryDefinition definition : definitions) {
            if (definition.getSlotType() != slotType
                    || !definition.matches(stack)) {
                continue;
            }
            if (match != null) {
                warnAmbiguous(stack, match, definition);
                return null;
            }
            match = definition;
        }
        return match;
    }

    public boolean isCompatible(AccessorySlotType slotType, ItemStack stack) {
        return find(slotType, stack) != null;
    }

    public boolean canEquip(EntityPlayer player,
                            AccessorySlotType slotType,
                            ItemStack stack) {
        AccessoryDefinition definition = find(slotType, stack);
        if (definition == null) {
            return false;
        }
        // Client containers may predict structural compatibility, but only the
        // server is allowed to decide character-specific eligibility.
        return player == null || player.worldObj == null
                || player.worldObj.isRemote
                || definition.canEquip(player, stack);
    }

    public synchronized List<AccessoryDefinition> getDefinitions() {
        return snapshotDefinitions();
    }

    public synchronized AccessoryDefinition getDefinition(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        return this.definitionsById.get(id);
    }

    private synchronized List<AccessoryDefinition> snapshotDefinitions() {
        if (this.frozen) {
            return this.frozenDefinitions;
        }
        return Collections.unmodifiableList(
                new ArrayList<AccessoryDefinition>(
                        this.definitionsById.values()));
    }

    private static void warnAmbiguous(
            ItemStack stack,
            AccessoryDefinition first,
            AccessoryDefinition second) {
        try {
            FMLLog.warning(
                    "[losttales] Refusing ambiguous accessory item %s: %s and %s",
                    String.valueOf(stack.getItem()),
                    first.getId(), second.getId());
        } catch (Throwable ignored) {
            // Early bootstrap and unit tests may not have an FML logger.
        }
    }
}
