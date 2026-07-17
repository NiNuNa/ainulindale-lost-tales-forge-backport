package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import org.junit.Test;

public final class ThirdPersonProjectileBallisticsTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void vanillaBowPredictionTracksActualDrawStrength() {
        ItemStack bow = new ItemStack(new ItemBow());

        ProjectileBallisticsProfile halfDraw =
                ThirdPersonProjectileBallistics.resolve(
                        bow, true, 10);
        ProjectileBallisticsProfile fullDraw =
                ThirdPersonProjectileBallistics.resolve(
                        bow, true, 20);

        assertNotNull(halfDraw);
        assertEquals(1.25D, halfDraw.getLaunchSpeed(), EPSILON);
        assertEquals(3.0D, fullDraw.getLaunchSpeed(), EPSILON);
        assertEquals(0.05D, fullDraw.getGravity(), EPSILON);
        assertEquals(0.99D, fullDraw.getDrag(), EPSILON);
        assertNull(ThirdPersonProjectileBallistics.resolve(
                bow, false, 0));
    }

    @Test
    public void vanillaThrowablesUseTheirOwnLaunchPhysics() {
        ProjectileBallisticsProfile snowball =
                ThirdPersonProjectileBallistics.resolve(
                        new ItemStack(new ItemSnowball()), false, 0);
        ProjectileBallisticsProfile potion =
                ThirdPersonProjectileBallistics.resolve(
                        new ItemStack(new ItemPotion(), 1, 16384),
                        false, 0);

        assertNotNull(snowball);
        assertEquals(1.5D, snowball.getLaunchSpeed(), EPSILON);
        assertEquals(0.03D, snowball.getGravity(), EPSILON);
        assertNotNull(potion);
        assertEquals(0.5D, potion.getLaunchSpeed(), EPSILON);
        assertEquals(0.05D, potion.getGravity(), EPSILON);
    }

    @Test
    public void underdrawnProjectilesDoNotDisplayAnInvalidArc() {
        assertEquals(0.0D,
                ThirdPersonProjectileBallistics.drawStrength(
                        1, 20, 0.10D), EPSILON);
    }
}
