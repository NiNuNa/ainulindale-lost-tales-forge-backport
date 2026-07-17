package com.ninuna.losttales.client.camera;

/** Deterministic locomotion-profile priority independent of Minecraft classes. */
public final class CameraProfileResolver {
    private CameraProfileResolver() {}

    public static CameraProfileId resolve(
            boolean riding, boolean swimming, boolean sneaking,
            boolean sprinting, boolean moving) {
        return resolve(riding, swimming, false, false, false,
                sneaking, sprinting, moving);
    }

    public static CameraProfileId resolve(
            boolean riding, boolean swimming,
            boolean aiming, boolean attacking, boolean combat,
            boolean sneaking, boolean sprinting, boolean moving) {
        if (riding) {
            return CameraProfileId.RIDING;
        }
        if (swimming) {
            return CameraProfileId.SWIMMING;
        }
        if (aiming) {
            return CameraProfileId.AIMING;
        }
        if (attacking) {
            return CameraProfileId.ATTACKING;
        }
        if (combat) {
            return CameraProfileId.COMBAT;
        }
        if (sneaking) {
            return CameraProfileId.SNEAKING;
        }
        if (sprinting) {
            return CameraProfileId.SPRINTING;
        }
        return moving ? CameraProfileId.MOVING : CameraProfileId.STANDING;
    }
}
