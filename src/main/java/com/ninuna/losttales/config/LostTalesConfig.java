package com.ninuna.losttales.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

/**
 * Small legacy Forge config holder.
 *
 * Keep this intentionally simple: modern NeoForge ModConfigSpec values are
 * replaced with static fields loaded during preInit.
 */
public final class LostTalesConfig {
    public static final String CATEGORY_CLIENT = "client";
    public static final String CATEGORY_QUESTS = "quests";

    public static final String HUD_PRESET_CUSTOM = "custom";
    public static final String HUD_PRESET_DEFAULT = "default";
    public static final String HUD_PRESET_LOTR_SAFE = "lotr-safe";
    public static final String HUD_PRESET_COMPACT = "compact";
    public static final String HUD_PRESET_MINIMAL = "minimal";
    public static final String[] HUD_PRESET_VALUES = new String[] {
            HUD_PRESET_CUSTOM,
            HUD_PRESET_DEFAULT,
            HUD_PRESET_LOTR_SAFE,
            HUD_PRESET_COMPACT,
            HUD_PRESET_MINIMAL
    };

    private static File loadedConfigFile;

    public static boolean showLostTalesHud = true;
    public static String hudPlacementPreset = HUD_PRESET_CUSTOM;

    public static boolean showCompassHud = true;
    public static boolean linkShowCompassHud = false;
    public static int compassHudOffsetX = 50;
    public static int compassHudOffsetY = 2;
    public static int compassHudDisplayRadius = 90;
    public static boolean showStaticCompassMarkers = true;
    public static boolean showLotrWaypointCompassMarkers = true;
    public static boolean onlyShowUnlockedLotrWaypoints = true;
    public static boolean showHostileCompassMarkers = true;
    public static boolean onlyShowAggroHostileCompassMarkers = false;

    public static boolean showQuickLootHud = true;
    public static boolean linkShowQuickLootHud = false;
    public static int quickLootHudMaxRows = 5;
    public static int quickLootHudOffsetX = 24;
    public static int quickLootHudOffsetY = 32;

    public static boolean showQuestHud = true;
    public static boolean linkShowQuestHud = false;
    public static int questHudOffsetX = 2;
    public static int questHudOffsetY = 38;
    public static int questHudMaxObjectives = 3;
    public static boolean showWorldQuestMarkers = true;
    public static int worldQuestMarkerMaxDistance = 128;

    public static boolean showQuestChatFeedback = true;
    public static boolean playQuestSounds = true;

    public static boolean enableQuestPrerequisites = true;
    public static boolean enableQuestRewards = true;
    public static boolean allowQuestJournalStarts = true;
    public static boolean allowQuestItemStarts = true;
    public static boolean allowQuestInteractionStarts = true;
    public static boolean enableQuestMarkerDiscovery = true;
    public static boolean autoRevealQuestMarkersOnStart = true;

    private LostTalesConfig() {}

