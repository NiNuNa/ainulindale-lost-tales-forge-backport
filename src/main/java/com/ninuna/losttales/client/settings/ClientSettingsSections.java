package com.ninuna.losttales.client.settings;

import com.ninuna.losttales.client.camera.CameraPresetDefinition;
import com.ninuna.losttales.client.camera.CameraPresetFileStore;
import com.ninuna.losttales.client.camera.ThirdPersonCameraRuntime;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.gui.screen.LostTalesHudPlacementGui;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Every client option but the chat's, each section where it belongs: HUD,
 * Compass, Camera (the camera's own file, its options under small
 * headings), Screens, Appearance and Developer on the Client Settings
 * page; Quests in Quest Settings, Map in Map Settings, and Motion in
 * Motion Settings, the Motion Lab's. A row reads and writes its option's
 * field ({@link OptionField}), keeps within the bounds its option is
 * defined with, restores the value the mod ships, and saves its file the
 * moment it changes. The HUD section opens the HUD placement editor.
 */
public final class ClientSettingsSections {
    private static final String PREFIX = "gui.losttales.settings.";

    /** The sections, given to every Settings as it is made. */
    public static final Settings.Sections SECTIONS = new Settings.Sections() {
        @Override
        public void addTo(Settings settings) {
            settings.addSection(Settings.Place.CLIENT, hud());
            settings.addSection(Settings.Place.CLIENT, compass());
            settings.addSection(Settings.Place.QUESTS, quests());
            settings.addSection(Settings.Place.MAP, map());
            settings.addSection(Settings.Place.CLIENT, camera());
            settings.addSection(Settings.Place.MOTION, motion());
            settings.addSection(Settings.Place.CLIENT, screens());
            settings.addSection(Settings.Place.CLIENT, appearance());
            settings.addSection(Settings.Place.CLIENT, developer());
        }
    };

    private ClientSettingsSections() {}

    /** Gives every Settings made from now on these sections, after those already given. */
    public static void install() {
        Settings.addSections(SECTIONS);
    }

    /** Which file an option is kept in: what its definition, its shipped value and its save come from. */
    enum OptionFile {
        CLIENT(LostTalesConfig.class, Settings.Store.CLIENT_FILE) {
            @Override
            String shipped(String key) {
                return LostTalesConfig.shippedClientValue(key);
            }

            @Override
            double[] bounds(String key) {
                return LostTalesConfig.shippedClientBounds(key);
            }
        },
        CAMERA(LostTalesThirdPersonConfig.class, Settings.Store.CAMERA_FILE) {
            @Override
            String shipped(String key) {
                return LostTalesThirdPersonConfig.shippedValue(key);
            }

            @Override
            double[] bounds(String key) {
                return LostTalesThirdPersonConfig.shippedBounds(key);
            }
        };

        final Class<?> owner;
        final Settings.Store store;

        OptionFile(Class<?> owner, Settings.Store store) {
            this.owner = owner;
            this.store = store;
        }

        /** The option as the mod ships it, as the file writes it; null where none is known. */
        abstract String shipped(String key);

        /** The option's bounds, {min, max}; null where none are known. */
        abstract double[] bounds(String key);
    }

    /** A section of these, its heading's lang key made from its id. */
    private abstract static class OptionSection extends Settings.GroupedSection {
        private final String id;

        OptionSection(String id) {
            this.id = id;
        }

        @Override
        public String titleKey() {
            return PREFIX + "section." + this.id;
        }
    }

    /* ---- HUD ---- */

