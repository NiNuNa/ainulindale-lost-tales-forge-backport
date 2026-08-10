package com.ninuna.losttales.client.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

public final class LostTalesInputIconAnimationTest {
    @Test
    public void pressJumpsStraightToThirdFrameAndHoldsThere() {
        LostTalesInputIconAnimation animation =
                new LostTalesInputIconAnimation();

        assertEquals(LostTalesInputIconAnimation.IDLE_FRAME,
                animation.frame(false, 1L));
        assertEquals(LostTalesInputIconAnimation.PRESSED_FRAME,
                animation.frame(true, 2L));
        assertEquals(LostTalesInputIconAnimation.PRESSED_FRAME,
                animation.frame(true, 20_000_000L));
    }

    @Test
    public void releaseMovesFromThirdThroughSecondToIdle() {
        LostTalesInputIconAnimation animation =
                new LostTalesInputIconAnimation();
        animation.frame(true, 1L);

        long released = 10_000_000L;
        assertEquals(LostTalesInputIconAnimation.RELEASE_FRAME,
                animation.frame(false, released));
        assertEquals(LostTalesInputIconAnimation.RELEASE_FRAME,
                animation.frame(false, released
                        + LostTalesInputIconAnimation.RELEASE_FRAME_NANOS - 1L));
        assertEquals(LostTalesInputIconAnimation.IDLE_FRAME,
                animation.frame(false, released
                        + LostTalesInputIconAnimation.RELEASE_FRAME_NANOS));
    }

    @Test
    public void staleHiddenKeyStateDoesNotAnimateWhenGuiReopens() {
        LostTalesInputIconAnimation animation =
                new LostTalesInputIconAnimation();
        animation.frame(true, 1L);

        assertEquals(LostTalesInputIconAnimation.IDLE_FRAME,
                animation.frame(false,
                        LostTalesInputIconAnimation.STALE_AFTER_NANOS + 2L));
    }

    @Test
    public void impactSquashesWithVolumeAndTightensItsShadow() {
        LostTalesInputIconAnimation animation =
                new LostTalesInputIconAnimation();
        long pressed = 1L;
        animation.pose(true, pressed, Keyboard.KEY_A);

        LostTalesInputIconAnimation.Pose impact = animation.pose(true,
                pressed + LostTalesInputIconAnimation.PRESS_IMPACT_NANOS,
                Keyboard.KEY_A);
        assertEquals(LostTalesInputIconAnimation.PRESSED_FRAME,
                impact.getFrame());
        assertTrue(impact.getScaleX() > 1.0F);
        assertTrue(impact.getScaleY() < 1.0F);
        assertTrue(impact.getOffsetY() > 0.4F);
        assertTrue(impact.getBrightness() > 1.0F);
        assertTrue(impact.getShadowOffsetY() < 0.3F);
    }

    @Test
    public void heldJitterMovesTheWholeKeyOnBothAxesDeterministically() {
        LostTalesInputIconAnimation first =
                new LostTalesInputIconAnimation();
        LostTalesInputIconAnimation second =
                new LostTalesInputIconAnimation();
        first.pose(true, 1L, Keyboard.KEY_A);
        second.pose(true, 1L, Keyboard.KEY_A);
        first.pose(true, 100_000_000L, Keyboard.KEY_A);
        second.pose(true, 100_000_000L, Keyboard.KEY_A);
        first.pose(true, 200_000_000L, Keyboard.KEY_A);
        second.pose(true, 200_000_000L, Keyboard.KEY_A);

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minRotation = Float.MAX_VALUE;
        float maxRotation = -Float.MAX_VALUE;
        for (long time = 300_000_000L; time <= 650_000_000L;
             time += 16_000_000L) {
            LostTalesInputIconAnimation.Pose a =
                    first.pose(true, time, Keyboard.KEY_A);
            LostTalesInputIconAnimation.Pose b =
                    second.pose(true, time, Keyboard.KEY_A);
            assertEquals(a.getOffsetX(), b.getOffsetX(), 0.0F);
            assertEquals(a.getOffsetY(), b.getOffsetY(), 0.0F);
            assertEquals(a.getRotationDegrees(),
                    b.getRotationDegrees(), 0.0F);
            minX = Math.min(minX, a.getOffsetX());
            maxX = Math.max(maxX, a.getOffsetX());
            minY = Math.min(minY, a.getOffsetY());
            maxY = Math.max(maxY, a.getOffsetY());
            minRotation = Math.min(minRotation, a.getRotationDegrees());
            maxRotation = Math.max(maxRotation, a.getRotationDegrees());
            assertTrue(Math.abs(a.getOffsetX()) < 0.3F);
            assertTrue(a.getOffsetY() > 0.3F && a.getOffsetY() < 0.75F);
        }
        assertTrue(maxX - minX > 0.25F);
        assertTrue(maxY - minY > 0.15F);
        assertTrue(maxRotation - minRotation > 0.25F);
    }

