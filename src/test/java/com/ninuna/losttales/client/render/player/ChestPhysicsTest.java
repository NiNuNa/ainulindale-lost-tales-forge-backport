package com.ninuna.losttales.client.render.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The chest spring must move with the body, stay small, and come to rest. */
public final class ChestPhysicsTest {

    @Test
    public void startsAtRestAndStaysThereWithoutInput() {
        ChestPhysics physics = new ChestPhysics();
        assertTrue(physics.isAtRest());
        for (int tick = 0; tick < 20; tick++) {
            physics.tick(0.0F, 0.0F, 0.0F, 0, 0.35F);
        }
        assertTrue(physics.isAtRest());
        assertEquals(0.0F, physics.bounceY(0.5F), 0.0F);
    }

    @Test
    public void jumpingLeavesTheChestBehindAndItSettlesAgain() {
        ChestPhysics physics = new ChestPhysics();
        physics.tick(0.42F, 0.0F, 0.0F, 0, 0.35F);
        physics.tick(0.33F, 0.0F, 0.0F, 0, 0.35F);
        assertTrue("moving up pulls the chest down", physics.bounceY(1.0F) < 0.0F);
        float peak = Math.abs(physics.bounceY(1.0F));
        for (int tick = 0; tick < 200; tick++) {
            physics.tick(0.0F, 0.0F, 0.0F, 0, 0.35F);
        }
        assertTrue(peak > 0.02F);
        assertTrue(physics.isAtRest());
    }

    @Test
    public void turningSwaysSidewaysAndDisplacementStaysBounded() {
        ChestPhysics physics = new ChestPhysics();
        for (int tick = 0; tick < 40; tick++) {
            physics.tick(5.0F, 1.0F, 90.0F, 1, 1.0F);
        }
        assertTrue(Math.abs(physics.swayX(1.0F)) <= ChestPhysics.MAX_DISPLACEMENT);
        assertTrue(Math.abs(physics.bounceY(1.0F)) <= ChestPhysics.MAX_DISPLACEMENT);
        assertTrue(physics.swayX(1.0F) != 0.0F);
    }

    @Test
    public void zeroBounceMeansNoMovement() {
        ChestPhysics physics = new ChestPhysics();
        for (int tick = 0; tick < 10; tick++) {
            physics.tick(1.0F, 1.0F, 45.0F, 1, 0.0F);
        }
        assertTrue(physics.isAtRest());
    }

    @Test
    public void renderValuesInterpolateBetweenTicks() {
        ChestPhysics physics = new ChestPhysics();
        physics.tick(0.5F, 0.0F, 0.0F, 0, 1.0F);
        float end = physics.bounceY(1.0F);
        assertEquals(0.0F, physics.bounceY(0.0F), 0.0F);
        assertEquals(end * 0.5F, physics.bounceY(0.5F), 0.0001F);
    }

    @Test
    public void fullnessGrowsButSlowsTowardsTheTop() {
        float low = LostTalesPlayerModel.fullness(0.0F);
        float mid = LostTalesPlayerModel.fullness(0.5F);
        float high = LostTalesPlayerModel.fullness(1.0F);
        assertTrue(low < mid && mid < high);
        assertTrue(mid - low > high - mid);
        assertTrue(high < 1.0F && low > 0.5F);
    }
}
