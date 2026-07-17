package com.ninuna.losttales.client.camera;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ThirdPersonCompatibilityPolicyTest {

    @Test
    public void missingCameraTransformerLeavesOverhaulInactive() {
        assertFalse(ThirdPersonCompatibilityPolicy
                .canActivateOverhaul(true, false));
        assertFalse(ThirdPersonCompatibilityPolicy
                .canActivateOverhaul(false, true));
        assertTrue(ThirdPersonCompatibilityPolicy
                .canActivateOverhaul(true, true));
    }

    @Test
    public void targetingRequiresBothNarrowHooks() {
        assertFalse(ThirdPersonCompatibilityPolicy
                .canUseAuthoritativeTargeting(true, true, false));
        assertFalse(ThirdPersonCompatibilityPolicy
                .canUseAuthoritativeTargeting(true, false, true));
        assertTrue(ThirdPersonCompatibilityPolicy
                .canUseAuthoritativeTargeting(true, true, true));
    }
}
