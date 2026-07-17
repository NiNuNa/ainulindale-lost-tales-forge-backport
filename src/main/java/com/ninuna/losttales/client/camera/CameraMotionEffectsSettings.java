package com.ninuna.losttales.client.camera;

/** User-facing multipliers for situational camera motion. */
public final class CameraMotionEffectsSettings {
    public static final CameraMotionEffectsSettings NONE =
            new CameraMotionEffectsSettings(
                    0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D);

    private final double airborneMultiplier;
    private final double landingMultiplier;
    private final double ridingMultiplier;
    private final double swimmingMultiplier;
    private final double attackMultiplier;
    private final double damageMultiplier;
    private final double explosionMultiplier;

    public CameraMotionEffectsSettings(
            double airborneMultiplier, double landingMultiplier,
            double ridingMultiplier, double swimmingMultiplier,
            double attackMultiplier, double damageMultiplier,
            double explosionMultiplier) {
        CameraMath.requireNonNegativeFinite(
                "airborneMultiplier", airborneMultiplier);
        CameraMath.requireNonNegativeFinite(
                "landingMultiplier", landingMultiplier);
        CameraMath.requireNonNegativeFinite(
                "ridingMultiplier", ridingMultiplier);
        CameraMath.requireNonNegativeFinite(
                "swimmingMultiplier", swimmingMultiplier);
        CameraMath.requireNonNegativeFinite(
                "attackMultiplier", attackMultiplier);
        CameraMath.requireNonNegativeFinite(
                "damageMultiplier", damageMultiplier);
        CameraMath.requireNonNegativeFinite(
                "explosionMultiplier", explosionMultiplier);
        this.airborneMultiplier = airborneMultiplier;
        this.landingMultiplier = landingMultiplier;
        this.ridingMultiplier = ridingMultiplier;
        this.swimmingMultiplier = swimmingMultiplier;
        this.attackMultiplier = attackMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.explosionMultiplier = explosionMultiplier;
    }

    public double getAirborneMultiplier() {
        return airborneMultiplier;
    }

    public double getLandingMultiplier() {
        return landingMultiplier;
    }

    public double getRidingMultiplier() {
        return ridingMultiplier;
    }

    public double getSwimmingMultiplier() {
        return swimmingMultiplier;
    }

    public double getAttackMultiplier() {
        return attackMultiplier;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public double getExplosionMultiplier() {
        return explosionMultiplier;
    }
}
