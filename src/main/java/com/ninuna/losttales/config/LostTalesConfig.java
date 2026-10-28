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
import com.ninuna.losttales.chat.ChatCommandText;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelIconCatalog;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.chat.server.ChatWelcome;
import com.ninuna.losttales.util.LostTalesLanguages;
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
    public static final String CATEGORY_PROGRESSION = "progression";
    /** The chat roles and their assignments; a server file of its own. */
    public static final String CATEGORY_ROLES = "roles";
    /** The chat channels' gates and definitions; a server file of its own. */
    public static final String CATEGORY_CHANNELS = "channels";

    /** The panels where the player put them, in no layout. */
    public static final String HUD_LAYOUT_CUSTOM = "custom";
    /** The layout every panel starts in, and where Default Place puts one back. */
    public static final String HUD_LAYOUT_DEFAULT = "default";
    /** What a HUD layout's id is made of: lower-case letters, digits, - and _, at most 64. */
    public static final String HUD_LAYOUT_ID = "[a-z0-9_-]{1,64}";

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

    /** The HUD panels by the names the client file and the layout files keep their places under. */
    private static final List<String> HUD_ELEMENTS = Collections.unmodifiableList(
            Arrays.asList("compass", "fellowship", "quick_loot", "quest",
                    "notifications"));
    /**
     * The HUD layouts that loaded (MAP-11), by id in the order the HUD
     * Placement page lists them: where each puts every panel, {across,
     * down} for each panel of {@link #HUD_ELEMENTS} in turn, a share of
     * the room the panel's box can move in (MAP-10). The client reads them
     * from the layout files as the resources load; none before that, and
     * none on a dedicated server.
     */
    private static volatile Map<String, double[][]> hudLayouts =
            Collections.<String, double[][]>emptyMap();
    /** Whether the layout files were read yet. */
    private static volatile boolean hudLayoutsRead;
    /** Where a panel stands until a layout places it: centred. */
    private static final double HUD_PANEL_UNPLACED = 50.0D;

    public static boolean showLostTalesHud = true;
    public static String hudPanelLayout = HUD_LAYOUT_DEFAULT;

    public static boolean showCompassHud = true;
    public static double compassHudOffsetX = HUD_PANEL_UNPLACED;
    public static double compassHudOffsetY = HUD_PANEL_UNPLACED;
    public static int compassHudDisplayRadius = 90;
    public static boolean showStaticCompassMarkers = true;
    public static boolean showLotrWaypointCompassMarkers = true;
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
    public static double fellowshipHudOffsetX = HUD_PANEL_UNPLACED;
    public static double fellowshipHudOffsetY = HUD_PANEL_UNPLACED;
    public static int fellowshipCompassMarkerFadeRadius = 100;

    public static int fellowshipStatusUpdateIntervalTicks = 10;
    public static int fellowshipStatusHeartbeatTicks = 100;
    public static int fellowshipTrackingUpdateIntervalTicks = 10;
    public static int fellowshipTrackingHeartbeatTicks = 100;
    public static boolean enableSharedQuestProgress = true;
    public static int fellowshipSharedQuestRadius = 32;

    public static boolean showQuickLootHud = true;
    public static int quickLootHudMaxRows = 5;
    public static double quickLootHudOffsetX = HUD_PANEL_UNPLACED;
    public static double quickLootHudOffsetY = HUD_PANEL_UNPLACED;

    public static boolean showQuestTracker = true;
    public static double questTrackerHudOffsetX = HUD_PANEL_UNPLACED;
    public static double questTrackerHudOffsetY = HUD_PANEL_UNPLACED;
    public static int questTrackerMaxQuests = 4;
    public static int questTrackerObjectiveLineCount = 2;
    public static boolean showQuestBanners = true;
    public static boolean showNativeLotrQuestTracker = false;
    /**
     * Whether a quest offered by somebody is talked about in the Lost
     * Tales conversation screen. Off, a Middle-earth quest is offered on
     * LOTR's own screen, and a Lost Tales quest giver's words go to the
     * chat while their quest is taken or handed in on touch.
     */
    public static boolean enableQuestConversation = true;
    /** The one slot every passing notice shares: quest banners, discoveries, area names. */
    public static double notificationHudOffsetX = HUD_PANEL_UNPLACED;
    public static double notificationHudOffsetY = HUD_PANEL_UNPLACED;
    public static boolean showWorldLabels = true;
    public static boolean showDiscoveredWorldMapMarkers = true;
    public static int worldQuestMarkerMaxDistance = 256;

    public static boolean showQuestChatFeedback = true;
    public static boolean playQuestSounds = true;

    /**
     * How far a line said nearby carries, in blocks, by its range
     * (CHAT-37, {@code ChatSpeechRange}): whispered, talked, shouted.
     */
    public static int chatProximityWhisperRange = 8;
    public static int chatProximityTalkRange = 32;
    public static int chatProximityShoutRange = 96;
    /** The range a Proximity copy speaks at until its button picks another. */
    public static String chatSpeechRange = "talk";
    /** The words a size step by distance may take: three sizes, sizes and fainter, or off. */
    public static final String CHAT_DISTANCE_THREE_SIZES = "three_sizes";
    public static final String CHAT_DISTANCE_SIZES_AND_FAINTER = "sizes_and_fainter";
    public static final String CHAT_DISTANCE_OFF = "off";
    static final String[] CHAT_DISTANCE_SIZES = {CHAT_DISTANCE_THREE_SIZES,
            CHAT_DISTANCE_SIZES_AND_FAINTER, CHAT_DISTANCE_OFF};
    /** How a line said nearby shows how far its speaker stood. */
    public static String chatDistanceSizes = CHAT_DISTANCE_THREE_SIZES;
    /** The lines each whisper pair keeps past the history's total, as a channel keeps its floor. */
    public static int chatWhisperHistoryFloor = 50;
    /** The commands a mute holds besides the game's own, one name per line. */
    public static String[] chatMutedCommands = {"r", "reply", "pm", "m",
            "message", "whisper", "mail"};
    /** Client-only presentation preferences; neither affects recipients. */
    public static boolean enableChatEmojis = true;
    public static boolean convertChatEmoticons = true;
    /**
     * How the words on the chat's profanity list read on this client:
     * one of {@link ChatProfanityMode}'s names.
     */
    public static String chatProfanityFilter = ChatProfanityMode.SILLY.name();
    public static boolean enableChatMessageGrouping = true;
    /** Whether the account behind a character stands faint after its name on chat lines and in member lists; the card names it either way. */
    public static boolean chatShowAccounts = true;
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
    /** The edges the feed's lines may stand against. */
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
    /** The same row in the feed, a step over the feed's own words. */
    public static String chatFeedSpeakerSize = CHAT_SIZE_LARGER;
    /** What a message says, in the feed. */
    public static String chatFeedMessageSize = CHAT_SIZE_SMALLER;
    /** The row a reply opens with, in the open window. */
    public static String chatQuoteSize = CHAT_SIZE_SMALLER;
    /** The same row in the feed, as large as the feed's words. */
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
     * account skin, with the arms chosen. Empty path disables it.
     */
    public static String devSkinOverridePath = "";
    /** The override skin's arms, as the option names them. */
    public static final String[] DEV_SKIN_ARMS = {"broad", "slender"};
    public static String devSkinOverrideArms = DEV_SKIN_ARMS[0];
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
    /** Other mods' commands whose words the Server Log and an echo never repeat, one name per line. */
    public static String[] chatPrivateCommands = new String[0];
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
     * ({@code DiscordChannelLinks}); none in a fresh file, since a
     * link is made by a code with {@code /losttales discord link}.
     */
    public static String[] discordChannelLinks = new String[0];
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
    /** Least seconds between two topic writes; ten minutes at least, one of Discord's two edits kept for the stop. */
    public static int discordChannelStatusIntervalSeconds = 600;
    /** Connect the bot to Discord's gateway for instant relay and slash commands. */
    public static boolean discordGateway = true;
    public static boolean discordSlashCommands = true;
    /** List the members of the linked Discord channels, with their Discord status. */
    public static boolean discordMemberList = true;
    /** Let a player's line ping the Discord members its mentions name. */
    public static boolean discordPingMembers = true;
    /** How the profanity list's words read in what the bridge posts: a {@link ChatProfanityMode} name. */
    public static String discordProfanityFilter = ChatProfanityMode.OFF.name();
    /**
     * The language a link writes in on Discord unless one was chosen for
     * it, one the mod ships a lang file for ({@link LostTalesLanguages},
     * DISC-30); an unknown one reads as English.
     */
    public static String discordLanguage = LostTalesLanguages.ENGLISH;
    /** The most lines each linked Discord channel sends into the game in five seconds (DISC-31). */
    public static int discordLinesPerChannel = 40;

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
    /** Whether a rumour whose file names no marker points to the first place its quest names (QUEST-20). */
    public static boolean rumoursPointToQuestPlaces = true;
    public static boolean autoTrackQuestOnStart = true;
    public static boolean autoDiscoverNearbyMapMarkers = true;
    public static int mapMarkerDiscoveryScanIntervalTicks = 40;
    /**
     * World quests started on the server's real clock (QUEST-36), one
     * line each: {@code <quest id> <weekday> <HH:MM>} or
     * {@code <quest id> every <days> days <HH:MM>}.
     */
    public static String[] worldQuestSchedules = new String[0];
    /** The quests whose sealed starters every new character carries (QUEST-29); empty for none. */
    public static String newCharacterStarterQuests =
            "losttales:tutorial/starter_note,losttales:path/survivalist/first_sparks";

    public static boolean enableWaystoneRecipe = true;
    public static String waystoneRecipeCornerIngredient =
            "minecraft:stonebrick";
    public static String waystoneRecipeEdgeIngredient = "ore:ingotGold";
    public static String waystoneRecipeCenterIngredient =
            "minecraft:ender_pearl";

    /** Experience from skill level 1 to 2 (PROG-03). */
    public static double skillCurveFirstLevel = 100.0D;
    /** How much more each skill level needs than the one before, in percent (PROG-03). */
    public static double skillCurveGrowthPercent = 5.0D;
    /** The progress from character level 1 to 2 less one step (PROG-04). */
    public static int characterLevelBase = 75;
    /** How much more each character level needs than the one before (PROG-04). */
    public static int characterLevelStep = 25;
    /** Perk points each character level gives (PROG-06). */
    public static int perkPointsPerLevel = 1;
    /** Whether other players count toward the combat skills (PROG-02). */
    public static boolean playersCountForCombat = false;
    /** How many repeats of one act on one thing count in full (PROG-08). */
    public static int repeatsCountedInFull = 10;
    /** How much less each repeat past those counts, in percent (PROG-08). */
    public static double repeatShrinkPercent = 10.0D;
    /** The least share a repeat still counts for, in percent (PROG-08). */
    public static double repeatFloorPercent = 10.0D;
    /** Seconds after which one repeat is forgotten (PROG-08). */
    public static int repeatRecoverySeconds = 60;
    /** The most experience one skill gains in an hour; 0 for no cap (PROG-08). */
    public static int skillHourlyCap = 0;
    /** Whether what a finished quest earns by its size goes past the hourly cap (PROG-08). */
    public static boolean finishedQuestsPassHourlyCap = true;
    /** Whether what a first find earns goes past the hourly cap (PROG-08). */
    public static boolean firstFindsPassHourlyCap = true;
    /** Whether a foe a Soft Step wearer strikes picks them whatever the range (PROG-17). */
    public static boolean softStepEndsOnStrike = true;
    /** LOTR coins the first reset at a statue costs a character (PROG-12). */
    public static int statueFirstPrice = 100;
    /** What each later reset of the same character costs, times the one before (PROG-12). */
    public static double statuePriceFactor = 2.0D;
    /** An item a reset also takes, as an item name; empty for none (PROG-12). */
    public static String statueItem = "";
    /** How much more speed four light pieces give, in percent (PROG-18). */
    public static double lightSetSpeedPercent = 5.0D;
    /** The chance four heavy pieces give that a blow does not push, in percent (PROG-18). */
    public static double heavySetKnockbackPercent = 20.0D;
    /** How much a heavy helmet or pair of boots slows, in percent (CONTENT-06). */
    public static double heavyHelmetBootsSlowPercent = 2.5D;
    /** How much a heavy chestplate or pair of leggings slows, in percent (CONTENT-06). */
    public static double heavyChestLegsSlowPercent = 5.0D;

    public static boolean enableMissiveBoardRecipe = true;
    public static String missiveBoardRecipeCornerIngredient = "ore:plankWood";
    public static String missiveBoardRecipeEdgeIngredient = "ore:stickWood";
    public static String missiveBoardRecipeCenterIngredient = "minecraft:paper";

    public static boolean enableMissiveBoards = true;
    /** Whether boards post from the bundled missive templates beside a server's own (QUEST-42). */
    public static boolean bundledMissiveTemplates = true;
    public static int missiveBoardMinAvailable = 5;
    public static int missiveBoardMaxAvailable = 9;
    public static int missiveBoardGenerationIntervalTicks = 36000;
    public static int missiveBoardMinPostedPerCycle = 1;
    public static int missiveBoardMaxPostedPerCycle = 3;
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
    /** Inactivity, in seconds, before each cooldown stage steps back, from the first stage up. */
    public static int[] characterSwitchDecaySeconds =
            new int[] {3600, 10800, 21600, 43200, 86400};
    public static int characterSwitchCombatGraceSeconds = 20;
    public static int characterSwitchTeleportGraceSeconds = 5;
    public static int characterSwitchStableGroundTicks = 20;
    public static double characterSwitchTeleportDistancePerTick = 16.0D;
    public static int characterStateMaxSnapshotBytes = 2 * 1024 * 1024;
    public static int characterStateCheckpointIntervalSeconds = 300;
    public static int characterStateCheckpointPlayersPerTick = 1;
    public static int characterDeletionRetentionDays = 30;
    /** Whether a worn ring drops at death with the player's things (ACC-05). */
    public static boolean ringDropsAtDeath = true;
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
            ringDropsAtDeath = config.getBoolean(
                    "ringDropsAtDeath",
                    CATEGORY_CHARACTERS,
                    ringDropsAtDeath,
                    TIP
            );
            sanitizeCharacterSwitchOptions();

            showLostTalesHud = config.getBoolean(
                    "showLostTalesHud",
                    CATEGORY_CLIENT,
                    showLostTalesHud,
                    TIP
            );
            Property hudLayoutProperty = config.get(
                    CATEGORY_CLIENT,
                    "hudPanelLayout",
                    hudPanelLayout,
                    TIP
            );
            if (hudLayoutsRead) {
                hudLayoutProperty.setValidValues(hudPresetValues());
            }
            // Kept as written until the layout files are read: whether it
            // names one that loads is told then (useHudLayouts).
            hudPanelLayout = normalizeHudLayout(hudLayoutProperty.getString());

            showCompassHud = config.getBoolean(
                    "showCompassHud",
                    CATEGORY_CLIENT,
                    showCompassHud,
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

            showQuestTracker = config.getBoolean(
                    "showQuestTracker",
                    CATEGORY_CLIENT,
                    showQuestTracker,
                    TIP
            );
            questTrackerHudOffsetX = getHudPercent(
                    config, "questTrackerHudOffsetX",
                    questTrackerHudOffsetX, 0.0D, 100.0D);
            questTrackerHudOffsetY = getHudPercent(
                    config, "questTrackerHudOffsetY",
                    questTrackerHudOffsetY, 0.0D, 100.0D);
            questTrackerMaxQuests = config.getInt(
                    "questTrackerMaxQuests",
                    CATEGORY_CLIENT,
                    questTrackerMaxQuests,
                    1,
                    8,
                    TIP
            );
            questTrackerObjectiveLineCount = config.getInt(
                    "questTrackerObjectiveLineCount",
                    CATEGORY_CLIENT,
                    questTrackerObjectiveLineCount,
                    1,
                    3,
                    TIP
            );
            showQuestBanners = config.getBoolean(
                    "showQuestBanners",
                    CATEGORY_CLIENT,
                    showQuestBanners,
                    TIP
            );
            showNativeLotrQuestTracker = config.getBoolean(
                    "showNativeLotrQuestTracker",
                    CATEGORY_CLIENT,
                    showNativeLotrQuestTracker,
                    TIP
            );
            enableQuestConversation = config.getBoolean(
                    "enableQuestConversation",
                    CATEGORY_CLIENT,
                    enableQuestConversation,
                    TIP
            );
            notificationHudOffsetX = getHudPercent(
                    config, "notificationHudOffsetX",
                    notificationHudOffsetX, 0.0D, 100.0D);
            notificationHudOffsetY = getHudPercent(
                    config, "notificationHudOffsetY",
                    notificationHudOffsetY, 0.0D, 100.0D);
            showWorldLabels = config.getBoolean(
                    "showWorldLabels",
                    CATEGORY_CLIENT,
                    showWorldLabels,
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
            chatProximityWhisperRange = config.getInt(
                    "proximityWhisperRange",
                    CATEGORY_CHAT,
                    chatProximityWhisperRange,
                    1,
                    512,
                    TIP
            );
            chatProximityTalkRange = config.getInt(
                    "proximityTalkRange",
                    CATEGORY_CHAT,
                    chatProximityTalkRange,
                    1,
                    512,
                    TIP
            );
            chatProximityShoutRange = config.getInt(
                    "proximityShoutRange",
                    CATEGORY_CHAT,
                    chatProximityShoutRange,
                    1,
                    512,
                    TIP
            );
            sanitizeSpeechRanges(config);
            chatWhisperHistoryFloor = config.getInt(
                    "whisperHistoryFloor",
                    CATEGORY_CHAT,
                    chatWhisperHistoryFloor,
                    0,
                    200,
                    TIP
            );
            chatMutedCommands = config.getStringList(
                    "mutedCommands",
                    CATEGORY_CHAT,
                    chatMutedCommands,
                    TIP
            );
            Property speechRangeProperty = config.get(
                    CATEGORY_CLIENT, "chatSpeechRange", chatSpeechRange, TIP);
            speechRangeProperty.setValidValues(
                    com.ninuna.losttales.chat.ChatSpeechRange.ids());
            chatSpeechRange = com.ninuna.losttales.chat.ChatSpeechRange.of(
                    speechRangeProperty.getString(),
                    com.ninuna.losttales.chat.ChatSpeechRange.TALK).id;
            Property distanceProperty = config.get(
                    CATEGORY_CLIENT, "chatDistanceSizes", chatDistanceSizes, TIP);
            distanceProperty.setValidValues(CHAT_DISTANCE_SIZES);
            chatDistanceSizes = normalizeDistanceSizes(distanceProperty.getString());
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
            chatPrivateCommands = config.getStringList(
                    "privateCommands",
                    CATEGORY_CHAT,
                    chatPrivateCommands,
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
            Property linksProperty = config.get(
                    CATEGORY_DISCORD,
                    "channelLinks",
                    new String[0],
                    TIP
            );
            discordChannelLinks = linksProperty.getStringList();
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
                    600,
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
            String discordLanguageRead = config.getString(
                    "language",
                    CATEGORY_DISCORD,
                    discordLanguage,
                    TIP
            );
            discordLanguage = LostTalesLanguages.resolve(discordLanguageRead,
                    CATEGORY_DISCORD + ".language");
            if (!discordLanguage.equals(discordLanguageRead)) {
                // Spelled the game's way, or English for a language the
                // mod ships no file for; the file is put right.
                config.getCategory(CATEGORY_DISCORD).get("language").set(discordLanguage);
            }
            discordLinesPerChannel = config.getInt(
                    "linesPerChannel",
                    CATEGORY_DISCORD,
                    discordLinesPerChannel,
                    1,
                    256,
                    TIP
            );
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
            chatShowAccounts = config.getBoolean(
                    "chatShowAccounts",
                    CATEGORY_CLIENT,
                    chatShowAccounts,
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
            devSkinOverrideArms = config.getString(
                    "devSkinOverrideArms",
                    CATEGORY_CLIENT,
                    devSkinOverrideArms,
                    TIP,
                    DEV_SKIN_ARMS
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
            rumoursPointToQuestPlaces = config.getBoolean(
                    "rumoursPointToQuestPlaces",
                    CATEGORY_QUESTS,
                    rumoursPointToQuestPlaces,
                    TIP
            );
            autoTrackQuestOnStart = config.getBoolean(
                    "autoTrackQuestOnStart",
                    CATEGORY_QUESTS,
                    autoTrackQuestOnStart,
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
            newCharacterStarterQuests = config.getString(
                    "newCharacterStarterQuests",
                    CATEGORY_QUESTS,
                    newCharacterStarterQuests,
                    TIP
            );
            worldQuestSchedules = config.get(
                    CATEGORY_QUESTS,
                    "worldQuestSchedules",
                    worldQuestSchedules,
                    TIP
            ).getStringList();

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

            skillCurveFirstLevel = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "skillCurveFirstLevel",
                    skillCurveFirstLevel, 1.0D, 100000.0D);
            skillCurveGrowthPercent = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "skillCurveGrowthPercent",
                    skillCurveGrowthPercent, 0.0D, 50.0D);
            characterLevelBase = config.getInt("characterLevelBase",
                    CATEGORY_PROGRESSION, characterLevelBase, 1, 100000, TIP);
            characterLevelStep = config.getInt("characterLevelStep",
                    CATEGORY_PROGRESSION, characterLevelStep, 0, 100000, TIP);
            perkPointsPerLevel = config.getInt("perkPointsPerLevel",
                    CATEGORY_PROGRESSION, perkPointsPerLevel, 0, 10, TIP);
            playersCountForCombat = config.getBoolean("playersCountForCombat",
                    CATEGORY_PROGRESSION, playersCountForCombat, TIP);
            repeatsCountedInFull = config.getInt("repeatsCountedInFull",
                    CATEGORY_PROGRESSION, repeatsCountedInFull, 0, 1000, TIP);
            repeatShrinkPercent = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "repeatShrinkPercent",
                    repeatShrinkPercent, 0.0D, 100.0D);
            repeatFloorPercent = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "repeatFloorPercent",
                    repeatFloorPercent, 0.0D, 100.0D);
            repeatRecoverySeconds = config.getInt("repeatRecoverySeconds",
                    CATEGORY_PROGRESSION, repeatRecoverySeconds, 1, 86400, TIP);
            skillHourlyCap = config.getInt("skillHourlyCap",
                    CATEGORY_PROGRESSION, skillHourlyCap, 0, 1000000, TIP);
            finishedQuestsPassHourlyCap = config.getBoolean("finishedQuestsPassHourlyCap",
                    CATEGORY_PROGRESSION, finishedQuestsPassHourlyCap, TIP);
            firstFindsPassHourlyCap = config.getBoolean("firstFindsPassHourlyCap",
                    CATEGORY_PROGRESSION, firstFindsPassHourlyCap, TIP);
            softStepEndsOnStrike = config.getBoolean("softStepEndsOnStrike",
                    CATEGORY_PROGRESSION, softStepEndsOnStrike, TIP);
            statueFirstPrice = config.getInt("statueFirstPrice",
                    CATEGORY_PROGRESSION, statueFirstPrice, 0, 1000000, TIP);
            statuePriceFactor = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "statuePriceFactor",
                    statuePriceFactor, 1.0D, 10.0D);
            statueItem = config.getString("statueItem",
                    CATEGORY_PROGRESSION, statueItem, TIP);
            lightSetSpeedPercent = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "lightSetSpeedPercent",
                    lightSetSpeedPercent, 0.0D, 50.0D);
            heavySetKnockbackPercent = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "heavySetKnockbackPercent",
                    heavySetKnockbackPercent, 0.0D, 100.0D);
            heavyHelmetBootsSlowPercent = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "heavyHelmetBootsSlowPercent",
                    heavyHelmetBootsSlowPercent, 0.0D, 25.0D);
            heavyChestLegsSlowPercent = getBoundedDouble(config,
                    CATEGORY_PROGRESSION, "heavyChestLegsSlowPercent",
                    heavyChestLegsSlowPercent, 0.0D, 25.0D);

            enableMissiveBoardRecipe = config.getBoolean(
                    "enableMissiveBoardRecipe",
                    CATEGORY_MISSIVES,
                    enableMissiveBoardRecipe,
                    TIP
            );
            missiveBoardRecipeCornerIngredient = config.getString(
                    "missiveBoardRecipeCornerIngredient",
                    CATEGORY_MISSIVES,
                    missiveBoardRecipeCornerIngredient,
                    TIP
            );
            missiveBoardRecipeEdgeIngredient = config.getString(
                    "missiveBoardRecipeEdgeIngredient",
                    CATEGORY_MISSIVES,
                    missiveBoardRecipeEdgeIngredient,
                    TIP
            );
            missiveBoardRecipeCenterIngredient = config.getString(
                    "missiveBoardRecipeCenterIngredient",
                    CATEGORY_MISSIVES,
                    missiveBoardRecipeCenterIngredient,
                    TIP
            );

            enableMissiveBoards = config.getBoolean(
                    "enableMissiveBoards",
                    CATEGORY_MISSIVES,
                    enableMissiveBoards,
                    TIP
            );
            bundledMissiveTemplates = config.getBoolean(
                    "bundledMissiveTemplates",
                    CATEGORY_MISSIVES,
                    bundledMissiveTemplates,
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
            missiveBoardMinPostedPerCycle = config.getInt(
                    "missiveBoardMinPostedPerCycle",
                    CATEGORY_MISSIVES,
                    missiveBoardMinPostedPerCycle,
                    1,
                    9,
                    TIP
            );
            missiveBoardMaxPostedPerCycle = config.getInt(
                    "missiveBoardMaxPostedPerCycle",
                    CATEGORY_MISSIVES,
                    missiveBoardMaxPostedPerCycle,
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

            followHudLayout();
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
        // Other mods' commands the server keeps private, beside the ones
        // the mod knows, sent to every client with its chat access.
        List<String> commandWarnings = new ArrayList<String>();
        ChatCommandText.installServerNames(ChatCommandText.parseNames(
                chatPrivateCommands, commandWarnings));
        // The commands a mute holds besides the game's own; a login or a
        // password command never stands on that list.
        ChatCommandText.installMutedNames(ChatCommandText.parseMutedNames(
                chatMutedCommands, commandWarnings));
        for (String warning : commandWarnings) {
            warnings.warn(warning);
        }
        // The welcome lines past the most a welcome sends.
        List<String> welcomeWarnings = new ArrayList<String>();
        ChatWelcome.lines(chatWelcomeLines, welcomeWarnings);
        for (String warning : welcomeWarnings) {
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

    /** Shows or hides the HUD; every panel follows it, each keeping its own switch (MAP-15). */
    public static void setShowLostTalesHud(boolean show) {
        showLostTalesHud = show;
        save();
    }

    public static boolean applyHudLayout(String preset) {
        String key = normalizeHudLayout(preset);
        if (HUD_LAYOUT_CUSTOM.equals(key)) {
            if (!isCustomHudPreset(preset)) {
                return false;
            }
            hudPanelLayout = HUD_LAYOUT_CUSTOM;
            save();
            return true;
        }

        if (!isKnownHudLayout(key)) {
            return false;
        }

        hudPanelLayout = key;
        applyHudLayoutValues(key);
        clampHudOffsets();
        save();
        return true;
    }

    /** The HUD panels by the names the client file and the layout files keep their places under. */
    public static List<String> hudElements() {
        return HUD_ELEMENTS;
    }

    /**
     * Takes the HUD layouts the client read (MAP-11), by id in the order
     * the page lists them, each {across, down} for every panel of
     * {@link #hudElements} in turn; a layout of another shape is passed
     * by. The chosen layout puts the panels in its places again; a chosen
     * layout that did not load leaves them where they stand, as the
     * player's own, and says so in the log.
     */
    public static synchronized void useHudLayouts(Map<String, double[][]> layouts) {
        Map<String, double[][]> taken = new java.util.LinkedHashMap<String, double[][]>();
        if (layouts != null) {
            for (Map.Entry<String, double[][]> layout : layouts.entrySet()) {
                double[][] places = copyHudPlaces(layout.getValue());
                if (layout.getKey() != null && places != null) {
                    taken.put(layout.getKey(), places);
                }
            }
        }
        hudLayouts = Collections.unmodifiableMap(taken);
        hudLayoutsRead = true;
        if (followHudLayout()) {
            save();
        }
    }

    /** Forgets the HUD layouts, as before the files are read; for tests. */
    static synchronized void forgetHudLayouts() {
        hudLayouts = Collections.<String, double[][]>emptyMap();
        hudLayoutsRead = false;
    }

    /** The ids of the HUD layouts that loaded, in the order the page lists them. */
    public static List<String> hudLayoutIds() {
        return new ArrayList<String>(hudLayouts.keySet());
    }

    /** What the layout option takes: custom, then every layout that loaded. */
    private static String[] hudPresetValues() {
        List<String> values = new ArrayList<String>();
        values.add(HUD_LAYOUT_CUSTOM);
        values.addAll(hudLayouts.keySet());
        return values.toArray(new String[values.size()]);
    }

    /**
     * Puts the panels where the chosen layout has them. A chosen layout
     * that did not load turns the layout custom, the panels where they
     * stand; before the layout files are read nothing moves. Answers
     * whether anything changed.
     */
    private static boolean followHudLayout() {
        if (HUD_LAYOUT_CUSTOM.equals(hudPanelLayout) || !hudLayoutsRead) {
            return false;
        }
        double[][] places = hudLayouts.get(hudPanelLayout);
        if (places == null) {
            com.ninuna.losttales.util.LostTalesLog.warning(
                    "The HUD layout %s the client file chooses did not load;"
                            + " the panels stay where they stand, as custom",
                    hudPanelLayout);
            hudPanelLayout = HUD_LAYOUT_CUSTOM;
            return true;
        }
        boolean moved = false;
        for (int panel = 0; panel < places.length; panel++) {
            double[] now = hudPlace(HUD_ELEMENTS.get(panel));
            moved |= now[0] != places[panel][0] || now[1] != places[panel][1];
        }
        applyHudLayoutValues(hudPanelLayout);
        return moved;
    }

    /** A layout's places copied, or null for one that does not place every panel on both axes. */
    private static double[][] copyHudPlaces(double[][] places) {
        if (places == null || places.length != HUD_ELEMENTS.size()) {
            return null;
        }
        double[][] copied = new double[places.length][];
        for (int panel = 0; panel < places.length; panel++) {
            if (places[panel] == null || places[panel].length != 2) {
                return null;
            }
            copied[panel] = places[panel].clone();
        }
        return copied;
    }

    /**
     * Where a panel stands in the Default layout, {across, down}, each a
     * share of the room its box can move in; null for a name no panel
     * goes by, or while no Default layout loaded. The HUD Placement page
     * puts one panel back there.
     */
    public static double[] defaultHudOffset(String element) {
        int panel = HUD_ELEMENTS.indexOf(normalizeHudElement(element));
        double[][] places = hudLayouts.get(HUD_LAYOUT_DEFAULT);
        return panel < 0 || places == null ? null : places[panel].clone();
    }

    /**
     * Where a layout puts every panel, {across, down} for each panel of
     * {@link #hudElements} in turn; null for custom or a layout that did
     * not load.
     */
    public static double[][] hudLayoutPlaces(String preset) {
        double[][] places = hudLayouts.get(normalizeHudLayout(preset));
        return places == null ? null : copyHudPlaces(places);
    }

    private static void applyHudLayoutValues(String preset) {
        double[][] places = hudLayoutPlaces(preset);
        if (places == null) {
            return;
        }
        for (int panel = 0; panel < places.length; panel++) {
            setHudPlace(HUD_ELEMENTS.get(panel), places[panel][0],
                    places[panel][1]);
        }
    }

    /**
     * A layout's name as the client file keeps it, trimmed and in lower
     * case; custom for anything no layout file could take as its id.
     */
    public static String normalizeHudLayout(String preset) {
        if (preset == null) {
            return HUD_LAYOUT_CUSTOM;
        }
        String key = preset.trim().toLowerCase(java.util.Locale.ROOT);
        return key.matches(HUD_LAYOUT_ID) ? key : HUD_LAYOUT_CUSTOM;
    }

    public static boolean isCustomHudPreset(String preset) {
        if (preset == null) {
            return false;
        }
        return HUD_LAYOUT_CUSTOM.equals(preset.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Whether a layout of that name loaded. */
    public static boolean isKnownHudLayout(String preset) {
        return hudLayouts.containsKey(normalizeHudLayout(preset));
    }

    public static boolean setHudOffset(String element,
                                       double x, double y) {
        boolean updated = updateHudOffset(element, x, y);
        if (updated) {
            save();
        }
        return updated;
    }

    /**
     * Moves a panel and leaves the layout the panels' own, without writing
     * the file: the live preview, every frame of a drag.
     */
    public static boolean updateHudOffset(String element,
                                          double x, double y) {
        String key = normalizeHudElement(element);
        if (key.length() == 0) {
            return false;
        }
        hudPanelLayout = HUD_LAYOUT_CUSTOM;
        setHudPlace(key, x, y);
        return true;
    }

    /** Puts a panel of the client file's names at {@code across}, {@code down}, each held between 0 and 100. */
    private static void setHudPlace(String key, double across, double down) {
        double x = clampPercent(across);
        double y = clampPercent(down);
        if ("compass".equals(key)) {
            compassHudOffsetX = x;
            compassHudOffsetY = y;
        } else if ("fellowship".equals(key)) {
            fellowshipHudOffsetX = x;
            fellowshipHudOffsetY = y;
        } else if ("quick_loot".equals(key)) {
            quickLootHudOffsetX = x;
            quickLootHudOffsetY = y;
        } else if ("quest".equals(key)) {
            questTrackerHudOffsetX = x;
            questTrackerHudOffsetY = y;
        } else if ("notifications".equals(key)) {
            notificationHudOffsetX = x;
            notificationHudOffsetY = y;
        }
    }

    /** Where a panel of the client file's names stands, {across, down}. */
    private static double[] hudPlace(String key) {
        if ("compass".equals(key)) {
            return new double[] {compassHudOffsetX, compassHudOffsetY};
        }
        if ("fellowship".equals(key)) {
            return new double[] {fellowshipHudOffsetX, fellowshipHudOffsetY};
        }
        if ("quick_loot".equals(key)) {
            return new double[] {quickLootHudOffsetX, quickLootHudOffsetY};
        }
        if ("quest".equals(key)) {
            return new double[] {questTrackerHudOffsetX, questTrackerHudOffsetY};
        }
        return new double[] {notificationHudOffsetX, notificationHudOffsetY};
    }

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
        questTrackerHudOffsetX = clampPercent(questTrackerHudOffsetX);
        questTrackerHudOffsetY = clampPercent(questTrackerHudOffsetY);
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
        config.getCategory(CATEGORY_PROGRESSION).setLanguageKey(
                "losttales.config.category.progression");
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
        config.get(CATEGORY_CHAT, "proximityWhisperRange",
                chatProximityWhisperRange).set(chatProximityWhisperRange);
        config.get(CATEGORY_CHAT, "proximityTalkRange",
                chatProximityTalkRange).set(chatProximityTalkRange);
        config.get(CATEGORY_CHAT, "proximityShoutRange",
                chatProximityShoutRange).set(chatProximityShoutRange);
        config.get(CATEGORY_CHAT, "whisperHistoryFloor",
                chatWhisperHistoryFloor).set(chatWhisperHistoryFloor);
        config.get(CATEGORY_CHAT, "mutedCommands", chatMutedCommands)
                .set(chatMutedCommands);
        Property speechRangeProperty = config.get(
                CATEGORY_CLIENT, "chatSpeechRange", chatSpeechRange);
        speechRangeProperty.set(chatSpeechRange);
        speechRangeProperty.setValidValues(
                com.ninuna.losttales.chat.ChatSpeechRange.ids());
        Property distanceProperty = config.get(
                CATEGORY_CLIENT, "chatDistanceSizes", chatDistanceSizes);
        distanceProperty.set(chatDistanceSizes);
        distanceProperty.setValidValues(CHAT_DISTANCE_SIZES);
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
        config.get(CATEGORY_CHAT, "privateCommands", chatPrivateCommands)
                .set(chatPrivateCommands);
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
        config.get(CATEGORY_CLIENT, "chatShowAccounts",
                chatShowAccounts).set(chatShowAccounts);
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
        config.get(CATEGORY_CHARACTERS, "ringDropsAtDeath",
                ringDropsAtDeath).set(ringDropsAtDeath);
        config.get(CATEGORY_CLIENT, "showLostTalesHud", showLostTalesHud).set(showLostTalesHud);
        Property hudLayoutProperty = config.get(CATEGORY_CLIENT, "hudPanelLayout", hudPanelLayout);
        hudLayoutProperty.set(hudPanelLayout);
        if (hudLayoutsRead) {
            hudLayoutProperty.setValidValues(hudPresetValues());
        }
        config.get(CATEGORY_CLIENT, "showCompassHud", showCompassHud).set(showCompassHud);
        config.get(CATEGORY_CLIENT, "compassHudOffsetX", compassHudOffsetX).set(compassHudOffsetX);
        config.get(CATEGORY_CLIENT, "compassHudOffsetY", compassHudOffsetY).set(compassHudOffsetY);
        config.get(CATEGORY_CLIENT, "compassHudDisplayRadius", compassHudDisplayRadius).set(compassHudDisplayRadius);
        config.get(CATEGORY_CLIENT, "showStaticCompassMarkers", showStaticCompassMarkers).set(showStaticCompassMarkers);
        config.get(CATEGORY_CLIENT, "showLotrWaypointCompassMarkers", showLotrWaypointCompassMarkers).set(showLotrWaypointCompassMarkers);
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
        config.get(CATEGORY_CLIENT, "fellowshipHudOffsetX", fellowshipHudOffsetX).set(fellowshipHudOffsetX);
        config.get(CATEGORY_CLIENT, "fellowshipHudOffsetY", fellowshipHudOffsetY).set(fellowshipHudOffsetY);
        config.get(CATEGORY_FELLOWSHIP, "statusUpdateIntervalTicks", fellowshipStatusUpdateIntervalTicks).set(fellowshipStatusUpdateIntervalTicks);
        config.get(CATEGORY_FELLOWSHIP, "statusHeartbeatTicks", fellowshipStatusHeartbeatTicks).set(fellowshipStatusHeartbeatTicks);
        config.get(CATEGORY_FELLOWSHIP, "trackingUpdateIntervalTicks", fellowshipTrackingUpdateIntervalTicks).set(fellowshipTrackingUpdateIntervalTicks);
        config.get(CATEGORY_FELLOWSHIP, "trackingHeartbeatTicks", fellowshipTrackingHeartbeatTicks).set(fellowshipTrackingHeartbeatTicks);
        config.get(CATEGORY_FELLOWSHIP, "enableSharedQuestProgress", enableSharedQuestProgress).set(enableSharedQuestProgress);
        config.get(CATEGORY_FELLOWSHIP, "sharedQuestRadius", fellowshipSharedQuestRadius).set(fellowshipSharedQuestRadius);
        config.get(CATEGORY_CLIENT, "showQuickLootHud", showQuickLootHud).set(showQuickLootHud);
        config.get(CATEGORY_CLIENT, "quickLootHudOffsetX", quickLootHudOffsetX).set(quickLootHudOffsetX);
        config.get(CATEGORY_CLIENT, "quickLootHudOffsetY", quickLootHudOffsetY).set(quickLootHudOffsetY);
        config.get(CATEGORY_CLIENT, "quickLootHudMaxRows", quickLootHudMaxRows).set(quickLootHudMaxRows);
        config.get(CATEGORY_CLIENT, "showQuestTracker", showQuestTracker).set(showQuestTracker);
        config.get(CATEGORY_CLIENT, "questTrackerHudOffsetX", questTrackerHudOffsetX).set(questTrackerHudOffsetX);
        config.get(CATEGORY_CLIENT, "questTrackerHudOffsetY", questTrackerHudOffsetY).set(questTrackerHudOffsetY);
        config.get(CATEGORY_CLIENT, "questTrackerMaxQuests", questTrackerMaxQuests).set(questTrackerMaxQuests);
        config.get(CATEGORY_CLIENT, "questTrackerObjectiveLineCount", questTrackerObjectiveLineCount).set(questTrackerObjectiveLineCount);
        config.get(CATEGORY_CLIENT, "showQuestBanners", showQuestBanners).set(showQuestBanners);
        config.get(CATEGORY_CLIENT, "showNativeLotrQuestTracker", showNativeLotrQuestTracker).set(showNativeLotrQuestTracker);
        config.get(CATEGORY_CLIENT, "notificationHudOffsetX",
                notificationHudOffsetX).set(notificationHudOffsetX);
        config.get(CATEGORY_CLIENT, "notificationHudOffsetY",
                notificationHudOffsetY).set(notificationHudOffsetY);
        config.get(CATEGORY_CLIENT, "showWorldLabels", showWorldLabels).set(showWorldLabels);
        config.get(CATEGORY_CLIENT, "showDiscoveredWorldMapMarkers", showDiscoveredWorldMapMarkers).set(showDiscoveredWorldMapMarkers);
        config.get(CATEGORY_CLIENT, "worldQuestMarkerMaxDistance", worldQuestMarkerMaxDistance).set(worldQuestMarkerMaxDistance);
        config.get(CATEGORY_CLIENT, "showQuestChatFeedback", showQuestChatFeedback).set(showQuestChatFeedback);
        config.get(CATEGORY_CLIENT, "playQuestSounds", playQuestSounds).set(playQuestSounds);
        config.get(CATEGORY_CLIENT, "enableQuestConversation", enableQuestConversation).set(enableQuestConversation);
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
        Property armsProperty = config.get(CATEGORY_CLIENT,
                "devSkinOverrideArms", devSkinOverrideArms);
        armsProperty.set(devSkinOverrideArms);
        armsProperty.setValidValues(DEV_SKIN_ARMS);
        config.get(CATEGORY_QUESTS, "enableQuestPrerequisites", enableQuestPrerequisites).set(enableQuestPrerequisites);
        config.get(CATEGORY_QUESTS, "enableQuestRewards", enableQuestRewards).set(enableQuestRewards);
        config.get(CATEGORY_QUESTS, "allowQuestItemStarts", allowQuestItemStarts).set(allowQuestItemStarts);
        config.get(CATEGORY_QUESTS, "allowQuestInteractionStarts", allowQuestInteractionStarts).set(allowQuestInteractionStarts);
        config.get(CATEGORY_QUESTS, "autoRevealQuestMarkersOnStart", autoRevealQuestMarkersOnStart).set(autoRevealQuestMarkersOnStart);
        config.get(CATEGORY_QUESTS, "rumoursPointToQuestPlaces", rumoursPointToQuestPlaces).set(rumoursPointToQuestPlaces);
        config.get(CATEGORY_QUESTS, "autoTrackQuestOnStart", autoTrackQuestOnStart).set(autoTrackQuestOnStart);
        config.get(CATEGORY_QUESTS, "autoDiscoverNearbyMapMarkers", autoDiscoverNearbyMapMarkers).set(autoDiscoverNearbyMapMarkers);
        config.get(CATEGORY_QUESTS, "mapMarkerDiscoveryScanIntervalTicks", mapMarkerDiscoveryScanIntervalTicks).set(mapMarkerDiscoveryScanIntervalTicks);
        config.get(CATEGORY_QUESTS, "newCharacterStarterQuests", newCharacterStarterQuests).set(newCharacterStarterQuests);
        config.get(CATEGORY_QUESTS, "worldQuestSchedules", worldQuestSchedules).set(worldQuestSchedules);
        config.get(CATEGORY_WAYSTONES, "enableWaystoneRecipe", enableWaystoneRecipe).set(enableWaystoneRecipe);
        config.get(CATEGORY_WAYSTONES, "waystoneRecipeCornerIngredient", waystoneRecipeCornerIngredient).set(waystoneRecipeCornerIngredient);
        config.get(CATEGORY_WAYSTONES, "waystoneRecipeEdgeIngredient", waystoneRecipeEdgeIngredient).set(waystoneRecipeEdgeIngredient);
        config.get(CATEGORY_WAYSTONES, "waystoneRecipeCenterIngredient", waystoneRecipeCenterIngredient).set(waystoneRecipeCenterIngredient);
        config.get(CATEGORY_PROGRESSION, "skillCurveFirstLevel", skillCurveFirstLevel).set(skillCurveFirstLevel);
        config.get(CATEGORY_PROGRESSION, "skillCurveGrowthPercent", skillCurveGrowthPercent).set(skillCurveGrowthPercent);
        config.get(CATEGORY_PROGRESSION, "characterLevelBase", characterLevelBase).set(characterLevelBase);
        config.get(CATEGORY_PROGRESSION, "characterLevelStep", characterLevelStep).set(characterLevelStep);
        config.get(CATEGORY_PROGRESSION, "perkPointsPerLevel", perkPointsPerLevel).set(perkPointsPerLevel);
        config.get(CATEGORY_PROGRESSION, "playersCountForCombat", playersCountForCombat).set(playersCountForCombat);
        config.get(CATEGORY_PROGRESSION, "repeatsCountedInFull", repeatsCountedInFull).set(repeatsCountedInFull);
        config.get(CATEGORY_PROGRESSION, "repeatShrinkPercent", repeatShrinkPercent).set(repeatShrinkPercent);
        config.get(CATEGORY_PROGRESSION, "repeatFloorPercent", repeatFloorPercent).set(repeatFloorPercent);
        config.get(CATEGORY_PROGRESSION, "repeatRecoverySeconds", repeatRecoverySeconds).set(repeatRecoverySeconds);
        config.get(CATEGORY_PROGRESSION, "skillHourlyCap", skillHourlyCap).set(skillHourlyCap);
        config.get(CATEGORY_PROGRESSION, "finishedQuestsPassHourlyCap",
                finishedQuestsPassHourlyCap).set(finishedQuestsPassHourlyCap);
        config.get(CATEGORY_PROGRESSION, "firstFindsPassHourlyCap",
                firstFindsPassHourlyCap).set(firstFindsPassHourlyCap);
        config.get(CATEGORY_PROGRESSION, "softStepEndsOnStrike",
                softStepEndsOnStrike).set(softStepEndsOnStrike);
        config.get(CATEGORY_PROGRESSION, "statueFirstPrice", statueFirstPrice).set(statueFirstPrice);
        config.get(CATEGORY_PROGRESSION, "statuePriceFactor", statuePriceFactor).set(statuePriceFactor);
        config.get(CATEGORY_PROGRESSION, "statueItem", statueItem).set(statueItem);
        config.get(CATEGORY_PROGRESSION, "lightSetSpeedPercent", lightSetSpeedPercent).set(lightSetSpeedPercent);
        config.get(CATEGORY_PROGRESSION, "heavySetKnockbackPercent", heavySetKnockbackPercent).set(heavySetKnockbackPercent);
        config.get(CATEGORY_PROGRESSION, "heavyHelmetBootsSlowPercent", heavyHelmetBootsSlowPercent).set(heavyHelmetBootsSlowPercent);
        config.get(CATEGORY_PROGRESSION, "heavyChestLegsSlowPercent", heavyChestLegsSlowPercent).set(heavyChestLegsSlowPercent);
        config.get(CATEGORY_MISSIVES, "enableMissiveBoardRecipe", enableMissiveBoardRecipe).set(enableMissiveBoardRecipe);
        config.get(CATEGORY_MISSIVES, "missiveBoardRecipeCornerIngredient", missiveBoardRecipeCornerIngredient).set(missiveBoardRecipeCornerIngredient);
        config.get(CATEGORY_MISSIVES, "missiveBoardRecipeEdgeIngredient", missiveBoardRecipeEdgeIngredient).set(missiveBoardRecipeEdgeIngredient);
        config.get(CATEGORY_MISSIVES, "missiveBoardRecipeCenterIngredient", missiveBoardRecipeCenterIngredient).set(missiveBoardRecipeCenterIngredient);
        config.get(CATEGORY_MISSIVES, "enableMissiveBoards", enableMissiveBoards).set(enableMissiveBoards);
        config.get(CATEGORY_MISSIVES, "bundledMissiveTemplates", bundledMissiveTemplates).set(bundledMissiveTemplates);
        config.get(CATEGORY_MISSIVES, "missiveBoardMinAvailable", missiveBoardMinAvailable).set(missiveBoardMinAvailable);
        config.get(CATEGORY_MISSIVES, "missiveBoardMaxAvailable", missiveBoardMaxAvailable).set(missiveBoardMaxAvailable);
        config.get(CATEGORY_MISSIVES, "missiveBoardGenerationIntervalTicks", missiveBoardGenerationIntervalTicks).set(missiveBoardGenerationIntervalTicks);
        config.get(CATEGORY_MISSIVES, "missiveBoardMinPostedPerCycle", missiveBoardMinPostedPerCycle).set(missiveBoardMinPostedPerCycle);
        config.get(CATEGORY_MISSIVES, "missiveBoardMaxPostedPerCycle", missiveBoardMaxPostedPerCycle).set(missiveBoardMaxPostedPerCycle);
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
        missiveBoardMinPostedPerCycle = clampInt(missiveBoardMinPostedPerCycle, 1, 9);
        missiveBoardMaxPostedPerCycle = clampInt(missiveBoardMaxPostedPerCycle, 1, 9);
        if (missiveBoardMaxPostedPerCycle < missiveBoardMinPostedPerCycle) {
            missiveBoardMaxPostedPerCycle = missiveBoardMinPostedPerCycle;
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
                new int[] {3600, 10800, 21600, 43200, 86400},
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

    /** The named way of showing distance, or three sizes when the file names none. */
    public static String normalizeDistanceSizes(String value) {
        String normalized = value == null
                ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        for (int index = 0; index < CHAT_DISTANCE_SIZES.length; index++) {
            if (CHAT_DISTANCE_SIZES[index].equals(normalized)) {
                return CHAT_DISTANCE_SIZES[index];
            }
        }
        return CHAT_DISTANCE_THREE_SIZES;
    }

    /**
     * Puts the speech ranges in order: talk carries at least as far as a
     * whisper and a shout at least as far as talk. A range raised here is
     * written back, so the file says what is in force.
     */
    private static void sanitizeSpeechRanges(Configuration config) {
        int[] ranges = orderedSpeechRanges(chatProximityWhisperRange,
                chatProximityTalkRange, chatProximityShoutRange);
        if (ranges[1] != chatProximityTalkRange) {
            chatProximityTalkRange = ranges[1];
            config.getCategory(CATEGORY_CHAT).get("proximityTalkRange")
                    .set(chatProximityTalkRange);
        }
        if (ranges[2] != chatProximityShoutRange) {
            chatProximityShoutRange = ranges[2];
            config.getCategory(CATEGORY_CHAT).get("proximityShoutRange")
                    .set(chatProximityShoutRange);
        }
    }

    /** Whisper, talk and shout ranges with each at least the one before it. */
    static int[] orderedSpeechRanges(int whisper, int talk, int shout) {
        int talked = Math.max(whisper, talk);
        return new int[] {whisper, talked, Math.max(talked, shout)};
    }

    /**
     * The feed alignment a config value names, case and surrounding space
     * aside: {@code LEFT} or {@code RIGHT}, and the shipped {@code CENTRE}
     * for anything else.
     */
    public static String normalizeFeedAlignment(String value) {
        String normalized = value == null
                ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return "LEFT".equals(normalized) || "RIGHT".equals(normalized)
                ? normalized : DEFAULT_CHAT_FEED_ALIGNMENT;
    }

    /** A HUD panel's offset, a client option held to its bounds. */
    static double getHudPercent(
            Configuration config, String key, double defaultValue,
            double minimum, double maximum) {
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
