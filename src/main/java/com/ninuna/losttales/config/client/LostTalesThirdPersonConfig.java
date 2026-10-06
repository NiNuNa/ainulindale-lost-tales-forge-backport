package com.ninuna.losttales.config.client;

import com.ninuna.losttales.client.camera.CameraPresetId;
import com.ninuna.losttales.client.camera.CameraPresetFileStore;
import com.ninuna.losttales.config.LostTalesConfigDefinitions;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import com.ninuna.losttales.config.LostTalesConfigWords;
import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Client-only options for the optional third-person camera overhaul, in
 * their own file: set in Client Settings, Camera, and saved the moment one
 * changes ({@link #save}). An option's name and comment are lines of the
 * lang file ({@link LostTalesConfigWords}), not words in the code.
 */
public final class LostTalesThirdPersonConfig {
    public static final String CATEGORY_CAMERA = "third_person_camera";
    private static final double DEFAULT_HEAD_TRACKING_ANGLE = 100.0D;
    private static final double MAXIMUM_HEAD_TRACKING_ANGLE = 120.0D;

    private static File loadedConfigFile;
    /**
     * Every camera option as the mod ships it
     * ({@link LostTalesConfigDefinitions}): read on the first load against
     * no file, while each field still holds its shipped value.
     */
    private static Configuration shipped;
    /** What every option is read with where Forge asks for a comment. */
    private static final String TIP = LostTalesConfigWords.TIP;

    public static boolean enabled = false;
    public static String cameraPreset =
            CameraPresetId.MODERN_ACTION_RPG.getConfigValue();
    public static boolean enableFovEffects = true;
    public static boolean enableTargetCrosshair = true;
    public static boolean enableCameraIntentTargeting = true;
    public static boolean enableTargetLock = true;
    public static boolean enableTargetLockIndicator = true;
    public static double targetLockSelectionRange = 24.0D;
    public static double targetLockReleaseRange = 28.0D;
    public static double targetLockSelectionAngle = 65.0D;
    public static double targetLockYawSpeed = 420.0D;
    public static double targetLockPitchSpeed = 300.0D;
    public static double targetLockHeightFactor = 0.65D;
    public static double targetLockLineOfSightGraceSeconds = 0.75D;
    public static boolean enableProjectileAimCorrection = true;
    public static boolean enableProjectilePrediction = true;
    public static double projectileAimDistance = 96.0D;
    public static int projectileTrajectorySamplesPerTick = 6;
    public static double projectileTrajectorySmoothing = 0.40D;
    public static double projectileTrajectoryOriginBlendDistance = 3.0D;
    public static double projectileTrajectoryLineWidth = 1.5D;
    public static double projectileTrajectoryOpacity = 0.78D;
    public static boolean enableChargeTierFeedback = true;
    public static boolean enableChargeTierParticles = true;
    public static boolean enableChargeTierSounds = true;
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
    public static double headTrackingHysteresisAngle = 6.0D;
    public static double headTrackingTransitionSeconds = 0.30D;
    public static boolean combatProfileWithWeaponHeld = true;
    public static double combatProfileHoldSeconds = 3.0D;
    public static double attackCommitmentSeconds = 0.25D;
    public static double aimingBodyRotationSpeed = 720.0D;
    public static double attackBodyRotationSpeed = 900.0D;
    public static boolean enableSwimmingDirectionalMovement = true;

    private LostTalesThirdPersonConfig() {}

    /** Reads the camera options from the client's folder under Forge's config directory. */
    public static void load(File configDirectory) {
        loadedConfigFile = LostTalesConfigFiles.clientFile(configDirectory,
                LostTalesConfigFiles.CAMERA_OPTIONS);
        if (shipped == null) {
            // Nothing has changed a field yet: read against no file, every
            // option keeps its shipped value, and what is left is each
            // option as it is defined.
            Configuration definitions = new Configuration();
            defineOptions(definitions);
            shipped = definitions;
        }
        readOptions(new Configuration(loadedConfigFile), true);
    }

    /**
     * Reads every camera option into {@code definitions}, a configuration
     * of no file, as it is defined: the first load's first step, taken
     * while each field still holds its shipped value. Each option's
     * comment is then its English tip from the lang file.
     */
    static void defineOptions(Configuration definitions) {
        readOptions(definitions, false);
        LostTalesConfigWords.apply(definitions);
    }

    /**
     * Writes every camera option as its field holds it now to the file,
     * each in its shipped definition; nothing before the first load.
     */
    public static synchronized void save() {
        if (loadedConfigFile == null) {
            return;
        }
        Configuration config = new Configuration(loadedConfigFile);
        try {
            config.load();
            writeCurrentValues(config);
            applyGuiMetadata(config);
            applyShippedDefinitions(config);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    /**
     * A camera option as the mod ships it, as the file writes it; null
     * before the first load, or for a key the file does not hold. What
     * Settings' Default and Restore Defaults put back.
     */
    public static String shippedValue(String key) {
        Configuration definitions = shipped;
        if (definitions == null || !definitions.hasCategory(CATEGORY_CAMERA)) {
            return null;
        }
        Property property = definitions.getCategory(CATEGORY_CAMERA).get(key);
        return property == null ? null : property.getString();
    }

    /**
     * A number camera option's bounds as it is defined, {min, max}; null
     * before the first load, or for an option with none.
     */
    public static double[] shippedBounds(String key) {
        return LostTalesConfigDefinitions.bounds(shipped, CATEGORY_CAMERA, key);
    }

    public static void applyGuiMetadata(Configuration config) {
        if (config != null && config.hasCategory(CATEGORY_CAMERA)) {
            config.getCategory(CATEGORY_CAMERA).setLanguageKey(
                    "losttales.config.category.client.thirdPersonCamera");
        }
    }

    /**
     * Gives every camera option of {@code config} the definition the mod
     * ships it with — default, comment, bounds, words — and leaves what
     * each is set to alone ({@link LostTalesConfigDefinitions}). Nothing
     * before the first load.
     */
    public static void applyShippedDefinitions(Configuration config) {
        LostTalesConfigDefinitions.apply(shipped, config);
    }

    /**
     * Reads every camera option of {@code config} into its field, the
     * field's own value standing as the default of an option the
     * configuration does not hold. From the file ({@code fromFiles}) the
     * configuration is loaded first and written back, every option in
     * its shipped definition, when anything changed; a configuration of
     * no file is only read.
     */
    private static void readOptions(Configuration config, boolean fromFiles) {
        try {
            if (fromFiles) {
                config.load();
            }

            enabled = config.getBoolean(
                    "enabled", CATEGORY_CAMERA, enabled, TIP);
            Property presetProperty = config.get(
                    CATEGORY_CAMERA, "cameraPreset", cameraPreset, TIP);
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
                    "enableFovEffects", CATEGORY_CAMERA, enableFovEffects, TIP);
            enableTargetCrosshair = config.getBoolean(
                    "enableTargetCrosshair", CATEGORY_CAMERA,
                    enableTargetCrosshair, TIP);
            enableCameraIntentTargeting = config.getBoolean(
                    "enableCameraIntentTargeting", CATEGORY_CAMERA,
                    enableCameraIntentTargeting, TIP);
            enableTargetLock = config.getBoolean(
                    "enableTargetLock", CATEGORY_CAMERA,
                    enableTargetLock, TIP);
            enableTargetLockIndicator = config.getBoolean(
                    "enableTargetLockIndicator", CATEGORY_CAMERA,
                    enableTargetLockIndicator, TIP);
            targetLockSelectionRange = getClampedDouble(
                    config, "targetLockSelectionRange",
                    targetLockSelectionRange, 4.0D, 64.0D);
            targetLockReleaseRange = getClampedDouble(
                    config, "targetLockReleaseRange",
                    targetLockReleaseRange, 4.0D, 80.0D);
            if (targetLockReleaseRange < targetLockSelectionRange) {
                targetLockReleaseRange = targetLockSelectionRange;
                // Back as a value: a get naming a default would redefine
                // the option and drop its comment.
                config.getCategory(CATEGORY_CAMERA)
                        .get("targetLockReleaseRange")
                        .set(targetLockReleaseRange);
            }
            targetLockSelectionAngle = getClampedDouble(
                    config, "targetLockSelectionAngle",
                    targetLockSelectionAngle, 10.0D, 120.0D);
            targetLockYawSpeed = getClampedDouble(
                    config, "targetLockYawSpeed",
                    targetLockYawSpeed, 90.0D, 1080.0D);
            targetLockPitchSpeed = getClampedDouble(
                    config, "targetLockPitchSpeed",
                    targetLockPitchSpeed, 60.0D, 720.0D);
            targetLockHeightFactor = getClampedDouble(
                    config, "targetLockHeightFactor",
                    targetLockHeightFactor, 0.25D, 0.90D);
            targetLockLineOfSightGraceSeconds = getClampedDouble(
                    config, "targetLockLineOfSightGraceSeconds",
                    targetLockLineOfSightGraceSeconds, 0.0D, 3.0D);
            enableProjectileAimCorrection = config.getBoolean(
                    "enableProjectileAimCorrection", CATEGORY_CAMERA,
                    enableProjectileAimCorrection, TIP);
            enableProjectilePrediction = config.getBoolean(
                    "enableProjectilePrediction", CATEGORY_CAMERA,
                    enableProjectilePrediction, TIP);
            projectileAimDistance = getClampedDouble(
                    config, "projectileAimDistance",
                    projectileAimDistance, 16.0D, 256.0D);
            projectileTrajectorySamplesPerTick = config.getInt(
                    "projectileTrajectorySamplesPerTick",
                    CATEGORY_CAMERA,
                    projectileTrajectorySamplesPerTick, 1, 12, TIP);
            projectileTrajectorySmoothing = getClampedDouble(
                    config, "projectileTrajectorySmoothing",
                    projectileTrajectorySmoothing, 0.0D, 0.5D);
            projectileTrajectoryOriginBlendDistance = getClampedDouble(
                    config, "projectileTrajectoryOriginBlendDistance",
                    projectileTrajectoryOriginBlendDistance,
                    0.5D, 8.0D);
            projectileTrajectoryLineWidth = getClampedDouble(
                    config, "projectileTrajectoryLineWidth",
                    projectileTrajectoryLineWidth, 1.0D, 4.0D);
            projectileTrajectoryOpacity = getClampedDouble(
                    config, "projectileTrajectoryOpacity",
                    projectileTrajectoryOpacity, 0.10D, 1.0D);
            enableChargeTierFeedback = config.getBoolean(
                    "enableChargeTierFeedback", CATEGORY_CAMERA,
                    enableChargeTierFeedback, TIP);
            enableChargeTierParticles = config.getBoolean(
                    "enableChargeTierParticles", CATEGORY_CAMERA,
                    enableChargeTierParticles, TIP);
            enableChargeTierSounds = config.getBoolean(
                    "enableChargeTierSounds", CATEGORY_CAMERA,
                    enableChargeTierSounds, TIP);
            enableCameraMotion = config.getBoolean(
                    "enableCameraMotion", CATEGORY_CAMERA,
                    enableCameraMotion, TIP);
            cameraMotionMultiplier = getClampedDouble(
                    config, "cameraMotionMultiplier",
                    cameraMotionMultiplier, 0.0D, 2.0D);
            airborneMotionMultiplier = getClampedDouble(
                    config, "airborneMotionMultiplier",
                    airborneMotionMultiplier, 0.0D, 2.0D);
            landingMotionMultiplier = getClampedDouble(
                    config, "landingMotionMultiplier",
                    landingMotionMultiplier, 0.0D, 2.0D);
            ridingMotionMultiplier = getClampedDouble(
                    config, "ridingMotionMultiplier",
                    ridingMotionMultiplier, 0.0D, 2.0D);
            swimmingMotionMultiplier = getClampedDouble(
                    config, "swimmingMotionMultiplier",
                    swimmingMotionMultiplier, 0.0D, 2.0D);
            attackMotionMultiplier = getClampedDouble(
                    config, "attackMotionMultiplier",
                    attackMotionMultiplier, 0.0D, 2.0D);
            damageMotionMultiplier = getClampedDouble(
                    config, "damageMotionMultiplier",
                    damageMotionMultiplier, 0.0D, 2.0D);
            explosionMotionMultiplier = getClampedDouble(
                    config, "explosionMotionMultiplier",
                    explosionMotionMultiplier, 0.0D, 2.0D);
            explosionMotionRadius = getClampedDouble(
                    config, "explosionMotionRadius",
                    explosionMotionRadius, 8.0D, 64.0D);
            distanceMultiplier = getClampedDouble(
                    config, "distanceMultiplier", distanceMultiplier,
                    0.50D, 2.00D);
            minimumZoomDistance = getClampedDouble(
                    config, "minimumZoomDistance",
                    minimumZoomDistance, 0.75D, 4.0D);
            maximumZoomDistance = getClampedDouble(
                    config, "maximumZoomDistance",
                    maximumZoomDistance, 2.0D, 16.0D);
            if (maximumZoomDistance < minimumZoomDistance) {
                maximumZoomDistance = minimumZoomDistance;
                config.getCategory(CATEGORY_CAMERA)
                        .get("maximumZoomDistance").set(maximumZoomDistance);
            }
            zoomStep = getClampedDouble(
                    config, "zoomStep", zoomStep,
                    0.05D, 1.0D);
            shoulderOffsetMultiplier = getClampedDouble(
                    config, "shoulderOffsetMultiplier",
                    shoulderOffsetMultiplier, 0.00D, 2.00D);
            verticalOffsetMultiplier = getClampedDouble(
                    config, "verticalOffsetMultiplier",
                    verticalOffsetMultiplier, 0.00D, 2.00D);
            transitionSpeedMultiplier = getClampedDouble(
                    config, "transitionSpeedMultiplier",
                    transitionSpeedMultiplier, 0.25D, 3.00D);
            collisionPadding = getClampedDouble(
                    config, "collisionPadding", collisionPadding,
                    0.02D, 0.40D);
            collisionReleaseRate = getClampedDouble(
                    config, "collisionReleaseRate", collisionReleaseRate,
                    1.00D, 30.00D);
            defaultRightShoulder = config.getBoolean(
                    "defaultRightShoulder", CATEGORY_CAMERA,
                    defaultRightShoulder, TIP);
            enableDirectionalMovement = config.getBoolean(
                    "enableDirectionalMovement", CATEGORY_CAMERA,
                    enableDirectionalMovement, TIP);
            bodyRotationSpeed = getClampedDouble(
                    config, "bodyRotationSpeed", bodyRotationSpeed,
                    90.0D, 1080.0D);
            sprintBodyRotationSpeed = getClampedDouble(
                    config, "sprintBodyRotationSpeed",
                    sprintBodyRotationSpeed, 90.0D, 1440.0D);
            headTrackingAngle = getClampedDouble(
                    config, "headTrackingAngle", headTrackingAngle,
                    0.0D, MAXIMUM_HEAD_TRACKING_ANGLE);
            headTrackingSpeed = getClampedDouble(
                    config, "headTrackingSpeed", headTrackingSpeed,
                    180.0D, 1440.0D);
            headTrackingHysteresisAngle = getClampedDouble(
                    config, "headTrackingHysteresisAngle",
                    headTrackingHysteresisAngle, 0.0D, 20.0D);
            headTrackingTransitionSeconds = getClampedDouble(
                    config, "headTrackingTransitionSeconds",
                    headTrackingTransitionSeconds, 0.05D, 1.50D);
            combatProfileWithWeaponHeld = config.getBoolean(
                    "combatProfileWithWeaponHeld", CATEGORY_CAMERA,
                    combatProfileWithWeaponHeld, TIP);
            combatProfileHoldSeconds = getClampedDouble(
                    config, "combatProfileHoldSeconds",
                    combatProfileHoldSeconds, 0.0D, 10.0D);
            attackCommitmentSeconds = getClampedDouble(
                    config, "attackCommitmentSeconds",
                    attackCommitmentSeconds, 0.0D, 1.0D);
            aimingBodyRotationSpeed = getClampedDouble(
                    config, "aimingBodyRotationSpeed",
                    aimingBodyRotationSpeed, 90.0D, 1440.0D);
            attackBodyRotationSpeed = getClampedDouble(
                    config, "attackBodyRotationSpeed",
                    attackBodyRotationSpeed, 90.0D, 1440.0D);
            enableSwimmingDirectionalMovement = config.getBoolean(
                    "enableSwimmingDirectionalMovement", CATEGORY_CAMERA,
                    enableSwimmingDirectionalMovement, TIP);
            applyGuiMetadata(config);
            if (fromFiles) {
                applyShippedDefinitions(config);
            }
        } finally {
            if (fromFiles && config.hasChanged()) {
                config.save();
            }
        }
    }

    /**
     * Sets every camera option to what its field holds now, in
     * {@code config}; {@link #save} writes nothing else.
     */
    static void writeCurrentValues(Configuration config) {
        config.get(CATEGORY_CAMERA, "enabled", enabled).set(enabled);
        config.get(CATEGORY_CAMERA, "cameraPreset", cameraPreset)
                .set(cameraPreset);
        config.get(CATEGORY_CAMERA, "enableFovEffects", enableFovEffects)
                .set(enableFovEffects);
        config.get(CATEGORY_CAMERA, "enableTargetCrosshair",
                enableTargetCrosshair).set(enableTargetCrosshair);
        config.get(CATEGORY_CAMERA, "enableCameraIntentTargeting",
                enableCameraIntentTargeting).set(enableCameraIntentTargeting);
        config.get(CATEGORY_CAMERA, "enableTargetLock", enableTargetLock)
                .set(enableTargetLock);
        config.get(CATEGORY_CAMERA, "enableTargetLockIndicator",
                enableTargetLockIndicator).set(enableTargetLockIndicator);
        config.get(CATEGORY_CAMERA, "targetLockSelectionRange",
                targetLockSelectionRange).set(targetLockSelectionRange);
        config.get(CATEGORY_CAMERA, "targetLockReleaseRange",
                targetLockReleaseRange).set(targetLockReleaseRange);
        config.get(CATEGORY_CAMERA, "targetLockSelectionAngle",
                targetLockSelectionAngle).set(targetLockSelectionAngle);
        config.get(CATEGORY_CAMERA, "targetLockYawSpeed", targetLockYawSpeed)
                .set(targetLockYawSpeed);
        config.get(CATEGORY_CAMERA, "targetLockPitchSpeed",
                targetLockPitchSpeed).set(targetLockPitchSpeed);
        config.get(CATEGORY_CAMERA, "targetLockHeightFactor",
                targetLockHeightFactor).set(targetLockHeightFactor);
        config.get(CATEGORY_CAMERA, "targetLockLineOfSightGraceSeconds",
                targetLockLineOfSightGraceSeconds)
                .set(targetLockLineOfSightGraceSeconds);
        config.get(CATEGORY_CAMERA, "enableProjectileAimCorrection",
                enableProjectileAimCorrection)
                .set(enableProjectileAimCorrection);
        config.get(CATEGORY_CAMERA, "enableProjectilePrediction",
                enableProjectilePrediction).set(enableProjectilePrediction);
        config.get(CATEGORY_CAMERA, "projectileAimDistance",
                projectileAimDistance).set(projectileAimDistance);
        config.get(CATEGORY_CAMERA, "projectileTrajectorySamplesPerTick",
                projectileTrajectorySamplesPerTick)
                .set(projectileTrajectorySamplesPerTick);
        config.get(CATEGORY_CAMERA, "projectileTrajectorySmoothing",
                projectileTrajectorySmoothing)
                .set(projectileTrajectorySmoothing);
        config.get(CATEGORY_CAMERA, "projectileTrajectoryOriginBlendDistance",
                projectileTrajectoryOriginBlendDistance)
                .set(projectileTrajectoryOriginBlendDistance);
        config.get(CATEGORY_CAMERA, "projectileTrajectoryLineWidth",
                projectileTrajectoryLineWidth)
                .set(projectileTrajectoryLineWidth);
        config.get(CATEGORY_CAMERA, "projectileTrajectoryOpacity",
                projectileTrajectoryOpacity).set(projectileTrajectoryOpacity);
        config.get(CATEGORY_CAMERA, "enableChargeTierFeedback",
                enableChargeTierFeedback).set(enableChargeTierFeedback);
        config.get(CATEGORY_CAMERA, "enableChargeTierParticles",
                enableChargeTierParticles).set(enableChargeTierParticles);
        config.get(CATEGORY_CAMERA, "enableChargeTierSounds",
                enableChargeTierSounds).set(enableChargeTierSounds);
        config.get(CATEGORY_CAMERA, "enableCameraMotion", enableCameraMotion)
                .set(enableCameraMotion);
        config.get(CATEGORY_CAMERA, "cameraMotionMultiplier",
                cameraMotionMultiplier).set(cameraMotionMultiplier);
        config.get(CATEGORY_CAMERA, "airborneMotionMultiplier",
                airborneMotionMultiplier).set(airborneMotionMultiplier);
        config.get(CATEGORY_CAMERA, "landingMotionMultiplier",
                landingMotionMultiplier).set(landingMotionMultiplier);
        config.get(CATEGORY_CAMERA, "ridingMotionMultiplier",
                ridingMotionMultiplier).set(ridingMotionMultiplier);
        config.get(CATEGORY_CAMERA, "swimmingMotionMultiplier",
                swimmingMotionMultiplier).set(swimmingMotionMultiplier);
        config.get(CATEGORY_CAMERA, "attackMotionMultiplier",
                attackMotionMultiplier).set(attackMotionMultiplier);
        config.get(CATEGORY_CAMERA, "damageMotionMultiplier",
                damageMotionMultiplier).set(damageMotionMultiplier);
        config.get(CATEGORY_CAMERA, "explosionMotionMultiplier",
                explosionMotionMultiplier).set(explosionMotionMultiplier);
        config.get(CATEGORY_CAMERA, "explosionMotionRadius",
                explosionMotionRadius).set(explosionMotionRadius);
        config.get(CATEGORY_CAMERA, "distanceMultiplier", distanceMultiplier)
                .set(distanceMultiplier);
        config.get(CATEGORY_CAMERA, "minimumZoomDistance",
                minimumZoomDistance).set(minimumZoomDistance);
        config.get(CATEGORY_CAMERA, "maximumZoomDistance",
                maximumZoomDistance).set(maximumZoomDistance);
        config.get(CATEGORY_CAMERA, "zoomStep", zoomStep).set(zoomStep);
        config.get(CATEGORY_CAMERA, "shoulderOffsetMultiplier",
                shoulderOffsetMultiplier).set(shoulderOffsetMultiplier);
        config.get(CATEGORY_CAMERA, "verticalOffsetMultiplier",
                verticalOffsetMultiplier).set(verticalOffsetMultiplier);
        config.get(CATEGORY_CAMERA, "transitionSpeedMultiplier",
                transitionSpeedMultiplier).set(transitionSpeedMultiplier);
        config.get(CATEGORY_CAMERA, "collisionPadding", collisionPadding)
                .set(collisionPadding);
        config.get(CATEGORY_CAMERA, "collisionReleaseRate",
                collisionReleaseRate).set(collisionReleaseRate);
        config.get(CATEGORY_CAMERA, "defaultRightShoulder",
                defaultRightShoulder).set(defaultRightShoulder);
        config.get(CATEGORY_CAMERA, "enableDirectionalMovement",
                enableDirectionalMovement).set(enableDirectionalMovement);
        config.get(CATEGORY_CAMERA, "bodyRotationSpeed", bodyRotationSpeed)
                .set(bodyRotationSpeed);
        config.get(CATEGORY_CAMERA, "sprintBodyRotationSpeed",
                sprintBodyRotationSpeed).set(sprintBodyRotationSpeed);
        config.get(CATEGORY_CAMERA, "headTrackingAngle", headTrackingAngle)
                .set(headTrackingAngle);
        config.get(CATEGORY_CAMERA, "headTrackingSpeed", headTrackingSpeed)
                .set(headTrackingSpeed);
        config.get(CATEGORY_CAMERA, "headTrackingHysteresisAngle",
                headTrackingHysteresisAngle).set(headTrackingHysteresisAngle);
        config.get(CATEGORY_CAMERA, "headTrackingTransitionSeconds",
                headTrackingTransitionSeconds)
                .set(headTrackingTransitionSeconds);
        config.get(CATEGORY_CAMERA, "combatProfileWithWeaponHeld",
                combatProfileWithWeaponHeld).set(combatProfileWithWeaponHeld);
        config.get(CATEGORY_CAMERA, "combatProfileHoldSeconds",
                combatProfileHoldSeconds).set(combatProfileHoldSeconds);
        config.get(CATEGORY_CAMERA, "attackCommitmentSeconds",
                attackCommitmentSeconds).set(attackCommitmentSeconds);
        config.get(CATEGORY_CAMERA, "aimingBodyRotationSpeed",
                aimingBodyRotationSpeed).set(aimingBodyRotationSpeed);
        config.get(CATEGORY_CAMERA, "attackBodyRotationSpeed",
                attackBodyRotationSpeed).set(attackBodyRotationSpeed);
        config.get(CATEGORY_CAMERA, "enableSwimmingDirectionalMovement",
                enableSwimmingDirectionalMovement)
                .set(enableSwimmingDirectionalMovement);
    }

    private static double getClampedDouble(
            Configuration config, String key, double defaultValue,
            double minimum, double maximum) {
        Property property = config.get(
                CATEGORY_CAMERA, key, defaultValue, TIP,
                minimum, maximum);
        double value = property.getDouble(defaultValue);
        double clamped = Math.max(minimum, Math.min(maximum, value));
        if (clamped != value) {
            property.set(clamped);
        }
        return clamped;
    }
}