    /**
     * The placement editor first, then the HUD as a whole and its layout,
     * then each panel under its name: shown or not, whether it is shown
     * and hidden with the HUD, and where it stands, a share of the screen.
     */
    private static Settings.Section hud() {
        return new OptionSection("hud") {
            {
                add(new Settings.Action("arrange", PREFIX + "hud.arrange") {
                    @Override
                    protected void run() {
                        Minecraft minecraft = Minecraft.getMinecraft();
                        minecraft.displayGuiScreen(new LostTalesHudPlacementGui(
                                minecraft.currentScreen));
                    }
                });
                add(new Settings.ModSwitch("showLostTalesHud",
                        PREFIX + "hud.show") {
                    @Override
                    protected boolean get() {
                        return LostTalesConfig.showLostTalesHud;
                    }

                    @Override
                    protected void set(boolean on) {
                        // The panels switched with the HUD follow it.
                        LostTalesConfig.setShowLostTalesHud(on);
                    }
                });
                add(new Settings.ModChoice("hudPlacementPreset",
                        PREFIX + "hud.preset", LostTalesConfig.HUD_PRESET_VALUES,
                        PREFIX + "hud.preset.") {
                    @Override
                    protected String get() {
                        return LostTalesConfig.normalizeHudPreset(
                                LostTalesConfig.hudPlacementPreset);
                    }

                    @Override
                    protected void set(String word) {
                        // A layout puts every panel in its place.
                        LostTalesConfig.applyHudPreset(word);
                    }
                });
                panel("compass", "compass", "Compass", true);
                panel("fellowship", "fellowship", "Fellowship", true);
                panel("quick_loot", "quickloot", "QuickLoot", true);
                add(number(OptionFile.CLIENT, "quickLootHudMaxRows",
                        "hud.quick_loot_rows", 1.0D, 0));
                panel("quest", "quest", "Quest", true);
                panel("notifications", "notifications", "notification", false);
            }

            /**
             * A panel's rows under its name: its switches, where the panel
             * has them, and its place.
             */
            private void panel(String group, String element, String name,
                               boolean switched) {
                group(PREFIX + "hud.group." + group);
                String field = Character.toLowerCase(name.charAt(0))
                        + name.substring(1);
                if (switched) {
                    add(toggle(OptionFile.CLIENT, "show" + name + "Hud",
                            "hud.panel.show"));
                    add(toggle(OptionFile.CLIENT, "linkShow" + name + "Hud",
                            "hud.panel.link"));
                }
                add(offset(element, field + "HudOffsetX", field + "HudOffsetY",
                        true));
                add(offset(element, field + "HudOffsetY", field + "HudOffsetX",
                        false));
            }
        };
    }

    /**
     * One axis of where a panel stands, a share of the screen: moving it
     * moves the panel as dragging it in the editor does, and leaves the
     * layout the panels' own.
     */
    private static Settings.Setting offset(final String element, String key,
                                           String otherKey,
                                           final boolean across) {
        final OptionField axis = OptionField.find(LostTalesConfig.class, key,
                double.class);
        final OptionField other = OptionField.find(LostTalesConfig.class,
                otherKey, double.class);
        if (axis == null || other == null) {
            return null;
        }
        return new Settings.Numeric(key, PREFIX
                + (across ? "hud.panel.across" : "hud.panel.down"), 1.0D, 1) {
            @Override
            protected double get() {
                return axis.getNumber();
            }

            @Override
            protected void set(double value) {
                LostTalesConfig.updateHudOffset(element,
                        across ? value : other.getNumber(),
                        across ? other.getNumber() : value);
            }
        };
    }

    /* ---- Compass ---- */

    private static Settings.Section compass() {
        return new OptionSection("compass") {
            {
                add(number(OptionFile.CLIENT, "compassHudDisplayRadius",
                        "compass.field", 5.0D, 0));
                add(toggle(OptionFile.CLIENT, "showStaticCompassMarkers",
                        "compass.markers"));
                add(toggle(OptionFile.CLIENT, "showLotrWaypointCompassMarkers",
                        "compass.waypoints"));
                add(toggle(OptionFile.CLIENT, "onlyShowUnlockedLotrWaypoints",
                        "compass.unlocked"));
                add(toggle(OptionFile.CLIENT, "showHostileCompassMarkers",
                        "compass.enemies"));
                add(number(OptionFile.CLIENT, "hostileCompassMarkerScanRadius",
                        "compass.enemy_range", 1.0D, 0));
                add(number(OptionFile.CLIENT, "fellowshipCompassMarkerFadeRadius",
                        "compass.fellowship_fade", 10.0D, 0));
            }
        };
    }

    /* ---- Quests ---- */

