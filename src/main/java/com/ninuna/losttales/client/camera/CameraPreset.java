package com.ninuna.losttales.client.camera;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Complete set of camera profiles selected by the client configuration. */
public final class CameraPreset {
    private final Map<CameraProfileId, CameraProfile> profiles;

    public CameraPreset(Map<CameraProfileId, CameraProfile> profiles) {
        if (profiles == null) {
            throw new IllegalArgumentException("profiles are required");
        }
        EnumMap<CameraProfileId, CameraProfile> copy =
                new EnumMap<CameraProfileId, CameraProfile>(
                        CameraProfileId.class);
        copy.putAll(profiles);
        for (CameraProfileId id : CameraProfileId.values()) {
            CameraProfile profile = copy.get(id);
            if (profile == null || profile.getId() != id) {
                throw new IllegalArgumentException(
                        "Missing or mismatched camera profile " + id);
            }
        }
        this.profiles = Collections.unmodifiableMap(copy);
    }

    public CameraProfile get(CameraProfileId id) {
        if (id == null) {
            throw new IllegalArgumentException("profile id is required");
        }
        return profiles.get(id);
    }

    public static CameraPreset forId(CameraPresetId id) {
        return modernActionRpgDefaults();
    }

    /**
     * Original Lost Tales defaults for a close, modern action-RPG camera.
     * Values are intentionally restrained because Minecraft scale, collision,
     * and FOV differ significantly from third-party games.
     */
    public static CameraPreset modernActionRpgDefaults() {
        CameraSmoothing travel = new CameraSmoothing(
                9.0D, 6.5D, 15.0D, 9.0D, 12.0D, 10.0D, 6.0D);
        CameraSmoothing responsive = new CameraSmoothing(
                12.0D, 9.0D, 20.0D, 14.0D, 18.0D, 14.0D, 9.0D);
        EnumMap<CameraProfileId, CameraProfile> profiles =
                new EnumMap<CameraProfileId, CameraProfile>(
                        CameraProfileId.class);
        put(profiles, CameraProfileId.STANDING,
                2.65D, 0.62D, 0.25D, 0.0D, travel,
                motion(0.35D, 0.18D, 0.0D, 0.0D,
                        0.0D, 0.085D, 0.045D, 0.055D,
                        8.0D, 220.0D, 0.8D, 7.0D,
                        0.035D, 0.022D, 0.014D, 0.110D));
        put(profiles, CameraProfileId.MOVING,
                2.85D, 0.62D, 0.22D, 1.0D, travel,
                motion(0.45D, 0.22D, 0.035D, 0.018D,
                        0.012D, 0.075D, 0.040D, 0.050D,
                        8.0D, 240.0D, 0.8D, 7.0D,
                        0.030D, 0.018D, 0.012D, 0.110D));
        put(profiles, CameraProfileId.SPRINTING,
                3.20D, 0.52D, 0.15D, 3.0D, travel,
                motion(0.58D, 0.26D, 0.055D, 0.030D,
                        0.025D, 0.065D, 0.035D, 0.045D,
                        9.0D, 280.0D, 0.95D, 8.0D,
                        0.024D, 0.015D, 0.010D, 0.120D));
        put(profiles, CameraProfileId.SNEAKING,
                2.45D, 0.68D, 0.05D, -1.0D, travel,
                motion(0.30D, 0.14D, 0.018D, 0.010D,
                        0.008D, 0.070D, 0.040D, 0.045D,
                        7.0D, 220.0D, 0.65D, 6.0D,
                        0.028D, 0.018D, 0.011D, 0.095D));
        put(profiles, CameraProfileId.SWIMMING,
                3.05D, 0.45D, 0.20D, 2.0D, travel,
                motion(0.50D, 0.30D, 0.035D, 0.025D,
                        0.020D, 0.090D, 0.055D, 0.060D,
                        6.0D, 200.0D, 0.60D, 5.5D,
                        0.028D, 0.020D, 0.014D, 0.085D));
        put(profiles, CameraProfileId.RIDING,
                4.20D, 0.78D, 0.55D, 3.0D, travel,
                motion(0.65D, 0.35D, 0.040D, 0.025D,
                        0.025D, 0.100D, 0.055D, 0.070D,
                        6.0D, 220.0D, 0.50D, 5.5D,
                        0.035D, 0.022D, 0.014D, 0.080D));
        put(profiles, CameraProfileId.COMBAT,
                2.55D, 0.74D, 0.20D, 1.0D, responsive,
                motion(0.35D, 0.16D, 0.022D, 0.010D,
                        0.008D, 0.060D, 0.035D, 0.040D,
                        11.0D, 260.0D, 0.80D, 9.0D,
                        0.030D, 0.018D, 0.012D, 0.110D));
        put(profiles, CameraProfileId.AIMING,
                2.10D, 0.84D, 0.20D, -2.0D, responsive,
                motion(0.22D, 0.10D, 0.005D, 0.003D,
                        0.0D, 0.025D, 0.015D, 0.015D,
                        14.0D, 300.0D, 0.70D, 12.0D,
                        0.012D, 0.007D, 0.004D, 0.080D));
        put(profiles, CameraProfileId.ATTACKING,
                2.45D, 0.76D, 0.18D, 0.5D, responsive,
                motion(0.34D, 0.16D, 0.025D, 0.012D,
                        0.010D, 0.055D, 0.030D, 0.035D,
                        12.0D, 260.0D, 0.85D, 10.0D,
                        0.024D, 0.015D, 0.009D, 0.110D));
        return new CameraPreset(profiles);
    }

    private static void put(
            Map<CameraProfileId, CameraProfile> profiles,
            CameraProfileId id, double distance, double shoulderOffset,
            double verticalOffset, double fovOffset,
            CameraSmoothing smoothing, CameraMotionProfile motion) {
        profiles.put(id, new CameraProfile(
                id, distance, shoulderOffset, verticalOffset,
                fovOffset, smoothing, motion));
    }

    private static CameraMotionProfile motion(
            double horizontalFollowLimit, double verticalFollowLimit,
            double sideSway, double verticalSway, double forwardSway,
            double turnSway, double lookPitchSway,
            double lookForwardSway, double lookResponseRate,
            double lookReferenceSpeed, double swayCyclesPerBlock,
            double responseRate, double idleSideSway,
            double idleVerticalSway, double idleForwardSway,
            double idleCyclesPerSecond) {
        return new CameraMotionProfile(
                horizontalFollowLimit, verticalFollowLimit,
                sideSway, verticalSway, forwardSway,
                turnSway, lookPitchSway, lookForwardSway,
                lookResponseRate, lookReferenceSpeed,
                swayCyclesPerBlock, responseRate,
                idleSideSway, idleVerticalSway,
                idleForwardSway, idleCyclesPerSecond);
    }
}
