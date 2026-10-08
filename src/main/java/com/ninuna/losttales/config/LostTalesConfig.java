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
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelIconCatalog;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
/**
 * Every option of the mod but the camera's, each a static field read from
 * the config files during preInit and again on every reload. The client
 * category lives in the client's file, every other category in the
 * server's, with the roles and the channels in files of their own
 * ({@link LostTalesSidedConfiguration} routes each category to its file).
 * An option's name and comment are lines of the lang file
 * ({@link LostTalesConfigWords}), not words in the code.
 */
public final class LostTalesConfig {
    public static final String CATEGORY_CLIENT = "client";
    public static final String CATEGORY_QUESTS = "quests";
    public static final String CATEGORY_MISSIVES = "missives";
    public static final String CATEGORY_CHARACTERS = "characters";
    public static final String CATEGORY_COMBAT_MARKERS = "combat_markers";
    public static final String CATEGORY_FELLOWSHIP = "fellowship";
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
    /**
     * Every option as the mod ships it ({@link LostTalesConfigDefinitions}):
     * read on the first load against no file, while each field still
     * holds its shipped value. Null until then.
     */
    private static Configuration shipped;
    /** What every option is read with where Forge asks for a comment. */
    private static final String TIP = LostTalesConfigWords.TIP;


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
    public static boolean fellowshipSharedAggroTracking = true;

    public static boolean showFellowshipHud = true;
    public static boolean linkShowFellowshipHud = false;
    public static double fellowshipHudOffsetX = 2.0D;
    public static double fellowshipHudOffsetY = 18.0D;
    public static int fellowshipCompassMarkerFadeRadius = 100;

    public static int fellowshipStatusUpdateIntervalTicks = 10;
    public static int fellowshipStatusHeartbeatTicks = 100;
    public static int fellowshipTrackingUpdateIntervalTicks = 10;
    public static int fellowshipTrackingHeartbeatTicks = 100;
    public static boolean enableSharedQuestProgress = true;
    public static int fellowshipSharedQuestRadius = 32;

    public static boolean showQuickLootHud = true;
    public static boolean linkShowQuickLootHud = false;
    public static int quickLootHudMaxRows = 5;
    public static double quickLootHudOffsetX = 24.0D;
    public static double quickLootHudOffsetY = 32.0D;

    public static boolean showQuestHud = true;
    public static boolean linkShowQuestHud = false;
    public static double questHudOffsetX = 2.0D;
    public static double questHudOffsetY = 38.0D;
    public static int questHudMaxTrackedQuests = 4;
    public static int questHudObjectiveLineCount = 2;
    public static boolean showQuestHudNotifications = true;
    public static boolean showNativeLotrQuestTracker = false;
    /**
     * Whether a quest offered by somebody is talked about in the Lost
     * Tales conversation screen. Off, a Middle-earth quest is offered on
     * LOTR's own screen, and a Lost Tales quest giver's words go to the
     * chat while their quest is taken or handed in on touch.
     */
    public static boolean enableQuestDialogue = true;
    /** The one slot every passing notice shares: quest banners, discoveries, area names. */
    public static double notificationHudOffsetX = 50.0D;
    public static double notificationHudOffsetY = 35.0D;
    public static boolean showWorldQuestMarkers = true;
    public static boolean showDiscoveredWorldMapMarkers = true;
    public static int worldQuestMarkerMaxDistance = 256;

    public static boolean showQuestChatFeedback = true;
    public static boolean playQuestSounds = true;

    /** Server-authoritative recipient radius for the Proximity channel. */
    public static int chatProximityRadius = 64;
    /** Client-only presentation preferences; neither affects recipients. */
    public static boolean enableChatEmojis = true;
    public static boolean convertChatEmoticons = true;
    /**
     * How the words on the chat's profanity list read on this client:
     * one of {@link ChatProfanityMode}'s names.
     */
    public static String chatProfanityFilter = ChatProfanityMode.SILLY.name();
    public static boolean enableChatMessageGrouping = true;
    /** Whether the world blurs behind every window while the window screen is open. */
    public static boolean windowBackgroundBlur = true;
    public static boolean enableNpcChatStyling = true;
    /**
     * How many conversations with NPCs stand open by themselves; the ones
     * quiet longest close as another opens, and wait in the {@code +}.
     */
    public static int npcConversationsOpen = 3;
    public static boolean showChatSpeechBubbles = true;
    public static boolean enableChatPings = true;
    /** The chat's own mention cue, bundled with the mod. */
    static final String DEFAULT_CHAT_PING_SOUND = "losttales:chat.ping";
    public static String chatPingSound = DEFAULT_CHAT_PING_SOUND;
    /**
     * The windows' three colours and the chat's own, each a palette entry
     * by name: the windows' primary, secondary and accent
     * ({@code LostTalesUiTheme}), a line that mentions this player and a
     * line a reply jumped to. Names, not numbers, so every choice is one of
     * the palette's colours.
     */
    public static final String DEFAULT_WINDOW_PRIMARY_COLOR = "PLUM_BLACK";
    public static final String DEFAULT_WINDOW_SECONDARY_COLOR = "PLUM_GRAY";
    public static final String DEFAULT_WINDOW_ACCENT_COLOR = "HONEY";
    public static final String DEFAULT_CHAT_MENTION_LINE_COLOR = "CORAL";
    public static final String DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR = "APRICOT";
    /**
     * A colour option's value that follows another colour instead of
     * naming a palette entry: the selected mention's, which is by
     * default the mention colour a shade lighter.
     */
    public static final String CHAT_COLOR_AUTOMATIC = "AUTO";
    public static String windowPrimaryColor = DEFAULT_WINDOW_PRIMARY_COLOR;
    public static String windowSecondaryColor = DEFAULT_WINDOW_SECONDARY_COLOR;
    public static String windowAccentColor = DEFAULT_WINDOW_ACCENT_COLOR;
    public static String chatMentionLineColor = DEFAULT_CHAT_MENTION_LINE_COLOR;
    /** A line that mentions this player, under the pointer; automatic until chosen. */
    public static String chatSelectedMentionColor = CHAT_COLOR_AUTOMATIC;
    /** The line a reply's quote jumps to, lit while the eye finds it. */
    public static String chatReplyHighlightColor = DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR;
    /** The edges the closed-chat feed's lines may stand against. */
    static final String[] CHAT_FEED_ALIGNMENTS = {"LEFT", "CENTRE", "RIGHT"};
    /** Where the feed's lines stand until chosen: the middle. */
    static final String DEFAULT_CHAT_FEED_ALIGNMENT = "CENTRE";
    /** Which of them the feed's lines stand against. */
    public static String chatFeedAlignment = DEFAULT_CHAT_FEED_ALIGNMENT;
    /**
     * The sizes a row of the chat may be drawn at, against the words of
     * a message in the open window: one whole display pixel per font
     * pixel down, the same, or one up. Only whole display pixels keep
     * the font and the pixel art crisp, so these are the steps there
     * are.
     */
    public static final String CHAT_SIZE_SMALLER = "SMALLER";
    public static final String CHAT_SIZE_SAME = "SAME";
    public static final String CHAT_SIZE_LARGER = "LARGER";
    static final String[] CHAT_SIZES = {
            CHAT_SIZE_SMALLER, CHAT_SIZE_SAME, CHAT_SIZE_LARGER};
    /** The row a message names its speaker on, in the open window. */
    public static String chatSpeakerSize = CHAT_SIZE_LARGER;
    /** The same row in the closed feed, a step over the feed's own words. */
    public static String chatFeedSpeakerSize = CHAT_SIZE_LARGER;
    /** What a message says, in the closed feed. */
    public static String chatFeedMessageSize = CHAT_SIZE_SMALLER;
    /** The row a reply opens with, in the open window. */
    public static String chatQuoteSize = CHAT_SIZE_SMALLER;
    /** The same row in the closed feed, as large as the feed's words. */
    public static String chatFeedQuoteSize = CHAT_SIZE_SAME;
    /** How wide the chat feed stands, in GUI pixels. */
    public static int chatFeedWidth = 320;
    /** The most lines the chat feed shows at once. */
    public static int chatFeedLines = 10;
    /** How many seconds a line stays in the chat feed before it has faded. */
    public static int chatFeedSeconds = 10;
    /**
     * Whether the game's HUD and the mod's panels fade out while the
     * window screen is open, leaving the world and the windows.
     */
    public static boolean hideHudWithWindows = true;
    /** How strong a window pinned to the screen shows while playing, in percent of how it shows on the window screen. */
    public static int pinnedWindowOpacity = 70;
    /**
     * Whether anything of the mod's moves: every screen, HUD panel and
     * chat motion. Off, everything stands where it ends.
     */
    public static boolean animations = true;
    /** How fast every motion plays: 2 is twice as fast, 0.5 half as fast. */
    public static double animationSpeed = 1.0D;
    /**
     * Motion kept to short fades: nothing travels, overshoots, anticipates
     * or stretches.
     */
    public static boolean reducedMotion = false;
    /**
     * Developer aid: a local PNG drawn on the local player instead of the
     * account skin, with a chosen arm width. Empty path disables it.
     */
    public static String devSkinOverridePath = "";
    /** The override skin's arm widths, as the option names them. */
    public static final String[] DEV_SKIN_BODY_TYPES = {"wide", "slim"};
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
    /** What a new player is greeted with on their first join, in OOC Chat; none greets nobody. */
    public static String[] chatWelcomeLines = new String[0];
    /** The address players join by, in the Server's status line and Discord's topics; empty for none. */
    public static String serverAddress = "";
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
     * and the webhook addresses in the links are secrets: they stay in
     * this file and are never logged, shown or sent to a client.
     */
    public static boolean discordEnabled;
    public static String discordBotToken = "";
    public static int discordPollIntervalSeconds = 3;
    /**
     * The links, one entry per Discord channel a game channel goes to
     * ({@code DiscordChannelBindings}); none in a fresh file, since a
     * link is made by a code with {@code /losttales discord link}.
     */
    public static String[] discordChannelBindings = new String[0];
    /** The picture a post carries: {name}/{uuid} of the sender's account. */
    public static String discordAvatarUrlTemplate =
            "https://mc-heads.net/head/{name}/64";
    /** Post player join and leave notices to OOC's Discord channels. */
    public static boolean discordJoinsAndLeaves = true;
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
    /** List the members of the linked Discord channels, with their Discord status. */
    public static boolean discordMemberList = true;
    /** Let a player's line ping the Discord members its mentions name. */
    public static boolean discordPingMembers = true;
    /** How the profanity list's words read in what the bridge posts: a {@link ChatProfanityMode} name. */
    public static String discordProfanityFilter = ChatProfanityMode.OFF.name();