    private static Settings.Section quests() {
        return new OptionSection("quests") {
            {
                add(number(OptionFile.CLIENT, "questHudMaxTrackedQuests",
                        "quests.tracked", 1.0D, 0));
                add(number(OptionFile.CLIENT, "questHudObjectiveLineCount",
                        "quests.objective_lines", 1.0D, 0));
                add(toggle(OptionFile.CLIENT, "showQuestHudNotifications",
                        "quests.banners"));
                add(toggle(OptionFile.CLIENT, "showNativeLotrQuestTracker",
                        "quests.lotr_tracker"));
                add(toggle(OptionFile.CLIENT, "enableQuestDialogue",
                        "quests.dialogue"));
                add(toggle(OptionFile.CLIENT, "showQuestChatFeedback",
                        "quests.chat"));
                add(toggle(OptionFile.CLIENT, "playQuestSounds",
                        "quests.sounds"));
                group(PREFIX + "quests.group.world");
                add(toggle(OptionFile.CLIENT, "showWorldQuestMarkers",
                        "quests.world_quest"));
                add(toggle(OptionFile.CLIENT, "showDiscoveredWorldMapMarkers",
                        "quests.world_other"));
                add(number(OptionFile.CLIENT, "worldQuestMarkerMaxDistance",
                        "quests.world_distance", 8.0D, 0));
            }
        };
    }

    /* ---- Map ---- */

    /**
     * The enemies the map shows, then where its close-up terrain fades in:
     * the fade always ends past where it starts.
     */
    private static Settings.Section map() {
        return new OptionSection("map") {
            {
                add(toggle(OptionFile.CLIENT, "showHostileMapMarkers",
                        "map.enemies"));
                add(number(OptionFile.CLIENT, "hostileMapMarkerDisplayRadius",
                        "map.enemy_range", 1.0D, 0));
                group(PREFIX + "map.group.terrain");
                add(new Settings.Numeric("closeMapTerrainTransitionStartZoom",
                        PREFIX + "map.terrain_start", 0.05D, 2) {
                    @Override
                    protected double get() {
                        return LostTalesConfig.closeMapTerrainTransitionStartZoom;
                    }

                    @Override
                    protected void set(double value) {
                        LostTalesConfig.closeMapTerrainTransitionStartZoom = value;
                        keepTerrainFadeInOrder();
                    }
                });
                add(new Settings.Numeric("closeMapTerrainTransitionEndZoom",
                        PREFIX + "map.terrain_end", 0.05D, 2) {
                    @Override
                    protected double get() {
                        return LostTalesConfig.closeMapTerrainTransitionEndZoom;
                    }

                    @Override
                    protected void set(double value) {
                        LostTalesConfig.closeMapTerrainTransitionEndZoom = value;
                        keepTerrainFadeInOrder();
                    }
                });
            }
        };
    }

    /** The terrain's fade ends a step past where it starts at the least, as the file's read keeps it. */
    private static void keepTerrainFadeInOrder() {
        if (LostTalesConfig.closeMapTerrainTransitionEndZoom
                > LostTalesConfig.closeMapTerrainTransitionStartZoom) {
            return;
        }
        double[] bounds = LostTalesConfig.shippedClientBounds(
                "closeMapTerrainTransitionEndZoom");
        double highest = bounds == null ? Double.MAX_VALUE : bounds[1];
        LostTalesConfig.closeMapTerrainTransitionEndZoom = Math.min(highest,
                LostTalesConfig.closeMapTerrainTransitionStartZoom + 0.05D);
    }

    /* ---- Camera ---- */

