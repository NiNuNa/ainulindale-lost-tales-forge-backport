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
    public static final String CATEGORY_CHARACTERS = "characters";
    public static final String CATEGORY_COMBAT_MARKERS = "combat_markers";
    public static final String CATEGORY_PARTY = "party";
    public static final String CATEGORY_RANGED_COMBAT = "ranged_combat";

    public static final String HUD_PRESET_CUSTOM = "custom";
    public static final String HUD_PRESET_DEFAULT = "default";
    public static final String HUD_PRESET_LOTR_SAFE = "lotr-safe";
    public static final String HUD_PRESET_COMPACT = "compact";
    public static final String HUD_PRESET_MINIMAL = "minimal";
    public static final int HUD_PLACEMENT_VERSION = 2;
    public static final String[] HUD_PRESET_VALUES = new String[] {
            HUD_PRESET_CUSTOM,
            HUD_PRESET_DEFAULT,
            HUD_PRESET_LOTR_SAFE,
            HUD_PRESET_COMPACT,
            HUD_PRESET_MINIMAL
    };

    private static File loadedConfigFile;
    private static Configuration pendingGuiConfiguration;

    public static boolean showLostTalesHud = true;
    public static String hudPlacementPreset = HUD_PRESET_CUSTOM;
    public static int hudPlacementVersion = HUD_PLACEMENT_VERSION;

    public static boolean showCompassHud = true;
    public static boolean linkShowCompassHud = false;
    public static double compassHudOffsetX = 50.0D;
    public static double compassHudOffsetY = 2.0D;
    public static int compassHudDisplayRadius = 90;
    public static boolean showStaticCompassMarkers = true;
    public static boolean showLotrWaypointCompassMarkers = true;
    public static boolean onlyShowUnlockedLotrWaypoints = true;
    public static boolean showHostileCompassMarkers = true;
    public static boolean onlyShowAggroHostileCompassMarkers = true;
    public static int hostileCompassMarkerScanRadius = 48;
    public static boolean showHostileMapMarkers = true;
    public static int hostileMapMarkerDisplayRadius = 64;

    public static int combatMarkerTrackingRadius = 64;
    public static int combatMarkerUpdateIntervalTicks = 10;
    public static int combatMarkerDisengagementGraceTicks = 20;
    public static boolean combatMarkerDebugLogging = false;
    public static boolean partySharedAggroTracking = true;

    public static boolean showPartyHud = true;
    public static boolean linkShowPartyHud = false;
    public static double partyHudOffsetX = 2.0D;
    public static double partyHudOffsetY = 18.0D;
    public static int partyCompassMarkerFadeRadius = 100;

    public static int partyStatusUpdateIntervalTicks = 10;
    public static int partyStatusHeartbeatTicks = 100;
    public static int partyTrackingUpdateIntervalTicks = 10;
    public static int partyTrackingHeartbeatTicks = 100;
    public static boolean enablePartySharedQuestKillProgress = true;
    public static int partySharedQuestRadius = 32;

    public static boolean showQuickLootHud = true;
    public static boolean linkShowQuickLootHud = false;
    public static int quickLootHudMaxRows = 5;
    public static double quickLootHudOffsetX = 24.0D;
    public static double quickLootHudOffsetY = 32.0D;

    public static boolean showQuestHud = true;
    public static boolean linkShowQuestHud = false;
    public static double questHudOffsetX = 2.0D;
    public static double questHudOffsetY = 38.0D;
    public static int questHudMaxObjectives = 3;
    public static int questHudMaxTrackedQuests = 4;
    public static int questHudObjectiveLineCount = 2;
    public static boolean showQuestHudNotifications = true;
    public static double questNotificationHudOffsetX = 50.0D;
    public static double questNotificationHudOffsetY = 75.0D;
    public static double mapDiscoveryHudOffsetX = 50.0D;
    public static double mapDiscoveryHudOffsetY = 35.0D;
    public static double areaNoticeHudOffsetX = 50.0D;
    public static double areaNoticeHudOffsetY = 15.0D;
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

    /** Empty allow-list means all LOTR-playable factions are eligible. */
    public static String[] allowedStartingFactionIds = new String[0];
    /** Deny-list always wins over the allow-list and race category matching. */
    public static String[] deniedStartingFactionIds = new String[0];

    /** Cooldown applied at each escalation stage, in seconds. */
    public static int[] characterSwitchCooldownSeconds =
            new int[] {60, 180, 300, 900, 1800, 3600};
    /** Inactivity needed to decay each current stage, in seconds. */
    public static int[] characterSwitchDecaySeconds =
            new int[] {0, 3600, 10800, 21600, 43200, 86400};
    public static int characterSwitchCombatGraceSeconds = 20;
    public static int characterSwitchTeleportGraceSeconds = 5;
    public static int characterSwitchStableGroundTicks = 20;
    public static double characterSwitchTeleportDistancePerTick = 16.0D;
    public static int characterStateMaxSnapshotBytes = 2 * 1024 * 1024;
    public static int characterStateCheckpointIntervalSeconds = 300;
    public static int characterStateCheckpointPlayersPerTick = 1;
    public static int characterDeletionRetentionDays = 30;
    public static long characterSwitchCombatGraceMillis = 20000L;
    public static long characterSwitchTeleportGraceMillis = 5000L;

    public static boolean enableChargeTiers = true;
    public static int chargeTierOneTicks = 10;
    public static int chargeTierTwoTicks = 24;
    public static int chargeTierThreeTicks = 42;
    public static double chargeTierOneDamageMultiplier = 1.12D;
    public static double chargeTierTwoDamageMultiplier = 1.30D;
    public static double chargeTierThreeDamageMultiplier = 1.60D;
    public static double chargeTierOneVelocityMultiplier = 1.04D;
    public static double chargeTierTwoVelocityMultiplier = 1.09D;
    public static double chargeTierThreeVelocityMultiplier = 1.16D;
    public static double chargeTierOneKnockback = 0.0D;
    public static double chargeTierTwoKnockback = 0.12D;
    public static double chargeTierThreeKnockback = 0.24D;

    private LostTalesConfig() {}

    public static void load(File configFile) {
        loadedConfigFile = configFile;
        Configuration config = new Configuration(configFile);
        try {
            config.load();
            applyGuiMetadata(config);

            enableChargeTiers = config.getBoolean(
                    "enableChargeTiers", CATEGORY_RANGED_COMBAT,
                    enableChargeTiers,
                    "Enable server-authoritative post-full-draw charge tiers for bows, chargeable spears, and explicitly registered compatible weapons.");
            chargeTierOneTicks = config.getInt(
                    "chargeTierOneTicks", CATEGORY_RANGED_COMBAT,
                    chargeTierOneTicks, 1, 200,
                    "Ticks held after the weapon reaches its normal full draw before charge tier one activates.");
            chargeTierTwoTicks = config.getInt(
                    "chargeTierTwoTicks", CATEGORY_RANGED_COMBAT,
                    chargeTierTwoTicks, 1, 400,
                    "Ticks held after normal full draw before charge tier two activates.");
            chargeTierThreeTicks = config.getInt(
                    "chargeTierThreeTicks", CATEGORY_RANGED_COMBAT,
                    chargeTierThreeTicks, 1, 600,
                    "Ticks held after normal full draw before charge tier three activates.");
            chargeTierTwoTicks = Math.max(
                    chargeTierOneTicks + 1, chargeTierTwoTicks);
            chargeTierThreeTicks = Math.max(
                    chargeTierTwoTicks + 1, chargeTierThreeTicks);
            config.get(CATEGORY_RANGED_COMBAT,
                    "chargeTierTwoTicks", chargeTierTwoTicks)
                    .set(chargeTierTwoTicks);
            config.get(CATEGORY_RANGED_COMBAT,
                    "chargeTierThreeTicks", chargeTierThreeTicks)
                    .set(chargeTierThreeTicks);
            chargeTierOneDamageMultiplier = getBoundedDouble(
                    config, "chargeTierOneDamageMultiplier",
                    chargeTierOneDamageMultiplier, 1.0D, 3.0D,
                    "Damage multiplier applied by a tier-one projectile.");
            chargeTierTwoDamageMultiplier = getBoundedDouble(
                    config, "chargeTierTwoDamageMultiplier",
                    chargeTierTwoDamageMultiplier, 1.0D, 4.0D,
                    "Damage multiplier applied by a tier-two projectile.");
            chargeTierThreeDamageMultiplier = getBoundedDouble(
                    config, "chargeTierThreeDamageMultiplier",
                    chargeTierThreeDamageMultiplier, 1.0D, 6.0D,
                    "Damage multiplier applied by a tier-three projectile.");
            chargeTierOneVelocityMultiplier = getBoundedDouble(
                    config, "chargeTierOneVelocityMultiplier",
                    chargeTierOneVelocityMultiplier, 1.0D, 2.0D,
                    "Launch-speed multiplier applied to a tier-one projectile.");
            chargeTierTwoVelocityMultiplier = getBoundedDouble(
                    config, "chargeTierTwoVelocityMultiplier",
                    chargeTierTwoVelocityMultiplier, 1.0D, 2.0D,
                    "Launch-speed multiplier applied to a tier-two projectile.");
            chargeTierThreeVelocityMultiplier = getBoundedDouble(
                    config, "chargeTierThreeVelocityMultiplier",
                    chargeTierThreeVelocityMultiplier, 1.0D, 2.0D,
                    "Launch-speed multiplier applied to a tier-three projectile.");
            chargeTierOneKnockback = getBoundedDouble(
                    config, "chargeTierOneKnockback",
                    chargeTierOneKnockback, 0.0D, 1.0D,
                    "Additional horizontal knockback velocity from a tier-one projectile.");
            chargeTierTwoKnockback = getBoundedDouble(
                    config, "chargeTierTwoKnockback",
                    chargeTierTwoKnockback, 0.0D, 1.0D,
                    "Additional horizontal knockback velocity from a tier-two projectile.");
            chargeTierThreeKnockback = getBoundedDouble(
                    config, "chargeTierThreeKnockback",
                    chargeTierThreeKnockback, 0.0D, 1.0D,
                    "Additional horizontal knockback velocity from a tier-three projectile.");

            allowedStartingFactionIds = config.get(
                    CATEGORY_CHARACTERS,
                    "allowedStartingFactionIds",
                    allowedStartingFactionIds,
                    "Optional canonical LOTR faction IDs (for example lotr:lothlorien). Empty means all LOTR-playable factions may be offered before race filtering."
            ).getStringList();
            deniedStartingFactionIds = config.get(
                    CATEGORY_CHARACTERS,
                    "deniedStartingFactionIds",
                    deniedStartingFactionIds,
                    "Canonical LOTR faction IDs excluded from character creation. Deny entries override allow entries."
            ).getStringList();
            characterSwitchCooldownSeconds = config.get(
                    CATEGORY_CHARACTERS,
                    "switchCooldownSeconds",
                    characterSwitchCooldownSeconds,
                    "Escalating server-authoritative cooldown stages in seconds. The default is 1m, 3m, 5m, 15m, 30m, 60m."
            ).getIntList();
            characterSwitchDecaySeconds = config.get(
                    CATEGORY_CHARACTERS,
                    "switchCooldownDecaySeconds",
                    characterSwitchDecaySeconds,
                    "Inactivity required to decay each current cooldown stage. Higher stages should use longer values."
            ).getIntList();
            characterSwitchCombatGraceSeconds = config.getInt(
                    "switchCombatGraceSeconds",
                    CATEGORY_CHARACTERS,
                    characterSwitchCombatGraceSeconds,
                    0,
                    3600,
                    "Reject switching for this many seconds after incoming or outgoing combat evidence."
            );
            characterSwitchTeleportGraceSeconds = config.getInt(
                    "switchTeleportGraceSeconds",
                    CATEGORY_CHARACTERS,
                    characterSwitchTeleportGraceSeconds,
                    0,
                    300,
                    "Reject switching for this many seconds after teleport, respawn, or dimension-transition evidence."
            );
            characterSwitchStableGroundTicks = config.getInt(
                    "switchStableGroundTicks",
                    CATEGORY_CHARACTERS,
                    characterSwitchStableGroundTicks,
                    0,
                    200,
                    "Number of consecutive safe grounded ticks required before switching."
            );
            characterSwitchTeleportDistancePerTick = config.get(
                    CATEGORY_CHARACTERS,
                    "switchTeleportDistancePerTick",
                    characterSwitchTeleportDistancePerTick,
                    "Movement farther than this many blocks in one tick is treated as teleportation."
            ).getDouble(characterSwitchTeleportDistancePerTick);
            characterStateMaxSnapshotBytes = config.getInt(
                    "characterStateMaxSnapshotBytes",
                    CATEGORY_CHARACTERS,
                    characterStateMaxSnapshotBytes,
                    64 * 1024,
                    16 * 1024 * 1024,
                    "Maximum compressed size of one character-owned player-state snapshot. Oversized snapshots are rejected before switching."
            );
            characterStateCheckpointIntervalSeconds = config.getInt(
                    "characterStateCheckpointIntervalSeconds",
                    CATEGORY_CHARACTERS,
                    characterStateCheckpointIntervalSeconds,
                    30,
                    3600,
                    "Seconds between durable checkpoints of every online active character. Work is spread across server ticks."
            );
            characterStateCheckpointPlayersPerTick = config.getInt(
                    "characterStateCheckpointPlayersPerTick",
                    CATEGORY_CHARACTERS,
                    characterStateCheckpointPlayersPerTick,
                    1,
                    4,
                    "Maximum online characters durably checkpointed in one server tick."
            );
            characterDeletionRetentionDays = config.getInt(
                    "characterDeletionRetentionDays",
                    CATEGORY_CHARACTERS,
                    characterDeletionRetentionDays,
                    1,
                    3650,
                    "Minimum number of days a deleted character and its player-state generations remain recoverable before an administrator may permanently purge them."
            );
            sanitizeCharacterSwitchOptions();

            showLostTalesHud = config.getBoolean(
                    "showLostTalesHud",
                    CATEGORY_CLIENT,
                    showLostTalesHud,
                    "Master toggle for Lost Tales HUD elements."
            );
            int loadedHudPlacementVersion = config.getInt(
                    "hudPlacementVersion",
                    CATEGORY_CLIENT,
                    1,
                    1,
                    HUD_PLACEMENT_VERSION,
                    "Internal client HUD coordinate version. Older Quick Loot positions are migrated automatically."
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
            compassHudOffsetX = getHudPercent(
                    config, "compassHudOffsetX",
                    compassHudOffsetX, 0.0D, 100.0D,
                    "Horizontal compass position as a percentage of the scaled screen width."
            );
            compassHudOffsetY = getHudPercent(
                    config, "compassHudOffsetY",
                    compassHudOffsetY, 0.0D, 100.0D,
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
                    "Render server-approved enemies actively engaged with the local player on the Lost Tales compass."
            );
            Property strictHostileMarkers = config.get(
                    CATEGORY_CLIENT,
                    "onlyShowAggroHostileCompassMarkers",
                    true,
                    "Compatibility key retained from older builds. Active-combat filtering is now always required and this value is forced to true."
            );
            strictHostileMarkers.set(true);
            onlyShowAggroHostileCompassMarkers = true;
            hostileCompassMarkerScanRadius = config.getInt(
                    "hostileCompassMarkerScanRadius",
                    CATEGORY_CLIENT,
                    hostileCompassMarkerScanRadius,
                    8,
                    128,
                    "Client display radius in blocks for enemy compass markers. The server tracking radius remains authoritative."
            );
            showHostileMapMarkers = config.getBoolean(
                    "showHostileMapMarkers",
                    CATEGORY_CLIENT,
                    showHostileMapMarkers,
                    "Render transient active-combat enemy markers on the LOTR main map. These markers are never saved."
            );
            hostileMapMarkerDisplayRadius = config.getInt(
                    "hostileMapMarkerDisplayRadius",
                    CATEGORY_CLIENT,
                    hostileMapMarkerDisplayRadius,
                    8,
                    128,
                    "Client display radius in blocks for transient enemy markers on the LOTR main map. The server tracking radius remains authoritative."
            );

            combatMarkerTrackingRadius = config.getInt(
                    "trackingRadius",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerTrackingRadius,
                    8,
                    128,
                    "Server-authoritative maximum range in blocks for player-specific combat marker tracking."
            );
            combatMarkerUpdateIntervalTicks = config.getInt(
                    "updateIntervalTicks",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerUpdateIntervalTicks,
                    1,
                    40,
                    "Server ticks between combat-state scans. Snapshots are sent only when their contents change."
            );
            combatMarkerDisengagementGraceTicks = config.getInt(
                    "disengagementGraceTicks",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerDisengagementGraceTicks,
                    0,
                    60,
                    "Ticks that an exact player/entity combat relationship may remain visible after direct evidence disappears."
            );
            combatMarkerDebugLogging = config.getBoolean(
                    "debugLogging",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerDebugLogging,
                    "Log changed combat marker snapshots. Disabled by default to avoid log spam."
            );
            partySharedAggroTracking = config.getBoolean(
                    "shareWithParty",
                    CATEGORY_COMBAT_MARKERS,
                    partySharedAggroTracking,
                    "Share server-approved active-combat enemy markers with authorized nearby members of the same role-playing party."
            );

            showPartyHud = config.getBoolean(
                    "showPartyHud",
                    CATEGORY_CLIENT,
                    showPartyHud,
                    "Render the compact party member HUD while the active role-playing character belongs to a party."
            );
            linkShowPartyHud = config.getBoolean(
                    "linkShowPartyHud",
                    CATEGORY_CLIENT,
                    linkShowPartyHud,
                    "When true, changing showLostTalesHud also changes showPartyHud."
            );
            partyHudOffsetX = getHudPercent(
                    config, "partyHudOffsetX",
                    partyHudOffsetX, 0.0D, 100.0D,
                    "Horizontal party HUD position as a percentage of the scaled screen width."
            );
            partyHudOffsetY = getHudPercent(
                    config, "partyHudOffsetY",
                    partyHudOffsetY, 0.0D, 100.0D,
                    "Vertical party HUD position as a percentage of the scaled screen height."
            );
            partyCompassMarkerFadeRadius = config.getInt(
                    "partyCompassMarkerFadeRadius",
                    CATEGORY_CLIENT,
                    partyCompassMarkerFadeRadius,
                    16,
                    2048,
                    "Distance in blocks over which party-member compass markers fade to their minimum opacity. Beyond this distance they remain visible at the opacity floor."
            );
            partyStatusUpdateIntervalTicks = config.getInt(
                    "statusUpdateIntervalTicks",
                    CATEGORY_PARTY,
                    partyStatusUpdateIntervalTicks,
                    2,
                    40,
                    "Server ticks between party health and availability checks. Packets are sent only when state changes or a heartbeat is due."
            );
            partyStatusHeartbeatTicks = config.getInt(
                    "statusHeartbeatTicks",
                    CATEGORY_PARTY,
                    partyStatusHeartbeatTicks,
                    20,
                    400,
                    "Maximum server ticks between unchanged party status snapshots for online party members."
            );
            partyTrackingUpdateIntervalTicks = config.getInt(
                    "trackingUpdateIntervalTicks",
                    CATEGORY_PARTY,
                    partyTrackingUpdateIntervalTicks,
                    2,
                    40,
                    "Server ticks between authorized party position checks. Coordinates are quantized and packets are sent only when state changes or a heartbeat is due."
            );
            partyTrackingHeartbeatTicks = config.getInt(
                    "trackingHeartbeatTicks",
                    CATEGORY_PARTY,
                    partyTrackingHeartbeatTicks,
                    20,
                    400,
                    "Maximum server ticks between unchanged party tracking snapshots for online party members."
            );
            enablePartySharedQuestKillProgress = config.getBoolean(
                    "enableSharedQuestKillProgress",
                    CATEGORY_PARTY,
                    enablePartySharedQuestKillProgress,
                    "Allow one authoritative kill event to advance matching Lost Tales kill objectives for eligible nearby party members. Completion and rewards remain individual."
            );
            partySharedQuestRadius = config.getInt(
                    "sharedQuestRadius",
                    CATEGORY_PARTY,
                    partySharedQuestRadius,
                    1,
                    128,
                    "Maximum block distance for conservative party-shared kill objective progress. Members must be online, alive, in the same dimension, using the party character, and independently possess the matching quest."
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
            quickLootHudOffsetX = getHudPercent(
                    config, "quickLootHudOffsetX",
                    loadedHudPlacementVersion < HUD_PLACEMENT_VERSION
                            ? 24.0D : quickLootHudOffsetX,
                    0.0D, 100.0D,
                    "Horizontal quick-loot position as a percentage of the scaled screen width."
            );
            quickLootHudOffsetY = getHudPercent(
                    config, "quickLootHudOffsetY",
                    quickLootHudOffsetY, 0.0D, 100.0D,
                    "Vertical quick-loot position as a percentage of the scaled screen height."
            );
            if (loadedHudPlacementVersion < HUD_PLACEMENT_VERSION) {
                quickLootHudOffsetX = migrateLegacyQuickLootOffsetX(
                        quickLootHudOffsetX);
            }
            hudPlacementVersion = HUD_PLACEMENT_VERSION;
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
            questHudOffsetX = getHudPercent(
                    config, "questHudOffsetX",
                    questHudOffsetX, 0.0D, 100.0D,
                    "Horizontal quest tracker position as a percentage of the scaled screen width."
            );
            questHudOffsetY = getHudPercent(
                    config, "questHudOffsetY",
                    questHudOffsetY, 0.0D, 100.0D,
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
            questNotificationHudOffsetX = getHudPercent(
                    config, "questNotificationHudOffsetX",
                    questNotificationHudOffsetX, 0.0D, 100.0D,
                    "Horizontal quest-notification position within the available scaled screen width."
            );
            questNotificationHudOffsetY = getHudPercent(
                    config, "questNotificationHudOffsetY",
                    questNotificationHudOffsetY, 0.0D, 100.0D,
                    "Vertical quest-notification position within the available scaled screen height."
            );
            mapDiscoveryHudOffsetX = getHudPercent(
                    config, "mapDiscoveryHudOffsetX",
                    mapDiscoveryHudOffsetX, 0.0D, 100.0D,
                    "Horizontal location-discovery banner position within the available scaled screen width."
            );
            mapDiscoveryHudOffsetY = getHudPercent(
                    config, "mapDiscoveryHudOffsetY",
                    mapDiscoveryHudOffsetY, 0.0D, 100.0D,
                    "Vertical location-discovery banner position within the available scaled screen height."
            );
            areaNoticeHudOffsetX = getHudPercent(
                    config, "areaNoticeHudOffsetX",
                    areaNoticeHudOffsetX, 0.0D, 100.0D,
                    "Horizontal area-name notice position within the available scaled screen width."
            );
            areaNoticeHudOffsetY = getHudPercent(
                    config, "areaNoticeHudOffsetY",
                    areaNoticeHudOffsetY, 0.0D, 100.0D,
                    "Vertical area-name notice position within the available scaled screen height."
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

    public static synchronized Configuration createConfiguration() {
        pendingGuiConfiguration = loadedConfigFile == null
                ? null : new Configuration(loadedConfigFile);
        return pendingGuiConfiguration;
    }

    /** Saves the exact Configuration whose properties the Forge GUI edited. */
    public static synchronized void savePendingGuiConfiguration() {
        Configuration pending = pendingGuiConfiguration;
        if (pending != null) {
            pending.save();
        }
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
        if (linkShowPartyHud) {
            showPartyHud = showLostTalesHud;
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
            partyHudOffsetX = 2;
            partyHudOffsetY = 18;
            quickLootHudOffsetX = 62;
            quickLootHudOffsetY = 32;
            questHudOffsetX = 2;
            questHudOffsetY = 38;
            setNotificationPresetOffsets(50, 75, 50, 35, 50, 15);
        } else if (HUD_PRESET_LOTR_SAFE.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 12;
            partyHudOffsetX = 2;
            partyHudOffsetY = 28;
            quickLootHudOffsetX = 61;
            quickLootHudOffsetY = 34;
            questHudOffsetX = 2;
            questHudOffsetY = 52;
            setNotificationPresetOffsets(50, 78, 50, 32, 50, 27);
        } else if (HUD_PRESET_COMPACT.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 7;
            partyHudOffsetX = 1;
            partyHudOffsetY = 26;
            quickLootHudOffsetX = 67;
            quickLootHudOffsetY = 37;
            questHudOffsetX = 1;
            questHudOffsetY = 57;
            setNotificationPresetOffsets(50, 78, 50, 30, 50, 22);
        } else if (HUD_PRESET_MINIMAL.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 4;
            partyHudOffsetX = 1;
            partyHudOffsetY = 18;
            quickLootHudOffsetX = 71;
            quickLootHudOffsetY = 42;
            questHudOffsetX = 1;
            questHudOffsetY = 70;
            setNotificationPresetOffsets(50, 82, 50, 28, 50, 18);
        }
    }

    private static void setNotificationPresetOffsets(
            int questX, int questY,
            int discoveryX, int discoveryY,
            int areaX, int areaY) {
        questNotificationHudOffsetX = questX;
        questNotificationHudOffsetY = questY;
        mapDiscoveryHudOffsetX = discoveryX;
        mapDiscoveryHudOffsetY = discoveryY;
        areaNoticeHudOffsetX = areaX;
        areaNoticeHudOffsetY = areaY;
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
        return setHudOffset(element, (double)x, (double)y);
    }

    public static boolean setHudOffset(String element,
                                       double x, double y) {
        boolean updated = updateHudOffset(element, x, y);
        if (updated) {
            save();
        }
        return updated;
    }

    /** Updates the live client preview without writing the config every drag frame. */
    public static boolean updateHudOffset(String element,
                                          double x, double y) {
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
        } else if ("party".equals(key)) {
            partyHudOffsetX = x;
            partyHudOffsetY = y;
        } else if ("quickloot".equals(key)) {
            quickLootHudOffsetX = x;
            quickLootHudOffsetY = y;
        } else if ("quest".equals(key)) {
            questHudOffsetX = x;
            questHudOffsetY = y;
        } else if ("questnotifications".equals(key)) {
            questNotificationHudOffsetX = x;
            questNotificationHudOffsetY = y;
        } else if ("mapdiscovery".equals(key)) {
            mapDiscoveryHudOffsetX = x;
            mapDiscoveryHudOffsetY = y;
        } else if ("areanotice".equals(key)) {
            areaNoticeHudOffsetX = x;
            areaNoticeHudOffsetY = y;
        } else {
            return false;
        }
        return true;
    }

    public static boolean moveHudOffset(String element, int dx, int dy) {
        String key = normalizeHudElement(element);
        if ("compass".equals(key)) {
            return setHudOffset(key, compassHudOffsetX + dx, compassHudOffsetY + dy);
        }
        if ("party".equals(key)) {
            return setHudOffset(key, partyHudOffsetX + dx, partyHudOffsetY + dy);
        }
        if ("quickloot".equals(key)) {
            return setHudOffset(key, quickLootHudOffsetX + dx, quickLootHudOffsetY + dy);
        }
        if ("quest".equals(key)) {
            return setHudOffset(key, questHudOffsetX + dx, questHudOffsetY + dy);
        }
        if ("questnotifications".equals(key)) {
            return setHudOffset(key, questNotificationHudOffsetX + dx,
                    questNotificationHudOffsetY + dy);
        }
        if ("mapdiscovery".equals(key)) {
            return setHudOffset(key, mapDiscoveryHudOffsetX + dx,
                    mapDiscoveryHudOffsetY + dy);
        }
        if ("areanotice".equals(key)) {
            return setHudOffset(key, areaNoticeHudOffsetX + dx,
                    areaNoticeHudOffsetY + dy);
        }
        return false;
    }

    public static String normalizeHudElement(String element) {
        if (element == null) {
            return "";
        }
        String key = element.trim().toLowerCase()
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        if ("compass".equals(key)) {
            return "compass";
        }
        if ("party".equals(key) || "partyhud".equals(key)) {
            return "party";
        }
        if ("quickloot".equals(key) || "loot".equals(key) || "quickloothud".equals(key)) {
            return "quickloot";
        }
        if ("quest".equals(key) || "quests".equals(key) || "questhud".equals(key) || "tracker".equals(key)) {
            return "quest";
        }
        if ("questnotifications".equals(key)
                || "questnotification".equals(key)
                || "notifications".equals(key)
                || "notification".equals(key)
                || "toast".equals(key)
                || "toasts".equals(key)) {
            return "questnotifications";
        }
        if ("mapdiscovery".equals(key)
                || "locationdiscovery".equals(key)
                || "discovery".equals(key)) {
            return "mapdiscovery";
        }
        if ("areanotice".equals(key)
                || "areaname".equals(key)
                || "area".equals(key)) {
            return "areanotice";
        }
        return "";
    }

    public static void clampHudOffsets() {
        compassHudOffsetX = clampPercent(compassHudOffsetX);
        compassHudOffsetY = clampPercent(compassHudOffsetY);
        partyHudOffsetX = clampPercent(partyHudOffsetX);
        partyHudOffsetY = clampPercent(partyHudOffsetY);
        quickLootHudOffsetX = clampPercent(quickLootHudOffsetX);
        quickLootHudOffsetY = clampPercent(quickLootHudOffsetY);
        questHudOffsetX = clampPercent(questHudOffsetX);
        questHudOffsetY = clampPercent(questHudOffsetY);
        questNotificationHudOffsetX = clampPercent(
                questNotificationHudOffsetX);
        questNotificationHudOffsetY = clampPercent(
                questNotificationHudOffsetY);
        mapDiscoveryHudOffsetX = clampPercent(mapDiscoveryHudOffsetX);
        mapDiscoveryHudOffsetY = clampPercent(mapDiscoveryHudOffsetY);
        areaNoticeHudOffsetX = clampPercent(areaNoticeHudOffsetX);
        areaNoticeHudOffsetY = clampPercent(areaNoticeHudOffsetY);
    }

    static double migrateLegacyQuickLootOffsetX(double legacyOffsetX) {
        return clampPercent(50.0D + clampPercent(legacyOffsetX) / 2.0D);
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, value));
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
        config.getCategory(CATEGORY_CHARACTERS).setLanguageKey("losttales.config.category.characters");
        config.getCategory(CATEGORY_COMBAT_MARKERS).setLanguageKey("losttales.config.category.combatMarkers");
        config.getCategory(CATEGORY_PARTY).setLanguageKey("losttales.config.category.party");
        config.getCategory(CATEGORY_RANGED_COMBAT).setLanguageKey(
                "losttales.config.category.rangedCombat");
    }

    private static void writeCurrentValues(Configuration config) {
        if (config == null) {
            return;
        }

        config.get(CATEGORY_RANGED_COMBAT, "enableChargeTiers",
                enableChargeTiers).set(enableChargeTiers);
        config.get(CATEGORY_RANGED_COMBAT, "chargeTierOneTicks",
                chargeTierOneTicks).set(chargeTierOneTicks);
        config.get(CATEGORY_RANGED_COMBAT, "chargeTierTwoTicks",
                chargeTierTwoTicks).set(chargeTierTwoTicks);
        config.get(CATEGORY_RANGED_COMBAT, "chargeTierThreeTicks",
                chargeTierThreeTicks).set(chargeTierThreeTicks);
        config.get(CATEGORY_RANGED_COMBAT,
                "chargeTierOneDamageMultiplier",
                chargeTierOneDamageMultiplier).set(
                chargeTierOneDamageMultiplier);
        config.get(CATEGORY_RANGED_COMBAT,
                "chargeTierTwoDamageMultiplier",
                chargeTierTwoDamageMultiplier).set(
                chargeTierTwoDamageMultiplier);
        config.get(CATEGORY_RANGED_COMBAT,
                "chargeTierThreeDamageMultiplier",
                chargeTierThreeDamageMultiplier).set(
                chargeTierThreeDamageMultiplier);
        config.get(CATEGORY_RANGED_COMBAT,
                "chargeTierOneVelocityMultiplier",
                chargeTierOneVelocityMultiplier).set(
                chargeTierOneVelocityMultiplier);
        config.get(CATEGORY_RANGED_COMBAT,
                "chargeTierTwoVelocityMultiplier",
                chargeTierTwoVelocityMultiplier).set(
                chargeTierTwoVelocityMultiplier);
        config.get(CATEGORY_RANGED_COMBAT,
                "chargeTierThreeVelocityMultiplier",
                chargeTierThreeVelocityMultiplier).set(
                chargeTierThreeVelocityMultiplier);
        config.get(CATEGORY_RANGED_COMBAT, "chargeTierOneKnockback",
                chargeTierOneKnockback).set(chargeTierOneKnockback);
        config.get(CATEGORY_RANGED_COMBAT, "chargeTierTwoKnockback",
                chargeTierTwoKnockback).set(chargeTierTwoKnockback);
        config.get(CATEGORY_RANGED_COMBAT, "chargeTierThreeKnockback",
                chargeTierThreeKnockback).set(chargeTierThreeKnockback);

        config.get(CATEGORY_CHARACTERS, "allowedStartingFactionIds",
                allowedStartingFactionIds).set(allowedStartingFactionIds);
        config.get(CATEGORY_CHARACTERS, "deniedStartingFactionIds",
                deniedStartingFactionIds).set(deniedStartingFactionIds);
        config.get(CATEGORY_CHARACTERS, "switchCooldownSeconds",
                characterSwitchCooldownSeconds).set(characterSwitchCooldownSeconds);
        config.get(CATEGORY_CHARACTERS, "switchCooldownDecaySeconds",
                characterSwitchDecaySeconds).set(characterSwitchDecaySeconds);
        config.get(CATEGORY_CHARACTERS, "switchCombatGraceSeconds",
                characterSwitchCombatGraceSeconds).set(characterSwitchCombatGraceSeconds);
        config.get(CATEGORY_CHARACTERS, "switchTeleportGraceSeconds",
                characterSwitchTeleportGraceSeconds).set(characterSwitchTeleportGraceSeconds);
        config.get(CATEGORY_CHARACTERS, "switchStableGroundTicks",
                characterSwitchStableGroundTicks).set(characterSwitchStableGroundTicks);
        config.get(CATEGORY_CHARACTERS, "switchTeleportDistancePerTick",
                characterSwitchTeleportDistancePerTick).set(characterSwitchTeleportDistancePerTick);
        config.get(CATEGORY_CHARACTERS, "characterStateMaxSnapshotBytes",
                characterStateMaxSnapshotBytes).set(characterStateMaxSnapshotBytes);
        config.get(CATEGORY_CHARACTERS, "characterStateCheckpointIntervalSeconds",
                characterStateCheckpointIntervalSeconds).set(
                characterStateCheckpointIntervalSeconds);
        config.get(CATEGORY_CHARACTERS, "characterStateCheckpointPlayersPerTick",
                characterStateCheckpointPlayersPerTick).set(
                characterStateCheckpointPlayersPerTick);
        config.get(CATEGORY_CHARACTERS, "characterDeletionRetentionDays",
                characterDeletionRetentionDays).set(characterDeletionRetentionDays);
        config.get(CATEGORY_CLIENT, "showLostTalesHud", showLostTalesHud).set(showLostTalesHud);
        config.get(CATEGORY_CLIENT, "hudPlacementVersion",
                hudPlacementVersion).set(hudPlacementVersion);
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
        config.get(CATEGORY_CLIENT, "onlyShowAggroHostileCompassMarkers", true).set(true);
        config.get(CATEGORY_CLIENT, "hostileCompassMarkerScanRadius", hostileCompassMarkerScanRadius).set(hostileCompassMarkerScanRadius);
        config.get(CATEGORY_CLIENT, "showHostileMapMarkers", showHostileMapMarkers).set(showHostileMapMarkers);
        config.get(CATEGORY_CLIENT, "hostileMapMarkerDisplayRadius", hostileMapMarkerDisplayRadius).set(hostileMapMarkerDisplayRadius);
        config.get(CATEGORY_COMBAT_MARKERS, "trackingRadius", combatMarkerTrackingRadius).set(combatMarkerTrackingRadius);
        config.get(CATEGORY_COMBAT_MARKERS, "updateIntervalTicks", combatMarkerUpdateIntervalTicks).set(combatMarkerUpdateIntervalTicks);
        config.get(CATEGORY_COMBAT_MARKERS, "disengagementGraceTicks", combatMarkerDisengagementGraceTicks).set(combatMarkerDisengagementGraceTicks);
        config.get(CATEGORY_COMBAT_MARKERS, "debugLogging", combatMarkerDebugLogging).set(combatMarkerDebugLogging);
        config.get(CATEGORY_COMBAT_MARKERS, "shareWithParty", partySharedAggroTracking).set(partySharedAggroTracking);
        config.get(CATEGORY_CLIENT, "showPartyHud", showPartyHud).set(showPartyHud);
        config.get(CATEGORY_CLIENT, "linkShowPartyHud", linkShowPartyHud).set(linkShowPartyHud);
        config.get(CATEGORY_CLIENT, "partyHudOffsetX", partyHudOffsetX).set(partyHudOffsetX);
        config.get(CATEGORY_CLIENT, "partyHudOffsetY", partyHudOffsetY).set(partyHudOffsetY);
        config.get(CATEGORY_PARTY, "statusUpdateIntervalTicks", partyStatusUpdateIntervalTicks).set(partyStatusUpdateIntervalTicks);
        config.get(CATEGORY_PARTY, "statusHeartbeatTicks", partyStatusHeartbeatTicks).set(partyStatusHeartbeatTicks);
        config.get(CATEGORY_PARTY, "trackingUpdateIntervalTicks", partyTrackingUpdateIntervalTicks).set(partyTrackingUpdateIntervalTicks);
        config.get(CATEGORY_PARTY, "trackingHeartbeatTicks", partyTrackingHeartbeatTicks).set(partyTrackingHeartbeatTicks);
        config.get(CATEGORY_PARTY, "enableSharedQuestKillProgress", enablePartySharedQuestKillProgress).set(enablePartySharedQuestKillProgress);
        config.get(CATEGORY_PARTY, "sharedQuestRadius", partySharedQuestRadius).set(partySharedQuestRadius);
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
        config.get(CATEGORY_CLIENT, "questNotificationHudOffsetX",
                questNotificationHudOffsetX).set(questNotificationHudOffsetX);
        config.get(CATEGORY_CLIENT, "questNotificationHudOffsetY",
                questNotificationHudOffsetY).set(questNotificationHudOffsetY);
        config.get(CATEGORY_CLIENT, "mapDiscoveryHudOffsetX",
                mapDiscoveryHudOffsetX).set(mapDiscoveryHudOffsetX);
        config.get(CATEGORY_CLIENT, "mapDiscoveryHudOffsetY",
                mapDiscoveryHudOffsetY).set(mapDiscoveryHudOffsetY);
        config.get(CATEGORY_CLIENT, "areaNoticeHudOffsetX",
                areaNoticeHudOffsetX).set(areaNoticeHudOffsetX);
        config.get(CATEGORY_CLIENT, "areaNoticeHudOffsetY",
                areaNoticeHudOffsetY).set(areaNoticeHudOffsetY);
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


    private static void sanitizeCharacterSwitchOptions() {
        characterSwitchCooldownSeconds = sanitizePositiveIntArray(
                characterSwitchCooldownSeconds,
                new int[] {60, 180, 300, 900, 1800, 3600},
                1,
                86400 * 30);
        characterSwitchDecaySeconds = sanitizePositiveIntArray(
                characterSwitchDecaySeconds,
                new int[] {0, 3600, 10800, 21600, 43200, 86400},
                0,
                86400 * 365);
        characterSwitchCombatGraceSeconds = clampInt(
                characterSwitchCombatGraceSeconds, 0, 3600);
        characterSwitchTeleportGraceSeconds = clampInt(
                characterSwitchTeleportGraceSeconds, 0, 300);
        characterSwitchStableGroundTicks = clampInt(
                characterSwitchStableGroundTicks, 0, 200);
        if (Double.isNaN(characterSwitchTeleportDistancePerTick)
                || Double.isInfinite(characterSwitchTeleportDistancePerTick)) {
            characterSwitchTeleportDistancePerTick = 16.0D;
        }
        characterSwitchTeleportDistancePerTick = Math.max(1.0D,
                Math.min(1024.0D, characterSwitchTeleportDistancePerTick));
        characterStateMaxSnapshotBytes = clampInt(
                characterStateMaxSnapshotBytes, 64 * 1024, 16 * 1024 * 1024);
        characterStateCheckpointIntervalSeconds = clampInt(
                characterStateCheckpointIntervalSeconds, 30, 3600);
        characterStateCheckpointPlayersPerTick = clampInt(
                characterStateCheckpointPlayersPerTick, 1, 4);
        characterDeletionRetentionDays = clampInt(
                characterDeletionRetentionDays, 1, 3650);
        characterSwitchCombatGraceMillis =
                (long) characterSwitchCombatGraceSeconds * 1000L;
        characterSwitchTeleportGraceMillis =
                (long) characterSwitchTeleportGraceSeconds * 1000L;
    }

    private static int[] sanitizePositiveIntArray(int[] values, int[] fallback,
                                                   int minimum, int maximum) {
        int[] source = values == null || values.length == 0 ? fallback : values;
        int length = Math.max(1, Math.min(16, source.length));
        int[] result = new int[length];
        for (int index = 0; index < length; index++) {
            result[index] = clampInt(source[index], minimum, maximum);
            if (index > 0 && result[index] < result[index - 1]) {
                result[index] = result[index - 1];
            }
        }
        return result;
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

    static double getHudPercent(
            Configuration config, String key, double defaultValue,
            double minimum, double maximum, String comment) {
        if (config.hasKey(CATEGORY_CLIENT, key)) {
            Property existing = config.getCategory(CATEGORY_CLIENT).get(key);
            if (existing != null
                    && existing.getType() != Property.Type.DOUBLE) {
                // Old releases declared HUD offsets as integers. Recreate the
                // property so Forge's config GUI accepts precise drag values.
                defaultValue = existing.getDouble(defaultValue);
                config.getCategory(CATEGORY_CLIENT).remove(key);
            }
        }
        return getBoundedDouble(config, CATEGORY_CLIENT, key, defaultValue,
                minimum, maximum, comment);
    }

    private static double getBoundedDouble(
            Configuration config, String key, double defaultValue,
            double minimum, double maximum, String comment) {
        return getBoundedDouble(config, CATEGORY_RANGED_COMBAT, key,
                defaultValue, minimum, maximum, comment);
    }

    private static double getBoundedDouble(
            Configuration config, String category, String key,
            double defaultValue, double minimum, double maximum,
            String comment) {
        Property property = config.get(
                category, key, defaultValue,
                comment, minimum, maximum);
        double value = property.getDouble(defaultValue);
        double bounded = Math.max(minimum, Math.min(maximum, value));
        if (bounded != value) {
            property.set(bounded);
        }
        return bounded;
    }
}
