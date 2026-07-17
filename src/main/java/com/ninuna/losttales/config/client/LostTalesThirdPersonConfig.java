package com.ninuna.losttales.config.client;

import com.ninuna.losttales.client.camera.CameraPresetId;
import com.ninuna.losttales.client.camera.CameraPresetFileStore;
import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Client-only options for the optional third-person camera overhaul. */
public final class LostTalesThirdPersonConfig {
    public static final String CATEGORY_CAMERA = "third_person_camera";
    private static final String FILE_NAME = "losttales-third-person.cfg";
    private static final double ORIGINAL_HEAD_TRACKING_ANGLE = 35.0D;
    private static final double PREVIOUS_HEAD_TRACKING_ANGLE = 65.0D;
    private static final double RECENT_HEAD_TRACKING_ANGLE = 70.0D;
    private static final double DEFAULT_HEAD_TRACKING_ANGLE = 80.0D;

    private static File loadedConfigFile;
    private static Configuration pendingGuiConfiguration;

    public static boolean enabled = false;
    public static String cameraPreset =
            CameraPresetId.MODERN_ACTION_RPG.getConfigValue();
    public static boolean enableFovEffects = true;
    public static boolean enableTargetCrosshair = true;
    public static boolean enableCameraIntentTargeting = true;
    public static boolean enableProjectileAimCorrection = true;
    public static boolean enableProjectilePrediction = true;
    public static double projectileAimDistance = 96.0D;
    public static boolean enableCameraMotion = true;
    public static double cameraMotionMultiplier = 1.0D;
    public static double airborneMotionMultiplier = 1.0D;
    public static double landingMotionMultiplier = 1.0D;
    public static double ridingMotionMultiplier = 1.0D;
    public static double swimmingMotionMultiplier = 1.0D;
    public static double attackMotionMultiplier = 1.0D;
    public static double damageMotionMultiplier = 1.0D;
    public static double explosionMotionMultiplier = 1.0D;
    public static double explosionMotionRadius = 32.0D;
    public static double distanceMultiplier = 1.0D;
    public static double minimumZoomDistance = 1.35D;
    public static double maximumZoomDistance = 8.0D;
    public static double zoomStep = 0.30D;
    public static double shoulderOffsetMultiplier = 1.0D;
    public static double verticalOffsetMultiplier = 1.0D;
    public static double transitionSpeedMultiplier = 1.0D;
    public static double collisionPadding = 0.12D;
    public static double collisionReleaseRate = 10.0D;
    public static boolean defaultRightShoulder = true;
    public static boolean enableDirectionalMovement = true;
    public static double bodyRotationSpeed = 540.0D;
    public static double sprintBodyRotationSpeed = 720.0D;
    public static double headTrackingAngle = DEFAULT_HEAD_TRACKING_ANGLE;
    public static double headTrackingSpeed = 720.0D;
    public static boolean combatProfileWithWeaponHeld = true;
    public static double combatProfileHoldSeconds = 3.0D;
    public static double attackCommitmentSeconds = 0.25D;
    public static double aimingBodyRotationSpeed = 720.0D;
    public static double attackBodyRotationSpeed = 900.0D;
    public static boolean enableSwimmingDirectionalMovement = true;

    private LostTalesThirdPersonConfig() {}

    public static void load(File configDirectory) {
        if (configDirectory == null) {
            throw new IllegalArgumentException("configDirectory is required");
        }
        loadedConfigFile = new File(configDirectory, FILE_NAME);
        loadConfiguration(new Configuration(loadedConfigFile));
    }

    public static void reload() {
        if (loadedConfigFile != null) {
            loadConfiguration(new Configuration(loadedConfigFile));
        }
    }

    public static synchronized Configuration createConfiguration() {
        pendingGuiConfiguration = loadedConfigFile == null
                ? null : new Configuration(loadedConfigFile);
        return pendingGuiConfiguration;
    }

    /** Saves the exact Configuration whose properties the Forge GUI edited. */
    public static synchronized void savePendingGuiConfiguration() {
        Configuration pending = pendingGuiConfiguration;
        if (pending != null && pending.hasChanged()) {
            pending.save();
        }
    }

    public static void applyGuiMetadata(Configuration config) {
        if (config != null && config.hasCategory(CATEGORY_CAMERA)) {
            config.getCategory(CATEGORY_CAMERA).setLanguageKey(
                    "losttales.config.category.client.thirdPersonCamera");
        }
    }