    /**
     * The third-person camera, from its own file: whether it is on, its
     * preset and what reaches every view, then its options under small
     * headings — framing, aiming, target lock, projectiles, camera motion,
     * the body and the head.
     */
    private static Settings.Section camera() {
        return new OptionSection("camera") {
            {
                add(new Settings.ModSwitch("enabled", PREFIX + "camera.enabled") {
                    @Override
                    protected boolean get() {
                        return LostTalesThirdPersonConfig.enabled;
                    }

                    @Override
                    protected void set(boolean on) {
                        LostTalesThirdPersonConfig.enabled = on;
                    }

                    @Override
                    protected String shipped() {
                        return shippedOf(OptionFile.CAMERA, this.key);
                    }

                    @Override
                    public Settings.Store store() {
                        return Settings.Store.CAMERA_FILE;
                    }

                    @Override
                    public void changed() {
                        ThirdPersonCameraRuntime.resetSession();
                    }
                });
                add(new CameraPreset());
                add(toggle(OptionFile.CAMERA, "enableFovEffects", "camera.fov"));
                add(toggle(OptionFile.CAMERA, "defaultRightShoulder",
                        "camera.right_shoulder"));

                group(PREFIX + "camera.group.framing");
                add(number(OptionFile.CAMERA, "distanceMultiplier",
                        "camera.distance", 0.05D, 2));
                add(new CameraNumber("minimumZoomDistance",
                        "camera.zoom_closest", 0.05D, 2) {
                    @Override
                    protected double get() {
                        return LostTalesThirdPersonConfig.minimumZoomDistance;
                    }

                    @Override
                    protected void set(double value) {
                        LostTalesThirdPersonConfig.minimumZoomDistance = value;
                        LostTalesThirdPersonConfig.maximumZoomDistance = Math.max(
                                LostTalesThirdPersonConfig.maximumZoomDistance,
                                value);
                    }
                });
                add(new CameraNumber("maximumZoomDistance",
                        "camera.zoom_farthest", 0.25D, 2) {
                    @Override
                    protected double get() {
                        return LostTalesThirdPersonConfig.maximumZoomDistance;
                    }

                    @Override
                    protected void set(double value) {
                        LostTalesThirdPersonConfig.maximumZoomDistance = Math.max(
                                value,
                                LostTalesThirdPersonConfig.minimumZoomDistance);
                    }
                });
                add(number(OptionFile.CAMERA, "zoomStep", "camera.zoom_step",
                        0.05D, 2));
                add(number(OptionFile.CAMERA, "shoulderOffsetMultiplier",
                        "camera.shoulder_offset", 0.05D, 2));
                add(number(OptionFile.CAMERA, "verticalOffsetMultiplier",
                        "camera.height_offset", 0.05D, 2));
                add(number(OptionFile.CAMERA, "transitionSpeedMultiplier",
                        "camera.transition_speed", 0.05D, 2));
                add(number(OptionFile.CAMERA, "collisionPadding",
                        "camera.collision_padding", 0.01D, 2));
                add(number(OptionFile.CAMERA, "collisionReleaseRate",
                        "camera.collision_release", 0.5D, 1));

                group(PREFIX + "camera.group.aiming");
                add(toggle(OptionFile.CAMERA, "enableTargetCrosshair",
                        "camera.crosshair"));
                add(toggle(OptionFile.CAMERA, "enableCameraIntentTargeting",
                        "camera.camera_aim"));

                group(PREFIX + "camera.group.lock");
                add(toggle(OptionFile.CAMERA, "enableTargetLock",
                        "camera.lock"));
                add(toggle(OptionFile.CAMERA, "enableTargetLockIndicator",
                        "camera.lock_indicator"));
                add(new CameraNumber("targetLockSelectionRange",
                        "camera.lock_range", 1.0D, 0) {
                    @Override
                    protected double get() {
                        return LostTalesThirdPersonConfig.targetLockSelectionRange;
                    }

                    @Override
                    protected void set(double value) {
                        LostTalesThirdPersonConfig.targetLockSelectionRange = value;
                        LostTalesThirdPersonConfig.targetLockReleaseRange = Math.max(
                                LostTalesThirdPersonConfig.targetLockReleaseRange,
                                value);
                    }
                });
                add(new CameraNumber("targetLockReleaseRange",
                        "camera.lock_release", 1.0D, 0) {
                    @Override
                    protected double get() {
                        return LostTalesThirdPersonConfig.targetLockReleaseRange;
                    }

                    @Override
                    protected void set(double value) {
                        LostTalesThirdPersonConfig.targetLockReleaseRange = Math.max(
                                value,
                                LostTalesThirdPersonConfig.targetLockSelectionRange);
                    }
                });
                add(number(OptionFile.CAMERA, "targetLockSelectionAngle",
                        "camera.lock_angle", 1.0D, 0));
                add(number(OptionFile.CAMERA, "targetLockYawSpeed",
                        "camera.lock_turn_across", 10.0D, 0));
                add(number(OptionFile.CAMERA, "targetLockPitchSpeed",
                        "camera.lock_turn_up", 10.0D, 0));
                add(number(OptionFile.CAMERA, "targetLockHeightFactor",
                        "camera.lock_height", 0.05D, 2));
                add(number(OptionFile.CAMERA,
                        "targetLockLineOfSightGraceSeconds",
                        "camera.lock_grace", 0.05D, 2));

                group(PREFIX + "camera.group.projectiles");
                add(toggle(OptionFile.CAMERA, "enableProjectileAimCorrection",
                        "camera.aim_correction"));
                add(toggle(OptionFile.CAMERA, "enableProjectilePrediction",
                        "camera.arc"));
                add(number(OptionFile.CAMERA, "projectileAimDistance",
                        "camera.aim_distance", 4.0D, 0));
                add(number(OptionFile.CAMERA,
                        "projectileTrajectorySamplesPerTick",
                        "camera.arc_samples", 1.0D, 0));
                add(number(OptionFile.CAMERA, "projectileTrajectorySmoothing",
                        "camera.arc_smoothing", 0.05D, 2));
                add(number(OptionFile.CAMERA,
                        "projectileTrajectoryOriginBlendDistance",
                        "camera.arc_blend", 0.1D, 1));
                add(number(OptionFile.CAMERA, "projectileTrajectoryLineWidth",
                        "camera.arc_width", 0.1D, 1));
                add(number(OptionFile.CAMERA, "projectileTrajectoryOpacity",
                        "camera.arc_opacity", 0.05D, 2));
                add(toggle(OptionFile.CAMERA, "enableChargeTierFeedback",
                        "camera.charge_feedback"));
                add(toggle(OptionFile.CAMERA, "enableChargeTierParticles",
                        "camera.charge_particles"));
                add(toggle(OptionFile.CAMERA, "enableChargeTierSounds",
                        "camera.charge_sounds"));

                group(PREFIX + "camera.group.motion");
                add(toggle(OptionFile.CAMERA, "enableCameraMotion",
                        "camera.motion"));
                add(number(OptionFile.CAMERA, "cameraMotionMultiplier",
                        "camera.motion_strength", 0.05D, 2));
                add(number(OptionFile.CAMERA, "airborneMotionMultiplier",
                        "camera.motion_airborne", 0.05D, 2));
                add(number(OptionFile.CAMERA, "landingMotionMultiplier",
                        "camera.motion_landing", 0.05D, 2));
                add(number(OptionFile.CAMERA, "ridingMotionMultiplier",
                        "camera.motion_riding", 0.05D, 2));
                add(number(OptionFile.CAMERA, "swimmingMotionMultiplier",
                        "camera.motion_swimming", 0.05D, 2));
                add(number(OptionFile.CAMERA, "attackMotionMultiplier",
                        "camera.motion_attack", 0.05D, 2));
                add(number(OptionFile.CAMERA, "damageMotionMultiplier",
                        "camera.motion_damage", 0.05D, 2));
                add(number(OptionFile.CAMERA, "explosionMotionMultiplier",
                        "camera.motion_explosion", 0.05D, 2));
                add(number(OptionFile.CAMERA, "explosionMotionRadius",
                        "camera.motion_explosion_reach", 1.0D, 0));

                group(PREFIX + "camera.group.body");
                add(toggle(OptionFile.CAMERA, "enableDirectionalMovement",
                        "camera.body_turn"));
                add(toggle(OptionFile.CAMERA,
                        "enableSwimmingDirectionalMovement",
                        "camera.body_turn_swimming"));
                add(number(OptionFile.CAMERA, "bodyRotationSpeed",
                        "camera.body_speed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "sprintBodyRotationSpeed",
                        "camera.body_speed_sprinting", 10.0D, 0));
                add(number(OptionFile.CAMERA, "aimingBodyRotationSpeed",
                        "camera.body_speed_aiming", 10.0D, 0));
                add(number(OptionFile.CAMERA, "attackBodyRotationSpeed",
                        "camera.body_speed_attacking", 10.0D, 0));
                add(number(OptionFile.CAMERA, "attackCommitmentSeconds",
                        "camera.attack_commitment", 0.05D, 2));
                add(toggle(OptionFile.CAMERA, "combatProfileWithWeaponHeld",
                        "camera.combat_weapon"));
                add(number(OptionFile.CAMERA, "combatProfileHoldSeconds",
                        "camera.combat_hold", 0.25D, 2));

                group(PREFIX + "camera.group.head");
                add(number(OptionFile.CAMERA, "headTrackingAngle",
                        "camera.head_angle", 1.0D, 0));
                add(number(OptionFile.CAMERA, "headTrackingSpeed",
                        "camera.head_speed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "headTrackingHysteresisAngle",
                        "camera.head_margin", 0.5D, 1));
                add(number(OptionFile.CAMERA, "headTrackingTransitionSeconds",
                        "camera.head_blend", 0.05D, 2));
            }
        };
    }

