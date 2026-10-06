package com.ninuna.losttales.client.settings;

import com.ninuna.losttales.client.camera.CameraPresetDefinition;
import com.ninuna.losttales.client.camera.CameraPresetFileStore;
import com.ninuna.losttales.client.camera.ThirdPersonCameraRuntime;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.LostTalesConfigWords;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Every client option but the chat's, each section where it belongs:
 * Camera (the camera's own file, its options under small headings),
 * Screens, Appearance and Developer on the Client Settings page; the
 * HUD's panels and the Compass in HUD Settings, the HUD Placement page's;
 * Quests in Quest Settings, Map in Map Settings, and Motion in Motion
 * Settings, the Motion Lab's. A row is named by its option's name line
 * ({@link LostTalesConfigWords}), reads and writes its option's field
 * ({@link OptionField}), keeps within the bounds its option is defined
 * with, restores the value the mod ships, and saves its file the moment
 * it changes. The HUD's panels and the compass stand in HUD
 * Settings, behind the HUD Placement page's cog; the switches that show
 * them are that page's own options.
 */
public final class ClientSettingsSections {
    private static final String PREFIX = "gui.losttales.settings.";

    /** The sections, given to every Settings as it is made. */
    public static final Settings.Sections SECTIONS = new Settings.Sections() {
        @Override
        public void addTo(Settings settings) {
            settings.addSection(Settings.Place.HUD, hud());
            settings.addSection(Settings.Place.HUD, compass());
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
        CLIENT(LostTalesConfig.class, Settings.Store.CLIENT_FILE,
                LostTalesConfig.CATEGORY_CLIENT) {
            @Override
            String shipped(String key) {
                return LostTalesConfig.shippedClientValue(key);
            }

            @Override
            double[] bounds(String key) {
                return LostTalesConfig.shippedClientBounds(key);
            }
        },
        CAMERA(LostTalesThirdPersonConfig.class, Settings.Store.CAMERA_FILE,
                LostTalesThirdPersonConfig.CATEGORY_CAMERA) {
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
        /** The category its options are read under. */
        final String category;

        OptionFile(Class<?> owner, Settings.Store store, String category) {
            this.owner = owner;
            this.store = store;
            this.category = category;
        }

        /** The lang key of an option's name: what the row standing for it is labelled. */
        String nameKey(String key) {
            return LostTalesConfigWords.nameKey(this.category, key);
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
     * Each panel under its name, in HUD Settings: whether it follows the
     * HUD key, where the panel has a switch of its own, and where it
     * stands, a share of the screen. Whether the HUD and each panel show,
     * and the layout, are the HUD Placement page's own options.
     */
    private static Settings.Section hud() {
        return new OptionSection("hud") {
            {
                panel("compass", "compass", "Compass", true);
                panel("fellowship", "fellowship", "Fellowship", true);
                panel("quick_loot", "quickloot", "QuickLoot", true);
                add(number(OptionFile.CLIENT, "quickLootHudMaxRows", 1.0D, 0));
                panel("quest", "quest", "Quest", true);
                panel("notifications", "notifications", "notification", false);
            }

            /**
             * A panel's rows under its name: whether it follows the HUD
             * key, where the panel has a switch, and its place.
             */
            private void panel(String group, String element, String name,
                               boolean switched) {
                group(PREFIX + "hud.group." + group);
                String field = Character.toLowerCase(name.charAt(0))
                        + name.substring(1);
                if (switched) {
                    add(toggle(OptionFile.CLIENT, "linkShow" + name + "Hud",
                            PREFIX + "hud.panel.link"));
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
     * moves the panel as dragging its box on the HUD Placement page does,
     * and leaves the layout the panels' own.
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
                add(number(OptionFile.CLIENT, "compassHudDisplayRadius", 5.0D, 0));
                add(toggle(OptionFile.CLIENT, "showStaticCompassMarkers"));
                add(toggle(OptionFile.CLIENT, "showLotrWaypointCompassMarkers"));
                add(toggle(OptionFile.CLIENT, "onlyShowUnlockedLotrWaypoints"));
                add(toggle(OptionFile.CLIENT, "showHostileCompassMarkers"));
                add(number(OptionFile.CLIENT, "hostileCompassMarkerScanRadius", 1.0D, 0));
                add(number(OptionFile.CLIENT, "fellowshipCompassMarkerFadeRadius", 10.0D, 0));
            }
        };
    }

    /* ---- Quests ---- */

    private static Settings.Section quests() {
        return new OptionSection("quests") {
            {
                add(number(OptionFile.CLIENT, "questHudMaxTrackedQuests", 1.0D, 0));
                add(number(OptionFile.CLIENT, "questHudObjectiveLineCount", 1.0D, 0));
                add(toggle(OptionFile.CLIENT, "showQuestHudNotifications"));
                add(toggle(OptionFile.CLIENT, "showNativeLotrQuestTracker"));
                add(toggle(OptionFile.CLIENT, "enableQuestDialogue"));
                add(toggle(OptionFile.CLIENT, "showQuestChatFeedback"));
                add(toggle(OptionFile.CLIENT, "playQuestSounds"));
                group(PREFIX + "quests.group.world");
                add(toggle(OptionFile.CLIENT, "showWorldQuestMarkers"));
                add(toggle(OptionFile.CLIENT, "showDiscoveredWorldMapMarkers"));
                add(number(OptionFile.CLIENT, "worldQuestMarkerMaxDistance", 8.0D, 0));
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
                add(toggle(OptionFile.CLIENT, "showHostileMapMarkers"));
                add(number(OptionFile.CLIENT, "hostileMapMarkerDisplayRadius", 1.0D, 0));
                group(PREFIX + "map.group.terrain");
                add(new Settings.Numeric("closeMapTerrainTransitionStartZoom",
                        OptionFile.CLIENT.nameKey(
                                "closeMapTerrainTransitionStartZoom"), 0.05D, 2) {
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
                        OptionFile.CLIENT.nameKey(
                                "closeMapTerrainTransitionEndZoom"), 0.05D, 2) {
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
                add(new Settings.ModSwitch("enabled",
                        OptionFile.CAMERA.nameKey("enabled")) {
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
                add(toggle(OptionFile.CAMERA, "enableFovEffects"));
                add(toggle(OptionFile.CAMERA, "defaultRightShoulder"));

                group(PREFIX + "camera.group.framing");
                add(number(OptionFile.CAMERA, "distanceMultiplier", 0.05D, 2));
                add(new CameraNumber("minimumZoomDistance", 0.05D, 2) {
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
                add(new CameraNumber("maximumZoomDistance", 0.25D, 2) {
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
                add(number(OptionFile.CAMERA, "zoomStep", 0.05D, 2));
                add(number(OptionFile.CAMERA, "shoulderOffsetMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "verticalOffsetMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "transitionSpeedMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "collisionPadding", 0.01D, 2));
                add(number(OptionFile.CAMERA, "collisionReleaseRate", 0.5D, 1));

                group(PREFIX + "camera.group.aiming");
                add(toggle(OptionFile.CAMERA, "enableTargetCrosshair"));
                add(toggle(OptionFile.CAMERA, "enableCameraIntentTargeting"));

                group(PREFIX + "camera.group.lock");
                add(toggle(OptionFile.CAMERA, "enableTargetLock"));
                add(toggle(OptionFile.CAMERA, "enableTargetLockIndicator"));
                add(new CameraNumber("targetLockSelectionRange", 1.0D, 0) {
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
                add(new CameraNumber("targetLockReleaseRange", 1.0D, 0) {
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
                add(number(OptionFile.CAMERA, "targetLockSelectionAngle", 1.0D, 0));
                add(number(OptionFile.CAMERA, "targetLockYawSpeed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "targetLockPitchSpeed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "targetLockHeightFactor", 0.05D, 2));
                add(number(OptionFile.CAMERA,
                        "targetLockLineOfSightGraceSeconds", 0.05D, 2));

                group(PREFIX + "camera.group.projectiles");
                add(toggle(OptionFile.CAMERA, "enableProjectileAimCorrection"));
                add(toggle(OptionFile.CAMERA, "enableProjectilePrediction"));
                add(number(OptionFile.CAMERA, "projectileAimDistance", 4.0D, 0));
                add(number(OptionFile.CAMERA,
                        "projectileTrajectorySamplesPerTick", 1.0D, 0));
                add(number(OptionFile.CAMERA, "projectileTrajectorySmoothing", 0.05D, 2));
                add(number(OptionFile.CAMERA,
                        "projectileTrajectoryOriginBlendDistance", 0.1D, 1));
                add(number(OptionFile.CAMERA, "projectileTrajectoryLineWidth", 0.1D, 1));
                add(number(OptionFile.CAMERA, "projectileTrajectoryOpacity", 0.05D, 2));
                add(toggle(OptionFile.CAMERA, "enableChargeTierFeedback"));
                add(toggle(OptionFile.CAMERA, "enableChargeTierParticles"));
                add(toggle(OptionFile.CAMERA, "enableChargeTierSounds"));

                group(PREFIX + "camera.group.motion");
                add(toggle(OptionFile.CAMERA, "enableCameraMotion"));
                add(number(OptionFile.CAMERA, "cameraMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "airborneMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "landingMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "ridingMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "swimmingMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "attackMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "damageMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "explosionMotionMultiplier", 0.05D, 2));
                add(number(OptionFile.CAMERA, "explosionMotionRadius", 1.0D, 0));

                group(PREFIX + "camera.group.body");
                add(toggle(OptionFile.CAMERA, "enableDirectionalMovement"));
                add(toggle(OptionFile.CAMERA,
                        "enableSwimmingDirectionalMovement"));
                add(number(OptionFile.CAMERA, "bodyRotationSpeed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "sprintBodyRotationSpeed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "aimingBodyRotationSpeed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "attackBodyRotationSpeed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "attackCommitmentSeconds", 0.05D, 2));
                add(toggle(OptionFile.CAMERA, "combatProfileWithWeaponHeld"));
                add(number(OptionFile.CAMERA, "combatProfileHoldSeconds", 0.25D, 2));

                group(PREFIX + "camera.group.head");
                add(number(OptionFile.CAMERA, "headTrackingAngle", 1.0D, 0));
                add(number(OptionFile.CAMERA, "headTrackingSpeed", 10.0D, 0));
                add(number(OptionFile.CAMERA, "headTrackingHysteresisAngle", 0.5D, 1));
                add(number(OptionFile.CAMERA, "headTrackingTransitionSeconds", 0.05D, 2));
            }
        };
    }

    /** A camera number whose field is written by hand: one that keeps another in order with it. */
    private abstract static class CameraNumber extends Settings.Numeric {
        CameraNumber(String key, double step, int decimals) {
            super(key, OptionFile.CAMERA.nameKey(key), step, decimals);
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
            super("cameraPreset", OptionFile.CAMERA.nameKey("cameraPreset"));
        }

        @Override
        public String value() {
            CameraPresetDefinition definition = CameraPresetFileStore
                    .getDefinition(LostTalesThirdPersonConfig.cameraPreset);
            return definition == null ? LostTalesThirdPersonConfig.cameraPreset
                    : CameraPresetFileStore.displayName(definition);
        }

        @Override
        public List<String> words() {
            List<String> words = new ArrayList<String>();
            for (String preset : CameraPresetFileStore.getConfigValues()) {
                CameraPresetDefinition definition =
                        CameraPresetFileStore.getDefinition(preset);
                words.add(definition == null ? preset
                        : CameraPresetFileStore.displayName(definition));
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
                add(toggle(OptionFile.CLIENT, "animations"));
                add(number(OptionFile.CLIENT, "animationSpeed", 0.05D, 2));
                add(toggle(OptionFile.CLIENT, "reducedMotion"));
            }
        };
    }

    /* ---- Screens, Appearance, Developer ---- */

    /** The veil and the blur behind the mod's screens. */
    private static Settings.Section screens() {
        return new OptionSection("screens") {
            {
                add(toggle(OptionFile.CLIENT, "enableGuiBackground"));
                add(number(OptionFile.CLIENT, "guiBackgroundOpacity", 0.05D, 2));
                add(toggle(OptionFile.CLIENT, "enableGuiBackgroundBlur"));
                add(toggle(OptionFile.CLIENT, "guiAlwaysBlur"));
                add(number(OptionFile.CLIENT, "guiBlurStrength", 0.5D, 1));
            }
        };
    }

    /** How players' skins and bodies are drawn. */
    private static Settings.Section appearance() {
        return new OptionSection("appearance") {
            {
                add(toggle(OptionFile.CLIENT, "showSkinOverlays"));
                add(toggle(OptionFile.CLIENT, "chestPhysics"));
                add(number(OptionFile.CLIENT, "chestBounce", 0.05D, 2));
            }
        };
    }

    /** A skin file drawn on your own player instead of your account's, for trying skins out. */
    private static Settings.Section developer() {
        return new OptionSection("developer") {
            {
                add(line(OptionFile.CLIENT, "devSkinOverridePath"));
                add(choice(OptionFile.CLIENT, "devSkinOverrideBodyType",
                        LostTalesConfig.DEV_SKIN_BODY_TYPES, "developer.arms."));
            }
        };
    }

    /* ---- The rows ---- */

    /** An option as its file ships it; empty where none is known. */
    private static String shippedOf(OptionFile file, String key) {
        String value = file.shipped(key);
        return value == null ? "" : value;
    }

    /** An on-and-off option, named by its name line; null where its field cannot be read. */
    static Settings.Setting toggle(OptionFile file, String key) {
        return toggle(file, key, file.nameKey(key));
    }

    /**
     * An on-and-off option labelled by {@code labelKey}, as a row whose
     * words its group completes; null where its field cannot be read.
     */
    static Settings.Setting toggle(final OptionFile file, String key,
                                   String labelKey) {
        final OptionField field = OptionField.find(file.owner, key,
                boolean.class);
        if (field == null) {
            return null;
        }
        return new Settings.ModSwitch(key, labelKey) {
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
                                   double step, int decimals) {
        final OptionField field = OptionField.find(file.owner, key,
                int.class, float.class, double.class);
        if (field == null) {
            return null;
        }
        return new Settings.Numeric(key, file.nameKey(key), step, decimals) {
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
                                   final String[] words, String wordPrefix) {
        final OptionField field = OptionField.find(file.owner, key,
                String.class);
        if (field == null) {
            return null;
        }
        return new Settings.ModChoice(key, file.nameKey(key), words,
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
    static Settings.Setting line(final OptionFile file, String key) {
        final OptionField field = OptionField.find(file.owner, key,
                String.class);
        if (field == null) {
            return null;
        }
        return new Settings.Line(key, file.nameKey(key)) {
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