    private static void loadConfiguration(Configuration config) {
        try {
            config.load();

            enabled = config.getBoolean(
                    "enabled", CATEGORY_CAMERA, enabled,
                    "Enable the Lost Tales third-person overhaul. F5 uses only first and rear third person while enabled; disabling the overhaul restores vanilla's front-facing F5 view.");
            Property presetProperty = config.get(
                    CATEGORY_CAMERA, "cameraPreset", cameraPreset,
                    "Base camera framing preset loaded from config/losttales/camera_presets. The multipliers below fine-tune the selected preset.");
            cameraPreset = CameraPresetFileStore.normalizeId(
                    presetProperty.getString());
            CameraPresetFileStore.ensureLoaded();
            if (CameraPresetFileStore.getDefinition(cameraPreset) == null) {
                cameraPreset = CameraPresetId.MODERN_ACTION_RPG
                        .getConfigValue();
            }
            if (!cameraPreset.equals(presetProperty.getString())) {
                presetProperty.set(cameraPreset);
            }
            enableFovEffects = config.getBoolean(
                    "enableFovEffects", CATEGORY_CAMERA, enableFovEffects,
                    "Apply the restrained movement-profile FOV offsets.");
            enableTargetCrosshair = config.getBoolean(
                    "enableTargetCrosshair", CATEGORY_CAMERA,
                    enableTargetCrosshair,
                    "Draw a fixed screen-center third-person reticle for the camera-intent ray. The validated target may change behind it, but the reticle never drifts or jitters. Vanilla first-person and disabled-overhaul crosshairs are unchanged.");
            enableCameraIntentTargeting = config.getBoolean(
                    "enableCameraIntentTargeting", CATEGORY_CAMERA,
                    enableCameraIntentTargeting,
                    "Aim from the camera center, then validate the result from the player's eye using vanilla reach and line of sight. Disable this compatibility fallback to retain vanilla eye-forward targeting.");
            enableProjectileAimCorrection = config.getBoolean(
                    "enableProjectileAimCorrection", CATEGORY_CAMERA,
                    enableProjectileAimCorrection,
                    "Align supported vanilla and LOTR projectiles with the long-range third-person crosshair. The server limits the submitted direction to the player's current look cone.");
            enableProjectilePrediction = config.getBoolean(
                    "enableProjectilePrediction", CATEGORY_CAMERA,
                    enableProjectilePrediction,
                    "Draw a depth-tested 3D trajectory arc for supported vanilla and LOTR projectiles. This client-only preview never moves the fixed crosshair or changes server projectile physics.");
            projectileAimDistance = getClampedDouble(
                    config, "projectileAimDistance",
                    projectileAimDistance, 16.0D, 256.0D,
                    "Maximum raycast distance for the ranged third-person crosshair. This does not change projectile speed, damage, gravity, or entity tracking range.");
            enableCameraMotion = config.getBoolean(
                    "enableCameraMotion", CATEGORY_CAMERA,
                    enableCameraMotion,
                    "Enable bounded world-follow motion, stride and idle sway, and the situational airborne, riding, swimming, combat, damage, and explosion effects below. Target validation and the fixed crosshair remain stable.");
            cameraMotionMultiplier = getClampedDouble(
                    config, "cameraMotionMultiplier",
                    cameraMotionMultiplier, 0.0D, 2.0D,
                    "Master scale for all follow, locomotion, idle, and situational camera translation. Zero disables camera motion while retaining profile framing transitions.");
            airborneMotionMultiplier = getClampedDouble(
                    config, "airborneMotionMultiplier",
                    airborneMotionMultiplier, 0.0D, 2.0D,
                    "Scales the restrained vertical camera lag while jumping or falling. Zero disables airborne motion.");
            landingMotionMultiplier = getClampedDouble(
                    config, "landingMotionMultiplier",
                    landingMotionMultiplier, 0.0D, 2.0D,
                    "Scales the landing rebound according to fall speed. Zero disables landing motion.");
            ridingMotionMultiplier = getClampedDouble(
                    config, "ridingMotionMultiplier",
                    ridingMotionMultiplier, 0.0D, 2.0D,
                    "Scales speed-linked mount cadence in the riding camera profile. Zero disables mount-specific motion.");
            swimmingMotionMultiplier = getClampedDouble(
                    config, "swimmingMotionMultiplier",
                    swimmingMotionMultiplier, 0.0D, 2.0D,
                    "Scales the slow directional drift used by the swimming camera profile. Zero disables swimming-specific motion.");
            attackMotionMultiplier = getClampedDouble(
                    config, "attackMotionMultiplier",
                    attackMotionMultiplier, 0.0D, 2.0D,
                    "Scales the small forward camera impulse at the beginning of a player attack. Zero disables attack motion.");
            damageMotionMultiplier = getClampedDouble(
                    config, "damageMotionMultiplier",
                    damageMotionMultiplier, 0.0D, 2.0D,
                    "Scales the short camera reaction when the player takes damage. Zero disables damage motion.");
            explosionMotionMultiplier = getClampedDouble(
                    config, "explosionMotionMultiplier",
                    explosionMotionMultiplier, 0.0D, 2.0D,
                    "Scales distance-faded camera shake from nearby explosion sounds. Zero disables explosion motion.");
            explosionMotionRadius = getClampedDouble(
                    config, "explosionMotionRadius",
                    explosionMotionRadius, 8.0D, 64.0D,
                    "Maximum distance in blocks at which an explosion can shake the third-person camera.");
            distanceMultiplier = getClampedDouble(
                    config, "distanceMultiplier", distanceMultiplier,
                    0.50D, 2.00D,
                    "Multiplier for every profile's camera distance.");
            minimumZoomDistance = getClampedDouble(
                    config, "minimumZoomDistance",
                    minimumZoomDistance, 0.75D, 4.0D,
                    "Closest distance allowed when holding the Lost Tales modifier key and scrolling in.");
            maximumZoomDistance = getClampedDouble(
                    config, "maximumZoomDistance",
                    maximumZoomDistance, 2.0D, 16.0D,
                    "Farthest distance allowed when holding the Lost Tales modifier key and scrolling out.");
            if (maximumZoomDistance < minimumZoomDistance) {
                maximumZoomDistance = minimumZoomDistance;
                config.get(CATEGORY_CAMERA, "maximumZoomDistance",
                        maximumZoomDistance).set(maximumZoomDistance);
            }
            zoomStep = getClampedDouble(
                    config, "zoomStep", zoomStep,
                    0.05D, 1.0D,
                    "Distance changed by each modifier-key mouse-wheel step.");
            shoulderOffsetMultiplier = getClampedDouble(
                    config, "shoulderOffsetMultiplier",
                    shoulderOffsetMultiplier, 0.00D, 2.00D,
                    "Multiplier for the left/right shoulder offset.");
            verticalOffsetMultiplier = getClampedDouble(
                    config, "verticalOffsetMultiplier",
                    verticalOffsetMultiplier, 0.00D, 2.00D,
                    "Multiplier for the camera's vertical framing offset.");
            transitionSpeedMultiplier = getClampedDouble(
                    config, "transitionSpeedMultiplier",
                    transitionSpeedMultiplier, 0.25D, 3.00D,
                    "Multiplier for frame-rate-independent camera transition speeds.");
            collisionPadding = getClampedDouble(
                    config, "collisionPadding", collisionPadding,
                    0.02D, 0.40D,
                    "Camera collision padding in blocks. Larger values keep the view farther from surfaces.");
            collisionReleaseRate = getClampedDouble(
                    config, "collisionReleaseRate", collisionReleaseRate,
                    1.00D, 30.00D,
                    "How quickly the camera moves back out after an obstruction clears.");
            defaultRightShoulder = config.getBoolean(
                    "defaultRightShoulder", CATEGORY_CAMERA,
                    defaultRightShoulder,
                    "Start each world/session over the player's right shoulder.");
            enableDirectionalMovement = config.getBoolean(
                    "enableDirectionalMovement", CATEGORY_CAMERA,
                    enableDirectionalMovement,
                    "Turn the locally rendered player body toward camera-relative movement while keeping vanilla look, targeting, and network direction authoritative.");
            bodyRotationSpeed = getClampedDouble(
                    config, "bodyRotationSpeed", bodyRotationSpeed,
                    90.0D, 1080.0D,
                    "Normal body turn speed in degrees per second.");
            sprintBodyRotationSpeed = getClampedDouble(
                    config, "sprintBodyRotationSpeed",
                    sprintBodyRotationSpeed, 90.0D, 1440.0D,
                    "Sprinting body turn speed in degrees per second.");
            headTrackingAngle = getHeadTrackingAngle(config);
            headTrackingSpeed = getClampedDouble(
                    config, "headTrackingSpeed", headTrackingSpeed,
                    180.0D, 1440.0D,
                    "Visual head turn speed in degrees per second, including the smooth transition between normal and reverse camera tracking.");
            combatProfileWithWeaponHeld = config.getBoolean(
                    "combatProfileWithWeaponHeld", CATEGORY_CAMERA,
                    combatProfileWithWeaponHeld,
                    "Use the combat camera while a recognized vanilla, Lost Tales, or LOTR weapon is held.");
            combatProfileHoldSeconds = getClampedDouble(
                    config, "combatProfileHoldSeconds",
                    combatProfileHoldSeconds, 0.0D, 10.0D,
                    "How long the combat camera remains after attacking, aiming, or taking damage.");
            attackCommitmentSeconds = getClampedDouble(
                    config, "attackCommitmentSeconds",
                    attackCommitmentSeconds, 0.0D, 1.0D,
                    "How long a melee swing visually commits the body to its attack direction. This never blocks or changes server movement input.");
            aimingBodyRotationSpeed = getClampedDouble(
                    config, "aimingBodyRotationSpeed",
                    aimingBodyRotationSpeed, 90.0D, 1440.0D,
                    "Body turn speed in degrees per second while actively aiming a supported ranged weapon.");
            attackBodyRotationSpeed = getClampedDouble(
                    config, "attackBodyRotationSpeed",
                    attackBodyRotationSpeed, 90.0D, 1440.0D,
                    "Maximum visual body turn speed during and immediately after an attack.");
            enableSwimmingDirectionalMovement = config.getBoolean(
                    "enableSwimmingDirectionalMovement", CATEGORY_CAMERA,
                    enableSwimmingDirectionalMovement,
                    "Keep camera-relative visual body facing while swimming. Disable to retain vanilla swimming body rotation.");
            removeObsoleteProperties(config);
            applyGuiMetadata(config);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    private static double getClampedDouble(
            Configuration config, String key, double defaultValue,
            double minimum, double maximum, String comment) {
        Property property = config.get(
                CATEGORY_CAMERA, key, defaultValue, comment,
                minimum, maximum);
        double value = property.getDouble(defaultValue);
        double clamped = Math.max(minimum, Math.min(maximum, value));
        if (clamped != value) {
            property.set(clamped);
        }
        return clamped;
    }

    private static double getHeadTrackingAngle(Configuration config) {
        Property property = config.get(
                CATEGORY_CAMERA, "headTrackingAngle",
                headTrackingAngle,
                "Maximum side angle for normal camera-relative head tracking. Crossing this angle selects the opposite camera-facing direction; the visual head then turns smoothly across without a forward-only dead zone.",
                0.0D, 85.0D);
        double value = property.getDouble(headTrackingAngle);
        if (isPreviousHeadTrackingDefault(value)) {
            value = DEFAULT_HEAD_TRACKING_ANGLE;
            property.set(value);
        }
        double clamped = Math.max(0.0D, Math.min(85.0D, value));
        if (clamped != value) {
            property.set(clamped);
        }
        return clamped;
    }

    private static boolean isPreviousHeadTrackingDefault(double value) {
        return Math.abs(value - ORIGINAL_HEAD_TRACKING_ANGLE) < 0.000001D
                || Math.abs(value - PREVIOUS_HEAD_TRACKING_ANGLE)
                < 0.000001D
                || Math.abs(value - RECENT_HEAD_TRACKING_ANGLE)
                < 0.000001D;
    }

    private static void removeObsoleteProperties(Configuration config) {
        if (config == null || !config.hasCategory(CATEGORY_CAMERA)) {
            return;
        }
        config.getCategory(CATEGORY_CAMERA).remove(
                "crosshairSmoothingSpeed");
        config.getCategory(CATEGORY_CAMERA).remove(
                "maximumHeadTurnAngle");
        config.getCategory(CATEGORY_CAMERA).remove(
                "enableAutomaticRecentering");
        config.getCategory(CATEGORY_CAMERA).remove(
                "automaticRecenteringDelaySeconds");
        config.getCategory(CATEGORY_CAMERA).remove(
                "automaticRecenteringSpeed");
    }
}