    /** A camera number whose field is written by hand: one that keeps another in order with it. */
    private abstract static class CameraNumber extends Settings.Numeric {
        CameraNumber(String key, String label, double step, int decimals) {
            super(key, PREFIX + label, step, decimals);
        }

        @Override
        protected double[] bounds() {
            return OptionFile.CAMERA.bounds(this.key);
        }

        @Override
        protected String shipped() {
            return shippedOf(OptionFile.CAMERA, this.key);
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.CAMERA_FILE;
        }
    }

    /**
     * The camera's preset: one of the preset files there are now, each
     * read by the name the file gives it, picked from its words. A new
     * preset starts the camera's session again.
     */
    private static final class CameraPreset extends Settings.ModSetting {
        CameraPreset() {
            super("cameraPreset", PREFIX + "camera.preset");
        }

        @Override
        public String value() {
            CameraPresetDefinition definition = CameraPresetFileStore
                    .getDefinition(LostTalesThirdPersonConfig.cameraPreset);
            return definition == null ? LostTalesThirdPersonConfig.cameraPreset
                    : definition.getName();
        }

        @Override
        public List<String> words() {
            List<String> words = new ArrayList<String>();
            for (String preset : CameraPresetFileStore.getConfigValues()) {
                CameraPresetDefinition definition =
                        CameraPresetFileStore.getDefinition(preset);
                words.add(definition == null ? preset : definition.getName());
            }
            return words;
        }

