package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/** Conservative visual-only classification for the combat camera profile. */
final class ThirdPersonCombatItemPolicy {
    private static final String[] WEAPON_NAME_PARTS = {
            "sword", "dagger", "spear", "battleaxe", "warhammer",
            "mace", "halberd", "pike", "scimitar", "trident"
    };

    private ThirdPersonCombatItemPolicy() {}

    static boolean isCombatItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemSword
                || ThirdPersonProjectileItemPolicy.isSupported(stack)) {
            return true;
        }
        String simpleName = item.getClass().getSimpleName()
                .toLowerCase(Locale.ROOT);
        for (String part : WEAPON_NAME_PARTS) {
            if (simpleName.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