    /** Client-only screen background preferences. */
    public static boolean enableGuiBackground = true;
    public static double guiBackgroundOpacity = 0.65D;
    public static boolean guiAlwaysBlur = false;
    public static boolean enableGuiBackgroundBlur = true;
    public static double guiBlurStrength = 4.5D;

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
     * each field still holds its shipped value. Each option's comment is
     * then its English tip from the lang file.
     */
    static void defineOptions(Configuration definitions) {
        readOptions(definitions, false);
        LostTalesConfigWords.apply(definitions);
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
                    enableChargeTiers, TIP);
            chargeTierOneTicks = config.getInt(
                    "chargeTierOneTicks", CATEGORY_RANGED_COMBAT,
                    chargeTierOneTicks, 1, 200, TIP);
            chargeTierTwoTicks = config.getInt(
                    "chargeTierTwoTicks", CATEGORY_RANGED_COMBAT,
                    chargeTierTwoTicks, 1, 400, TIP);
            chargeTierThreeTicks = config.getInt(
                    "chargeTierThreeTicks", CATEGORY_RANGED_COMBAT,
                    chargeTierThreeTicks, 1, 600, TIP);
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
                    chargeTierOneDamageMultiplier, 1.0D, 3.0D);
            chargeTierTwoDamageMultiplier = getBoundedDouble(
                    config, "chargeTierTwoDamageMultiplier",
                    chargeTierTwoDamageMultiplier, 1.0D, 4.0D);
            chargeTierThreeDamageMultiplier = getBoundedDouble(
                    config, "chargeTierThreeDamageMultiplier",
                    chargeTierThreeDamageMultiplier, 1.0D, 6.0D);
            chargeTierOneVelocityMultiplier = getBoundedDouble(
                    config, "chargeTierOneVelocityMultiplier",
                    chargeTierOneVelocityMultiplier, 1.0D, 2.0D);
            chargeTierTwoVelocityMultiplier = getBoundedDouble(
                    config, "chargeTierTwoVelocityMultiplier",
                    chargeTierTwoVelocityMultiplier, 1.0D, 2.0D);
            chargeTierThreeVelocityMultiplier = getBoundedDouble(
                    config, "chargeTierThreeVelocityMultiplier",
                    chargeTierThreeVelocityMultiplier, 1.0D, 2.0D);
            chargeTierOneKnockback = getBoundedDouble(
                    config, "chargeTierOneKnockback",
                    chargeTierOneKnockback, 0.0D, 1.0D);
            chargeTierTwoKnockback = getBoundedDouble(
                    config, "chargeTierTwoKnockback",
                    chargeTierTwoKnockback, 0.0D, 1.0D);
            chargeTierThreeKnockback = getBoundedDouble(
                    config, "chargeTierThreeKnockback",
                    chargeTierThreeKnockback, 0.0D, 1.0D);

            allowedStartingFactionIds = config.get(
                    CATEGORY_CHARACTERS,
                    "allowedStartingFactionIds",
                    allowedStartingFactionIds,
                    TIP
            ).getStringList();
            deniedStartingFactionIds = config.get(
                    CATEGORY_CHARACTERS,
                    "deniedStartingFactionIds",
                    deniedStartingFactionIds,
                    TIP
            ).getStringList();
            characterSwitchCooldownSeconds = config.get(
                    CATEGORY_CHARACTERS,
                    "switchCooldownSeconds",
                    characterSwitchCooldownSeconds,
                    TIP
            ).getIntList();
            characterSwitchDecaySeconds = config.get(
                    CATEGORY_CHARACTERS,
                    "switchCooldownDecaySeconds",
                    characterSwitchDecaySeconds,
                    TIP
            ).getIntList();
            characterSwitchCombatGraceSeconds = config.getInt(
                    "switchCombatGraceSeconds",
                    CATEGORY_CHARACTERS,
                    characterSwitchCombatGraceSeconds,
                    0,
                    3600,
                    TIP
            );
            characterSwitchTeleportGraceSeconds = config.getInt(
                    "switchTeleportGraceSeconds",
                    CATEGORY_CHARACTERS,
                    characterSwitchTeleportGraceSeconds,
                    0,
                    300,
                    TIP
            );
            characterSwitchStableGroundTicks = config.getInt(
                    "switchStableGroundTicks",
                    CATEGORY_CHARACTERS,
                    characterSwitchStableGroundTicks,
                    0,
                    200,
                    TIP
            );
            characterSwitchTeleportDistancePerTick = config.get(
                    CATEGORY_CHARACTERS,
                    "switchTeleportDistancePerTick",
                    characterSwitchTeleportDistancePerTick,
                    TIP
            ).getDouble(characterSwitchTeleportDistancePerTick);
            characterStateMaxSnapshotBytes = config.getInt(
                    "characterStateMaxSnapshotBytes",
                    CATEGORY_CHARACTERS,
                    characterStateMaxSnapshotBytes,
                    64 * 1024,
                    16 * 1024 * 1024,
                    TIP
            );
            characterStateCheckpointIntervalSeconds = config.getInt(
                    "characterStateCheckpointIntervalSeconds",
                    CATEGORY_CHARACTERS,
                    characterStateCheckpointIntervalSeconds,
                    30,
                    3600,
                    TIP
            );
            characterStateCheckpointPlayersPerTick = config.getInt(
                    "characterStateCheckpointPlayersPerTick",
                    CATEGORY_CHARACTERS,
                    characterStateCheckpointPlayersPerTick,
                    1,
                    4,
                    TIP
            );
            characterDeletionRetentionDays = config.getInt(
                    "characterDeletionRetentionDays",
                    CATEGORY_CHARACTERS,
                    characterDeletionRetentionDays,
                    1,
                    3650,
                    TIP
            );
            sanitizeCharacterSwitchOptions();

            showLostTalesHud = config.getBoolean(
                    "showLostTalesHud",
                    CATEGORY_CLIENT,
                    showLostTalesHud,
                    TIP
            );
            Property hudPresetProperty = config.get(
                    CATEGORY_CLIENT,
                    "hudPlacementPreset",
                    hudPlacementPreset,
                    TIP
            );
            hudPresetProperty.setValidValues(HUD_PRESET_VALUES);
            hudPlacementPreset = normalizeHudPreset(hudPresetProperty.getString());

            showCompassHud = config.getBoolean(
                    "showCompassHud",
                    CATEGORY_CLIENT,
                    showCompassHud,
                    TIP
            );
            linkShowCompassHud = config.getBoolean(
                    "linkShowCompassHud",
                    CATEGORY_CLIENT,
                    linkShowCompassHud,
                    TIP
            );
            compassHudOffsetX = getHudPercent(
                    config, "compassHudOffsetX",
                    compassHudOffsetX, 0.0D, 100.0D);
            compassHudOffsetY = getHudPercent(
                    config, "compassHudOffsetY",
                    compassHudOffsetY, 0.0D, 100.0D);
            compassHudDisplayRadius = config.getInt(
                    "compassHudDisplayRadius",
                    CATEGORY_CLIENT,
                    compassHudDisplayRadius,
                    45,
                    225,
                    TIP
            );
            showStaticCompassMarkers = config.getBoolean(
                    "showStaticCompassMarkers",
                    CATEGORY_CLIENT,
                    showStaticCompassMarkers,
                    TIP
            );
            showLotrWaypointCompassMarkers = config.getBoolean(
                    "showLotrWaypointCompassMarkers",
                    CATEGORY_CLIENT,
                    showLotrWaypointCompassMarkers,
                    TIP
            );
            onlyShowUnlockedLotrWaypoints = config.getBoolean(
                    "onlyShowUnlockedLotrWaypoints",
                    CATEGORY_CLIENT,
                    onlyShowUnlockedLotrWaypoints,
                    TIP
            );
            showHostileCompassMarkers = config.getBoolean(
                    "showHostileCompassMarkers",
                    CATEGORY_CLIENT,
                    showHostileCompassMarkers,
                    TIP
            );
            hostileCompassMarkerScanRadius = config.getInt(
                    "hostileCompassMarkerScanRadius",
                    CATEGORY_CLIENT,
                    hostileCompassMarkerScanRadius,
                    8,
                    128,
                    TIP
            );
            showHostileMapMarkers = config.getBoolean(
                    "showHostileMapMarkers",
                    CATEGORY_CLIENT,
                    showHostileMapMarkers,
                    TIP
            );
            hostileMapMarkerDisplayRadius = config.getInt(
                    "hostileMapMarkerDisplayRadius",
                    CATEGORY_CLIENT,
                    hostileMapMarkerDisplayRadius,
                    8,
                    128,
                    TIP
            );
            closeMapTerrainTransitionStartZoom = getBoundedDouble(
                    config, CATEGORY_CLIENT,
                    "closeMapTerrainTransitionStartZoom",
                    closeMapTerrainTransitionStartZoom,
                    -2.25D, 9.20D);
            closeMapTerrainTransitionEndZoom = getBoundedDouble(
                    config, CATEGORY_CLIENT,
                    "closeMapTerrainTransitionEndZoom",
                    closeMapTerrainTransitionEndZoom,
                    -2.2D, 9.25D);
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
                    TIP
            ).getStringList();
            customWaypointColors = config.get(
                    CATEGORY_CLIENT,
                    "customWaypointColors",
                    customWaypointColors,
                    TIP
            ).getStringList();
            customWaypointNotes = config.get(
                    CATEGORY_CLIENT,
                    "customWaypointNotes",
                    customWaypointNotes,
                    TIP
            ).getStringList();

            combatMarkerTrackingRadius = config.getInt(
                    "trackingRadius",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerTrackingRadius,
                    8,
                    128,
                    TIP
            );
            combatMarkerUpdateIntervalTicks = config.getInt(
                    "updateIntervalTicks",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerUpdateIntervalTicks,
                    1,
                    40,
                    TIP
            );
            combatMarkerDisengagementGraceTicks = config.getInt(
                    "disengagementGraceTicks",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerDisengagementGraceTicks,
                    0,
                    60,
                    TIP
            );
            combatMarkerDebugLogging = config.getBoolean(
                    "debugLogging",
                    CATEGORY_COMBAT_MARKERS,
                    combatMarkerDebugLogging,
                    TIP
            );
            fellowshipSharedAggroTracking = config.getBoolean(
                    "shareWithFellowship",
                    CATEGORY_COMBAT_MARKERS,
                    fellowshipSharedAggroTracking,
                    TIP
            );

            showFellowshipHud = config.getBoolean(
                    "showFellowshipHud",
                    CATEGORY_CLIENT,
                    showFellowshipHud,
                    TIP
            );
            linkShowFellowshipHud = config.getBoolean(
                    "linkShowFellowshipHud",
                    CATEGORY_CLIENT,
                    linkShowFellowshipHud,
                    TIP
            );
            fellowshipHudOffsetX = getHudPercent(
                    config, "fellowshipHudOffsetX",
                    fellowshipHudOffsetX, 0.0D, 100.0D);
            fellowshipHudOffsetY = getHudPercent(
                    config, "fellowshipHudOffsetY",
                    fellowshipHudOffsetY, 0.0D, 100.0D);
            fellowshipCompassMarkerFadeRadius = config.getInt(
                    "fellowshipCompassMarkerFadeRadius",
                    CATEGORY_CLIENT,
                    fellowshipCompassMarkerFadeRadius,
                    16,
                    2048,
                    TIP
            );
            fellowshipStatusUpdateIntervalTicks = config.getInt(
                    "statusUpdateIntervalTicks",
                    CATEGORY_FELLOWSHIP,
                    fellowshipStatusUpdateIntervalTicks,
                    2,
                    40,
                    TIP
            );
            fellowshipStatusHeartbeatTicks = config.getInt(
                    "statusHeartbeatTicks",
                    CATEGORY_FELLOWSHIP,
                    fellowshipStatusHeartbeatTicks,
                    20,
                    400,
                    TIP
            );
            fellowshipTrackingUpdateIntervalTicks = config.getInt(
                    "trackingUpdateIntervalTicks",
                    CATEGORY_FELLOWSHIP,
                    fellowshipTrackingUpdateIntervalTicks,
                    2,
                    40,
                    TIP
            );
            fellowshipTrackingHeartbeatTicks = config.getInt(
                    "trackingHeartbeatTicks",
                    CATEGORY_FELLOWSHIP,
                    fellowshipTrackingHeartbeatTicks,
                    20,
                    400,
                    TIP
            );
            enableSharedQuestProgress = config.getBoolean(
                    "enableSharedQuestProgress",
                    CATEGORY_FELLOWSHIP,
                    enableSharedQuestProgress,
                    TIP
            );
            fellowshipSharedQuestRadius = config.getInt(
                    "sharedQuestRadius",
                    CATEGORY_FELLOWSHIP,
                    fellowshipSharedQuestRadius,
                    1,
                    128,
                    TIP
            );

            showQuickLootHud = config.getBoolean(
                    "showQuickLootHud",
                    CATEGORY_CLIENT,
                    showQuickLootHud,
                    TIP
            );
            linkShowQuickLootHud = config.getBoolean(
                    "linkShowQuickLootHud",
                    CATEGORY_CLIENT,
                    linkShowQuickLootHud,
                    TIP
            );
            quickLootHudOffsetX = getHudPercent(
                    config, "quickLootHudOffsetX",
                    quickLootHudOffsetX,
                    0.0D, 100.0D);
            quickLootHudOffsetY = getHudPercent(
                    config, "quickLootHudOffsetY",
                    quickLootHudOffsetY, 0.0D, 100.0D);
            quickLootHudMaxRows = config.getInt(
                    "quickLootHudMaxRows",
                    CATEGORY_CLIENT,
                    quickLootHudMaxRows,
                    1,
                    12,
                    TIP
            );

            showQuestHud = config.getBoolean(
                    "showQuestHud",
                    CATEGORY_CLIENT,
                    showQuestHud,
                    TIP
            );
            linkShowQuestHud = config.getBoolean(
                    "linkShowQuestHud",
                    CATEGORY_CLIENT,
                    linkShowQuestHud,
                    TIP
            );
            questHudOffsetX = getHudPercent(
                    config, "questHudOffsetX",
                    questHudOffsetX, 0.0D, 100.0D);
            questHudOffsetY = getHudPercent(
                    config, "questHudOffsetY",
                    questHudOffsetY, 0.0D, 100.0D);
            questHudMaxTrackedQuests = config.getInt(
                    "questHudMaxTrackedQuests",
                    CATEGORY_CLIENT,
                    questHudMaxTrackedQuests,
                    1,
                    8,
                    TIP
            );
            questHudObjectiveLineCount = config.getInt(
                    "questHudObjectiveLineCount",
                    CATEGORY_CLIENT,
                    questHudObjectiveLineCount,
                    1,
                    3,
                    TIP
            );
            showQuestHudNotifications = config.getBoolean(
                    "showQuestHudNotifications",
                    CATEGORY_CLIENT,
                    showQuestHudNotifications,
                    TIP
            );
            showNativeLotrQuestTracker = config.getBoolean(
                    "showNativeLotrQuestTracker",
                    CATEGORY_CLIENT,
                    showNativeLotrQuestTracker,
                    TIP
            );
            enableQuestDialogue = config.getBoolean(
                    "enableQuestDialogue",
                    CATEGORY_CLIENT,
                    enableQuestDialogue,
                    TIP
            );
            notificationHudOffsetX = getHudPercent(
                    config, "notificationHudOffsetX",
                    notificationHudOffsetX, 0.0D, 100.0D);
            notificationHudOffsetY = getHudPercent(
                    config, "notificationHudOffsetY",
                    notificationHudOffsetY, 0.0D, 100.0D);
            showWorldQuestMarkers = config.getBoolean(
                    "showWorldQuestMarkers",
                    CATEGORY_CLIENT,
                    showWorldQuestMarkers,
                    TIP
            );
            showDiscoveredWorldMapMarkers = config.getBoolean(
                    "showDiscoveredWorldMapMarkers",
                    CATEGORY_CLIENT,
                    showDiscoveredWorldMapMarkers,
                    TIP
            );
            worldQuestMarkerMaxDistance = config.getInt(
                    "worldQuestMarkerMaxDistance",
                    CATEGORY_CLIENT,
                    worldQuestMarkerMaxDistance,
                    48,
                    1024,
                    TIP
            );
            showQuestChatFeedback = config.getBoolean(
                    "showQuestChatFeedback",
                    CATEGORY_CLIENT,
                    showQuestChatFeedback,
                    TIP
            );
            chatProximityRadius = config.getInt(
                    "proximityRadius",
                    CATEGORY_CHAT,
                    chatProximityRadius,
                    1,
                    512,
                    TIP
            );
            chatTypingIndicators = config.getBoolean(
                    "typingIndicators",
                    CATEGORY_CHAT,
                    chatTypingIndicators,
                    TIP
            );
            chatProfanityWords = config.getStringList(
                    "profanityWords",
                    CATEGORY_CHAT,
                    chatProfanityWords,
                    TIP
            );
            chatWelcomeLines = config.getStringList(
                    "welcomeLines",
                    CATEGORY_CHAT,
                    chatWelcomeLines,
                    TIP
            );
            serverAddress = config.getString(
                    "serverAddress",
                    CATEGORY_CHAT,
                    serverAddress,
                    TIP
            );
            chatAuditLogEnabled = config.getBoolean(
                    "auditLog",
                    CATEGORY_CHAT,
                    chatAuditLogEnabled,
                    TIP
            );
            chatAuditRetentionDays = config.getInt(
                    "auditRetentionDays",
                    CATEGORY_CHAT,
                    chatAuditRetentionDays,
                    1,
                    365,
                    TIP
            );
            chatHistoryPersisted = config.getBoolean(
                    "historyPersisted",
                    CATEGORY_CHAT,
                    chatHistoryPersisted,
                    TIP
            );
            chatHistoryPerChannel = config.getInt(
                    "historyPerChannel",
                    CATEGORY_CHAT,
                    chatHistoryPerChannel,
                    20,
                    1000,
                    TIP
            );
            chatPermissions = config.getStringList(
                    "permissions",
                    CATEGORY_ROLES,
                    chatPermissions,
                    TIP
            );
            chatRoles = config.getStringList(
                    "definitions",
                    CATEGORY_ROLES,
                    new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY},
                    TIP
            );
            chatRoleMembers = config.getStringList(
                    "members",
                    CATEGORY_ROLES,
                    chatRoleMembers,
                    TIP
            );
            chatChannelDefinitions = config.getStringList(
                    "definitions",
                    CATEGORY_CHANNELS,
                    new String[0],
                    TIP
            );
            chatChannelRoles = config.getStringList(
                    "gates",
                    CATEGORY_CHANNELS,
                    new String[] {ChatRoleConfig.DEFAULT_OPERATOR_GATE},
                    TIP
            );
            chatChannelIcons = config.getStringList(
                    "icons",
                    CATEGORY_CHANNELS,
                    new String[0],
                    TIP
            );
            installChatRoles();
            discordEnabled = config.getBoolean(
                    "enabled",
                    CATEGORY_DISCORD,
                    discordEnabled,
                    TIP
            );
            discordBotToken = config.getString(
                    "botToken",
                    CATEGORY_DISCORD,
                    discordBotToken,
                    TIP
            );
            discordPollIntervalSeconds = config.getInt(
                    "pollIntervalSeconds",
                    CATEGORY_DISCORD,
                    discordPollIntervalSeconds,
                    2,
                    60,
                    TIP
            );
            Property bindingsProperty = config.get(
                    CATEGORY_DISCORD,
                    "channelBindings",
                    new String[0],
                    TIP
            );
            discordChannelBindings = bindingsProperty.getStringList();
            discordAvatarUrlTemplate = config.getString(
                    "avatarUrlTemplate",
                    CATEGORY_DISCORD,
                    discordAvatarUrlTemplate,
                    TIP
            );
            discordJoinsAndLeaves = config.getBoolean(
                    "joinsAndLeaves",
                    CATEGORY_DISCORD,
                    discordJoinsAndLeaves,
                    TIP
            );
            discordDeathMessages = config.getBoolean(
                    "deathMessages",
                    CATEGORY_DISCORD,
                    discordDeathMessages,
                    TIP
            );
            discordAchievements = config.getBoolean(
                    "achievements",
                    CATEGORY_DISCORD,
                    discordAchievements,
                    TIP
            );
            discordChannelStatus = config.getBoolean(
                    "channelStatus",
                    CATEGORY_DISCORD,
                    discordChannelStatus,
                    TIP
            );
            discordChannelStatusIntervalSeconds = config.getInt(
                    "channelStatusIntervalSeconds",
                    CATEGORY_DISCORD,
                    discordChannelStatusIntervalSeconds,
                    60,
                    3600,
                    TIP
            );
            discordGateway = config.getBoolean(
                    "gateway",
                    CATEGORY_DISCORD,
                    discordGateway,
                    TIP
            );
            discordSlashCommands = config.getBoolean(
                    "slashCommands",
                    CATEGORY_DISCORD,
                    discordSlashCommands,
                    TIP
            );
            discordMemberList = config.getBoolean(
                    "memberList",
                    CATEGORY_DISCORD,
                    discordMemberList,
                    TIP
            );
            discordPingMembers = config.getBoolean(
                    "pingMembers",
                    CATEGORY_DISCORD,
                    discordPingMembers,
                    TIP
            );
            Property discordProfanityProperty = config.get(
                    CATEGORY_DISCORD, "profanityFilter", discordProfanityFilter, TIP);
            discordProfanityProperty.setValidValues(ChatProfanityMode.names());
            discordProfanityFilter = ChatProfanityMode.of(
                    discordProfanityProperty.getString(), ChatProfanityMode.OFF).name();
            enableChatEmojis = config.getBoolean(
                    "enableChatEmojis",
                    CATEGORY_CLIENT,
                    enableChatEmojis,
                    TIP
            );
            convertChatEmoticons = config.getBoolean(
                    "convertChatEmoticons",
                    CATEGORY_CLIENT,
                    convertChatEmoticons,
                    TIP
            );
            Property profanityProperty = config.get(
                    CATEGORY_CLIENT, "chatProfanityFilter", chatProfanityFilter, TIP);
            profanityProperty.setValidValues(ChatProfanityMode.names());
            chatProfanityFilter = ChatProfanityMode.of(
                    profanityProperty.getString(), ChatProfanityMode.SILLY).name();
            enableChatMessageGrouping = config.getBoolean(
                    "enableChatMessageGrouping",
                    CATEGORY_CLIENT,
                    enableChatMessageGrouping,
                    TIP
            );
            windowBackgroundBlur = config.getBoolean(
                    "windowBackgroundBlur",
                    CATEGORY_CLIENT,
                    windowBackgroundBlur,
                    TIP
            );
            enableChatPings = config.getBoolean(
                    "enableChatPings",
                    CATEGORY_CLIENT,
                    enableChatPings,
                    TIP
            );
            chatPingSound = config.getString(
                    "chatPingSound",
                    CATEGORY_CLIENT,
                    chatPingSound,
                    TIP
            );
            windowPrimaryColor = paletteName(config.getString(
                    "windowPrimaryColor",
                    CATEGORY_CLIENT,
                    windowPrimaryColor,
                    TIP,
                    LostTalesColors.paletteNames()
            ), DEFAULT_WINDOW_PRIMARY_COLOR);
            windowSecondaryColor = paletteName(config.getString(
                    "windowSecondaryColor",
                    CATEGORY_CLIENT,
                    windowSecondaryColor,
                    TIP,
                    LostTalesColors.paletteNames()
            ), DEFAULT_WINDOW_SECONDARY_COLOR);
            windowAccentColor = paletteName(config.getString(
                    "windowAccentColor",
                    CATEGORY_CLIENT,
                    windowAccentColor,
                    TIP,
                    LostTalesColors.paletteNames()
            ), DEFAULT_WINDOW_ACCENT_COLOR);
            chatMentionLineColor = paletteName(config.getString(
                    "chatMentionLineColor",
                    CATEGORY_CLIENT,
                    chatMentionLineColor,
                    TIP,
                    LostTalesColors.paletteNames()
            ), DEFAULT_CHAT_MENTION_LINE_COLOR);
            chatSelectedMentionColor = automaticOrPaletteName(config.getString(
                    "chatSelectedMentionColor",
                    CATEGORY_CLIENT,
                    chatSelectedMentionColor,
                    TIP,
                    automaticOrPaletteNames()
            ));
            chatReplyHighlightColor = paletteName(config.getString(
                    "chatReplyHighlightColor",
                    CATEGORY_CLIENT,
                    chatReplyHighlightColor,
                    TIP,
                    LostTalesColors.paletteNames()
            ), DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR);
            Property feedAlignmentProperty = config.get(
                    CATEGORY_CLIENT, "chatFeedAlignment", chatFeedAlignment, TIP);
            feedAlignmentProperty.setValidValues(CHAT_FEED_ALIGNMENTS);
            chatFeedAlignment = normalizeFeedAlignment(
                    feedAlignmentProperty.getString());
            chatSpeakerSize = readSize(config, "chatSpeakerSize",
                    chatSpeakerSize);
            chatFeedSpeakerSize = readSize(config, "chatFeedSpeakerSize",
                    chatFeedSpeakerSize);
            chatFeedMessageSize = readSize(config, "chatFeedMessageSize",
                    chatFeedMessageSize);
            chatQuoteSize = readSize(config, "chatQuoteSize", chatQuoteSize);
            chatFeedQuoteSize = readSize(config, "chatFeedQuoteSize",
                    chatFeedQuoteSize);
            chatFeedWidth = config.getInt("chatFeedWidth", CATEGORY_CLIENT,
                    chatFeedWidth, 80, 640, TIP);
            chatFeedLines = config.getInt("chatFeedLines", CATEGORY_CLIENT,
                    chatFeedLines, 1, 20, TIP);
            chatFeedSeconds = config.getInt("chatFeedSeconds", CATEGORY_CLIENT,
                    chatFeedSeconds, 2, 60, TIP);
            hideHudWithWindows = config.getBoolean(
                    "hideHudWithWindows",
                    CATEGORY_CLIENT,
                    hideHudWithWindows,
                    TIP
            );
            pinnedWindowOpacity = config.getInt(
                    "pinnedWindowOpacity",
                    CATEGORY_CLIENT,
                    pinnedWindowOpacity,
                    20,
                    100,
                    TIP
            );

            devSkinOverridePath = config.getString(
                    "devSkinOverridePath",
                    CATEGORY_CLIENT,
                    devSkinOverridePath,
                    TIP
            );
            devSkinOverrideBodyType = config.getString(
                    "devSkinOverrideBodyType",
                    CATEGORY_CLIENT,
                    devSkinOverrideBodyType,
                    TIP,
                    DEV_SKIN_BODY_TYPES
            );
            showSkinOverlays = config.getBoolean(
                    "showSkinOverlays",
                    CATEGORY_CLIENT,
                    showSkinOverlays,
                    TIP
            );
            chestPhysics = config.getBoolean(
                    "chestPhysics",
                    CATEGORY_CLIENT,
                    chestPhysics,
                    TIP
            );
            chestBounce = config.getFloat(
                    "chestBounce",
                    CATEGORY_CLIENT,
                    chestBounce, 0.0F, 1.0F,
                    TIP
            );
            chatHistoryLines = config.getInt(
                    "chatHistoryLines",
                    CATEGORY_CLIENT,
                    chatHistoryLines,
                    100,
                    5000,
                    TIP
            );
            sendChatTypingStatus = config.getBoolean(
                    "sendChatTypingStatus",
                    CATEGORY_CLIENT,
                    sendChatTypingStatus,
                    TIP
            );
            showChatTypingIndicators = config.getBoolean(
                    "showChatTypingIndicators",
                    CATEGORY_CLIENT,
                    showChatTypingIndicators,
                    TIP
            );
            enableNpcChatStyling = config.getBoolean(
                    "enableNpcChatStyling",
                    CATEGORY_CLIENT,
                    enableNpcChatStyling,
                    TIP
            );
            npcConversationsOpen = config.getInt(
                    "npcConversationsOpen",
                    CATEGORY_CLIENT,
                    npcConversationsOpen,
                    1,
                    10,
                    TIP
            );
            showChatSpeechBubbles = config.getBoolean(
                    "showChatSpeechBubbles",
                    CATEGORY_CLIENT,
                    showChatSpeechBubbles,
                    TIP
            );
            animations = config.getBoolean(
                    "animations", CATEGORY_CLIENT, animations,
                    TIP
            );
            animationSpeed = getBoundedDouble(
                    config, CATEGORY_CLIENT, "animationSpeed",
                    animationSpeed, 0.25D, 4.0D);
            reducedMotion = config.getBoolean(
                    "reducedMotion", CATEGORY_CLIENT, reducedMotion,
                    TIP
            );
            enableGuiBackground = config.getBoolean(
                    "enableGuiBackground", CATEGORY_CLIENT,
                    enableGuiBackground,
                    TIP
            );
            guiBackgroundOpacity = getBoundedDouble(
                    config, CATEGORY_CLIENT, "guiBackgroundOpacity",
                    guiBackgroundOpacity, 0.0D, 1.0D);
            guiAlwaysBlur = config.getBoolean(
                    "guiAlwaysBlur", CATEGORY_CLIENT,
                    guiAlwaysBlur,
                    TIP
            );
            enableGuiBackgroundBlur = config.getBoolean(
                    "enableGuiBackgroundBlur", CATEGORY_CLIENT,
                    enableGuiBackgroundBlur,
                    TIP
            );
            guiBlurStrength = getBoundedDouble(
                    config, CATEGORY_CLIENT, "guiBlurStrength",
                    guiBlurStrength, 0.0D, 8.0D);
            playQuestSounds = config.getBoolean(
                    "playQuestSounds",
                    CATEGORY_CLIENT,
                    playQuestSounds,
                    TIP
            );

            enableQuestPrerequisites = config.getBoolean(
                    "enableQuestPrerequisites",
                    CATEGORY_QUESTS,
                    enableQuestPrerequisites,
                    TIP
            );
            enableQuestRewards = config.getBoolean(
                    "enableQuestRewards",
                    CATEGORY_QUESTS,
                    enableQuestRewards,
                    TIP
            );
            allowQuestItemStarts = config.getBoolean(
                    "allowQuestItemStarts",
                    CATEGORY_QUESTS,
                    allowQuestItemStarts,
                    TIP
            );
            allowQuestInteractionStarts = config.getBoolean(
                    "allowQuestInteractionStarts",
                    CATEGORY_QUESTS,
                    allowQuestInteractionStarts,
                    TIP
            );
            autoRevealQuestMarkersOnStart = config.getBoolean(
                    "autoRevealQuestMarkersOnStart",
                    CATEGORY_QUESTS,
                    autoRevealQuestMarkersOnStart,
                    TIP
            );
            autoPinQuestOnStart = config.getBoolean(
                    "autoPinQuestOnStart",
                    CATEGORY_QUESTS,
                    autoPinQuestOnStart,
                    TIP
            );
            autoDiscoverNearbyMapMarkers = config.getBoolean(
                    "autoDiscoverNearbyMapMarkers",
                    CATEGORY_QUESTS,
                    autoDiscoverNearbyMapMarkers,
                    TIP
            );
            mapMarkerDiscoveryScanIntervalTicks = config.getInt(
                    "mapMarkerDiscoveryScanIntervalTicks",
                    CATEGORY_QUESTS,
                    mapMarkerDiscoveryScanIntervalTicks,
                    20,
                    200,
                    TIP
            );

            enableWaystoneRecipe = config.getBoolean(
                    "enableWaystoneRecipe",
                    CATEGORY_WAYSTONES,
                    enableWaystoneRecipe,
                    TIP
            );
            waystoneRecipeCornerIngredient = config.getString(
                    "waystoneRecipeCornerIngredient",
                    CATEGORY_WAYSTONES,
                    waystoneRecipeCornerIngredient,
                    TIP
            );
            waystoneRecipeEdgeIngredient = config.getString(
                    "waystoneRecipeEdgeIngredient",
                    CATEGORY_WAYSTONES,
                    waystoneRecipeEdgeIngredient,
                    TIP
            );
            waystoneRecipeCenterIngredient = config.getString(
                    "waystoneRecipeCenterIngredient",
                    CATEGORY_WAYSTONES,
                    waystoneRecipeCenterIngredient,
                    TIP
            );

            enableDynamicMissiveBoards = config.getBoolean(
                    "enableDynamicMissiveBoards",
                    CATEGORY_MISSIVES,
                    enableDynamicMissiveBoards,
                    TIP
            );
            missiveBoardMinAvailable = config.getInt(
                    "missiveBoardMinAvailable",
                    CATEGORY_MISSIVES,
                    missiveBoardMinAvailable,
                    0,
                    9,
                    TIP
            );
            missiveBoardMaxAvailable = config.getInt(
                    "missiveBoardMaxAvailable",
                    CATEGORY_MISSIVES,
                    missiveBoardMaxAvailable,
                    1,
                    9,
                    TIP
            );
            missiveBoardGenerationIntervalTicks = config.getInt(
                    "missiveBoardGenerationIntervalTicks",
                    CATEGORY_MISSIVES,
                    missiveBoardGenerationIntervalTicks,
                    1200,
                    240000,
                    TIP
            );
            missiveBoardMinGeneratedPerCycle = config.getInt(
                    "missiveBoardMinGeneratedPerCycle",
                    CATEGORY_MISSIVES,
                    missiveBoardMinGeneratedPerCycle,
                    1,
                    9,
                    TIP
            );
            missiveBoardMaxGeneratedPerCycle = config.getInt(
                    "missiveBoardMaxGeneratedPerCycle",
                    CATEGORY_MISSIVES,
                    missiveBoardMaxGeneratedPerCycle,
                    1,
                    9,
                    TIP
            );
            expireMissiveBoardNotices = config.getBoolean(
                    "expireMissiveBoardNotices",
                    CATEGORY_MISSIVES,
                    expireMissiveBoardNotices,
                    TIP
            );
            missiveBoardNoticeExpirationDays = config.getInt(
                    "missiveBoardNoticeExpirationDays",
                    CATEGORY_MISSIVES,
                    missiveBoardNoticeExpirationDays,
                    1,
                    30,
                    TIP
            );
            enableTimedMissives = config.getBoolean(
                    "enableTimedMissives",
                    CATEGORY_MISSIVES,
                    enableTimedMissives,
                    TIP
            );
            timedMissiveChancePercent = config.getInt(
                    "timedMissiveChancePercent",
                    CATEGORY_MISSIVES,
                    timedMissiveChancePercent,
                    0,
                    100,
                    TIP
            );
            timedMissiveMinDays = config.getInt(
                    "timedMissiveMinDays",
                    CATEGORY_MISSIVES,
                    timedMissiveMinDays,
                    1,
                    30,
                    TIP
            );
            timedMissiveMaxDays = config.getInt(
                    "timedMissiveMaxDays",
                    CATEGORY_MISSIVES,
                    timedMissiveMaxDays,
                    1,
                    30,
                    TIP
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
        if (linkShowFellowshipHud) {
            showFellowshipHud = showLostTalesHud;
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

    /**
     * Where a panel stands in the Default layout, as shares of the screen
     * {x, y}; null for a name no panel goes by. The HUD Placement page puts
     * one panel back there.
     */
    public static double[] defaultHudOffset(String element) {
        String key = normalizeHudElement(element);
        if ("compass".equals(key)) {
            return new double[] {50, 2};
        }
        if ("fellowship".equals(key)) {
            return new double[] {2, 18};
        }
        if ("quickloot".equals(key)) {
            return new double[] {62, 32};
        }
        if ("quest".equals(key)) {
            return new double[] {2, 38};
        }
        if ("notifications".equals(key)) {
            return new double[] {50, 35};
        }
        return null;
    }

    private static void applyHudPresetValues(String preset) {
        String key = normalizeHudPreset(preset);
        if (HUD_PRESET_DEFAULT.equals(key)) {
            for (String element : new String[] {"compass", "fellowship",
                    "quickloot", "quest", "notifications"}) {
                double[] place = defaultHudOffset(element);
                updateHudOffset(element, place[0], place[1]);
            }
            hudPlacementPreset = HUD_PRESET_DEFAULT;
        } else if (HUD_PRESET_LOTR_SAFE.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 12;
            fellowshipHudOffsetX = 2;
            fellowshipHudOffsetY = 28;
            quickLootHudOffsetX = 61;
            quickLootHudOffsetY = 34;
            questHudOffsetX = 2;
            questHudOffsetY = 52;
            setNotificationPresetOffsets(50, 32);
        } else if (HUD_PRESET_COMPACT.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 7;
            fellowshipHudOffsetX = 1;
            fellowshipHudOffsetY = 26;
            quickLootHudOffsetX = 67;
            quickLootHudOffsetY = 37;
            questHudOffsetX = 1;
            questHudOffsetY = 57;
            setNotificationPresetOffsets(50, 30);
        } else if (HUD_PRESET_MINIMAL.equals(key)) {
            compassHudOffsetX = 50;
            compassHudOffsetY = 4;
            fellowshipHudOffsetX = 1;
            fellowshipHudOffsetY = 18;
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
        } else if ("fellowship".equals(key)) {
            fellowshipHudOffsetX = x;
            fellowshipHudOffsetY = y;
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

    /** The HUD panels by the names the client file keeps their places under. */
    private static final java.util.List<String> HUD_ELEMENTS = java.util.Arrays.asList(
            "compass", "fellowship", "quickloot", "quest", "notifications");

    /** A panel's name as the client file keeps it; empty for a name no panel goes by. */
    public static String normalizeHudElement(String element) {
        String key = element == null ? "" : element.trim().toLowerCase(java.util.Locale.ROOT);
        return HUD_ELEMENTS.contains(key) ? key : "";
    }

    public static void clampHudOffsets() {
        compassHudOffsetX = clampPercent(compassHudOffsetX);
        compassHudOffsetY = clampPercent(compassHudOffsetY);
        fellowshipHudOffsetX = clampPercent(fellowshipHudOffsetX);
        fellowshipHudOffsetY = clampPercent(fellowshipHudOffsetY);
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
        config.getCategory(CATEGORY_FELLOWSHIP).setLanguageKey("losttales.config.category.fellowship");
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

    /**
     * A client option as the mod ships it, as the file writes it; null
     * before the first load, or for an option the client category does
     * not hold. What Settings' Default and Restore Defaults put back.
     */
    public static String shippedClientValue(String key) {
        Configuration definitions = shipped;
        if (definitions == null || !definitions.hasCategory(CATEGORY_CLIENT)) {
            return null;
        }
        Property property = definitions.getCategory(CATEGORY_CLIENT).get(key);
        return property == null ? null : property.getString();
    }

    /**
     * A number client option's bounds as it is defined, {min, max}; null
     * before the first load, or for an option with none. What a number's
     * row in Settings keeps within.
     */
    public static double[] shippedClientBounds(String key) {
        return LostTalesConfigDefinitions.bounds(shipped, CATEGORY_CLIENT, key);
    }

    /**
     * Sets every option that is saved to what its field holds now, in
     * {@code config}; {@link #save} writes nothing else.
     */
    static void writeCurrentValues(Configuration config) {
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
        config.get(CATEGORY_CHAT, "welcomeLines", chatWelcomeLines)
                .set(chatWelcomeLines);
        config.get(CATEGORY_CHAT, "serverAddress", serverAddress)
                .set(serverAddress);
        config.get(CATEGORY_CLIENT, "enableChatEmojis",
                enableChatEmojis).set(enableChatEmojis);
        config.get(CATEGORY_CLIENT, "convertChatEmoticons",
                convertChatEmoticons).set(convertChatEmoticons);
        config.get(CATEGORY_CLIENT, "enableChatMessageGrouping",
                enableChatMessageGrouping).set(enableChatMessageGrouping);
        config.get(CATEGORY_CLIENT, "windowBackgroundBlur",
                windowBackgroundBlur).set(windowBackgroundBlur);
        config.get(CATEGORY_CLIENT, "sendChatTypingStatus",
                sendChatTypingStatus).set(sendChatTypingStatus);
        config.get(CATEGORY_CLIENT, "showChatTypingIndicators",
                showChatTypingIndicators).set(showChatTypingIndicators);
        config.get(CATEGORY_CLIENT, "showChatSpeechBubbles",
                showChatSpeechBubbles).set(showChatSpeechBubbles);
        config.get(CATEGORY_CLIENT, "enableNpcChatStyling",
                enableNpcChatStyling).set(enableNpcChatStyling);
        config.get(CATEGORY_CLIENT, "npcConversationsOpen",
                npcConversationsOpen).set(npcConversationsOpen);
        config.get(CATEGORY_CLIENT, "enableChatPings",
                enableChatPings).set(enableChatPings);
        config.get(CATEGORY_CLIENT, "chatPingSound",
                chatPingSound).set(chatPingSound);
        config.get(CATEGORY_CLIENT, "windowPrimaryColor",
                windowPrimaryColor).set(windowPrimaryColor);
        config.get(CATEGORY_CLIENT, "windowSecondaryColor",
                windowSecondaryColor).set(windowSecondaryColor);
        config.get(CATEGORY_CLIENT, "windowAccentColor",
                windowAccentColor).set(windowAccentColor);
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
        writeSize(config, "chatSpeakerSize", chatSpeakerSize);
        writeSize(config, "chatFeedSpeakerSize", chatFeedSpeakerSize);
        writeSize(config, "chatFeedMessageSize", chatFeedMessageSize);
        writeSize(config, "chatQuoteSize", chatQuoteSize);
        writeSize(config, "chatFeedQuoteSize", chatFeedQuoteSize);
        config.get(CATEGORY_CLIENT, "chatFeedWidth", chatFeedWidth)
                .set(chatFeedWidth);
        config.get(CATEGORY_CLIENT, "chatFeedLines", chatFeedLines)
                .set(chatFeedLines);
        config.get(CATEGORY_CLIENT, "chatFeedSeconds", chatFeedSeconds)
                .set(chatFeedSeconds);
        Property profanityProperty = config.get(
                CATEGORY_CLIENT, "chatProfanityFilter", chatProfanityFilter);
        profanityProperty.set(chatProfanityFilter);
        profanityProperty.setValidValues(ChatProfanityMode.names());
        config.get(CATEGORY_CLIENT, "hideHudWithWindows",
                hideHudWithWindows).set(hideHudWithWindows);
        config.get(CATEGORY_CLIENT, "pinnedWindowOpacity",
                pinnedWindowOpacity).set(pinnedWindowOpacity);

        config.get(CATEGORY_CLIENT, "animations",
                animations).set(animations);
        config.get(CATEGORY_CLIENT, "animationSpeed",
                animationSpeed).set(animationSpeed);
        config.get(CATEGORY_CLIENT, "reducedMotion",
                reducedMotion).set(reducedMotion);
        config.get(CATEGORY_CLIENT, "enableGuiBackground",
                enableGuiBackground).set(enableGuiBackground);
        config.get(CATEGORY_CLIENT, "guiBackgroundOpacity",
                guiBackgroundOpacity).set(guiBackgroundOpacity);
        config.get(CATEGORY_CLIENT, "guiAlwaysBlur",
                guiAlwaysBlur).set(guiAlwaysBlur);
        config.get(CATEGORY_CLIENT, "enableGuiBackgroundBlur",
                enableGuiBackgroundBlur).set(enableGuiBackgroundBlur);
        config.get(CATEGORY_CLIENT, "guiBlurStrength",
                guiBlurStrength).set(guiBlurStrength);
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
        config.get(CATEGORY_COMBAT_MARKERS, "shareWithFellowship", fellowshipSharedAggroTracking).set(fellowshipSharedAggroTracking);
        config.get(CATEGORY_CLIENT, "showFellowshipHud", showFellowshipHud).set(showFellowshipHud);
        config.get(CATEGORY_CLIENT, "linkShowFellowshipHud", linkShowFellowshipHud).set(linkShowFellowshipHud);
        config.get(CATEGORY_CLIENT, "fellowshipHudOffsetX", fellowshipHudOffsetX).set(fellowshipHudOffsetX);
        config.get(CATEGORY_CLIENT, "fellowshipHudOffsetY", fellowshipHudOffsetY).set(fellowshipHudOffsetY);
        config.get(CATEGORY_FELLOWSHIP, "statusUpdateIntervalTicks", fellowshipStatusUpdateIntervalTicks).set(fellowshipStatusUpdateIntervalTicks);
        config.get(CATEGORY_FELLOWSHIP, "statusHeartbeatTicks", fellowshipStatusHeartbeatTicks).set(fellowshipStatusHeartbeatTicks);
        config.get(CATEGORY_FELLOWSHIP, "trackingUpdateIntervalTicks", fellowshipTrackingUpdateIntervalTicks).set(fellowshipTrackingUpdateIntervalTicks);
        config.get(CATEGORY_FELLOWSHIP, "trackingHeartbeatTicks", fellowshipTrackingHeartbeatTicks).set(fellowshipTrackingHeartbeatTicks);
        config.get(CATEGORY_FELLOWSHIP, "enableSharedQuestProgress", enableSharedQuestProgress).set(enableSharedQuestProgress);
        config.get(CATEGORY_FELLOWSHIP, "sharedQuestRadius", fellowshipSharedQuestRadius).set(fellowshipSharedQuestRadius);
        config.get(CATEGORY_CLIENT, "showQuickLootHud", showQuickLootHud).set(showQuickLootHud);
        config.get(CATEGORY_CLIENT, "linkShowQuickLootHud", linkShowQuickLootHud).set(linkShowQuickLootHud);
        config.get(CATEGORY_CLIENT, "quickLootHudOffsetX", quickLootHudOffsetX).set(quickLootHudOffsetX);
        config.get(CATEGORY_CLIENT, "quickLootHudOffsetY", quickLootHudOffsetY).set(quickLootHudOffsetY);
        config.get(CATEGORY_CLIENT, "quickLootHudMaxRows", quickLootHudMaxRows).set(quickLootHudMaxRows);
        config.get(CATEGORY_CLIENT, "showQuestHud", showQuestHud).set(showQuestHud);
        config.get(CATEGORY_CLIENT, "linkShowQuestHud", linkShowQuestHud).set(linkShowQuestHud);
        config.get(CATEGORY_CLIENT, "questHudOffsetX", questHudOffsetX).set(questHudOffsetX);
        config.get(CATEGORY_CLIENT, "questHudOffsetY", questHudOffsetY).set(questHudOffsetY);
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
        config.get(CATEGORY_CLIENT, "enableQuestDialogue", enableQuestDialogue).set(enableQuestDialogue);
        config.get(CATEGORY_CLIENT, "fellowshipCompassMarkerFadeRadius",
                fellowshipCompassMarkerFadeRadius).set(fellowshipCompassMarkerFadeRadius);
        config.get(CATEGORY_CLIENT, "chatHistoryLines", chatHistoryLines).set(chatHistoryLines);
        config.get(CATEGORY_CLIENT, "showSkinOverlays", showSkinOverlays).set(showSkinOverlays);
        config.get(CATEGORY_CLIENT, "chestPhysics", chestPhysics).set(chestPhysics);
        // A float is kept as its own words, as the float's read defines it.
        config.get(CATEGORY_CLIENT, "chestBounce", Float.toString(chestBounce))
                .set(Float.toString(chestBounce));
        config.get(CATEGORY_CLIENT, "devSkinOverridePath", devSkinOverridePath)
                .set(devSkinOverridePath);
        Property bodyTypeProperty = config.get(CATEGORY_CLIENT,
                "devSkinOverrideBodyType", devSkinOverrideBodyType);
        bodyTypeProperty.set(devSkinOverrideBodyType);
        bodyTypeProperty.setValidValues(DEV_SKIN_BODY_TYPES);
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

    /**
     * One of the chat's row sizes as the file names it, the shipped
     * value for anything else. The words a screen steps through are
     * named on the option itself, so it is a button rather than a text
     * box.
     */
    private static String readSize(Configuration config,
                                   String key, String shipped) {
        Property property = config.get(CATEGORY_CLIENT, key, shipped, TIP);
        property.setValidValues(CHAT_SIZES);
        return normalizeSize(property.getString(), shipped);
    }

    /** That option's current value, with its words, for the next save. */
    private static void writeSize(Configuration config,
                                  String key, String value) {
        Property property = config.get(CATEGORY_CLIENT, key, value);
        property.set(value);
        property.setValidValues(CHAT_SIZES);
    }

    /** The named size, or {@code shipped} when the file names no size. */
    public static String normalizeSize(String value, String shipped) {
        String normalized = value == null
                ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        for (int index = 0; index < CHAT_SIZES.length; index++) {
            if (CHAT_SIZES[index].equals(normalized)) {
                return CHAT_SIZES[index];
            }
        }
        return shipped;
    }

    /**
     * The feed alignment a config value names, case and surrounding space
     * aside: {@code LEFT}, {@code CENTRE} — {@code CENTER} is read as it —
     * or {@code RIGHT}, and the shipped {@code CENTRE} for anything else.
     */
    public static String normalizeFeedAlignment(String value) {
        String normalized = value == null
                ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("CENTER".equals(normalized)) {
            return "CENTRE";
        }
        return "LEFT".equals(normalized) || "RIGHT".equals(normalized)
                ? normalized : DEFAULT_CHAT_FEED_ALIGNMENT;
    }

    static double getHudPercent(
            Configuration config, String key, double defaultValue,
            double minimum, double maximum) {
        if (config.hasKey(CATEGORY_CLIENT, key)) {
            Property existing = config.getCategory(CATEGORY_CLIENT).get(key);
            if (existing != null
                    && existing.getType() != Property.Type.DOUBLE) {
                // An offset written as a whole number is made again as a
                // double, so it holds the fraction a drag leaves it at.
                defaultValue = existing.getDouble(defaultValue);
                config.getCategory(CATEGORY_CLIENT).remove(key);
            }
        }
        return getBoundedDouble(config, CATEGORY_CLIENT, key, defaultValue,
                minimum, maximum);
    }

    private static double getBoundedDouble(
            Configuration config, String key, double defaultValue,
            double minimum, double maximum) {
        return getBoundedDouble(config, CATEGORY_RANGED_COMBAT, key,
                defaultValue, minimum, maximum);
    }

    private static double getBoundedDouble(
            Configuration config, String category, String key,
            double defaultValue, double minimum, double maximum) {
        Property property = config.get(
                category, key, defaultValue,
                TIP, minimum, maximum);
        double value = property.getDouble(defaultValue);
        double bounded = Math.max(minimum, Math.min(maximum, value));
        if (bounded != value) {
            property.set(bounded);
        }
        return bounded;
    }
}
