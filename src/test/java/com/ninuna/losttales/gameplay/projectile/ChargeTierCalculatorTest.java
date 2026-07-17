package com.ninuna.losttales.gameplay.projectile;

import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChargeTierCalculatorTest {

    @Test
    public void tiersBeginOnlyAfterNormalFullDraw() {
        assertEquals(0, ChargeTierCalculator.resolveTier(
                20, 20, 10, 24, 42));
        assertEquals(0, ChargeTierCalculator.resolveTier(
                29, 20, 10, 24, 42));
        assertEquals(1, ChargeTierCalculator.resolveTier(
                30, 20, 10, 24, 42));
        assertEquals(2, ChargeTierCalculator.resolveTier(
                44, 20, 10, 24, 42));
        assertEquals(3, ChargeTierCalculator.resolveTier(
                62, 20, 10, 24, 42));
    }

    @Test
    public void policyIncludesBowsButNotOrdinaryPotions() {
        assertTrue(ThirdPersonChargeItemPolicy.supportsChargeTiers(
                new ItemStack(new ItemBow())));
        assertEquals(20, ThirdPersonChargeItemPolicy.getFullDrawTicks(
                new ItemStack(new ItemBow())));
        assertFalse(ThirdPersonChargeItemPolicy.supportsChargeTiers(
                new ItemStack(new ItemPotion(), 1, 16384)));
    }
}