    public static void load(File configFile) {
        loadedConfigFile = configFile;
        Configuration config = new Configuration(configFile);
        try {
            config.load();
            applyGuiMetadata(config);

            showLostTalesHud = config.getBoolean(
                    "showLostTalesHud",
                    CATEGORY_CLIENT,
                    showLostTalesHud,
                    "Master toggle for Lost Tales HUD elements."
            );
            Property hudPresetProperty = config.get(
                    CATEGORY_CLIENT,
                    "hudPlacementPreset",
                    hudPlacementPreset,
                    "HUD placement preset used by the Mod List config GUI. Use custom to keep individual offsets exactly as configured. Valid values: custom, default, lotr-safe, compact, minimal."
            );
            hudPresetProperty.setValidValues(HUD_PRESET_VALUES);
            hudPlacementPreset = normalizeHudPreset(hudPresetProperty.getString());

            showCompassHud = config.getBoolean(
                    "showCompassHud",
                    CATEGORY_CLIENT,
                    showCompassHud,
                    "Render the Lost Tales compass HUD."
            );
            linkShowCompassHud = config.getBoolean(
                    "linkShowCompassHud",
                    CATEGORY_CLIENT,
                    linkShowCompassHud,
                    "When true, changing showLostTalesHud also changes showCompassHud. Disabled by default in the 1.7.10 backport to preserve existing per-HUD settings."
            );
            compassHudOffsetX = config.getInt(
                    "compassHudOffsetX",
                    CATEGORY_CLIENT,
                    compassHudOffsetX,
                    0,
                    100,
                    "Horizontal compass position as a percentage of the scaled screen width."
            );
            compassHudOffsetY = config.getInt(
                    "compassHudOffsetY",
                    CATEGORY_CLIENT,
                    compassHudOffsetY,
                    0,
                    100,
                    "Vertical compass position as a percentage of the scaled screen height."
            );
            compassHudDisplayRadius = config.getInt(
                    "compassHudDisplayRadius",
                    CATEGORY_CLIENT,
                    compassHudDisplayRadius,
                    45,
                    225,
                    "Visible compass field in degrees. Larger values show more markers but reduce precision."
            );
            showStaticCompassMarkers = config.getBoolean(
                    "showStaticCompassMarkers",
                    CATEGORY_CLIENT,
                    showStaticCompassMarkers,
                    "Render JSON-defined/static map markers on the compass."
            );
            showLotrWaypointCompassMarkers = config.getBoolean(
                    "showLotrWaypointCompassMarkers",
                    CATEGORY_CLIENT,
                    showLotrWaypointCompassMarkers,
                    "Render public Lord of the Rings Legacy waypoints as compass Point of Interest markers."
            );
            onlyShowUnlockedLotrWaypoints = config.getBoolean(
                    "onlyShowUnlockedLotrWaypoints",
                    CATEGORY_CLIENT,
                    onlyShowUnlockedLotrWaypoints,
                    "Only show LOTR waypoints on the compass after the player has unlocked them according to LOTR's own waypoint logic."
            );
            showHostileCompassMarkers = config.getBoolean(
                    "showHostileCompassMarkers",
                    CATEGORY_CLIENT,
                    showHostileCompassMarkers,
                    "Render nearby hostile mobs on the Lost Tales compass. Enabled by default so the red hostile marker sprite is visible during normal testing."
            );
            onlyShowAggroHostileCompassMarkers = config.getBoolean(
                    "onlyShowAggroHostileCompassMarkers",
                    CATEGORY_CLIENT,
                    onlyShowAggroHostileCompassMarkers,
                    "When true, hostile compass markers are shown only for mobs the server recently confirmed are targeting you. When false, the legacy 1.7.10 fallback also shows ordinary vanilla IMob hostiles."
            );

            showQuickLootHud = config.getBoolean(
                    "showQuickLootHud",
                    CATEGORY_CLIENT,
                    showQuickLootHud,
                    "Render the Quick Loot HUD when looking at supported inventories."
            );
            linkShowQuickLootHud = config.getBoolean(
                    "linkShowQuickLootHud",
                    CATEGORY_CLIENT,
                    linkShowQuickLootHud,
                    "When true, changing showLostTalesHud also changes showQuickLootHud. Disabled by default in the 1.7.10 backport to preserve existing per-HUD settings."
            );
            quickLootHudOffsetX = config.getInt(
                    "quickLootHudOffsetX",
                    CATEGORY_CLIENT,
                    quickLootHudOffsetX,
                    0,
                    100,
                    "Horizontal quick-loot position as a percentage of the right half of the screen."
            );
            quickLootHudOffsetY = config.getInt(
                    "quickLootHudOffsetY",
                    CATEGORY_CLIENT,
                    quickLootHudOffsetY,
                    0,
                    100,
                    "Vertical quick-loot position as a percentage of the scaled screen height."
            );
            quickLootHudMaxRows = config.getInt(
                    "quickLootHudMaxRows",
                    CATEGORY_CLIENT,
                    quickLootHudMaxRows,
                    1,
                    12,
                    "Maximum number of item rows visible in the Quick Loot HUD."
            );

            showQuestHud = config.getBoolean(
                    "showQuestHud",
                    CATEGORY_CLIENT,
                    showQuestHud,
                    "Render the Lost Tales active quest tracker and quest notifications."
            );
            linkShowQuestHud = config.getBoolean(
                    "linkShowQuestHud",
                    CATEGORY_CLIENT,
                    linkShowQuestHud,
                    "When true, changing showLostTalesHud also changes showQuestHud. Disabled by default in the 1.7.10 backport to preserve existing per-HUD settings."
            );
            questHudOffsetX = config.getInt(
                    "questHudOffsetX",
                    CATEGORY_CLIENT,
                    questHudOffsetX,
                    0,
                    100,
                    "Horizontal quest tracker position as a percentage of the scaled screen width."
            );
            questHudOffsetY = config.getInt(
                    "questHudOffsetY",
                    CATEGORY_CLIENT,
                    questHudOffsetY,
                    0,
                    100,
                    "Vertical quest tracker position as a percentage of the scaled screen height."
            );
            questHudMaxObjectives = config.getInt(
                    "questHudMaxObjectives",
                    CATEGORY_CLIENT,
                    questHudMaxObjectives,
                    1,
                    6,
                    "Maximum number of current-stage objectives shown on the quest HUD."
            );
            showWorldQuestMarkers = config.getBoolean(
                    "showWorldQuestMarkers",
                    CATEGORY_CLIENT,
                    showWorldQuestMarkers,
                    "Render lightweight world-space labels above discovered quest map markers. Uses legacy 1.7.10 nameplate rendering instead of the modern NeoForge level overlay system."
            );
            worldQuestMarkerMaxDistance = config.getInt(
                    "worldQuestMarkerMaxDistance",
                    CATEGORY_CLIENT,
                    worldQuestMarkerMaxDistance,
                    24,
                    512,
                    "Maximum distance in blocks for world-space quest marker labels."
            );
            showQuestChatFeedback = config.getBoolean(
                    "showQuestChatFeedback",
                    CATEGORY_CLIENT,
                    showQuestChatFeedback,
                    "Send lightweight chat feedback when quests start, advance, or complete."
            );
            playQuestSounds = config.getBoolean(
                    "playQuestSounds",
                    CATEGORY_CLIENT,
                    playQuestSounds,
                    "Play simple vanilla UI sounds for quest milestones."
            );

            enableQuestPrerequisites = config.getBoolean(
                    "enableQuestPrerequisites",
                    CATEGORY_QUESTS,
                    enableQuestPrerequisites,
                    "Require optional quest JSON prerequisites before quests can be started."
            );
            enableQuestRewards = config.getBoolean(
                    "enableQuestRewards",
                    CATEGORY_QUESTS,
                    enableQuestRewards,
                    "Grant optional quest JSON rewards when quests complete naturally or by command."
            );
            allowQuestJournalStarts = config.getBoolean(
                    "allowQuestJournalStarts",
                    CATEGORY_QUESTS,
                    allowQuestJournalStarts,
                    "Allow the development quest journal to request quest starts for quests whose startMode allows journal starts."
            );
            allowQuestItemStarts = config.getBoolean(
                    "allowQuestItemStarts",
                    CATEGORY_QUESTS,
                    allowQuestItemStarts,
                    "Allow right-click quest starter items with LostTalesQuestId NBT to start quests whose startMode allows item starts."
            );
            allowQuestInteractionStarts = config.getBoolean(
                    "allowQuestInteractionStarts",
                    CATEGORY_QUESTS,
                    allowQuestInteractionStarts,
                    "Allow right-click entity/block quest giver hooks for quests whose startMode allows interaction starts."
            );
            enableQuestMarkerDiscovery = config.getBoolean(
                    "enableQuestMarkerDiscovery",
                    CATEGORY_QUESTS,
                    enableQuestMarkerDiscovery,
                    "Sync player-discovered quest map marker IDs and hide marker hints until discovered."
            );
            autoRevealQuestMarkersOnStart = config.getBoolean(
                    "autoRevealQuestMarkersOnStart",
                    CATEGORY_QUESTS,
                    autoRevealQuestMarkersOnStart,
                    "Automatically reveal marker hints from a quest when that quest starts."
            );

            if (!HUD_PRESET_CUSTOM.equals(hudPlacementPreset)) {
                applyHudPresetValues(hudPlacementPreset);
            }
            syncLinkedHudOptions();
            clampHudOffsets();
            writeCurrentValues(config);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static File getLoadedConfigFile() {
        return loadedConfigFile;
    }

    public static Configuration createConfiguration() {
        return loadedConfigFile == null ? null : new Configuration(loadedConfigFile);
    }

    public static void reload() {
        if (loadedConfigFile != null) {
            load(loadedConfigFile);
        }
    }

    public static void toggleLostTalesHud() {
        setShowLostTalesHud(!showLostTalesHud);
    }

    public static void setShowLostTalesHud(boolean show) {
        showLostTalesHud = show;
        syncLinkedHudOptions();
        save();
    }

    public static void syncLinkedHudOptions() {
        if (linkShowCompassHud) {
            showCompassHud = showLostTalesHud;
        }
        if (linkShowQuickLootHud) {
            showQuickLootHud = showLostTalesHud;
        }
        if (linkShowQuestHud) {
            showQuestHud = showLostTalesHud;
        }
    }

    public static boolean applyHudPreset(String preset) {
        String key = normalizeHudPreset(preset);
        if (HUD_PRESET_CUSTOM.equals(key)) {
            if (!isCustomHudPreset(preset)) {
                return false;
            }
            hudPlacementPreset = HUD_PRESET_CUSTOM;
            save();
            return true;
        }

        if (!isKnownHudPreset(key)) {
            return false;
        }

        hudPlacementPreset = key;
        applyHudPresetValues(key);
        clampHudOffsets();
        save();
        return true;
    }

    private static void applyHudPresetValues(String preset) {
        String key = normalizeHudPreset(preset);
        if (HUD_PRESET_DEFAULT.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 2;
            quickLootHudOffsetX = 24;
            quickLootHudOffsetY = 32;
            questHudOffsetX = 2;
            questHudOffsetY = 38;
        } else if (HUD_PRESET_LOTR_SAFE.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 12;
            quickLootHudOffsetX = 22;
            quickLootHudOffsetY = 34;
            questHudOffsetX = 2;
            questHudOffsetY = 52;
        } else if (HUD_PRESET_COMPACT.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 7;
            quickLootHudOffsetX = 34;
            quickLootHudOffsetY = 37;
            questHudOffsetX = 1;
            questHudOffsetY = 57;
        } else if (HUD_PRESET_MINIMAL.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 4;
            quickLootHudOffsetX = 42;
            quickLootHudOffsetY = 42;
            questHudOffsetX = 1;
            questHudOffsetY = 70;
        }
    }

    public static String normalizeHudPreset(String preset) {
        if (preset == null) {
            return HUD_PRESET_CUSTOM;
        }
        String key = preset.trim().toLowerCase().replace('_', '-');
        if ("modern".equals(key)) {
            return HUD_PRESET_DEFAULT;
        }
        if ("lotr".equals(key) || "legacy".equals(key) || "lotr-safe".equals(key)) {
            return HUD_PRESET_LOTR_SAFE;
        }
        if (HUD_PRESET_DEFAULT.equals(key)
                || HUD_PRESET_LOTR_SAFE.equals(key)
                || HUD_PRESET_COMPACT.equals(key)
                || HUD_PRESET_MINIMAL.equals(key)) {
            return key;
        }
        return HUD_PRESET_CUSTOM;
    }

    public static boolean isCustomHudPreset(String preset) {
        if (preset == null) {
            return false;
        }
        return HUD_PRESET_CUSTOM.equals(preset.trim().toLowerCase().replace('_', '-'));
    }

    public static boolean isKnownHudPreset(String preset) {
        String key = normalizeHudPreset(preset);
        return HUD_PRESET_DEFAULT.equals(key)
                || HUD_PRESET_LOTR_SAFE.equals(key)
                || HUD_PRESET_COMPACT.equals(key)
                || HUD_PRESET_MINIMAL.equals(key);
    }

    public static boolean setHudOffset(String element, int x, int y) {
        String key = normalizeHudElement(element);
        if (key.length() == 0) {
            return false;
        }

        x = clampPercent(x);
        y = clampPercent(y);
        hudPlacementPreset = HUD_PRESET_CUSTOM;
        if ("compass".equals(key)) {
            compassHudOffsetX = x;
            compassHudOffsetY = y;
        } else if ("quickloot".equals(key)) {
            quickLootHudOffsetX = x;
            quickLootHudOffsetY = y;
        } else if ("quest".equals(key)) {
            questHudOffsetX = x;
            questHudOffsetY = y;
        } else {
            return false;
        }

        save();
        return true;
    }

    public static boolean moveHudOffset(String element, int dx, int dy) {
        String key = normalizeHudElement(element);
        if ("compass".equals(key)) {
            return setHudOffset(key, compassHudOffsetX + dx, compassHudOffsetY + dy);
        }
        if ("quickloot".equals(key)) {
            return setHudOffset(key, quickLootHudOffsetX + dx, quickLootHudOffsetY + dy);
        }
        if ("quest".equals(key)) {
            return setHudOffset(key, questHudOffsetX + dx, questHudOffsetY + dy);
        }
        return false;
    }

    public static String normalizeHudElement(String element) {
        if (element == null) {
            return "";
        }
        String key = element.trim().toLowerCase().replace("_", "").replace("-", "");
        if ("compass".equals(key)) {
            return "compass";
        }
        if ("quickloot".equals(key) || "loot".equals(key) || "quickloothud".equals(key)) {
            return "quickloot";
        }
        if ("quest".equals(key) || "quests".equals(key) || "questhud".equals(key) || "tracker".equals(key)) {
            return "quest";
        }
        return "";
    }

    public static void clampHudOffsets() {
        compassHudOffsetX = clampPercent(compassHudOffsetX);
        compassHudOffsetY = clampPercent(compassHudOffsetY);
        quickLootHudOffsetX = clampPercent(quickLootHudOffsetX);
        quickLootHudOffsetY = clampPercent(quickLootHudOffsetY);
        questHudOffsetX = clampPercent(questHudOffsetX);
        questHudOffsetY = clampPercent(questHudOffsetY);
    }

    private static int clampPercent(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }

    public static void save() {
        if (loadedConfigFile == null) {
            return;
        }

        Configuration config = new Configuration(loadedConfigFile);
        try {
            config.load();
            writeCurrentValues(config);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static void applyGuiMetadata(Configuration config) {
        if (config == null) {
            return;
        }
        config.getCategory(CATEGORY_CLIENT).setLanguageKey("losttales.config.category.client");
        config.getCategory(CATEGORY_QUESTS).setLanguageKey("losttales.config.category.quests");
    }

    private static void writeCurrentValues(Configuration config) {
        if (config == null) {
            return;
        }

        config.get(CATEGORY_CLIENT, "showLostTalesHud", showLostTalesHud).set(showLostTalesHud);
        Property hudPresetProperty = config.get(CATEGORY_CLIENT, "hudPlacementPreset", hudPlacementPreset);
        hudPresetProperty.set(hudPlacementPreset);
        hudPresetProperty.setValidValues(HUD_PRESET_VALUES);
        config.get(CATEGORY_CLIENT, "showCompassHud", showCompassHud).set(showCompassHud);
        config.get(CATEGORY_CLIENT, "linkShowCompassHud", linkShowCompassHud).set(linkShowCompassHud);
        config.get(CATEGORY_CLIENT, "compassHudOffsetX", compassHudOffsetX).set(compassHudOffsetX);
        config.get(CATEGORY_CLIENT, "compassHudOffsetY", compassHudOffsetY).set(compassHudOffsetY);
        config.get(CATEGORY_CLIENT, "compassHudDisplayRadius", compassHudDisplayRadius).set(compassHudDisplayRadius);
        config.get(CATEGORY_CLIENT, "showStaticCompassMarkers", showStaticCompassMarkers).set(showStaticCompassMarkers);
        config.get(CATEGORY_CLIENT, "showLotrWaypointCompassMarkers", showLotrWaypointCompassMarkers).set(showLotrWaypointCompassMarkers);
        config.get(CATEGORY_CLIENT, "onlyShowUnlockedLotrWaypoints", onlyShowUnlockedLotrWaypoints).set(onlyShowUnlockedLotrWaypoints);
        config.get(CATEGORY_CLIENT, "showHostileCompassMarkers", showHostileCompassMarkers).set(showHostileCompassMarkers);
        config.get(CATEGORY_CLIENT, "onlyShowAggroHostileCompassMarkers", onlyShowAggroHostileCompassMarkers).set(onlyShowAggroHostileCompassMarkers);
        config.get(CATEGORY_CLIENT, "showQuickLootHud", showQuickLootHud).set(showQuickLootHud);
        config.get(CATEGORY_CLIENT, "linkShowQuickLootHud", linkShowQuickLootHud).set(linkShowQuickLootHud);
        config.get(CATEGORY_CLIENT, "quickLootHudOffsetX", quickLootHudOffsetX).set(quickLootHudOffsetX);
        config.get(CATEGORY_CLIENT, "quickLootHudOffsetY", quickLootHudOffsetY).set(quickLootHudOffsetY);
        config.get(CATEGORY_CLIENT, "quickLootHudMaxRows", quickLootHudMaxRows).set(quickLootHudMaxRows);
        config.get(CATEGORY_CLIENT, "showQuestHud", showQuestHud).set(showQuestHud);
        config.get(CATEGORY_CLIENT, "linkShowQuestHud", linkShowQuestHud).set(linkShowQuestHud);
        config.get(CATEGORY_CLIENT, "questHudOffsetX", questHudOffsetX).set(questHudOffsetX);
        config.get(CATEGORY_CLIENT, "questHudOffsetY", questHudOffsetY).set(questHudOffsetY);
        config.get(CATEGORY_CLIENT, "questHudMaxObjectives", questHudMaxObjectives).set(questHudMaxObjectives);
        config.get(CATEGORY_CLIENT, "showWorldQuestMarkers", showWorldQuestMarkers).set(showWorldQuestMarkers);
        config.get(CATEGORY_CLIENT, "worldQuestMarkerMaxDistance", worldQuestMarkerMaxDistance).set(worldQuestMarkerMaxDistance);
        config.get(CATEGORY_CLIENT, "showQuestChatFeedback", showQuestChatFeedback).set(showQuestChatFeedback);
        config.get(CATEGORY_CLIENT, "playQuestSounds", playQuestSounds).set(playQuestSounds);
        config.get(CATEGORY_QUESTS, "enableQuestPrerequisites", enableQuestPrerequisites).set(enableQuestPrerequisites);
        config.get(CATEGORY_QUESTS, "enableQuestRewards", enableQuestRewards).set(enableQuestRewards);
        config.get(CATEGORY_QUESTS, "allowQuestJournalStarts", allowQuestJournalStarts).set(allowQuestJournalStarts);
        config.get(CATEGORY_QUESTS, "allowQuestItemStarts", allowQuestItemStarts).set(allowQuestItemStarts);
        config.get(CATEGORY_QUESTS, "allowQuestInteractionStarts", allowQuestInteractionStarts).set(allowQuestInteractionStarts);
        config.get(CATEGORY_QUESTS, "enableQuestMarkerDiscovery", enableQuestMarkerDiscovery).set(enableQuestMarkerDiscovery);
        config.get(CATEGORY_QUESTS, "autoRevealQuestMarkersOnStart", autoRevealQuestMarkersOnStart).set(autoRevealQuestMarkersOnStart);
    }
}
