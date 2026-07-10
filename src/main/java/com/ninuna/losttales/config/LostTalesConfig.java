package com.ninuna.losttales.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
/**
 * Small legacy Forge config holder.
 *
 * Keep this intentionally simple: modern NeoForge ModConfigSpec values are
 * replaced with static fields loaded during preInit.
 */
public final class LostTalesConfig {
    public static final String CATEGORY_CLIENT = "client";
    public static final String CATEGORY_QUESTS = "quests";
    public static final String CATEGORY_MISSIVES = "missives";

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
    public static int hostileCompassMarkerScanRadius = 48;

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
    public static int questHudMaxTrackedQuests = 4;
    public static int questHudObjectiveLineCount = 2;
    public static boolean showQuestHudNotifications = true;
    public static boolean showWorldQuestMarkers = true;
    public static boolean showDiscoveredWorldMapMarkers = true;
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
    public static boolean autoPinQuestOnStart = true;
    public static boolean autoDiscoverNearbyMapMarkers = true;
    public static int mapMarkerDiscoveryScanIntervalTicks = 40;

    public static boolean enableDynamicMissiveBoards = true;
    public static int missiveBoardMinAvailable = 5;
    public static int missiveBoardMaxAvailable = 9;
    public static int missiveBoardGenerationIntervalTicks = 36000;
    public static int missiveBoardMinGeneratedPerCycle = 1;
    public static int missiveBoardMaxGeneratedPerCycle = 3;
    public static boolean expireMissiveBoardNotices = true;
    public static int missiveBoardNoticeExpirationDays = 7;
    public static boolean enableTimedMissives = true;
    public static int timedMissiveChancePercent = 25;
    public static int timedMissiveMinDays = 1;
    public static int timedMissiveMaxDays = 3;

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
                    "When true, hostile compass markers are shown only for mobs the server recently confirmed are targeting you. When false, the legacy 1.7.10 fallback also shows ordinary vanilla IMob hostiles and unfriendly LOTR NPCs."
            );
            hostileCompassMarkerScanRadius = config.getInt(
                    "hostileCompassMarkerScanRadius",
                    CATEGORY_CLIENT,
                    hostileCompassMarkerScanRadius,
                    8,
                    128,
                    "Scan radius in blocks for hostile compass markers and server-side aggro sync."
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
                    "Maximum number of current-stage objectives shown per tracked quest on the quest HUD."
            );
            questHudMaxTrackedQuests = config.getInt(
                    "questHudMaxTrackedQuests",
                    CATEGORY_CLIENT,
                    questHudMaxTrackedQuests,
                    1,
                    8,
                    "Maximum number of tracked quests drawn on the quest HUD before showing an overflow count."
            );
            questHudObjectiveLineCount = config.getInt(
                    "questHudObjectiveLineCount",
                    CATEGORY_CLIENT,
                    questHudObjectiveLineCount,
                    1,
                    3,
                    "Maximum wrapped text lines drawn for each objective on the quest HUD."
            );
            showQuestHudNotifications = config.getBoolean(
                    "showQuestHudNotifications",
                    CATEGORY_CLIENT,
                    showQuestHudNotifications,
                    "Render centered quest notification banners for quest starts, objective progress, and completions."
            );
            showWorldQuestMarkers = config.getBoolean(
                    "showWorldQuestMarkers",
                    CATEGORY_CLIENT,
                    showWorldQuestMarkers,
                    "Render lightweight world-space labels above discovered quest map markers. Uses legacy 1.7.10 nameplate rendering instead of the modern NeoForge level overlay system."
            );
            showDiscoveredWorldMapMarkers = config.getBoolean(
                    "showDiscoveredWorldMapMarkers",
                    CATEGORY_CLIENT,
                    showDiscoveredWorldMapMarkers,
                    "Also render world-space labels for discovered non-quest map markers. Disable this if too many map markers clutter the world view."
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
            autoPinQuestOnStart = config.getBoolean(
                    "autoPinQuestOnStart",
                    CATEGORY_QUESTS,
                    autoPinQuestOnStart,
                    "Automatically track a quest on the HUD when it starts, as long as the player is not already tracking it."
            );
            autoDiscoverNearbyMapMarkers = config.getBoolean(
                    "autoDiscoverNearbyMapMarkers",
                    CATEGORY_QUESTS,
                    autoDiscoverNearbyMapMarkers,
                    "Automatically discover bundled map markers when a player walks within that marker's unlock radius."
            );
            mapMarkerDiscoveryScanIntervalTicks = config.getInt(
                    "mapMarkerDiscoveryScanIntervalTicks",
                    CATEGORY_QUESTS,
                    mapMarkerDiscoveryScanIntervalTicks,
                    20,
                    200,
                    "How often, in ticks, nearby map marker discovery is checked on the server."
            );

            enableDynamicMissiveBoards = config.getBoolean(
                    "enableDynamicMissiveBoards",
                    CATEGORY_MISSIVES,
                    enableDynamicMissiveBoards,
                    "When true, missive board tile entities generate dynamic missive letters on the server."
            );
            missiveBoardMinAvailable = config.getInt(
                    "missiveBoardMinAvailable",
                    CATEGORY_MISSIVES,
                    missiveBoardMinAvailable,
                    0,
                    9,
                    "Desired minimum number of notices a board tries to keep available. Existing boards clamp this to their 9-slot inventory."
            );
            missiveBoardMaxAvailable = config.getInt(
                    "missiveBoardMaxAvailable",
                    CATEGORY_MISSIVES,
                    missiveBoardMaxAvailable,
                    1,
                    9,
                    "Maximum number of notices a board can keep available. The missive board inventory has 9 notice slots."
            );
            missiveBoardGenerationIntervalTicks = config.getInt(
                    "missiveBoardGenerationIntervalTicks",
                    CATEGORY_MISSIVES,
                    missiveBoardGenerationIntervalTicks,
                    1200,
                    240000,
                    "Server-side interval in ticks between ordinary board refill attempts. 36000 ticks is roughly half an in-game hour."
            );
            missiveBoardMinGeneratedPerCycle = config.getInt(
                    "missiveBoardMinGeneratedPerCycle",
                    CATEGORY_MISSIVES,
                    missiveBoardMinGeneratedPerCycle,
                    1,
                    9,
                    "Minimum number of new notices generated per refill cycle when space is available."
            );
            missiveBoardMaxGeneratedPerCycle = config.getInt(
                    "missiveBoardMaxGeneratedPerCycle",
                    CATEGORY_MISSIVES,
                    missiveBoardMaxGeneratedPerCycle,
                    1,
                    9,
                    "Maximum number of new notices generated per refill cycle when space is available."
            );
            expireMissiveBoardNotices = config.getBoolean(
                    "expireMissiveBoardNotices",
                    CATEGORY_MISSIVES,
                    expireMissiveBoardNotices,
                    "When true, old unaccepted notices are removed from missive boards so they can be replaced over time."
            );
            missiveBoardNoticeExpirationDays = config.getInt(
                    "missiveBoardNoticeExpirationDays",
                    CATEGORY_MISSIVES,
                    missiveBoardNoticeExpirationDays,
                    1,
                    30,
                    "How many in-game days an unaccepted generated board notice remains available before expiring."
            );
            enableTimedMissives = config.getBoolean(
                    "enableTimedMissives",
                    CATEGORY_MISSIVES,
                    enableTimedMissives,
                    "When true, some generated missives receive a deadline after being accepted."
            );
            timedMissiveChancePercent = config.getInt(
                    "timedMissiveChancePercent",
                    CATEGORY_MISSIVES,
                    timedMissiveChancePercent,
                    0,
                    100,
                    "Percent chance that a generated missive receives a deadline."
            );
            timedMissiveMinDays = config.getInt(
                    "timedMissiveMinDays",
                    CATEGORY_MISSIVES,
                    timedMissiveMinDays,
                    1,
                    30,
                    "Minimum accepted-quest deadline length in in-game days for timed missives."
            );
            timedMissiveMaxDays = config.getInt(
                    "timedMissiveMaxDays",
                    CATEGORY_MISSIVES,
                    timedMissiveMaxDays,
                    1,
                    30,
                    "Maximum accepted-quest deadline length in in-game days for timed missives."
            );
            clampMissiveOptions();

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
        config.getCategory(CATEGORY_MISSIVES).setLanguageKey("losttales.config.category.missives");
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
        config.get(CATEGORY_CLIENT, "hostileCompassMarkerScanRadius", hostileCompassMarkerScanRadius).set(hostileCompassMarkerScanRadius);
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
        config.get(CATEGORY_CLIENT, "questHudMaxTrackedQuests", questHudMaxTrackedQuests).set(questHudMaxTrackedQuests);
        config.get(CATEGORY_CLIENT, "questHudObjectiveLineCount", questHudObjectiveLineCount).set(questHudObjectiveLineCount);
        config.get(CATEGORY_CLIENT, "showQuestHudNotifications", showQuestHudNotifications).set(showQuestHudNotifications);
        config.get(CATEGORY_CLIENT, "showWorldQuestMarkers", showWorldQuestMarkers).set(showWorldQuestMarkers);
        config.get(CATEGORY_CLIENT, "showDiscoveredWorldMapMarkers", showDiscoveredWorldMapMarkers).set(showDiscoveredWorldMapMarkers);
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
        config.get(CATEGORY_QUESTS, "autoPinQuestOnStart", autoPinQuestOnStart).set(autoPinQuestOnStart);
        config.get(CATEGORY_QUESTS, "autoDiscoverNearbyMapMarkers", autoDiscoverNearbyMapMarkers).set(autoDiscoverNearbyMapMarkers);
        config.get(CATEGORY_QUESTS, "mapMarkerDiscoveryScanIntervalTicks", mapMarkerDiscoveryScanIntervalTicks).set(mapMarkerDiscoveryScanIntervalTicks);
        config.get(CATEGORY_MISSIVES, "enableDynamicMissiveBoards", enableDynamicMissiveBoards).set(enableDynamicMissiveBoards);
        config.get(CATEGORY_MISSIVES, "missiveBoardMinAvailable", missiveBoardMinAvailable).set(missiveBoardMinAvailable);
        config.get(CATEGORY_MISSIVES, "missiveBoardMaxAvailable", missiveBoardMaxAvailable).set(missiveBoardMaxAvailable);
        config.get(CATEGORY_MISSIVES, "missiveBoardGenerationIntervalTicks", missiveBoardGenerationIntervalTicks).set(missiveBoardGenerationIntervalTicks);
        config.get(CATEGORY_MISSIVES, "missiveBoardMinGeneratedPerCycle", missiveBoardMinGeneratedPerCycle).set(missiveBoardMinGeneratedPerCycle);
        config.get(CATEGORY_MISSIVES, "missiveBoardMaxGeneratedPerCycle", missiveBoardMaxGeneratedPerCycle).set(missiveBoardMaxGeneratedPerCycle);
        config.get(CATEGORY_MISSIVES, "expireMissiveBoardNotices", expireMissiveBoardNotices).set(expireMissiveBoardNotices);
        config.get(CATEGORY_MISSIVES, "missiveBoardNoticeExpirationDays", missiveBoardNoticeExpirationDays).set(missiveBoardNoticeExpirationDays);
        config.get(CATEGORY_MISSIVES, "enableTimedMissives", enableTimedMissives).set(enableTimedMissives);
        config.get(CATEGORY_MISSIVES, "timedMissiveChancePercent", timedMissiveChancePercent).set(timedMissiveChancePercent);
        config.get(CATEGORY_MISSIVES, "timedMissiveMinDays", timedMissiveMinDays).set(timedMissiveMinDays);
        config.get(CATEGORY_MISSIVES, "timedMissiveMaxDays", timedMissiveMaxDays).set(timedMissiveMaxDays);
    }

    public static void clampMissiveOptions() {
        missiveBoardMinAvailable = clampInt(missiveBoardMinAvailable, 0, 9);
        missiveBoardMaxAvailable = clampInt(missiveBoardMaxAvailable, 1, 9);
        if (missiveBoardMaxAvailable < missiveBoardMinAvailable) {
            missiveBoardMaxAvailable = missiveBoardMinAvailable;
        }
        missiveBoardGenerationIntervalTicks = clampInt(missiveBoardGenerationIntervalTicks, 1200, 240000);
        missiveBoardMinGeneratedPerCycle = clampInt(missiveBoardMinGeneratedPerCycle, 1, 9);
        missiveBoardMaxGeneratedPerCycle = clampInt(missiveBoardMaxGeneratedPerCycle, 1, 9);
        if (missiveBoardMaxGeneratedPerCycle < missiveBoardMinGeneratedPerCycle) {
            missiveBoardMaxGeneratedPerCycle = missiveBoardMinGeneratedPerCycle;
        }
        missiveBoardNoticeExpirationDays = clampInt(missiveBoardNoticeExpirationDays, 1, 30);
        timedMissiveChancePercent = clampInt(timedMissiveChancePercent, 0, 100);
        timedMissiveMinDays = clampInt(timedMissiveMinDays, 1, 30);
        timedMissiveMaxDays = clampInt(timedMissiveMaxDays, 1, 30);
        if (timedMissiveMaxDays < timedMissiveMinDays) {
            timedMissiveMaxDays = timedMissiveMinDays;
        }
    }

    public static long getMissiveBoardNoticeExpirationTicks() {
        if (!expireMissiveBoardNotices || missiveBoardNoticeExpirationDays <= 0) {
            return 0L;
        }
        return (long) missiveBoardNoticeExpirationDays * 24000L;
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}

