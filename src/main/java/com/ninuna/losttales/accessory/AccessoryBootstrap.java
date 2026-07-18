package com.ninuna.losttales.accessory;

import com.ninuna.losttales.compat.lotr.LotrAccessoryCompatibility;
import com.ninuna.losttales.item.ELostTalesItem;

/** Registers built-in accessory definitions after all required items exist. */
public final class AccessoryBootstrap {

    public static final String ONE_RING_DEFINITION_ID =
            "losttales:the_one_ring";
    public static final String ONE_RING_SERVER_EFFECT_ID =
            "losttales:one_ring";
    public static final String WRAITH_WORLD_VISUAL_EFFECT_ID =
            "losttales:wraith_world";
    public static final String CONCEALED_PUBLIC_EFFECT_ID =
            "losttales:concealed";

    private static boolean initialized;

    private AccessoryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        AccessoryCompatibilityRegistry registry =
                AccessoryCompatibilityRegistry.getInstance();
        LotrAccessoryCompatibility.register(registry);
        registry.registerExactItem(
                ONE_RING_DEFINITION_ID,
                AccessorySlotType.RING,
                ELostTalesItem.THE_ONE_RING.getItem(),
                AccessoryEligibility.ALLOW_ALL,
                ONE_RING_SERVER_EFFECT_ID,
                WRAITH_WORLD_VISUAL_EFFECT_ID,
                CONCEALED_PUBLIC_EFFECT_ID);
        initialized = true;
    }

    public static void freeze() {
        AccessoryCompatibilityRegistry.getInstance().freeze();
    }
}