        @Override
        public int wordIndex() {
            return Arrays.asList(CameraPresetFileStore.getConfigValues())
                    .indexOf(LostTalesThirdPersonConfig.cameraPreset);
        }

        @Override
        public int shippedWordIndex() {
            return Arrays.asList(CameraPresetFileStore.getConfigValues())
                    .indexOf(CameraPresetFileStore.normalizeId(shipped()));
        }

        @Override
        public void pickWord(int index) {
            String[] presets = CameraPresetFileStore.getConfigValues();
            if (index >= 0 && index < presets.length) {
                LostTalesThirdPersonConfig.cameraPreset = presets[index];
            }
        }

        @Override
        public void restore() {
            String shipped = CameraPresetFileStore.normalizeId(shipped());
            if (CameraPresetFileStore.getDefinition(shipped) != null) {
                LostTalesThirdPersonConfig.cameraPreset = shipped;
            }
        }

        @Override
        protected String shipped() {
            return shippedOf(OptionFile.CAMERA, this.key);
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.CAMERA_FILE;
        }

        @Override
        public void changed() {
            ThirdPersonCameraRuntime.resetSession();
        }
    }

    /* ---- Motion ---- */

    /**
     * The one set of settings every motion of the screens and the HUD
     * answers to; each motion itself is tuned in the Motion Lab. The
     * camera's motion is the camera's own.
     */
    private static Settings.Section motion() {
        return new OptionSection("motion") {
            {
                add(toggle(OptionFile.CLIENT, "animations", "motion.animations"));
                add(number(OptionFile.CLIENT, "animationSpeed", "motion.speed",
                        0.05D, 2));
                add(toggle(OptionFile.CLIENT, "reducedMotion", "motion.reduced"));
            }
        };
    }

    /* ---- Screens, Appearance, Developer ---- */

    /** The veil and the blur behind the mod's screens. */
    private static Settings.Section screens() {
        return new OptionSection("screens") {
            {
                add(toggle(OptionFile.CLIENT, "enableGuiBackground",
                        "screens.darken"));
                add(number(OptionFile.CLIENT, "guiBackgroundOpacity",
                        "screens.darkness", 0.05D, 2));
                add(toggle(OptionFile.CLIENT, "enableGuiBackgroundBlur",
                        "screens.blur"));
                add(toggle(OptionFile.CLIENT, "guiAlwaysBlur",
                        "screens.blur_always"));
                add(number(OptionFile.CLIENT, "guiBlurStrength",
                        "screens.blur_strength", 0.5D, 1));
            }
        };
    }

