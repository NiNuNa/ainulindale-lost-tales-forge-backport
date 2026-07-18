package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.AccessoryEligibility;
import com.ninuna.losttales.accessory.AccessorySlotType;
import lotr.common.LOTRMod;
import net.minecraft.item.Item;

/** Exact LOTR Legacy ring registrations; no name-based matching is used. */
public final class LotrAccessoryCompatibility {

    private LotrAccessoryCompatibility() {}

    public static void register(AccessoryCompatibilityRegistry registry) {
        register(registry, "lotr:gold_ring", LOTRMod.goldRing);
        register(registry, "lotr:silver_ring", LOTRMod.silverRing);
        register(registry, "lotr:mithril_ring", LOTRMod.mithrilRing);
        register(registry, "lotr:hobbit_ring", LOTRMod.hobbitRing);
        register(registry, "lotr:dwarven_ring", LOTRMod.dwarvenRing);
    }

    private static void register(AccessoryCompatibilityRegistry registry,
                                 String id,
                                 Item item) {
        registry.registerExactItem(
                id, AccessorySlotType.RING, item,
                AccessoryEligibility.ALLOW_ALL,
                "", "", "");
    }
}
