package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CameraMotionEffectsStateTest {
    @Test
    public void fallingCreatesBoundedVerticalFollowMotion() {
        CameraMotionEffectsState state = new CameraMotionEffectsState();
        state.reset(grounded(false, 0));

        CameraMotionOffset offset = state.update(
                sample(true, false, false, false,
                        false, 0, -1.0D, 0.0D),
                settings(), 0.05D);

        assertTrue(offset.getVertical() > 0.0D);
        assertTrue(offset.getForward() < 0.0D);
        assertTrue(offset.getVertical() < 0.10D);
    }

    @Test
    public void landingUsesThePreviousFallSpeed() {
        CameraMotionEffectsState state = new CameraMotionEffectsState();
        state.reset(sample(true, false, false, false,
                false, 0, -1.0D, 0.0D));

        CameraMotionOffset offset = state.update(
                grounded(false, 0), settings(), 0.05D);

        assertTrue(magnitude(offset) > 0.001D);
    }

    @Test
    public void ridingAndSwimmingHaveIndependentCadence() {
        CameraMotionEffectsState riding = new CameraMotionEffectsState();
        CameraMotionEffectsState swimming = new CameraMotionEffectsState();
        CameraMotionEffectsSample rideSample = sample(
                false, false, true, false,
                false, 0, 0.0D, 4.0D);
        CameraMotionEffectsSample swimSample = sample(
                false, false, false, true,
                false, 0, 0.0D, 2.0D);
        riding.reset(rideSample);
        swimming.reset(swimSample);

        CameraMotionOffset rideOffset = riding.update(
                rideSample, settings(), 0.10D);
        CameraMotionOffset swimOffset = swimming.update(
                swimSample, settings(), 0.10D);

        assertTrue(magnitude(rideOffset) > 0.001D);
        assertTrue(magnitude(swimOffset) > 0.001D);
        assertTrue(Math.abs(rideOffset.getSide()
                - swimOffset.getSide()) > 0.0001D);
    }

    @Test
    public void attackDamageAndExplosionImpulsesDecayToRest() {
        CameraMotionEffectsState state = new CameraMotionEffectsState();
        state.reset(grounded(false, 0));
        state.triggerExplosion(1.0D);
        CameraMotionOffset initial = state.update(
                grounded(true, 10), settings(), 0.05D);

        CameraMotionOffset settled = initial;
        for (int i = 0; i < 120; i++) {
            settled = state.update(
                    grounded(false, 0), settings(), 0.05D);
        }

        assertTrue(magnitude(initial) > 0.01D);
        assertTrue(magnitude(settled) < 0.0001D);
    }

    @Test
    public void zeroMultipliersDisableEverySituationalEffect() {
        CameraMotionEffectsState state = new CameraMotionEffectsState();
        state.reset(sample(true, false, true, true,
                false, 0, -1.0D, 4.0D));
        state.triggerExplosion(1.0D);

        CameraMotionOffset offset = state.update(
                sample(true, false, true, true,
                        true, 10, -1.0D, 4.0D),
                CameraMotionEffectsSettings.NONE, 0.05D);

        assertTrue(magnitude(offset) == 0.0D);
    }

    private static CameraMotionEffectsSettings settings() {
        return new CameraMotionEffectsSettings(
                1.0D, 1.0D, 1.0D, 1.0D,
                1.0D, 1.0D, 1.0D);
    }

    private static CameraMotionEffectsSample grounded(
            boolean swinging, int hurtTime) {
        return sample(false, true, false, false,
                swinging, hurtTime, 0.0D, 0.0D);
    }

    private static CameraMotionEffectsSample sample(
            boolean airborne, boolean onGround,
            boolean riding, boolean swimming,
            boolean swinging, int hurtTime,
            double verticalVelocity, double horizontalSpeed) {
        return new CameraMotionEffectsSample(
                airborne, onGround, riding, swimming,
                swinging, hurtTime,
                verticalVelocity, horizontalSpeed);
    }

    private static double magnitude(CameraMotionOffset offset) {
        return Math.sqrt(
                offset.getSide() * offset.getSide()
                        + offset.getVertical() * offset.getVertical()
                        + offset.getForward() * offset.getForward());
    }
}
