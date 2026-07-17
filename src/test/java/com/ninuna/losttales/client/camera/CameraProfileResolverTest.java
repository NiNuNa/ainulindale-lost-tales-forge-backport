package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CameraProfileResolverTest {
    @Test
    public void locomotionProfilesUseSafePriorityOrder() {
        assertEquals(CameraProfileId.STANDING, resolve(false, false,
                false, false, false));
        assertEquals(CameraProfileId.MOVING, resolve(false, false,
                false, false, true));
        assertEquals(CameraProfileId.SPRINTING, resolve(false, false,
                false, true, true));
        assertEquals(CameraProfileId.SNEAKING, resolve(false, false,
                true, true, true));
        assertEquals(CameraProfileId.SWIMMING, resolve(false, true,
                true, true, true));
        assertEquals(CameraProfileId.RIDING, resolve(true, true,
                true, true, true));
    }

    @Test
    public void actionProfilesUseDeterministicPriority() {
        assertEquals(CameraProfileId.COMBAT,
                resolveActions(false, false,
                        false, false, true));
        assertEquals(CameraProfileId.ATTACKING,
                resolveActions(false, false,
                        false, true, true));
        assertEquals(CameraProfileId.AIMING,
                resolveActions(false, false,
                        true, true, true));
        assertEquals(CameraProfileId.SWIMMING,
                resolveActions(false, true,
                        true, true, true));
        assertEquals(CameraProfileId.RIDING,
                resolveActions(true, true,
                        true, true, true));
    }

    private static CameraProfileId resolve(
            boolean riding, boolean swimming, boolean sneaking,
            boolean sprinting, boolean moving) {
        return CameraProfileResolver.resolve(
                riding, swimming, sneaking, sprinting, moving);
    }

    private static CameraProfileId resolveActions(
            boolean riding, boolean swimming,
            boolean aiming, boolean attacking, boolean combat) {
        return CameraProfileResolver.resolve(
                riding, swimming, aiming, attacking, combat,
                false, false, false);
    }
}