    @Test
    public void arrowPressureTravelsInTheDirectionItRepresents() {
        LostTalesInputIconAnimation letter =
                new LostTalesInputIconAnimation();
        LostTalesInputIconAnimation right =
                new LostTalesInputIconAnimation();
        LostTalesInputIconAnimation up =
                new LostTalesInputIconAnimation();
        letter.pose(true, 1L, Keyboard.KEY_A);
        right.pose(true, 1L, Keyboard.KEY_RIGHT);
        up.pose(true, 1L, Keyboard.KEY_UP);
        long impact = 1L + LostTalesInputIconAnimation.PRESS_IMPACT_NANOS;

        LostTalesInputIconAnimation.Pose letterPose =
                letter.pose(true, impact, Keyboard.KEY_A);
        LostTalesInputIconAnimation.Pose rightPose =
                right.pose(true, impact, Keyboard.KEY_RIGHT);
        LostTalesInputIconAnimation.Pose upPose =
                up.pose(true, impact, Keyboard.KEY_UP);
        assertTrue(rightPose.getOffsetX() > letterPose.getOffsetX() + 0.35F);
        assertTrue(upPose.getOffsetY() < letterPose.getOffsetY() - 0.35F);
        assertTrue(rightPose.getShadowOffsetX() < 0.0F);
        assertTrue(upPose.getShadowOffsetY()
                > letterPose.getShadowOffsetY());
    }

    @Test
    public void releaseStretchesThenSettlesWithShadowFollowThrough() {
        LostTalesInputIconAnimation animation =
                new LostTalesInputIconAnimation();
        long pressed = 1L;
        long released = 220_000_000L;
        animation.pose(true, pressed, Keyboard.KEY_A);
        animation.pose(true, released - 1L, Keyboard.KEY_A);
        animation.pose(false, released, Keyboard.KEY_A);

        LostTalesInputIconAnimation.Pose stretch = animation.pose(false,
                released + LostTalesInputIconAnimation.RELEASE_FRAME_NANOS,
                Keyboard.KEY_A);
        assertEquals(LostTalesInputIconAnimation.IDLE_FRAME,
                stretch.getFrame());
        assertTrue(stretch.getScaleX() < 1.0F);
        assertTrue(stretch.getScaleY() > 1.0F);
        assertTrue(stretch.getOffsetY() < 0.0F);
        assertTrue(stretch.getShadowOffsetY() > 1.0F);

        LostTalesInputIconAnimation.Pose settled = animation.pose(false,
                released + LostTalesInputIconAnimation.RELEASE_SETTLE_NANOS,
                Keyboard.KEY_A);
        assertEquals(LostTalesInputIconAnimation.IDLE_FRAME,
                settled.getFrame());
        assertEquals(1.0F, settled.getScaleX(), 0.003F);
        assertEquals(1.0F, settled.getScaleY(), 0.003F);
        assertTrue(Math.abs(settled.getOffsetY()) < 0.05F);
    }

    @Test
    public void releaseStartsFromTheExactHeldJitterPose() {
        LostTalesInputIconAnimation animation =
                new LostTalesInputIconAnimation();
        animation.pose(true, 1L, Keyboard.KEY_A);
        animation.pose(true, 100_000_000L, Keyboard.KEY_A);
        animation.pose(true, 200_000_000L, Keyboard.KEY_A);
        long released = 316_000_000L;
        LostTalesInputIconAnimation.Pose held = animation.pose(
                true, released - 1L, Keyboard.KEY_A);
        LostTalesInputIconAnimation.Pose release = animation.pose(
                false, released, Keyboard.KEY_A);

        assertEquals(LostTalesInputIconAnimation.RELEASE_FRAME,
                release.getFrame());
        assertEquals(held.getOffsetX(), release.getOffsetX(), 0.00001F);
        assertEquals(held.getOffsetY(), release.getOffsetY(), 0.00001F);
        assertEquals(held.getRotationDegrees(),
                release.getRotationDegrees(), 0.00001F);
        assertEquals(held.getScaleX(), release.getScaleX(), 0.00001F);
        assertEquals(held.getScaleY(), release.getScaleY(), 0.00001F);
    }

    @Test
    public void longerPressStoresMoreReleaseEnergyThanAQuickTap() {
        LostTalesInputIconAnimation quick =
                new LostTalesInputIconAnimation();
        LostTalesInputIconAnimation held =
                new LostTalesInputIconAnimation();
        quick.pose(true, 1L, Keyboard.KEY_A);
        held.pose(true, 1L, Keyboard.KEY_A);
        long quickRelease = 20_000_000L;
        long heldRelease = 220_000_000L;
        quick.pose(false, quickRelease, Keyboard.KEY_A);
        held.pose(true, heldRelease - 1L, Keyboard.KEY_A);
        held.pose(false, heldRelease, Keyboard.KEY_A);

        LostTalesInputIconAnimation.Pose quickStretch = quick.pose(false,
                quickRelease + LostTalesInputIconAnimation.RELEASE_FRAME_NANOS,
                Keyboard.KEY_A);
        LostTalesInputIconAnimation.Pose heldStretch = held.pose(false,
                heldRelease + LostTalesInputIconAnimation.RELEASE_FRAME_NANOS,
                Keyboard.KEY_A);
        assertTrue(heldStretch.getScaleY() > quickStretch.getScaleY());
        assertTrue(heldStretch.getOffsetY() < quickStretch.getOffsetY());
    }
}