    /** How players' skins and bodies are drawn. */
    private static Settings.Section appearance() {
        return new OptionSection("appearance") {
            {
                add(toggle(OptionFile.CLIENT, "showSkinOverlays",
                        "appearance.overlays"));
                add(toggle(OptionFile.CLIENT, "chestPhysics",
                        "appearance.chest_physics"));
                add(number(OptionFile.CLIENT, "chestBounce",
                        "appearance.chest_bounce", 0.05D, 2));
            }
        };
    }

    /** A skin file drawn on your own player instead of your account's, for trying skins out. */
    private static Settings.Section developer() {
        return new OptionSection("developer") {
            {
                add(line(OptionFile.CLIENT, "devSkinOverridePath",
                        "developer.skin"));
                add(choice(OptionFile.CLIENT, "devSkinOverrideBodyType",
                        "developer.arms", LostTalesConfig.DEV_SKIN_BODY_TYPES,
                        "developer.arms."));
            }
        };
    }

    /* ---- The rows ---- */

    /** An option as its file ships it; empty where none is known. */
    private static String shippedOf(OptionFile file, String key) {
        String value = file.shipped(key);
        return value == null ? "" : value;
    }

    /** An on-and-off option; null where its field cannot be read. */
    static Settings.Setting toggle(final OptionFile file, String key,
                                   String label) {
        final OptionField field = OptionField.find(file.owner, key,
                boolean.class);
        if (field == null) {
            return null;
        }
        return new Settings.ModSwitch(key, PREFIX + label) {
            @Override
            protected boolean get() {
                return field.getBoolean();
            }

            @Override
            protected void set(boolean on) {
                field.setBoolean(on);
            }

            @Override
            protected String shipped() {
                return shippedOf(file, this.key);
            }

            @Override
            public Settings.Store store() {
                return file.store;
            }
        };
    }

    /**
     * A number, stepped by {@code step} and read to {@code decimals}
     * places within the bounds its option is defined with; null where its
     * field cannot be read.
     */
    static Settings.Setting number(final OptionFile file, String key,
                                   String label, double step, int decimals) {
        final OptionField field = OptionField.find(file.owner, key,
                int.class, float.class, double.class);
        if (field == null) {
            return null;
        }
        return new Settings.Numeric(key, PREFIX + label, step, decimals) {
            @Override
            protected double get() {
                return field.getNumber();
            }

            @Override
            protected void set(double value) {
                field.setNumber(value);
            }

            @Override
            protected double[] bounds() {
                return file.bounds(this.key);
            }

            @Override
            protected String shipped() {
                return shippedOf(file, this.key);
            }

            @Override
            public Settings.Store store() {
                return file.store;
            }
        };
    }

    /** A few-word option, each word read by its lang key; null where its field cannot be read. */
    static Settings.Setting choice(final OptionFile file, String key,
                                   String label, final String[] words,
                                   String wordPrefix) {
        final OptionField field = OptionField.find(file.owner, key,
                String.class);
        if (field == null) {
            return null;
        }
        return new Settings.ModChoice(key, PREFIX + label, words,
                PREFIX + wordPrefix) {
            @Override
            protected String get() {
                String now = field.getString().trim().toLowerCase(Locale.ROOT);
                for (String word : words) {
                    if (word.equalsIgnoreCase(now)) {
                        return word;
                    }
                }
                return words[0];
            }

            @Override
            protected void set(String word) {
                field.setString(word);
            }

            @Override
            protected String shipped() {
                return shippedOf(file, this.key);
            }

            @Override
            public Settings.Store store() {
                return file.store;
            }
        };
    }

    /** A line of words; null where its field cannot be read. */
    static Settings.Setting line(final OptionFile file, String key,
                                 String label) {
        final OptionField field = OptionField.find(file.owner, key,
                String.class);
        if (field == null) {
            return null;
        }
        return new Settings.Line(key, PREFIX + label) {
            @Override
            protected String get() {
                return field.getString();
            }

            @Override
            protected void set(String text) {
                field.setString(text);
            }

            @Override
            protected String shipped() {
                return shippedOf(file, this.key);
            }

            @Override
            public Settings.Store store() {
                return file.store;
            }
        };
    }
}
