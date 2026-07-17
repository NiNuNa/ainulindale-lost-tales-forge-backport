package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CameraPresetTest {
    @Test
    public void modernPresetDefinesEverySupportedState() {
        CameraPreset preset = CameraPreset.modernActionRpgDefaults();
        for (CameraProfileId id : CameraProfileId.values()) {
            assertNotNull(id.name(), preset.get(id));
            assertEquals(id, preset.get(id).getId());
        }
    }

    @Test
    public void modernPresetUsesResponsiveActionFraming() {
        CameraPreset preset = CameraPreset.modernActionRpgDefaults();
        CameraProfile standing = preset.get(CameraProfileId.STANDING);
        CameraProfile sprinting = preset.get(CameraProfileId.SPRINTING);
        CameraProfile aiming = preset.get(CameraProfileId.AIMING);
        CameraProfile riding = preset.get(CameraProfileId.RIDING);

        assertTrue(aiming.getDistance() < standing.getDistance());
        assertTrue(aiming.getShoulderOffset()
                > standing.getShoulderOffset());
        assertTrue(sprinting.getDistance() > standing.getDistance());
        assertTrue(sprinting.getFovOffset() > standing.getFovOffset());
        assertTrue(riding.getDistance() > sprinting.getDistance());
        assertTrue(aiming.getSmoothing().getRotationRate()
                > standing.getSmoothing().getRotationRate());
    }

    @Test
    public void everyNamedPresetDefinesEverySupportedState() {
        for (CameraPresetId presetId : CameraPresetId.values()) {
            CameraPreset preset = CameraPreset.forId(presetId);
            for (CameraProfileId profileId : CameraProfileId.values()) {
                assertNotNull(presetId + ":" + profileId,
                        preset.get(profileId));
            }
        }
    }

    @Test
    public void modernIsTheOnlyBundledPresetAndProvidesCameraMotion() {
        assertEquals(1, CameraPresetId.values().length);
        CameraMotionProfile standing = CameraPreset.forId(
                CameraPresetId.MODERN_ACTION_RPG)
                .get(CameraProfileId.STANDING).getMotion();
        assertTrue(standing.getIdleSideSway() > 0.0D);
        assertTrue(standing.getIdleVerticalSway() > 0.0D);
        assertTrue(standing.getIdleForwardSway() > 0.0D);
        assertTrue(standing.getIdleCyclesPerSecond() > 0.0D);
        assertTrue(standing.getTurnSway() > 0.0D);
        assertTrue(standing.getLookPitchSway() > 0.0D);
        assertTrue(standing.getLookForwardSway() > 0.0D);
        assertTrue(standing.getLookResponseRate() > 0.0D);
        assertTrue(standing.getLookReferenceSpeed() > 0.0D);
    }

    @Test
    public void overhaulPerspectiveCycleHasNoFrontFacingMode() {
        assertEquals(CameraPerspective.THIRD_PERSON,
                CameraPerspective.FIRST_PERSON.next());
        assertEquals(CameraPerspective.FIRST_PERSON,
                CameraPerspective.THIRD_PERSON.next());
        assertEquals(0,
                CameraPerspective.THIRD_PERSON.next().getVanillaValue());
        assertEquals(CameraPerspective.THIRD_PERSON,
                CameraPerspective.fromVanillaValue(2));
    }

    @Test
    public void disabledOverhaulPreservesVanillaFrontFacingMode() {
        assertEquals(2, CameraPerspective.normalizeVanillaValue(2, false));
        assertEquals(0, CameraPerspective.normalizeVanillaValue(2, true));
        assertEquals(1, CameraPerspective.normalizeVanillaValue(1, true));
    }
}
