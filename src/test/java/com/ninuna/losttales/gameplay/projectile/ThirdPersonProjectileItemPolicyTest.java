package com.ninuna.losttales.gameplay.projectile;

import com.ninuna.losttales.gameplay.item.ThirdPersonItemUsePolicy;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ThirdPersonProjectileItemPolicyTest {

    @Test
    public void onlyHeldUseWeaponsAreChargeable() {
        ItemStack bow = new ItemStack(new ItemBow());
        ItemStack snowball = new ItemStack(new ItemSnowball());
        ItemStack splashPotion = new ItemStack(
                new ItemPotion(), 1, 16384);

        assertTrue(ThirdPersonProjectileItemPolicy.isChargeable(bow));
        assertFalse(ThirdPersonProjectileItemPolicy
                .isChargeable(snowball));
        assertFalse(ThirdPersonProjectileItemPolicy
                .isChargeable(splashPotion));
        assertFalse(ThirdPersonProjectileItemPolicy
                .isRangedWeapon(splashPotion));
    }

    @Test
    public void immediateProjectileCanAlignBodyWithoutCharging() {
        ItemStack splashPotion = new ItemStack(
                new ItemPotion(), 1, 16384);

        assertTrue(ThirdPersonItemUsePolicy
                .shouldFaceAim(splashPotion, false));
        assertFalse(ThirdPersonProjectileItemPolicy
                .isActivelyCharging(splashPotion, true));
    }

    @Test
    public void fishingRodIsDirectionSensitiveButNotAProjectile() {
        ItemStack fishingRod = new ItemStack(new ItemFishingRod());

        assertTrue(ThirdPersonItemUsePolicy
                .shouldFaceAim(fishingRod, false));
        assertFalse(ThirdPersonProjectileItemPolicy
                .isSupported(fishingRod));
    }
}
