package com.ninuna.losttales.config;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import cpw.mods.fml.common.FMLLog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelIconCatalog;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.compat.discord.DiscordChannelBindings;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.config.ConfigCategory;
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
    public static final String CATEGORY_CHAT = "chat";
    public static final String CATEGORY_RANGED_COMBAT = "ranged_combat";
    public static final String CATEGORY_WAYSTONES = "waystones";
    public static final String CATEGORY_DISCORD = "discord";
    /** The chat roles and their assignments; a server file of its own. */
    public static final String CATEGORY_ROLES = "roles";
    /** The chat channels' gates and definitions; a server file of its own. */
    public static final String CATEGORY_CHANNELS = "channels";

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

    /**
     * The categories the client decides for itself; every other category
     * is the server's. The one place this split is stated: the loader
     * and the server settings snapshot both read it.
     */
    public static final Set<String> CLIENT_CATEGORIES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(CATEGORY_CLIENT)));

    private static File loadedClientFile;
    private static File loadedServerFile;
    private static File loadedRolesFile;
    private static File loadedChannelsFile;
    private static Configuration pendingGuiConfiguration;
    /**
     * Every option as the mod ships it ({@link LostTalesConfigDefinitions}):
     * read on the first load against no file, while each field still
     * holds its shipped value. Null until then.
     */
    private static Configuration shipped;


    public static boolean showLostTalesHud = true;
    public static String hudPlacementPreset = HUD_PRESET_CUSTOM;

    public static boolean showCompassHud = true;
    public static boolean linkShowCompassHud = false;
    public static double compassHudOffsetX = 50.0D;
    public static double compassHudOffsetY = 2.0D;
    public static int compassHudDisplayRadius = 90;
    public static boolean showStaticCompassMarkers = true;
    public static boolean showLotrWaypointCompassMarkers = true;
    public static boolean onlyShowUnlockedLotrWaypoints = true;
    public static boolean showHostileCompassMarkers = true;
    public static int hostileCompassMarkerScanRadius = 48;
    public static boolean showHostileMapMarkers = true;
    public static int hostileMapMarkerDisplayRadius = 64;
    public static double closeMapTerrainTransitionStartZoom = 4.0D;
    public static double closeMapTerrainTransitionEndZoom = 4.6D;
    public static String[] hiddenMapLegendCategories = new String[0];
    public static String[] customWaypointColors = new String[0];
    public static String[] customWaypointNotes = new String[0];

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
    public static boolean enableSharedQuestProgress = true;
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
    public static boolean showNativeLotrQuestTracker = false;
    /**
     * Whether a quest offered by somebody is talked about in the Lost
     * Tales conversation screen. Off, a Middle-earth quest is offered on
     * LOTR's own screen and a Lost Tales quest starts the moment its
     * giver is touched, as they did before there was a conversation.
     */
    public static boolean enableQuestDialogue = true;
    /** The one slot every passing notice shares: quest banners, discoveries, area names. */
    public static double notificationHudOffsetX = 50.0D;
    public static double notificationHudOffsetY = 35.0D;
    public static boolean showWorldQuestMarkers = true;
    public static boolean showDiscoveredWorldMapMarkers = true;
    public static int worldQuestMarkerMaxDistance = 128;

    public static boolean showQuestChatFeedback = true;
    public static boolean playQuestSounds = true;

    /** Server-authoritative recipient radius for the Proximity channel. */
    public static int chatProximityRadius = 64;
    /** Client-only presentation preferences; neither affects recipients. */
    public static boolean showChatTimestamps = true;
    public static boolean enableChatEmojis = true;
    public static boolean convertChatEmoticons = true;
    /**
     * How the words on the chat's profanity list read on this client:
     * one of {@link ChatProfanityMode}'s names.
     */
    public static String chatProfanityFilter = ChatProfanityMode.SILLY.name();
    public static boolean enableChatMessageGrouping = true;
    public static boolean enableChatBackgroundBlur = true;
    public static boolean enableNpcChatStyling = true;
    public static boolean showChatSpeechBubbles = true;
    public static boolean enableChatPings = true;
    /** The chat's own mention cue, bundled with the mod. */
    static final String DEFAULT_CHAT_PING_SOUND = "losttales:chat.ping";
    public static String chatPingSound = DEFAULT_CHAT_PING_SOUND;
    /**
     * The open chat's surfaces, each a palette entry by name: the
     * history panel and the rows that frame it, the line under the
     * pointer, and a line that mentions this player. Names, not
     * numbers, so every choice is one of the palette's colours.
     */
    static final String DEFAULT_CHAT_BACKGROUND_COLOR = "PLUM_BLACK";
    static final String DEFAULT_CHAT_SELECTED_LINE_COLOR = "PLUM_GRAY";
    static final String DEFAULT_CHAT_MENTION_LINE_COLOR = "ORCHID";
    static final String DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR = "APRICOT";
    /**
     * A colour option's value that follows another colour instead of
     * naming a palette entry: the selected mention's, which is by
     * default the mention colour a shade lighter.
     */
    public static final String CHAT_COLOR_AUTOMATIC = "AUTO";
    public static String chatBackgroundColor = DEFAULT_CHAT_BACKGROUND_COLOR;
    public static String chatSelectedLineColor = DEFAULT_CHAT_SELECTED_LINE_COLOR;
    public static String chatMentionLineColor = DEFAULT_CHAT_MENTION_LINE_COLOR;
    /** A line that mentions this player, under the pointer; automatic until chosen. */
    public static String chatSelectedMentionColor = CHAT_COLOR_AUTOMATIC;
    /** The line a reply's quote jumps to, lit while the eye finds it. */
    public static String chatReplyHighlightColor = DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR;
    /** The edges the closed-chat feed's lines may stand against. */
    static final String[] CHAT_FEED_ALIGNMENTS = {"LEFT", "CENTRE", "RIGHT"};
    /** Which of them the feed's lines stand against: the left until chosen. */
    public static String chatFeedAlignment = CHAT_FEED_ALIGNMENTS[0];
    /**
     * Whether the game's HUD and the mod's panels fade out while the chat
     * screen is open, leaving the world and the chat.
     */
    public static boolean hideHudWhileChatting = true;
    public static boolean enableChatAnimations = true;
    /**
     * Developer aid: a local PNG drawn on the local player instead of the
     * account skin, with a chosen arm width. Empty path disables it.
     */
    public static String devSkinOverridePath = "";
    /** The override skin's arm widths, as the option names them. */
    static final String[] DEV_SKIN_BODY_TYPES = {"wide", "slim"};
    public static String devSkinOverrideBodyType = DEV_SKIN_BODY_TYPES[0];
    /** Draw the jacket, sleeve, and trouser overlays of 64x64 skins. */
    public static boolean showSkinOverlays = true;
    /** Feminine chest physics on/off and bounce strength (0 to 1). */
    public static boolean chestPhysics = true;
    public static float chestBounce = 0.35F;
    /**
     * Messages (and wrapped lines) the chat history keeps, shared by
     * every channel; vanilla keeps a hundred. A safety bound as much as
     * a preference: the coremod feeds it to vanilla's own trimming.
     */
    public static int chatHistoryLines = 1000;
    /** Server only: the opt-in moderation record of what was said. */
    public static boolean chatAuditLogEnabled = false;
    public static int chatAuditRetentionDays = 30;
    /**
     * Server only: whether the recent chat history is written with the
     * world save and read back as the server starts, and how many
     * messages of each channel it keeps. The count is a safety bound as
     * much as a preference: the save is written with every world save.
     */
    public static boolean chatHistoryPersisted = true;
    public static int chatHistoryPerChannel = 200;
    /** Tell others when this player is typing; show others' typing. */
    public static boolean sendChatTypingStatus = true;
    public static boolean showChatTypingIndicators = true;
    /** Server switch for relaying typing presence at all. */
    public static boolean chatTypingIndicators = true;
    /** Words the server adds to the chat's profanity list, one per line as word=replacement. */
    public static String[] chatProfanityWords = new String[0];
    /** The config-defined permissions a role may grant; see {@code ChatRoleConfig}. */
    public static String[] chatPermissions = new String[0];
    /** The config-defined chat roles; see {@code ChatRoleConfig}. */
    public static String[] chatRoles = new String[0];
    /** The accounts assigned each role, by UUID. */
    public static String[] chatRoleMembers = new String[0];
    /** The roles a channel asks for, to read and to send. */
    public static String[] chatChannelDefinitions = new String[0];
    public static String[] chatChannelRoles = new String[0];
    /** The icon a channel wears before its name, by channel id. */
    public static String[] chatChannelIcons = new String[0];
    /**
     * The server's Discord bridge; read on the server only. The token
     * and the webhook URL are secrets: they stay in this file and are
     * never logged or sent to a client.
     */
    public static boolean discordEnabled;
    public static String discordBotToken = "";
    public static int discordPollIntervalSeconds = 3;
    /**
     * The bindings a fresh file offers: OOC &amp; Discord and Global, each
     * switched off until its channel and webhook are filled in.
     */
    private static final String[] DEFAULT_DISCORD_BINDINGS = {
            "ooc=DISABLED;channel=;webhook=",
            "all=DISABLED;webhook=",
    };
    /** One entry per bound game channel. See {@code DiscordChannelBindings}. */
    public static String[] discordChannelBindings = DEFAULT_DISCORD_BINDINGS.clone();
    /** The picture a post carries: {name}/{uuid} of the sender's account. */
    public static String discordAvatarUrlTemplate =
            "https://mc-heads.net/head/{name}/64";
    /** Post server start/stop and player join/leave notices to the webhook. */
    public static boolean discordServerEvents = true;
    /** Post every player death message to the webhook. */
    public static boolean discordDeathMessages = true;
    /** Post vanilla and LOTR achievement announcements to the webhook. */
    public static boolean discordAchievements = true;
    /** Keep the channel topic saying whether the server is up and who is on. */
    public static boolean discordChannelStatus = true;
    /** Least seconds between two topic writes; Discord allows two per ten minutes. */
    public static int discordChannelStatusIntervalSeconds = 300;
    /** Connect the bot to Discord's gateway for instant relay and slash commands. */
    public static boolean discordGateway = true;
    public static boolean discordSlashCommands = true;
    /** How the profanity list's words read in what the bridge posts: a {@link ChatProfanityMode} name. */
    public static String discordProfanityFilter = ChatProfanityMode.OFF.name();
    public static int chatAnimationDurationMillis = 180;
    public static int chatInputAnimationDurationMillis = 180;
    public static int chatSelectorAnimationDurationMillis = 140;

    /** Client-only general GUI motion and background preferences. */
    public static boolean enableGuiAnimations = true;
    public static int guiAnimationDurationMillis = 220;
    public static double guiAnimationScale = 1.0D;
    /** The foreground's easing styles, as the option names them. */
    static final String[] GUI_EASING_STYLES = {"BACK", "CUBIC", "SMOOTH"};
    public static String guiAnimationEasingStyle = GUI_EASING_STYLES[0];
    /** Where the foreground flies in toward its place from, or nowhere. */
    static final String[] GUI_DIRECTIONS = {"DOWN", "UP", "LEFT", "RIGHT", "NONE"};
    public static String guiAnimationDirection = GUI_DIRECTIONS[0];
    public static boolean reducedGuiMotion = false;
    public static boolean enableGuiBackground = true;
    public static double guiBackgroundOpacity = 0.65D;
    public static int guiBackgroundFadeTimeMillis = 150;
    public static boolean guiAlwaysBlur = false;
    public static boolean enableGuiBackgroundBlur = true;
    public static double guiBlurStrength = 4.5D;
    public static boolean enableSmoothInventoryMovement = true;
    public static int smoothInventoryAnimationDurationMillis = 140;

    public static boolean enableQuestPrerequisites = true;
    public static boolean enableQuestRewards = true;
    public static boolean allowQuestItemStarts = true;
    public static boolean allowQuestInteractionStarts = true;
    public static boolean autoRevealQuestMarkersOnStart = true;
    public static boolean autoPinQuestOnStart = true;
    public static boolean autoDiscoverNearbyMapMarkers = true;
    public static int mapMarkerDiscoveryScanIntervalTicks = 40;

    public static boolean enableWaystoneRecipe = true;
    public static String waystoneRecipeCornerIngredient =
            "minecraft:stonebrick";
    public static String waystoneRecipeEdgeIngredient = "ore:ingotGold";
    public static String waystoneRecipeCenterIngredient =
            "minecraft:ender_pearl";

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

    /** Whether the category is the client's own; see {@link #CLIENT_CATEGORIES}. */
    public static boolean isClientCategory(String category) {
        if (category == null) {
            return false;
        }
        String root = category;
        int split = root.indexOf(Configuration.CATEGORY_SPLITTER);
        if (split >= 0) {
            root = root.substring(0, split);
        }
        return CLIENT_CATEGORIES.contains(root.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Reads the client's options from {@code clientFile}, the server's
     * from {@code serverFile}, the roles from {@code rolesFile} and the
     * channels from {@code channelsFile}. A dedicated server passes no
     * client file, and the client categories then hold their defaults in
     * memory.
     */
    public static void load(File clientFile, File serverFile, File rolesFile,
                            File channelsFile) {
        loadedClientFile = clientFile;
        loadedServerFile = serverFile;
        loadedRolesFile = rolesFile == null ? serverFile : rolesFile;
        loadedChannelsFile = channelsFile == null ? serverFile : channelsFile;
        if (shipped == null) {
            // Nothing has changed a field yet: read against no file at
            // all, every option keeps the value the mod ships, and the
            // configuration left over is each option as it is defined.
            Configuration definitions = new Configuration();
            defineOptions(definitions);
            shipped = definitions;
        }
        readOptions(openSided(), true);
    }

    /**
     * Reads every option into {@code definitions}, a configuration of no
     * file, as it is defined: the first load's first step, taken while
     * each field still holds its shipped value.
     */
    static void defineOptions(Configuration definitions) {
        readOptions(definitions, false);
    }

    /**
     * Reads every option of {@code config} into its field, the field's
     * own value standing as the default of an option the configuration
     * does not hold. From the files ({@code fromFiles}) the configuration
     * is loaded first, and written back — every value as read and put
     * right, every option in its shipped definition — when anything
     * changed; a configuration of no file is only read.
     */
    private static void readOptions(Configuration config, boolean fromFiles) {
        try {
            if (fromFiles) {
                config.load();
            }
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
            // Put right, the values go back as values: a get naming a
            // default would redefine the options and drop their comments.
            config.getCategory(CATEGORY_RANGED_COMBAT)
                    .get("chargeTierTwoTicks").set(chargeTierTwoTicks);
            config.getCategory(CATEGORY_RANGED_COMBAT)
                    .get("chargeTierThreeTicks").set(chargeTierThreeTicks);
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
            closeMapTerrainTransitionStartZoom = getBoundedDouble(
                    config, CATEGORY_CLIENT,
                    "closeMapTerrainTransitionStartZoom",
                    closeMapTerrainTransitionStartZoom,
                    -2.25D, 9.20D,
                    "Zoom exponent where the close-map terrain transition begins. The ordinary LOTR map remains fully visible at and below this value."
            );
            closeMapTerrainTransitionEndZoom = getBoundedDouble(
                    config, CATEGORY_CLIENT,
                    "closeMapTerrainTransitionEndZoom",
                    closeMapTerrainTransitionEndZoom,
                    -2.2D, 9.25D,
                    "Zoom exponent where prepared close-map terrain may fully replace the ordinary LOTR map. Must be greater than closeMapTerrainTransitionStartZoom."
            );
            if (closeMapTerrainTransitionEndZoom
                    <= closeMapTerrainTransitionStartZoom) {
                closeMapTerrainTransitionEndZoom = Math.min(9.25D,
                        closeMapTerrainTransitionStartZoom + 0.05D);
                config.getCategory(CATEGORY_CLIENT)
                        .get("closeMapTerrainTransitionEndZoom")
                        .set(closeMapTerrainTransitionEndZoom);
            }
            hiddenMapLegendCategories = config.get(
                    CATEGORY_CLIENT,
                    "hiddenMapLegendCategories",
                    hiddenMapLegendCategories,
                    "Client-only map legend category IDs that are hidden. Unknown IDs are ignored and new categories remain visible by default."
            ).getStringList();
            customWaypointColors = config.get(
                    CATEGORY_CLIENT,
                    "customWaypointColors",
                    customWaypointColors,
                    "Client-only icon colour per custom waypoint, as name=colour. LOTR stores no colour for its waypoints, so this is presentation only and is not shared with anyone else."
            ).getStringList();
            customWaypointNotes = config.get(
                    CATEGORY_CLIENT,
                    "customWaypointNotes",
                    customWaypointNotes,
                    "Client-only tooltip note per custom waypoint, as name=note. LOTR stores no description for its waypoints, so this is presentation only and is not shared with anyone else."
            ).getStringList();

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
            enableSharedQuestProgress = config.getBoolean(
                    "enableSharedQuestProgress",
                    CATEGORY_PARTY,
                    enableSharedQuestProgress,
                    "Allow authoritative kill and destination-arrival events to advance matching Lost Tales objectives for eligible nearby party members. Gathering, crafting, hand-ins, completion, and rewards remain individual."
            );
            partySharedQuestRadius = config.getInt(
                    "sharedQuestRadius",
                    CATEGORY_PARTY,
                    partySharedQuestRadius,
                    1,
                    128,
                    "Maximum block distance for conservative party-shared kill and travel objective progress. Members must be online, alive, in the same dimension, using the party character, and independently possess the matching quest."
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
                    quickLootHudOffsetX,
                    0.0D, 100.0D,
                    "Horizontal quick-loot position as a percentage of the scaled screen width."
            );
            quickLootHudOffsetY = getHudPercent(
                    config, "quickLootHudOffsetY",
                    quickLootHudOffsetY, 0.0D, 100.0D,
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
            showNativeLotrQuestTracker = config.getBoolean(
                    "showNativeLotrQuestTracker",
                    CATEGORY_CLIENT,
                    showNativeLotrQuestTracker,
                    "Also render LOTR's original single-quest tracker as a compatibility fallback."
            );
            enableQuestDialogue = config.getBoolean(
                    "enableQuestDialogue",
                    CATEGORY_CLIENT,
                    enableQuestDialogue,
                    "Talk to a quest giver in the Lost Tales conversation screen. Off, Middle-earth quests are offered on LOTR's own screen and Lost Tales quests start as soon as their giver is touched."
            );
            notificationHudOffsetX = getHudPercent(
                    config, "notificationHudOffsetX",
                    notificationHudOffsetX, 0.0D, 100.0D,
                    "Horizontal position of the notification slot (quest banners, location discoveries, area names) within the available scaled screen width."
            );
            notificationHudOffsetY = getHudPercent(
                    config, "notificationHudOffsetY",
                    notificationHudOffsetY, 0.0D, 100.0D,
                    "Vertical position of the notification slot within the available scaled screen height."
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
            chatProximityRadius = config.getInt(
                    "proximityRadius",
                    CATEGORY_CHAT,
                    chatProximityRadius,
                    1,
                    512,
                    "Server-authoritative maximum distance in blocks for Proximity chat recipients."
            );
            chatTypingIndicators = config.getBoolean(
                    "typingIndicators",
                    CATEGORY_CHAT,
                    chatTypingIndicators,
                    "Relay who is typing into a channel to the players who would read the message; off drops every typing notice on the server."
            );
            chatProfanityWords = config.getStringList(
                    "profanityWords",
                    CATEGORY_CHAT,
                    chatProfanityWords,
                    "Words added to the chat's profanity list beside the ones the mod bundles, one per line as word=replacement, both a run of letters: the word, matched whole with stretched letters and the common endings, and the silly word that stands in for it. Sent to every client with its chat access, refused in a character's name, and applied to what the Discord bridge posts when discord.profanityFilter says so. An entry naming a bundled word replaces its stand-in."
            );
            chatAuditLogEnabled = config.getBoolean(
                    "auditLog",
                    CATEGORY_CHAT,
                    chatAuditLogEnabled,
                    "Server only: append every accepted chat message, edit, and deletion - private whispers included - as JSON lines under logs/losttales-chat/, one file per UTC day, for moderation. Off by default; tell your players before turning it on."
            );
            chatAuditRetentionDays = config.getInt(
                    "auditRetentionDays",
                    CATEGORY_CHAT,
                    chatAuditRetentionDays,
                    1,
                    365,
                    "Days of chat audit files kept; files older than this are deleted when the server starts and as the day rolls over."
            );
            chatHistoryPersisted = config.getBoolean(
                    "historyPersisted",
                    CATEGORY_CHAT,
                    chatHistoryPersisted,
                    "Server only: keep the recent chat history in the world save, so a player who rejoins after a restart is still shown the conversation they missed. Off keeps it in memory for the server's run alone."
            );
            chatHistoryPerChannel = config.getInt(
                    "historyPerChannel",
                    CATEGORY_CHAT,
                    chatHistoryPerChannel,
                    20,
                    1000,
                    "Server only: the most messages of each channel the history keeps, live and in the save; the oldest go first. A joining player is replayed at most 100 of a channel and 400 in all of them."
            );
            chatPermissions = config.getStringList(
                    "permissions",
                    CATEGORY_ROLES,
                    chatPermissions,
                    "What a role may grant, in the server's own words, one per line as <id>=capability:<id>;capability:<id>;desc:<text>. capability may repeat and names something the code can do: chat.moderate (mute, unmute, remove any message), chat.console.read (the Server Console), chat.narrate (speak as the Narrator in the roleplaying channels), roles.manage (/losttales role), server.config (the server settings, which includes these roles and permissions), character.admin, quest.admin, party.admin, mapmarker.manage, waystone.manage, hud.admin. A permission naming no capability the code has is kept and allows nothing. A role's grant naming no permission here is read as the capability of that id, so grant:chat.moderate needs nothing defined."
            );
            chatRoles = config.getStringList(
                    "definitions",
                    CATEGORY_ROLES,
                    new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY},
                    "The chat roles, one per line as <id>=name:<text>;tag:<[Text]>;color:<RRGGBB>;mention:<true|false>;rank:<number>;op:<level>;faction:<FACTION>@<rank>;grant:<permission>;desc:<text>. Every option is optional: a role is held by the accounts and characters listed under members, by anyone with the op level, and by anyone whose played identity holds the LOTR faction rank (a rank code name such as gondor.knight, or an alignment number). Lower rank comes first and colours the name; rank never grants anything. grant names a permission from the permissions list, or a capability directly; it may repeat, and one naming neither is kept and allows nothing until a permission of that id is defined. The operator entry a fresh file starts with is a role like any other; the Lost Tales Team mark is the code's alone and cannot be listed. Edit live from the Server Settings screen or /losttales role."
            );
            chatRoleMembers = config.getStringList(
                    "members",
                    CATEGORY_ROLES,
                    chatRoleMembers,
                    "Who holds each role, one role per line as <id>=<account uuid>,<account uuid>,character:<character uuid>. An account holds the role as every identity it plays and gains its grants; a character holds it as that character alone and gains no grant. /losttales role assign writes this."
            );
            chatChannelDefinitions = config.getStringList(
                    "definitions",
                    CATEGORY_CHANNELS,
                    new String[0],
                    "Channels this server has of its own, one per line as <id>=name:<shown name>;rule:<global|proximity|operators>;colour:<RRGGBB>;ooc:<true|false>;bridge:<true|false>. The id is permanent: packets, the layout file and the gates all name a channel by it. The routing rules a config cannot describe - a party, a faction, a whisper, a private console - are refused, and an entry naming a channel this build already has is refused rather than replacing it."
            );
            chatChannelRoles = config.getStringList(
                    "gates",
                    CATEGORY_CHANNELS,
                    new String[] {ChatRoleConfig.DEFAULT_ADMIN_GATE},
                    "The roles a channel asks for, one channel per line as <channel>=read:<role,role|any|none>;send:<role,role|any|none>. A side left out or set to any is open to everyone the channel already admits; none closes it; a side naming a role that does not exist is closed until the entry is fixed. A fresh file starts with the Operator channel asking for the operator role on both sides, and that line is put back whenever it is missing; to open the channel on purpose, keep the line and set its sides to any."
            );
            chatChannelIcons = config.getStringList(
                    "icons",
                    CATEGORY_CHANNELS,
                    new String[0],
                    "The icon a channel wears before its name, on its tab and wherever the name stands alone, one channel per line as <channel id>=emoji:<name> or <channel id>=item:<item id>[@<damage>], for example admin=item:minecraft:iron_sword or trade=emoji:moneybag. Built-in channels and the ones defined above alike; a channel not named here keeps the emoji the mod gives it, and a client without the item shows that emoji too."
            );
            installChatRoles();
            discordEnabled = config.getBoolean(
                    "enabled",
                    CATEGORY_DISCORD,
                    discordEnabled,
                    "Server only: bridge game channels to Discord text channels as channelBindings says, game to Discord through webhooks and Discord to game by polling with a bot. OOC & Discord exists for players whether or not this is on; the switch only says whether anything crosses."
            );
            discordBotToken = config.getString(
                    "botToken",
                    CATEGORY_DISCORD,
                    discordBotToken,
                    "Server only, secret: the bot's token, used to read every bound Discord channel. The bot must be in the server with access to those channels and have the Message Content intent enabled."
            );
            discordPollIntervalSeconds = config.getInt(
                    "pollIntervalSeconds",
                    CATEGORY_DISCORD,
                    discordPollIntervalSeconds,
                    2,
                    60,
                    "Server only: how often each bound Discord channel is read for new messages while the bot's gateway connection is down, in seconds. Lines said in the game are posted to Discord as soon as they are said, whatever this is."
            );
            Property bindingsProperty = config.get(
                    CATEGORY_DISCORD,
                    "channelBindings",
                    DEFAULT_DISCORD_BINDINGS,
                    "Server only, secrets: one entry per game channel and Discord channel it goes to, as <channel>=<direction>;channel=<Discord channel id>;webhook=<webhook URL>. A game channel may have several entries, one per Discord channel, in any guild the bot is in; a Discord channel is read into one game channel only. The channel is a wire id (all, proximity, faction, ooc, admin; ooc is OOC & Discord, the channel the bridge carries by default) or faction:<faction id> for one faction's Faction chat, with the id as LOTR names it (faction:lotr:gondor); the direction is DISABLED, GAME_TO_DISCORD, DISCORD_TO_GAME or BIDIRECTIONAL. channel= is needed to read, webhook= to post. Party, Console and whispers are private and refused."
            );
            discordChannelBindings = bindingsProperty.getStringList();
            discordAvatarUrlTemplate = config.getString(
                    "avatarUrlTemplate",
                    CATEGORY_DISCORD,
                    discordAvatarUrlTemplate,
                    "Server only: the https URL of the picture a Discord post carries, with {name} and/or {uuid} replaced by the sender's Minecraft account (the default is an isometric head; https://mc-heads.net/avatar/{name}/64 is the flat face); empty shows the webhook's own picture."
            );
            discordServerEvents = config.getBoolean(
                    "serverEvents",
                    CATEGORY_DISCORD,
                    discordServerEvents,
                    "Server only: post a notice when the server starts or shuts down and when a player joins or leaves, to every Discord channel a binding posts to, once each."
            );
            discordDeathMessages = config.getBoolean(
                    "deathMessages",
                    CATEGORY_DISCORD,
                    discordDeathMessages,
                    "Server only: post every player's death message, worded exactly as the game announces it, to every Discord channel a binding posts to, once each."
            );
            discordAchievements = config.getBoolean(
                    "achievements",
                    CATEGORY_DISCORD,
                    discordAchievements,
                    "Server only: post vanilla and Middle-earth achievement announcements, worded exactly as the game announces them, to every Discord channel a binding posts to, once each."
            );
            discordChannelStatus = config.getBoolean(
                    "channelStatus",
                    CATEGORY_DISCORD,
                    discordChannelStatus,
                    "Server only: keep the topic of every bound Discord channel saying whether the server is online and how many players are on (online, 3/20 players). Needs the bot token and the channel ids in the bindings, and the bot needs the Manage Channels permission in each channel."
            );
            discordChannelStatusIntervalSeconds = config.getInt(
                    "channelStatusIntervalSeconds",
                    CATEGORY_DISCORD,
                    discordChannelStatusIntervalSeconds,
                    60,
                    3600,
                    "Server only: the least time between two topic writes, in seconds. Discord allows a channel's topic to change only twice per ten minutes, so the default of five minutes is the fastest that never waits; joins and leaves inside the interval are folded into the next write."
            );
            discordGateway = config.getBoolean(
                    "gateway",
                    CATEGORY_DISCORD,
                    discordGateway,
                    "Server only: keep the bot connected to Discord's gateway, so Discord messages reach the game the moment they are sent and the bot's slash commands work. Needs botToken. Off, or a gateway Discord refuses, leaves polling to do the reading as before."
            );
            discordSlashCommands = config.getBoolean(
                    "slashCommands",
                    CATEGORY_DISCORD,
                    discordSlashCommands,
                    "Server only: register the bot's slash commands (/online, /who, /server) in every guild the bot is in when the gateway connects, and answer them. Needs gateway."
            );
            Property discordProfanityProperty = config.get(
                    CATEGORY_DISCORD, "profanityFilter", discordProfanityFilter,
                    "Server only: how the words on the chat's profanity list read in what the bridge posts to Discord: OFF as typed, SILLY as their silly stand-ins, STARS as their first letter and stars. Lines arriving from Discord are left as they are; each client shows them by its own setting.");
            discordProfanityProperty.setValidValues(ChatProfanityMode.names());
            discordProfanityFilter = ChatProfanityMode.of(
                    discordProfanityProperty.getString(), ChatProfanityMode.OFF).name();
            showChatTimestamps = config.getBoolean(
                    "showTimestamps",
                    CATEGORY_CLIENT,
                    showChatTimestamps,
                    "Show short local-time timestamps on Lost Tales player channel messages."
            );
            enableChatEmojis = config.getBoolean(
                    "enableChatEmojis",
                    CATEGORY_CLIENT,
                    enableChatEmojis,
                    "Render supported :shortcodes: as inline emojis and show the chat emoji picker."
            );
            convertChatEmoticons = config.getBoolean(
                    "convertChatEmoticons",
                    CATEGORY_CLIENT,
                    convertChatEmoticons,
                    "Turn classic emoticons you type (:) :D <3 ...) into their emojis as the message is sent."
            );
            Property profanityProperty = config.get(
                    CATEGORY_CLIENT, "chatProfanityFilter", chatProfanityFilter,
                    "How the words on the chat's profanity list read on this client: OFF as typed, SILLY as their silly stand-ins (fuck reads flip, shit reads poop), STARS as their first letter and stars (f***). Display only: what you type reaches everyone else as typed, and what they see is their own setting's. The list is the mod's, plus the server's words and any in client/chat/profanity.txt.");
            profanityProperty.setValidValues(ChatProfanityMode.names());
            chatProfanityFilter = ChatProfanityMode.of(
                    profanityProperty.getString(), ChatProfanityMode.SILLY).name();
            enableChatMessageGrouping = config.getBoolean(
                    "enableChatMessageGrouping",
                    CATEGORY_CLIENT,
                    enableChatMessageGrouping,
                    "Drop the repeated head and name when the same identity sends several messages in a row."
            );
            enableChatBackgroundBlur = config.getBoolean(
                    "enableChatBackgroundBlur",
                    CATEGORY_CLIENT,
                    enableChatBackgroundBlur,
                    "Blur the world inside each open chat window's box; the rest of the screen stays sharp. Needs enableGuiBackgroundBlur."
            );
            enableChatPings = config.getBoolean(
                    "enableChatPings",
                    CATEGORY_CLIENT,
                    enableChatPings,
                    "Highlight and play a sound for chat messages that @-mention your account or active character name."
            );
            chatPingSound = config.getString(
                    "chatPingSound",
                    CATEGORY_CLIENT,
                    chatPingSound,
                    "Sound event played when a chat message @-mentions you; empty disables the sound."
            );
            chatBackgroundColor = paletteName(config.getString(
                    "chatBackgroundColor",
                    CATEGORY_CLIENT,
                    chatBackgroundColor,
                    "Palette colour of the open chat's history panel and the rows framing it. Also set from a chat window's own menu.",
                    LostTalesColors.paletteNames()
            ), DEFAULT_CHAT_BACKGROUND_COLOR);
            chatSelectedLineColor = paletteName(config.getString(
                    "chatSelectedLineColor",
                    CATEGORY_CLIENT,
                    chatSelectedLineColor,
                    "Palette colour of the chat line under the pointer. Also set from a chat window's own menu.",
                    LostTalesColors.paletteNames()
            ), DEFAULT_CHAT_SELECTED_LINE_COLOR);
            chatMentionLineColor = paletteName(config.getString(
                    "chatMentionLineColor",
                    CATEGORY_CLIENT,
                    chatMentionLineColor,
                    "Palette colour of a chat line that @-mentions you. Also set from a chat window's own menu.",
                    LostTalesColors.paletteNames()
            ), DEFAULT_CHAT_MENTION_LINE_COLOR);
            chatSelectedMentionColor = automaticOrPaletteName(config.getString(
                    "chatSelectedMentionColor",
                    CATEGORY_CLIENT,
                    chatSelectedMentionColor,
                    "Palette colour of a chat line that @-mentions you while it is under the pointer. AUTO, the default, is the mention colour one shade lighter on its own ramp of the palette, or the selected-line colour where the mention colour is already its ramp's lightest. Also set from a chat window's own menu.",
                    automaticOrPaletteNames()
            ));
            chatReplyHighlightColor = paletteName(config.getString(
                    "chatReplyHighlightColor",
                    CATEGORY_CLIENT,
                    chatReplyHighlightColor,
                    "Palette colour a chat line is lit in when a reply's quote jumps to it. Also set from a chat window's own menu.",
                    LostTalesColors.paletteNames()
            ), DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR);
            Property feedAlignmentProperty = config.get(
                    CATEGORY_CLIENT, "chatFeedAlignment", chatFeedAlignment,
                    "Which edge the closed chat feed's lines stand against: LEFT, CENTRE, or RIGHT. Each line's background thins out away from that edge, from the middle to both sides for CENTRE.");
            feedAlignmentProperty.setValidValues(CHAT_FEED_ALIGNMENTS);
            chatFeedAlignment = normalizeFeedAlignment(
                    feedAlignmentProperty.getString());
            hideHudWhileChatting = config.getBoolean(
                    "hideHudWhileChatting",
                    CATEGORY_CLIENT,
                    hideHudWhileChatting,
                    "Fade the game's HUD (hotbar, health, crosshair and the rest) and the Lost Tales panels out while the chat screen is open, and back in when it closes."
            );
            devSkinOverridePath = config.getString(
                    "devSkinOverridePath",
                    CATEGORY_CLIENT,
                    devSkinOverridePath,
                    "Developer aid: path of a 64x32 or 64x64 PNG drawn on your own player instead of your account skin. Empty disables it."
            );
            devSkinOverrideBodyType = config.getString(
                    "devSkinOverrideBodyType",
                    CATEGORY_CLIENT,
                    devSkinOverrideBodyType,
                    "Arm width for the override skin: wide or slim. Anything else means wide.",
                    DEV_SKIN_BODY_TYPES
            );
            showSkinOverlays = config.getBoolean(
                    "showSkinOverlays",
                    CATEGORY_CLIENT,
                    showSkinOverlays,
                    "Draw the jacket, sleeve, and trouser overlay layers of 64x64 skins on players."
            );
            chestPhysics = config.getBoolean(
                    "chestPhysics",
                    CATEGORY_CLIENT,
                    chestPhysics,
                    "Let the chest sway and bounce with movement."
            );
            chestBounce = config.getFloat(
                    "chestBounce",
                    CATEGORY_CLIENT,
                    chestBounce, 0.0F, 1.0F,
                    "Strength of the chest physics; 0 is still, 1 is the most movement."
            );
            chatHistoryLines = config.getInt(
                    "chatHistoryLines",
                    CATEGORY_CLIENT,
                    chatHistoryLines,
                    100,
                    5000,
                    "Chat messages kept in the history, shared by every channel tab (vanilla keeps 100)."
            );
            sendChatTypingStatus = config.getBoolean(
                    "sendChatTypingStatus",
                    CATEGORY_CLIENT,
                    sendChatTypingStatus,
                    "Let the people who would read your message see that you are typing it."
            );
            showChatTypingIndicators = config.getBoolean(
                    "showChatTypingIndicators",
                    CATEGORY_CLIENT,
                    showChatTypingIndicators,
                    "Show who is typing into a channel above that window's input bar."
            );
            enableNpcChatStyling = config.getBoolean(
                    "enableNpcChatStyling",
                    CATEGORY_CLIENT,
                    enableNpcChatStyling,
                    "Show LOTR NPC speech through the Lost Tales chat style with head icons, timestamps, and channels."
            );
            showChatSpeechBubbles = config.getBoolean(
                    "showChatSpeechBubbles",
                    CATEGORY_CLIENT,
                    showChatSpeechBubbles,
                    "Show what a player says in character over their head, the way LOTR shows an NPC's speech, as far as the server's Proximity radius. In-character channels only (Global, Proximity, Party, Faction and whispers); never OOC & Discord, the operator channel or the console."
            );
            enableChatAnimations = config.getBoolean(
                    "enableChatAnimations",
                    CATEGORY_CLIENT,
                    enableChatAnimations,
                    "Use subtle time-based message, input-bar, and channel-selector animations."
            );
            chatAnimationDurationMillis = config.getInt(
                    "chatAnimationDurationMillis",
                    CATEGORY_CLIENT,
                    chatAnimationDurationMillis,
                    60,
                    1000,
                    "Duration of player-message entry easing in milliseconds."
            );
            chatInputAnimationDurationMillis = config.getInt(
                    "chatInputAnimationDurationMillis",
                    CATEGORY_CLIENT,
                    chatInputAnimationDurationMillis,
                    60,
                    1000,
                    "Duration of the chat input bars' opening animation in milliseconds."
            );
            chatSelectorAnimationDurationMillis = config.getInt(
                    "chatSelectorAnimationDurationMillis",
                    CATEGORY_CLIENT,
                    chatSelectorAnimationDurationMillis,
                    60,
                    1000,
                    "Duration of the upward channel-selector opening animation in milliseconds."
            );
            enableGuiAnimations = config.getBoolean(
                    "enableGuiAnimations", CATEGORY_CLIENT,
                    enableGuiAnimations,
                    "Apply safe elapsed-time opening transitions to compatible GUI screens."
            );
            guiAnimationDurationMillis = config.getInt(
                    "guiAnimationDurationMillis", CATEGORY_CLIENT,
                    guiAnimationDurationMillis, 10, 1000,
                    "Duration of compatible GUI foreground opening animations in milliseconds."
            );
            guiAnimationScale = getBoundedDouble(
                    config, CATEGORY_CLIENT, "guiAnimationScale",
                    guiAnimationScale, 0.5D, 3.0D,
                    "Starting scale of the foreground animation. 1.0 keeps pixel art at its native size."
            );
            Property guiEasingProperty = config.get(
                    CATEGORY_CLIENT, "guiAnimationEasingStyle",
                    guiAnimationEasingStyle,
                    "Foreground easing style: BACK, CUBIC, or SMOOTH.");
            guiEasingProperty.setValidValues(GUI_EASING_STYLES);
            guiAnimationEasingStyle = normalizeGuiEasing(
                    guiEasingProperty.getString());
            Property guiDirectionProperty = config.get(
                    CATEGORY_CLIENT, "guiAnimationDirection",
                    guiAnimationDirection,
                    "Direction the foreground flies toward its resting position: DOWN, UP, LEFT, RIGHT, or NONE.");
            guiDirectionProperty.setValidValues(GUI_DIRECTIONS);
            guiAnimationDirection = normalizeGuiDirection(
                    guiDirectionProperty.getString());
            reducedGuiMotion = config.getBoolean(
                    "reducedGuiMotion", CATEGORY_CLIENT,
                    reducedGuiMotion,
                    "Remove spatial foreground and control-bar movement and shorten the foreground transition."
            );
            enableGuiBackground = config.getBoolean(
                    "enableGuiBackground", CATEGORY_CLIENT,
                    enableGuiBackground,
                    "Draw the dark semi-transparent background behind compatible GUIs."
            );
            guiBackgroundOpacity = getBoundedDouble(
                    config, CATEGORY_CLIENT, "guiBackgroundOpacity",
                    guiBackgroundOpacity, 0.0D, 1.0D,
                    "Final opacity of the GUI's black background veil."
            );
            guiBackgroundFadeTimeMillis = config.getInt(
                    "guiBackgroundFadeTimeMillis", CATEGORY_CLIENT,
                    guiBackgroundFadeTimeMillis, 0, 800,
                    "Duration of the background darkness and blur fade in milliseconds."
            );
            guiAlwaysBlur = config.getBoolean(
                    "guiAlwaysBlur", CATEGORY_CLIENT,
                    guiAlwaysBlur,
                    "Blur every compatible in-world GUI background, including screens that opt out by default."
            );
            enableGuiBackgroundBlur = config.getBoolean(
                    "enableGuiBackgroundBlur", CATEGORY_CLIENT,
                    enableGuiBackgroundBlur,
                    "Blur the world behind compatible GUIs when legacy framebuffer shaders are available."
            );
            guiBlurStrength = getBoundedDouble(
                    config, CATEGORY_CLIENT, "guiBlurStrength",
                    guiBlurStrength, 0.0D, 8.0D,
                    "Background blur radius. Blur failure falls back to the normal GUI background."
            );
            enableSmoothInventoryMovement = config.getBoolean(
                    "enableSmoothInventoryMovement", CATEGORY_CLIENT,
                    enableSmoothInventoryMovement,
                    "Animate item stacks smoothly between inventory slots."
            );
            smoothInventoryAnimationDurationMillis = config.getInt(
                    "smoothInventoryAnimationDurationMillis",
                    CATEGORY_CLIENT,
                    smoothInventoryAnimationDurationMillis, 40, 600,
                    "Duration of inventory item movement in milliseconds."
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

            enableWaystoneRecipe = config.getBoolean(
                    "enableWaystoneRecipe",
                    CATEGORY_WAYSTONES,
                    enableWaystoneRecipe,
                    "Register the Lost Tales waystone crafting recipe. Recipe changes require a restart."
            );
            waystoneRecipeCornerIngredient = config.getString(
                    "waystoneRecipeCornerIngredient",
                    CATEGORY_WAYSTONES,
                    waystoneRecipeCornerIngredient,
                    "Registry name (namespace:path[@meta]) or ore dictionary name prefixed with ore: used in recipe corners."
            );
            waystoneRecipeEdgeIngredient = config.getString(
                    "waystoneRecipeEdgeIngredient",
                    CATEGORY_WAYSTONES,
                    waystoneRecipeEdgeIngredient,
                    "Registry name (namespace:path[@meta]) or ore dictionary name prefixed with ore: used on recipe edges."
            );
            waystoneRecipeCenterIngredient = config.getString(
                    "waystoneRecipeCenterIngredient",
                    CATEGORY_WAYSTONES,
                    waystoneRecipeCenterIngredient,
                    "Registry name (namespace:path[@meta]) or ore dictionary name prefixed with ore: used at the recipe center."
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
            if (fromFiles) {
                writeCurrentValues(config);
                applyShippedDefinitions(config);
            }
        } finally {
            if (fromFiles && config.hasChanged()) {
                config.save();
            }
        }
    }

    /** The server's options file, which the server settings screen and commands edit. */
    public static File getServerConfigFile() {
        return loadedServerFile;
    }

    /** The client's options file; null on a dedicated server. */
    public static File getClientConfigFile() {
        return loadedClientFile;
    }

    /** Every file as one configuration, over the files last loaded. */
    private static LostTalesSidedConfiguration openSided() {
        return LostTalesSidedConfiguration.open(loadedClientFile, loadedServerFile,
                CLIENT_CATEGORIES, serverFilesByCategory());
    }

    /**
     * The server's files as one configuration, the client categories in
     * memory only: what the server settings screen and the commands read
     * and write. Null before a file was loaded.
     */
    public static LostTalesSidedConfiguration openServerConfiguration() {
        if (loadedServerFile == null) {
            return null;
        }
        return LostTalesSidedConfiguration.open(null, loadedServerFile,
                CLIENT_CATEGORIES, serverFilesByCategory());
    }

    private static Map<String, File> serverFilesByCategory() {
        Map<String, File> files = new HashMap<String, File>();
        files.put(CATEGORY_ROLES, loadedRolesFile);
        files.put(CATEGORY_CHANNELS, loadedChannelsFile);
        return files;
    }

    /**
     * Puts the roles and gates the file describes in force, on the side
     * that owns them: the logical server. A client reading its own files
     * while connected to someone else's server leaves the catalogue the
     * access packet gave it alone, so saving client settings mid-session
     * cannot restyle the server's roles; before any server exists the
     * catalogue stays the built-in team mark, which is what a client
     * falls back to anyway. The server installs on start, so a
     * hand-edited file is picked up by loading a world.
     */
    private static void installChatRoles() {
        if (!isLogicalServer()) {
            return;
        }
        ChatRoleConfig.Warnings warnings = new ChatRoleConfig.Warnings() {
            @Override
            public void warn(String message) {
                FMLLog.warning("[%s] %s", LostTalesMetaData.MOD_ID, message);
            }
        };
        LostTalesPermissionCatalog permissions =
                ChatRoleConfig.parsePermissions(chatPermissions, warnings);
        LostTalesPermissionCatalog.install(permissions);
        ChatRoleCatalog catalog = ChatRoleConfig.parse(chatRoles, chatRoleMembers,
                permissions, warnings);
        ChatRoleCatalog.installServer(catalog);
        // The channels this server defines are put in force before the
        // gates are read, so a gate may name one of them. Every reload
        // starts from the built-ins: an id is registered once, and the
        // file is the only thing that says which others are in force.
        ChatChannel.installDefined(
                ChatRoleConfig.parseChannelDefinitions(
                        chatChannelDefinitions, warnings),
                new ChatChannel.Warnings() {
                    @Override
                    public void warn(String message) {
                        warnings.warn(message);
                    }
                });
        // The icons the channels wear, read once every channel is in
        // force, so one the file defines above may be given one.
        ChatChannelIconCatalog.install(
                ChatRoleConfig.parseChannelIcons(chatChannelIcons, warnings));
        // The words the server adds to the profanity list, over the
        // bundled ones, sent to every client with its chat access.
        List<String> profanityWarnings = new ArrayList<String>();
        ChatProfanityCatalog.installServerWords(ChatProfanityWords.parse(
                chatProfanityWords, ChatProfanityWords.MAX_WORDS, profanityWarnings));
        for (String warning : profanityWarnings) {
            warnings.warn(warning);
        }
        // The Operator channel's gate is put back when its line is
        // missing, before the gates are read, and the value written back
        // with the rest of the file at the end of this load.
        chatChannelRoles = ChatRoleConfig.withRequiredGates(chatChannelRoles, warnings);
        ChatChannelGates.install(ChatRoleConfig.parseGates(chatChannelRoles, catalog, warnings));
    }

    /**
     * Whether this side is the server the roles belong to: one is
     * running here, and has not stopped. A client that has left a world
     * of its own still holds the server it hosted, so the running flag
     * is asked besides — otherwise the last world played would keep
     * installing its roles over the ones a remote server states.
     */
    private static boolean isLogicalServer() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.isServerRunning();
    }

    /**
     * The client's options as the Forge screen edits them; null on a
     * dedicated server, which has no client options and no screen.
     */
    public static synchronized Configuration createConfiguration() {
        pendingGuiConfiguration = loadedClientFile == null
                ? null : new Configuration(loadedClientFile);
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
        if (loadedServerFile != null) {
            load(loadedClientFile, loadedServerFile, loadedRolesFile, loadedChannelsFile);
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
            setNotificationPresetOffsets(50, 35);
        } else if (HUD_PRESET_LOTR_SAFE.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 12;
            partyHudOffsetX = 2;
            partyHudOffsetY = 28;
            quickLootHudOffsetX = 61;
            quickLootHudOffsetY = 34;
            questHudOffsetX = 2;
            questHudOffsetY = 52;
            setNotificationPresetOffsets(50, 32);
        } else if (HUD_PRESET_COMPACT.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 7;
            partyHudOffsetX = 1;
            partyHudOffsetY = 26;
            quickLootHudOffsetX = 67;
            quickLootHudOffsetY = 37;
            questHudOffsetX = 1;
            questHudOffsetY = 57;
            setNotificationPresetOffsets(50, 30);
        } else if (HUD_PRESET_MINIMAL.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 4;
            partyHudOffsetX = 1;
            partyHudOffsetY = 18;
            quickLootHudOffsetX = 71;
            quickLootHudOffsetY = 42;
            questHudOffsetX = 1;
            questHudOffsetY = 70;
            setNotificationPresetOffsets(50, 28);
        }
    }

    private static void setNotificationPresetOffsets(int x, int y) {
        notificationHudOffsetX = x;
        notificationHudOffsetY = y;
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
        } else if ("notifications".equals(key)) {
            notificationHudOffsetX = x;
            notificationHudOffsetY = y;
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
        if ("notifications".equals(key)) {
            return setHudOffset(key, notificationHudOffsetX + dx,
                    notificationHudOffsetY + dy);
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
        // The three notices share one slot; every name any of them went
        // by names the slot.
        if ("notifications".equals(key)
                || "notification".equals(key)
                || "notices".equals(key)
                || "questnotifications".equals(key)
                || "questnotification".equals(key)
                || "toast".equals(key)
                || "toasts".equals(key)
                || "mapdiscovery".equals(key)
                || "locationdiscovery".equals(key)
                || "discovery".equals(key)
                || "areanotice".equals(key)
                || "areaname".equals(key)
                || "area".equals(key)) {
            return "notifications";
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
        notificationHudOffsetX = clampPercent(notificationHudOffsetX);
        notificationHudOffsetY = clampPercent(notificationHudOffsetY);
    }

    /**
     * A colour option's value as the palette knows it, or the option's
     * default when the file names no palette entry: a colour is only
     * ever one of the palette's.
     */
    static String paletteName(String value, String fallback) {
        if (!LostTalesColors.isPaletteName(value)) {
            return fallback;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * A colour option that may follow another colour: automatic
     * ({@link #CHAT_COLOR_AUTOMATIC}) or a palette entry's name, and
     * automatic for anything else.
     */
    static String automaticOrPaletteName(String value) {
        return value != null
                && CHAT_COLOR_AUTOMATIC.equalsIgnoreCase(value.trim())
                ? CHAT_COLOR_AUTOMATIC
                : paletteName(value, CHAT_COLOR_AUTOMATIC);
    }

    /** What such an option offers: automatic, then the palette. */
    private static String[] automaticOrPaletteNames() {
        String[] palette = LostTalesColors.paletteNames();
        String[] names = new String[palette.length + 1];
        names[0] = CHAT_COLOR_AUTOMATIC;
        System.arraycopy(palette, 0, names, 1, palette.length);
        return names;
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    public static void save() {
        if (loadedServerFile == null) {
            return;
        }

        Configuration config = openSided();
        try {
            config.load();
            writeCurrentValues(config);
            // Written back value by value, each option lost its default
            // and its comment; its definition puts them back.
            applyShippedDefinitions(config);
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
        config.getCategory(CATEGORY_CHAT).setLanguageKey(
                "losttales.config.category.chat");
        config.getCategory(CATEGORY_RANGED_COMBAT).setLanguageKey(
                "losttales.config.category.rangedCombat");
        config.getCategory(CATEGORY_WAYSTONES).setLanguageKey(
                "losttales.config.category.waystones");
        config.getCategory(CATEGORY_DISCORD).setLanguageKey(
                "losttales.config.category.discord");
        config.getCategory(CATEGORY_ROLES).setLanguageKey(
                "losttales.config.category.roles");
        config.getCategory(CATEGORY_CHANNELS).setLanguageKey(
                "losttales.config.category.channels");
    }

    /**
     * Gives every option of {@code config} the definition the mod ships
     * it with — its default, its comment, its bounds and its words — and
     * leaves what each is set to alone ({@link LostTalesConfigDefinitions}):
     * what a config screen needs to restore a default the file cannot
     * hold, and what a save writes above each value. Nothing before the
     * first load.
     */
    public static void applyShippedDefinitions(Configuration config) {
        LostTalesConfigDefinitions.apply(shipped, config);
    }

    private static void writeCurrentValues(Configuration config) {
        if (config == null) {
            return;
        }

        config.get(CATEGORY_RANGED_COMBAT, "enableChargeTiers",
                enableChargeTiers).set(enableChargeTiers);
        config.get(CATEGORY_CHAT, "proximityRadius",
                chatProximityRadius).set(chatProximityRadius);
        config.get(CATEGORY_CHAT, "historyPersisted",
                chatHistoryPersisted).set(chatHistoryPersisted);
        config.get(CATEGORY_CHAT, "historyPerChannel",
                chatHistoryPerChannel).set(chatHistoryPerChannel);
        config.get(CATEGORY_ROLES, "permissions", chatPermissions).set(chatPermissions);
        config.get(CATEGORY_ROLES, "definitions", chatRoles).set(chatRoles);
        config.get(CATEGORY_ROLES, "members", chatRoleMembers).set(chatRoleMembers);
        config.get(CATEGORY_CHANNELS, "definitions", chatChannelDefinitions)
                .set(chatChannelDefinitions);
        config.get(CATEGORY_CHANNELS, "gates", chatChannelRoles).set(chatChannelRoles);
        config.get(CATEGORY_CHANNELS, "icons", chatChannelIcons).set(chatChannelIcons);
        config.get(CATEGORY_CHAT, "profanityWords", chatProfanityWords)
                .set(chatProfanityWords);
        config.get(CATEGORY_CLIENT, "showTimestamps",
                showChatTimestamps).set(showChatTimestamps);
        config.get(CATEGORY_CLIENT, "enableChatEmojis",
                enableChatEmojis).set(enableChatEmojis);
        config.get(CATEGORY_CLIENT, "enableNpcChatStyling",
                enableNpcChatStyling).set(enableNpcChatStyling);
        config.get(CATEGORY_CLIENT, "enableChatPings",
                enableChatPings).set(enableChatPings);
        config.get(CATEGORY_CLIENT, "chatPingSound",
                chatPingSound).set(chatPingSound);
        config.get(CATEGORY_CLIENT, "chatBackgroundColor",
                chatBackgroundColor).set(chatBackgroundColor);
        config.get(CATEGORY_CLIENT, "chatSelectedLineColor",
                chatSelectedLineColor).set(chatSelectedLineColor);
        config.get(CATEGORY_CLIENT, "chatMentionLineColor",
                chatMentionLineColor).set(chatMentionLineColor);
        config.get(CATEGORY_CLIENT, "chatSelectedMentionColor",
                chatSelectedMentionColor).set(chatSelectedMentionColor);
        config.get(CATEGORY_CLIENT, "chatReplyHighlightColor",
                chatReplyHighlightColor).set(chatReplyHighlightColor);
        Property feedAlignmentProperty = config.get(
                CATEGORY_CLIENT, "chatFeedAlignment", chatFeedAlignment);
        feedAlignmentProperty.set(chatFeedAlignment);
        feedAlignmentProperty.setValidValues(CHAT_FEED_ALIGNMENTS);
        Property profanityProperty = config.get(
                CATEGORY_CLIENT, "chatProfanityFilter", chatProfanityFilter);
        profanityProperty.set(chatProfanityFilter);
        profanityProperty.setValidValues(ChatProfanityMode.names());
        config.get(CATEGORY_CLIENT, "hideHudWhileChatting",
                hideHudWhileChatting).set(hideHudWhileChatting);
        config.get(CATEGORY_CLIENT, "enableChatAnimations",
                enableChatAnimations).set(enableChatAnimations);
        config.get(CATEGORY_CLIENT, "chatAnimationDurationMillis",
                chatAnimationDurationMillis).set(
                chatAnimationDurationMillis);
        config.get(CATEGORY_CLIENT, "chatInputAnimationDurationMillis",
                chatInputAnimationDurationMillis).set(
                chatInputAnimationDurationMillis);
        config.get(CATEGORY_CLIENT,
                "chatSelectorAnimationDurationMillis",
                chatSelectorAnimationDurationMillis).set(
                chatSelectorAnimationDurationMillis);
        config.get(CATEGORY_CLIENT, "enableGuiAnimations",
                enableGuiAnimations).set(enableGuiAnimations);
        config.get(CATEGORY_CLIENT, "guiAnimationDurationMillis",
                guiAnimationDurationMillis).set(
                guiAnimationDurationMillis);
        config.get(CATEGORY_CLIENT, "guiAnimationScale",
                guiAnimationScale).set(guiAnimationScale);
        Property guiEasingProperty = config.get(
                CATEGORY_CLIENT, "guiAnimationEasingStyle",
                guiAnimationEasingStyle);
        guiEasingProperty.set(guiAnimationEasingStyle);
        guiEasingProperty.setValidValues(GUI_EASING_STYLES);
        Property guiDirectionProperty = config.get(
                CATEGORY_CLIENT, "guiAnimationDirection",
                guiAnimationDirection);
        guiDirectionProperty.set(guiAnimationDirection);
        guiDirectionProperty.setValidValues(GUI_DIRECTIONS);
        config.get(CATEGORY_CLIENT, "reducedGuiMotion",
                reducedGuiMotion).set(reducedGuiMotion);
        config.get(CATEGORY_CLIENT, "enableGuiBackground",
                enableGuiBackground).set(enableGuiBackground);
        config.get(CATEGORY_CLIENT, "guiBackgroundOpacity",
                guiBackgroundOpacity).set(guiBackgroundOpacity);
        config.get(CATEGORY_CLIENT, "guiBackgroundFadeTimeMillis",
                guiBackgroundFadeTimeMillis).set(
                guiBackgroundFadeTimeMillis);
        config.get(CATEGORY_CLIENT, "guiAlwaysBlur",
                guiAlwaysBlur).set(guiAlwaysBlur);
        config.get(CATEGORY_CLIENT, "enableGuiBackgroundBlur",
                enableGuiBackgroundBlur).set(enableGuiBackgroundBlur);
        config.get(CATEGORY_CLIENT, "guiBlurStrength",
                guiBlurStrength).set(guiBlurStrength);
        config.get(CATEGORY_CLIENT, "enableSmoothInventoryMovement",
                enableSmoothInventoryMovement).set(
                enableSmoothInventoryMovement);
        config.get(CATEGORY_CLIENT,
                "smoothInventoryAnimationDurationMillis",
                smoothInventoryAnimationDurationMillis).set(
                smoothInventoryAnimationDurationMillis);
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
        config.get(CATEGORY_CLIENT, "hostileCompassMarkerScanRadius", hostileCompassMarkerScanRadius).set(hostileCompassMarkerScanRadius);
        config.get(CATEGORY_CLIENT, "showHostileMapMarkers", showHostileMapMarkers).set(showHostileMapMarkers);
        config.get(CATEGORY_CLIENT, "hostileMapMarkerDisplayRadius", hostileMapMarkerDisplayRadius).set(hostileMapMarkerDisplayRadius);
        config.get(CATEGORY_CLIENT, "closeMapTerrainTransitionStartZoom",
                closeMapTerrainTransitionStartZoom).set(
                closeMapTerrainTransitionStartZoom);
        config.get(CATEGORY_CLIENT, "closeMapTerrainTransitionEndZoom",
                closeMapTerrainTransitionEndZoom).set(
                closeMapTerrainTransitionEndZoom);
        config.get(CATEGORY_CLIENT, "hiddenMapLegendCategories",
                hiddenMapLegendCategories).set(hiddenMapLegendCategories);
        config.get(CATEGORY_CLIENT, "customWaypointColors",
                customWaypointColors).set(customWaypointColors);
        config.get(CATEGORY_CLIENT, "customWaypointNotes",
                customWaypointNotes).set(customWaypointNotes);
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
        config.get(CATEGORY_PARTY, "enableSharedQuestProgress", enableSharedQuestProgress).set(enableSharedQuestProgress);
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
        config.get(CATEGORY_CLIENT, "showNativeLotrQuestTracker", showNativeLotrQuestTracker).set(showNativeLotrQuestTracker);
        config.get(CATEGORY_CLIENT, "notificationHudOffsetX",
                notificationHudOffsetX).set(notificationHudOffsetX);
        config.get(CATEGORY_CLIENT, "notificationHudOffsetY",
                notificationHudOffsetY).set(notificationHudOffsetY);
        config.get(CATEGORY_CLIENT, "showWorldQuestMarkers", showWorldQuestMarkers).set(showWorldQuestMarkers);
        config.get(CATEGORY_CLIENT, "showDiscoveredWorldMapMarkers", showDiscoveredWorldMapMarkers).set(showDiscoveredWorldMapMarkers);
        config.get(CATEGORY_CLIENT, "worldQuestMarkerMaxDistance", worldQuestMarkerMaxDistance).set(worldQuestMarkerMaxDistance);
        config.get(CATEGORY_CLIENT, "showQuestChatFeedback", showQuestChatFeedback).set(showQuestChatFeedback);
        config.get(CATEGORY_CLIENT, "playQuestSounds", playQuestSounds).set(playQuestSounds);
        config.get(CATEGORY_QUESTS, "enableQuestPrerequisites", enableQuestPrerequisites).set(enableQuestPrerequisites);
        config.get(CATEGORY_QUESTS, "enableQuestRewards", enableQuestRewards).set(enableQuestRewards);
        config.get(CATEGORY_QUESTS, "allowQuestItemStarts", allowQuestItemStarts).set(allowQuestItemStarts);
        config.get(CATEGORY_QUESTS, "allowQuestInteractionStarts", allowQuestInteractionStarts).set(allowQuestInteractionStarts);
        config.get(CATEGORY_QUESTS, "autoRevealQuestMarkersOnStart", autoRevealQuestMarkersOnStart).set(autoRevealQuestMarkersOnStart);
        config.get(CATEGORY_QUESTS, "autoPinQuestOnStart", autoPinQuestOnStart).set(autoPinQuestOnStart);
        config.get(CATEGORY_QUESTS, "autoDiscoverNearbyMapMarkers", autoDiscoverNearbyMapMarkers).set(autoDiscoverNearbyMapMarkers);
        config.get(CATEGORY_QUESTS, "mapMarkerDiscoveryScanIntervalTicks", mapMarkerDiscoveryScanIntervalTicks).set(mapMarkerDiscoveryScanIntervalTicks);
        config.get(CATEGORY_WAYSTONES, "enableWaystoneRecipe", enableWaystoneRecipe).set(enableWaystoneRecipe);
        config.get(CATEGORY_WAYSTONES, "waystoneRecipeCornerIngredient", waystoneRecipeCornerIngredient).set(waystoneRecipeCornerIngredient);
        config.get(CATEGORY_WAYSTONES, "waystoneRecipeEdgeIngredient", waystoneRecipeEdgeIngredient).set(waystoneRecipeEdgeIngredient);
        config.get(CATEGORY_WAYSTONES, "waystoneRecipeCenterIngredient", waystoneRecipeCenterIngredient).set(waystoneRecipeCenterIngredient);
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

    private static String normalizeGuiEasing(String value) {
        String normalized = value == null
                ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return "CUBIC".equals(normalized) || "SMOOTH".equals(normalized)
                ? normalized : "BACK";
    }

    /**
     * The feed alignment a config value names, case and surrounding space
     * aside: {@code LEFT}, {@code CENTRE} — {@code CENTER} is read as it —
     * or {@code RIGHT}, and the left for anything else.
     */
    public static String normalizeFeedAlignment(String value) {
        String normalized = value == null
                ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("CENTER".equals(normalized)) {
            return "CENTRE";
        }
        return "CENTRE".equals(normalized) || "RIGHT".equals(normalized)
                ? normalized : CHAT_FEED_ALIGNMENTS[0];
    }

    private static String normalizeGuiDirection(String value) {
        String normalized = value == null
                ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return "UP".equals(normalized) || "LEFT".equals(normalized)
                || "RIGHT".equals(normalized)
                || "NONE".equals(normalized)
                ? normalized : "DOWN";
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
