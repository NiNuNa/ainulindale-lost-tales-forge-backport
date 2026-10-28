package com.ninuna.losttales.proxy;

import com.ninuna.losttales.chat.server.ChatIdentitySelection;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import com.ninuna.losttales.fellowship.quest.FellowshipQuestProgressCoordinator;
import com.ninuna.losttales.fellowship.server.FellowshipDeclines;
import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterProfilePacket;
import com.ninuna.losttales.network.packet.character.CharacterGearPacket;
import java.io.File;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.accessory.Accessories;
import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.armour.ArmourEvents;
import com.ninuna.losttales.accessory.player.AccessoryPlayerEventHandler;
import com.ninuna.losttales.accessory.player.AccessoryInventorySyncManager;
import com.ninuna.losttales.accessory.effect.AccessoryConcealmentEventHandler;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.character.server.CharacterJoin;
import com.ninuna.losttales.character.server.CharacterPlayerEventHandler;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferCoordinator;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferStorage;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferWorldData;
import com.ninuna.losttales.character.server.CharacterRaceGameplayHandler;
import com.ninuna.losttales.character.server.CharacterSpawnOriginHandler;
import com.ninuna.losttales.compat.lotr.hired.LotrHiredUnitCustodyHandler;
import com.ninuna.losttales.compat.lotr.LotrSharedWaypoints;
import com.ninuna.losttales.compat.lotr.structure.LotrStructureBans;
import com.ninuna.losttales.character.server.CharacterStateCheckpointHandler;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.block.tileentity.ELostTalesTileEntity;
import com.ninuna.losttales.command.ELostTalesCommand;
import com.ninuna.losttales.compat.discord.DiscordGameEventRelay;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.crafting.ELostTalesCrafting;
import com.ninuna.losttales.entity.ELostTalesEntity;
import com.ninuna.losttales.entity.combat.LostTalesMobAggroEventHandler;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveEventHandler;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerEventHandler;
import com.ninuna.losttales.faction.ELostTalesFaction;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.server.LostTalesNetworkPlayerEventHandler;
import com.ninuna.losttales.network.server.LostTalesChargeService;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesThirdPersonAimService;
import com.ninuna.losttales.network.server.LostTalesThirdPersonProjectileAimHandler;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerDiscoveryPacket;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import com.ninuna.losttales.network.packet.LostTalesMissiveBoardStatePacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import com.ninuna.losttales.network.packet.LostTalesChargeTierSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatAccessPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatConsoleSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatHistorySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatUpdatePacket;
import com.ninuna.losttales.network.packet.LostTalesChatReactionSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatDeliveryMarkPacket;
import com.ninuna.losttales.network.packet.LostTalesServerConfigResultPacket;
import com.ninuna.losttales.network.packet.LostTalesServerConfigSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesFastTravelArrivalPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestFellowsPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestProgressPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesServerQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestOfferPacket;
import com.ninuna.losttales.network.packet.LostTalesWorldQuestSyncPacket;
import com.ninuna.losttales.quest.ServerQuestFiles;
import com.ninuna.losttales.quest.QuestLogSync;
import com.ninuna.losttales.quest.ServerQuestSync;
import com.ninuna.losttales.quest.world.WorldQuests;
import com.ninuna.losttales.storage.WorldClock;
import com.ninuna.losttales.network.packet.AccessoryInventorySyncPacket;
import com.ninuna.losttales.network.packet.AccessoryEffectSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterAppearanceSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreationCatalogSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterOperationResultPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterSyncPacket;
import com.ninuna.losttales.network.packet.character.LoreCharacterSyncPacket;
import com.ninuna.losttales.network.packet.fellowship.FellowshipMemberStatusSyncPacket;
import com.ninuna.losttales.network.packet.fellowship.FellowshipOperationResultPacket;
import com.ninuna.losttales.network.packet.fellowship.FellowshipStateSyncPacket;
import com.ninuna.losttales.network.packet.fellowship.FellowshipTrackingSyncPacket;
import com.ninuna.losttales.fellowship.server.FellowshipMemberStatusSyncManager;
import com.ninuna.losttales.fellowship.server.FellowshipTrackingSyncManager;
import com.ninuna.losttales.fellowship.server.FellowshipPlayerEventHandler;
import com.ninuna.losttales.fellowship.server.FellowshipMirrors;
import com.ninuna.losttales.fellowship.server.FellowshipSyncManager;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.missive.MissiveBoardWatches;
import com.ninuna.losttales.world.biome.ELostTalesBiome;
import com.ninuna.losttales.world.map.LostTalesMapOverlay;
import com.ninuna.losttales.world.map.road.ELostTalesRoad;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerWaypointRegistry;
import com.ninuna.losttales.world.spawning.ELostTalesSpawnList;
import com.ninuna.losttales.world.structure.ELostTalesStructure;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import com.ninuna.losttales.chat.ChatChannelIconCatalog;
import com.ninuna.losttales.chat.ChatCodeNames;
import com.ninuna.losttales.chat.moderation.ChatAuditLog;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.server.ChatConsoleCommandHandler;
import com.ninuna.losttales.chat.server.ChatSpeechGate;
import com.ninuna.losttales.chat.server.ChatArrivals;
import com.ninuna.losttales.chat.server.ChatWelcome;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatCommandText;
import com.ninuna.losttales.chat.server.ChatCommandContexts;
import com.ninuna.losttales.chat.server.ChatConsoleStream;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.chat.server.ChatHistoryStorage;
import com.ninuna.losttales.chat.server.ChatMemberDirectory;
import com.ninuna.losttales.chat.server.LostTalesChatRoleRosterWatcher;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.chat.server.LostTalesServerBroadcastHook;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneGenerationHandler;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatPresenceSyncPacket;
import com.ninuna.losttales.chat.server.ChatPresenceService;
import com.ninuna.losttales.network.packet.ProgressionCatalogPacket;
import com.ninuna.losttales.network.packet.ProgressionStatePacket;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.progression.ProgressionPlayerEvents;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.count.CountEvents;
import com.ninuna.losttales.progression.count.CountWatch;
import com.ninuna.losttales.progression.perk.PerkIndex;
import com.ninuna.losttales.progression.perk.PerkAnimalEvents;
import com.ninuna.losttales.progression.perk.PerkCombatEvents;
import com.ninuna.losttales.progression.perk.PerkFood;
import com.ninuna.losttales.progression.perk.PerkRuntime;
import com.ninuna.losttales.progression.perk.PerkWorkEvents;
import com.ninuna.losttales.progression.deed.DeedEvents;
import com.ninuna.losttales.network.packet.ProgressionStatuePacket;
import software.bernie.geckolib3.GeckoLib;

public class LostTalesCommonProxy {

    /**
     * Reads the options: the client's file and the server's on a client,
     * the server's alone on a dedicated server, which never creates the
     * client folder.
     */
    private static void loadConfig(File modConfigDirectory) {
        boolean clientSide = FMLCommonHandler.instance().getSide() == Side.CLIENT;
        LostTalesConfig.load(
                clientSide ? LostTalesConfigFiles.clientOptions(modConfigDirectory) : null,
                LostTalesConfigFiles.serverOptions(modConfigDirectory),
                LostTalesConfigFiles.rolesOptions(modConfigDirectory),
                LostTalesConfigFiles.channelsOptions(modConfigDirectory));
    }

    public void preInit(FMLPreInitializationEvent event) {
        loadConfig(event.getModConfigurationDirectory());
        ServerQuestFiles.configure(event.getModConfigurationDirectory());
        ProgressionFiles.configure(event.getModConfigurationDirectory());
        com.ninuna.losttales.mapmarker.LostTalesMapMarkerFiles.configure(
                event.getModConfigurationDirectory());
        GeckoLib.initialize();
        LostTalesNetworkHandler.registerCommonPackets();
        LostTalesQuestRegistry.loadFromClasspath();
        LostTalesQuestPlayerEventHandler questPlayerEventHandler = new LostTalesQuestPlayerEventHandler();
        AccessoryPlayerEventHandler accessoryPlayerEventHandler =
                new AccessoryPlayerEventHandler();
        AccessoryConcealmentEventHandler accessoryConcealmentEventHandler =
                new AccessoryConcealmentEventHandler();
        LostTalesQuestObjectiveEventHandler questObjectiveEventHandler = new LostTalesQuestObjectiveEventHandler();
        LostTalesMobAggroEventHandler mobAggroEventHandler = new LostTalesMobAggroEventHandler();
        CharacterLifecycleStateTracker characterLifecycleStateTracker =
                new CharacterLifecycleStateTracker();
        CharacterPlayerEventHandler characterPlayerEventHandler = new CharacterPlayerEventHandler();
        CharacterJoin characterJoin = new CharacterJoin();
        CharacterRaceGameplayHandler characterRaceGameplayHandler = new CharacterRaceGameplayHandler();
        CharacterSpawnOriginHandler characterSpawnOriginHandler = new CharacterSpawnOriginHandler();
        CharacterStateCheckpointHandler characterStateCheckpointHandler =
                new CharacterStateCheckpointHandler();
        FellowshipPlayerEventHandler fellowshipPlayerEventHandler = new FellowshipPlayerEventHandler();
        LostTalesServerTaskQueue serverTaskQueue = new LostTalesServerTaskQueue();
        LostTalesNetworkPlayerEventHandler networkPlayerEventHandler = new LostTalesNetworkPlayerEventHandler();
        LostTalesThirdPersonProjectileAimHandler projectileAimHandler =
                new LostTalesThirdPersonProjectileAimHandler();
        LostTalesChargeService chargeService =
                new LostTalesChargeService();
        LostTalesWaystoneGenerationHandler waystoneGenerationHandler =
                new LostTalesWaystoneGenerationHandler();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new ChatIdentitySelection());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new ChatPresenceService());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(
                new com.ninuna.losttales.chat.server.ChatServerStatus());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(
                new com.ninuna.losttales.chat.server.ChatMemberWatches());
        LostTalesChatRoleRosterWatcher chatRoleRosterWatcher =
                new LostTalesChatRoleRosterWatcher();
        MinecraftForge.EVENT_BUS.register(questPlayerEventHandler);
        ProgressionPlayerEvents progressionPlayerEvents = new ProgressionPlayerEvents();
        MinecraftForge.EVENT_BUS.register(progressionPlayerEvents);
        FMLCommonHandler.instance().bus().register(progressionPlayerEvents);
        CountEvents countEvents = new CountEvents();
        MinecraftForge.EVENT_BUS.register(countEvents);
        FMLCommonHandler.instance().bus().register(countEvents);
        for (Object perks : new Object[] { new PerkCombatEvents(), new PerkWorkEvents(),
                new PerkAnimalEvents(), new PerkFood(), new DeedEvents(), new ArmourEvents() }) {
            MinecraftForge.EVENT_BUS.register(perks);
            FMLCommonHandler.instance().bus().register(perks);
        }
        MinecraftForge.EVENT_BUS.register(accessoryPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(accessoryConcealmentEventHandler);
        MinecraftForge.EVENT_BUS.register(characterLifecycleStateTracker);
        MinecraftForge.EVENT_BUS.register(characterPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(characterJoin);
        MinecraftForge.EVENT_BUS.register(characterRaceGameplayHandler);
        MinecraftForge.EVENT_BUS.register(characterSpawnOriginHandler);
        MinecraftForge.EVENT_BUS.register(new LotrHiredUnitCustodyHandler());
        MinecraftForge.EVENT_BUS.register(new LotrStructureBans());
        MinecraftForge.EVENT_BUS.register(questObjectiveEventHandler);
        MinecraftForge.EVENT_BUS.register(mobAggroEventHandler);
        MinecraftForge.EVENT_BUS.register(projectileAimHandler);
        MinecraftForge.EVENT_BUS.register(chargeService);
        MinecraftForge.EVENT_BUS.register(waystoneGenerationHandler);
        // Commands on the Forge bus, RCON's waiting commands on the server tick.
        ChatConsoleCommandHandler consoleCommands = new ChatConsoleCommandHandler();
        MinecraftForge.EVENT_BUS.register(consoleCommands);
        FMLCommonHandler.instance().bus().register(consoleCommands);
        MinecraftForge.EVENT_BUS.register(new ChatSpeechGate());
        MinecraftForge.EVENT_BUS.register(new ChatArrivals());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new ChatWelcome());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(
                new com.ninuna.losttales.character.server.FirstJoinNames());
        MinecraftForge.TERRAIN_GEN_BUS.register(waystoneGenerationHandler);
        GameRegistry.registerWorldGenerator(
                waystoneGenerationHandler, 1000);
        FMLCommonHandler.instance().bus().register(questPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(accessoryPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(questObjectiveEventHandler);
        FMLCommonHandler.instance().bus().register(mobAggroEventHandler);
        FMLCommonHandler.instance().bus().register(characterLifecycleStateTracker);
        FMLCommonHandler.instance().bus().register(characterPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(characterJoin);
        FMLCommonHandler.instance().bus().register(characterRaceGameplayHandler);
        FMLCommonHandler.instance().bus().register(characterStateCheckpointHandler);
        FMLCommonHandler.instance().bus().register(new WorldClock());
        FMLCommonHandler.instance().bus().register(fellowshipPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(serverTaskQueue);
        FMLCommonHandler.instance().bus().register(networkPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(chargeService);
        FMLCommonHandler.instance().bus().register(
                waystoneGenerationHandler);
        FMLCommonHandler.instance().bus().register(chatRoleRosterWatcher);
        FMLCommonHandler.instance().bus().register(new MissiveBoardWatches());
        FMLCommonHandler.instance().bus().register(new WorldQuests());
        FMLCommonHandler.instance().bus().register(new LostTalesMapMarkerSyncManager());

        ELostTalesItem.initAndRegisterItems();
        ELostTalesBlock.initAndRegisterBlocks();
        ELostTalesTileEntity.initAndRegisterTileEntities();
        ELostTalesEntity.initAndRegisterEntities();
        ELostTalesBiome.initAndRegisterBiomes();
        ELostTalesSpawnList.initAndRegisterSpawnLists();
    }

    public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean(
                LostTalesClassTransformer.ACCESSORY_CONTAINER_ACTIVE_PROPERTY)
                || !Boolean.getBoolean(
                LostTalesClassTransformer.ACCESSORY_DEATH_ACTIVE_PROPERTY)
                || !com.ninuna.losttales.accessory.player.AccessoryDeathHooks
                .serverDropLanded()) {
            FMLLog.severe("[%s] Accessory lifecycle transformers are incomplete; "
                            + "the ring slot takes nothing",
                    LostTalesMetaData.MOD_ID);
        }
        ELostTalesStructure.initAndRegisterStructures();
        ELostTalesCrafting.initAndRegisterCrafting();
        ELostTalesFaction.initAndRegisterFactions();
        LostTalesMapOverlay.applyWorldGenerationMap();
        LostTalesMapMarkerWaypointRegistry.initAndRegisterWaypoints();
        ELostTalesAchievement.initAndRegisterAchievements();
        ProgressionCatalog.makeDeedAchievements();

        ELostTalesRoad.initAndRegisterRoads();
    }

    public void postInit(FMLPostInitializationEvent event) {
        // Every mod's items exist now, so the accessory files can name them (ACC-01).
        Accessories.loadBundled();
        // The armour weights and named sets, for both sides (CONTENT-06, CONTENT-07).
        Armour.loadBundled();
        // Run after Lost Tales has registered its additional LOTR factions so
        // the immutable character-creation catalogue includes them as well.
        LotrCharacterAdapter.getInstance().initialize();
        // A faction's chat is named by the faction (#gondor), on both sides.
        ChatCodeNames.installFactions(
                LotrCharacterAdapter.getInstance().getFactionIds());
    }

    /**
     * Opens the page of the missive letter in the player's inventory slot
     * {@code inventorySlot}, on the client. The common/server proxy does
     * nothing, so a dedicated server never loads a client class.
     */
    public void openMissiveLetterPage(int inventorySlot) {}

    /**
     * Client-bound packet hooks. The common/server proxy deliberately does
     * nothing here; the client proxy overrides these methods and updates
     * client-only caches. Packet handlers can safely call through this proxy on
     * either physical side without loading Minecraft client classes on a
     * dedicated server.
     */
    public void handleQuickLootContainerSync(LostTalesQuickLootContainerSyncPacket packet) {}

    public void handleQuestSync(LostTalesQuestSyncPacket packet) {}

    public void handleQuestProgress(LostTalesQuestProgressPacket packet) {}

    public void handleQuestFellows(LostTalesQuestFellowsPacket packet) {}

    public void handleProgressionCatalog(ProgressionCatalogPacket packet) {}

    /** The server's races and capes, as a player joins (CHAR-38, CHAR-39). */
    public void handleRaceCatalog(
            com.ninuna.losttales.network.packet.character.CharacterRaceCatalogPacket packet) {}

    public void handleProgressionState(ProgressionStatePacket packet) {}

    public void handleProgressionStatue(ProgressionStatuePacket packet) {}

    /** The perks the client's own player took; none on a dedicated server. */
    public PerkIndex clientPerkIndex(EntityPlayer player) {
        return PerkIndex.EMPTY;
    }

    public void handleServerQuestSync(LostTalesServerQuestSyncPacket packet) {}

    public void handleQuestOffer(LostTalesQuestOfferPacket packet) {}

    public void handleWorldQuestSync(LostTalesWorldQuestSyncPacket packet) {}

    public void handleMobAggroSync(LostTalesMobAggroSyncPacket packet) {}

    public void handleMapMarkerDiscovery(LostTalesMapMarkerDiscoveryPacket packet) {}

    public void handleMapMarkerSnapshot(
            LostTalesMapMarkerSnapshotPacket packet) {}

    public void handleWaystoneState(
            LostTalesWaystoneStatePacket packet) {}

    public void handleMissiveBoardState(
            LostTalesMissiveBoardStatePacket packet) {}

    public void handleChargeTierSync(LostTalesChargeTierSyncPacket packet) {}

    public void handleAccessoryInventorySync(AccessoryInventorySyncPacket packet) {}

    public void handleAccessoryEffectSync(AccessoryEffectSyncPacket packet) {}

    public boolean isAccessoryConcealed(EntityPlayer player) {
        return false;
    }

    /** The server's accessories, as a player joins (ACC-01). */
    public void handleAccessoryCatalog(
            com.ninuna.losttales.network.packet.AccessoryCatalogPacket packet) {}

    /** The server's armour weights, named sets and weight-set numbers, as a player joins. */
    public void handleArmourCatalog(
            com.ninuna.losttales.network.packet.ArmourCatalogPacket packet) {}

    /** Queues client-only packet work. The dedicated-server proxy is a no-op. */
    public void scheduleClientTask(Runnable task) {}

    public void handleCharacterRosterSync(CharacterRosterSyncPacket packet) {}

    public void handleCharacterOperationResult(CharacterOperationResultPacket packet) {}

    public void handleCharacterAppearanceSync(CharacterAppearanceSyncPacket packet) {}

    public void handleCharacterProfile(CharacterProfilePacket packet) {}

    /** What one of the player's own characters wears in its saved life, for the stage (CHAR-42). */
    public void handleCharacterGear(CharacterGearPacket packet) {}

    public void handleCharacterCreationCatalogSync(CharacterCreationCatalogSyncPacket packet) {}

    public void handleLoreCharacterSync(LoreCharacterSyncPacket packet) {}

    public void handleFellowshipStateSync(FellowshipStateSyncPacket packet) {}

    public void handleFellowshipOperationResult(FellowshipOperationResultPacket packet) {}

    public void handleFellowshipMemberStatusSync(FellowshipMemberStatusSyncPacket packet) {}

    public void handleFellowshipTrackingSync(FellowshipTrackingSyncPacket packet) {}

    public void handleChatIdentity(LostTalesChatIdentitySyncPacket packet) {}

    public void handleChatPresence(LostTalesChatPresenceSyncPacket packet) {}

    public void handleServerStatus(
            com.ninuna.losttales.network.packet.LostTalesServerStatusPacket packet) {}

    public void handleChatMembers(LostTalesChatMembersPacket packet) {}

    public void handleChatMessage(LostTalesChatMessagePacket packet) {}

    public void handleChatAccess(LostTalesChatAccessPacket packet) {}

    public void handleChatTyping(LostTalesChatTypingSyncPacket packet) {}

    public void handleChatUpdate(LostTalesChatUpdatePacket packet) {}

    /** The reactions on a message on screen changed; the client redraws its line. */
    public void handleChatReactions(LostTalesChatReactionSyncPacket packet) {}

    /**
     * How the Discord post of a line this player said is going; the
     * client marks its own line.
     */
    public void handleChatDeliveryMark(LostTalesChatDeliveryMarkPacket packet) {}

    public void handleChatHistory(LostTalesChatHistorySyncPacket packet) {}

    public void handleChatConsole(LostTalesChatConsoleSyncPacket packet) {}

    public void handleServerConfigSync(LostTalesServerConfigSyncPacket packet) {}

    public void handleServerConfigResult(LostTalesServerConfigResultPacket packet) {}

    public void handleFastTravelArrival(
            LostTalesFastTravelArrivalPacket packet) {}

    /**
     * Sets the server's stores up for this world. Each step runs through
     * {@link ServerStep}: one that throws is logged and the rest still run.
     */
    public void onServerStarting(FMLServerStartingEvent event) {
        // Before the reload, not after: the reload registers the channels,
        // icons and words this server's config defines, and resetting
        // afterwards would take them straight back out again. Every other
        // static store below holds nothing the reload puts there.
        ServerStep.run("ChatChannel.resetToBuiltIn", () -> ChatChannel.resetToBuiltIn());
        ServerStep.run("ChatChannelIconCatalog.resetToDefaults",
                () -> ChatChannelIconCatalog.resetToDefaults());
        ServerStep.run("ChatProfanityCatalog.resetToBundled",
                () -> ChatProfanityCatalog.resetToBundled());
        ServerStep.run("ChatCommandText.resetServerNames",
                () -> ChatCommandText.resetServerNames());
        // Then the config, so the roles, gates, channels and settings
        // everything below reads are the ones on disk now: the files may
        // have been edited since pre-init, and a client hosting a world
        // installs its catalogue here rather than at pre-init.
        ServerStep.run("LostTalesConfig.reload", () -> LostTalesConfig.reload());
        // The world's clock next: whatever lets data expire below, and on
        // the first ticks, asks it whether a jump of the clock holds that
        // back (DATA-09).
        ServerStep.run("WorldClock.reset", () -> WorldClock.reset());
        ServerStep.run("WorldClock.observe", () -> WorldClock.observe(event.getServer()));
        ServerStep.run("CharacterLifecycleStateTracker.markServerStarting",
                () -> CharacterLifecycleStateTracker.markServerStarting());
        // No figure until the files are read after the races below, so
        // nothing recovers a claim or release before that read.
        ServerStep.run("LoreCharacterRegistry.clear", () -> LoreCharacterRegistry.clear());
        ServerStep.run("initializeLoreCharacterOwnership",
                () -> initializeLoreCharacterOwnership(event));
        ServerStep.run("CharacterStateCheckpointHandler.reset",
                () -> CharacterStateCheckpointHandler.reset());
        ServerStep.run("CharacterSwitchCoordinator.clearAllRuntimeState",
                () -> CharacterSwitchCoordinator.getInstance().clearAllRuntimeState());
        ServerStep.run("AccessoryInventorySyncManager.clearAll",
                () -> AccessoryInventorySyncManager.clearAll());
        ServerStep.run("AccessoryEffectService.clearAll", () -> AccessoryEffectService.clearAll());
        ServerStep.run("LostTalesServerTaskQueue.startAccepting",
                () -> LostTalesServerTaskQueue.startAccepting());
        ServerStep.run("LostTalesRequestRateLimiter.clear",
                () -> LostTalesRequestRateLimiter.clear());
        ServerStep.run("LostTalesThirdPersonAimService.clear",
                () -> LostTalesThirdPersonAimService.clear());
        ServerStep.run("LostTalesChargeService.clear", () -> LostTalesChargeService.clear());
        ServerStep.run("FellowshipSyncManager.clear", () -> FellowshipSyncManager.clear());
        ServerStep.run("FellowshipMirrors.clear", () -> FellowshipMirrors.clear());
        ServerStep.run("LotrSharedWaypoints.clear", () -> LotrSharedWaypoints.clear());
        ServerStep.run("FellowshipMemberStatusSyncManager.clear",
                () -> FellowshipMemberStatusSyncManager.clear());
        ServerStep.run("FellowshipTrackingSyncManager.clear",
                () -> FellowshipTrackingSyncManager.clear());
        ServerStep.run("FellowshipDeclines.clear", () -> FellowshipDeclines.clear());
        ServerStep.run("FellowshipQuestProgressCoordinator.clear",
                () -> FellowshipQuestProgressCoordinator.getInstance().clear());
        ServerStep.run("MissiveBoardWatches.clear", () -> MissiveBoardWatches.clear());
        ServerStep.run("LostTalesMapMarkerSyncManager.clear",
                () -> LostTalesMapMarkerSyncManager.clear());
        ServerStep.run("LostTalesLotrWaystoneTravelAdapter.clear",
                () -> LostTalesLotrWaystoneTravelAdapter.clear());
        ServerStep.run("LostTalesWaystoneVisits.clear",
                () -> com.ninuna.losttales.mapmarker.LostTalesWaystoneVisits.clear());
        ServerStep.run("LostTalesChatRoleRosterWatcher.clear",
                () -> LostTalesChatRoleRosterWatcher.clear());
        ServerStep.run("ChatMessageIdAllocator.reset", () -> ChatMessageIdAllocator.reset());
        ServerStep.run("ChatHistory.clear", () -> ChatHistory.clear());
        ServerStep.run("ChatConsoleStream.clear", () -> ChatConsoleStream.clear());
        ServerStep.run("ChatReports.clear",
                () -> com.ninuna.losttales.chat.server.ChatReports.clear());
        // After the channels and the config above, since every kept
        // line names its channel by id: the save's recent lines and the
        // console's kept events come back as the live stores, and the
        // save follows them from here.
        ServerStep.run("ChatHistoryStorage.restore",
                () -> ChatHistoryStorage.restore(event.getServer()));
        // Straight after the history, whose kept messages decide which of
        // the save's Discord links come back, and before the bridge
        // starts below, which paces its topics by the save's topic
        // writes. Every server gets its own, so nothing reaches it from
        // the world before.
        ServerStep.run("LostTalesDiscordBridge.restoreSaved",
                () -> LostTalesDiscordBridge.getInstance().restoreSaved(event.getServer()));
        ServerStep.run("ChatCommandContexts.clear", () -> ChatCommandContexts.clear());
        ServerStep.run("LostTalesChatService.clear", () -> LostTalesChatService.clear());
        ServerStep.run("ChatIdentitySelection.clear", () -> ChatIdentitySelection.clear());
        ServerStep.run("ChatPresenceService.clear", () -> ChatPresenceService.clear());
        ServerStep.run("ChatServerStatus.clear",
                () -> com.ninuna.losttales.chat.server.ChatServerStatus.clear());
        // Missives belong to the world that made them; each
        // player's saved data registers its own again as it loads.
        ServerStep.run("LostTalesQuestRegistry.clearMissives",
                () -> LostTalesQuestRegistry.clearMissives());
        // The server's own quest files, as they are on disk now.
        ServerStep.run("LostTalesQuestRegistry.loadServerQuests",
                () -> LostTalesQuestRegistry.loadServerQuests());
        // The skills and race starts, bundled and the server's own.
        ServerStep.run("ProgressionCatalog.load", () -> ProgressionCatalog.load());
        // The races and capes, bundled and the server's own, after the
        // skills their starts name (CHAR-38, CHAR-39).
        ServerStep.run("CharacterRaceCatalog.load", () -> {
            com.ninuna.losttales.character.server.CharacterRaceCatalog.load(
                    event.getServer().worldServerForDimension(0));
        });
        // The lore characters, bundled and the server's own, after the
        // races they stand on (CHAR-43); then the claims and releases a
        // stop broke off, which wait for them.
        ServerStep.run("LoreCharacterRegistry.load", () -> LoreCharacterRegistry.load());
        ServerStep.run("LoreCharacterTransferCoordinator.recoverAll", () -> {
            LoreCharacterTransferCoordinator.getInstance().recoverAll(
                    event.getServer().worldServerForDimension(0));
        });
        // The server's own waystone houses and map marker files (MOD-17);
        // the marker store seeds itself from the files only after this.
        ServerStep.run("LostTalesMapMarkerFiles.loadServer",
                () -> com.ninuna.losttales.mapmarker.LostTalesMapMarkerFiles.loadServer());
        // The armour weights and named sets, bundled and the server's own
        // (CONTENT-06, CONTENT-07), before the kits that mark relics.
        ServerStep.run("Armour.load", () -> Armour.load());
        // The starting kits, checked against the server's races and relic
        // sets, so read once both are set (CHAR-40, CONTENT-11).
        ServerStep.run("StartingKits.load",
                () -> com.ninuna.losttales.character.kit.StartingKits.load());
        // The accessories, bundled and the server's own (ACC-01), before
        // any player's ring is read.
        ServerStep.run("Accessories.load", () -> Accessories.load());
        // The missive templates, bundled and the server's own (QUEST-42).
        ServerStep.run("MissiveTemplates.load",
                () -> com.ninuna.losttales.quest.missive.MissiveTemplates.load());
        ServerStep.run("ProgressionService.clear", () -> ProgressionService.clear());
        ServerStep.run("CountWatch.clear", () -> CountWatch.clear());
        ServerStep.run("PerkRuntime.clearServer", () -> PerkRuntime.clearServer());
        ServerStep.run("PerkCombatEvents.clear", () -> PerkCombatEvents.clear());
        ServerStep.run("PerkAnimalEvents.clear", () -> PerkAnimalEvents.clear());
        ServerStep.run("ArmourEvents.clear", () -> ArmourEvents.clear());
        ServerStep.run("DeedEvents.clear", () -> DeedEvents.clear());
        ServerStep.run("WorldQuestSchedules.logProblems",
                () -> com.ninuna.losttales.quest.world.WorldQuestSchedules.logProblems());
        ServerStep.run("ServerQuestSync.clear", () -> ServerQuestSync.clear());
        ServerStep.run("QuestLogSync.clear", () -> QuestLogSync.clear());
        ServerStep.run("QuestFellows.clear", () -> com.ninuna.losttales.quest.QuestFellows.clear());
        ServerStep.run("QuestOfferPause.clear",
                () -> com.ninuna.losttales.quest.QuestOfferPause.clear());
        ServerStep.run("LostTalesQuestRewardHelper.clear",
                () -> com.ninuna.losttales.quest.LostTalesQuestRewardHelper.clear());
        ServerStep.run("MissiveSeals.clear",
                () -> com.ninuna.losttales.quest.missive.MissiveSeals.clear());
        ServerStep.run("WorldQuests.clear", () -> WorldQuests.clear());
        ServerStep.run("LostTalesServerBroadcastHook.clear",
                () -> LostTalesServerBroadcastHook.clear());
        ServerStep.run("ChatArrivals.clear", () -> ChatArrivals.clear());
        ServerStep.run("ChatWelcome.clear", () -> ChatWelcome.clear());
        ServerStep.run("CharacterJoin.clear", () -> CharacterJoin.clear());
        ServerStep.run("CharacterSyncManager.clear",
                () -> com.ninuna.losttales.character.server.CharacterSyncManager.clear());
        ServerStep.run("ChatConsoleCommandHandler.clear", () -> ChatConsoleCommandHandler.clear());
        ServerStep.run("DiscordGameEventRelay.clear", () -> DiscordGameEventRelay.clear());
        ServerStep.run("LostTalesDiscordBridge.resetSession",
                () -> LostTalesDiscordBridge.getInstance().resetSession());
        ServerStep.run("ChatMemberDirectory.clear", () -> ChatMemberDirectory.clear());
        ServerStep.run("ChatMemberWatches.clear",
                () -> com.ninuna.losttales.chat.server.ChatMemberWatches.clear());
        ServerStep.run("ChatAuditLog.onServerStarting", () -> ChatAuditLog.onServerStarting());
        ServerStep.run("LostTalesMobAggroEventHandler.clearAll",
                () -> LostTalesMobAggroEventHandler.clearAll());
        ServerStep.run("LotrRaceProfileAdapter.clear",
                () -> LotrRaceProfileAdapter.getInstance().clear());
        ServerStep.run("ELostTalesCommand.initAndRegisterCommands",
                () -> ELostTalesCommand.initAndRegisterCommands(event));
        ServerStep.run("LostTalesDiscordBridge.start",
                () -> LostTalesDiscordBridge.getInstance().start());
    }

    private static void initializeLoreCharacterOwnership(
            FMLServerStartingEvent event) {
        if (event == null || event.getServer() == null
                || event.getServer().worldServerForDimension(0) == null) {
            return;
        }
        try {
            LoreCharacterOwnershipWorldData data =
                    LoreCharacterOwnershipStorage.get(
                            event.getServer().worldServerForDimension(0));
            if (data.isReadOnly()) {
                FMLLog.severe("[%s] Lore-character ownership is read-only (%s); claims and releases are disabled",
                        LostTalesMetaData.MOD_ID, data.getReadOnlyReason());
            }
            LoreCharacterTransferWorldData transfers =
                    LoreCharacterTransferStorage.get(
                            event.getServer().worldServerForDimension(0));
            if (transfers.isReadOnly()) {
                FMLLog.severe("[%s] Lore-character transfer journal is "
                                + "read-only (%s); claims and releases are disabled",
                        LostTalesMetaData.MOD_ID,
                        transfers.getReadOnlyReason());
            }
        } catch (RuntimeException exception) {
            FMLLog.severe("[%s] Failed to initialize lore-character ownership storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
        }
    }

    /**
     * The server accepts players from here on: the Server Log's
     * entry saying so, and the clock its status counts its time up by.
     * The server's coming and going is said in the Server Log alone;
     * Discord reads it in the linked channels' topics, which its status
     * fills from its first look.
     */
    public void onServerStarted(FMLServerStartedEvent event) {
        LostTalesChatService.console(ChatConsoleEvent.Kind.SERVER,
                ChatConsoleEvent.Severity.INFO,
                LostTalesServerBroadcastHook.SERVER_NAME, "Server started");
        com.ninuna.losttales.chat.server.ChatServerStatus.started();
    }

    /**
     * Lets the server's stores go and empties them for the next world.
     * Each step runs through {@link ServerStep}: one that throws is logged
     * and the rest still run.
     */
    public void onServerStopping(FMLServerStoppingEvent event) {
        // The Server Log's entry for the stop, the pair of the one the
        // start records: shown to its readers still online, and kept by
        // the history's snapshot below, so the Console shows it when the
        // server is next up. First, before the ids are reset below.
        ServerStep.run("LostTalesChatService.console", () -> {
            LostTalesChatService.console(ChatConsoleEvent.Kind.SERVER,
                    ChatConsoleEvent.Severity.INFO,
                    LostTalesServerBroadcastHook.SERVER_NAME,
                    "Server shutting down");
        });
        // The offline topic, queued before the stop, which gives the
        // worker a bounded moment to send it.
        ServerStep.run("LostTalesDiscordBridge.onServerStopping",
                () -> LostTalesDiscordBridge.getInstance().onServerStopping());
        ServerStep.run("LostTalesDiscordBridge.stop",
                () -> LostTalesDiscordBridge.getInstance().stop());
        // Once the bridge has stopped adding to them, the links and the
        // topic writes, the offline one among them, go to the save the
        // worlds are written with after this event, and the live ones are
        // emptied for the next world.
        ServerStep.run("LostTalesDiscordBridge.releaseSaved",
                () -> LostTalesDiscordBridge.getInstance().releaseSaved());
        ServerStep.run("LostTalesServerTaskQueue.stopAcceptingAndClear",
                () -> LostTalesServerTaskQueue.stopAcceptingAndClear());
        ServerStep.run("CharacterLifecycleStateTracker.markServerStopping",
                () -> CharacterLifecycleStateTracker.markServerStopping());
        ServerStep.run("CharacterSwitchCoordinator.checkpointAllOnlinePlayers", () -> {
            int checkpointed = CharacterSwitchCoordinator.getInstance()
                    .checkpointAllOnlinePlayers(MinecraftServer.getServer());
            FMLLog.info("[%s] Shutdown character checkpoint completed for %d online accounts",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(checkpointed));
        });
        // The last time seen and the clean stop's mark go to the save the
        // worlds are written with (DATA-09).
        ServerStep.run("WorldClock.stopped", () -> WorldClock.stopped(MinecraftServer.getServer()));
        ServerStep.run("CharacterStateCheckpointHandler.reset",
                () -> CharacterStateCheckpointHandler.reset());
        ServerStep.run("CharacterSwitchCoordinator.clearAllRuntimeState",
                () -> CharacterSwitchCoordinator.getInstance().clearAllRuntimeState());
        ServerStep.run("AccessoryInventorySyncManager.clearAll",
                () -> AccessoryInventorySyncManager.clearAll());
        ServerStep.run("AccessoryEffectService.clearAll", () -> AccessoryEffectService.clearAll());
        ServerStep.run("LostTalesRequestRateLimiter.clear",
                () -> LostTalesRequestRateLimiter.clear());
        ServerStep.run("LostTalesThirdPersonAimService.clear",
                () -> LostTalesThirdPersonAimService.clear());
        ServerStep.run("LostTalesChargeService.clear", () -> LostTalesChargeService.clear());
        ServerStep.run("FellowshipSyncManager.clear", () -> FellowshipSyncManager.clear());
        ServerStep.run("FellowshipMirrors.clear", () -> FellowshipMirrors.clear());
        ServerStep.run("LotrSharedWaypoints.clear", () -> LotrSharedWaypoints.clear());
        ServerStep.run("FellowshipMemberStatusSyncManager.clear",
                () -> FellowshipMemberStatusSyncManager.clear());
        ServerStep.run("FellowshipTrackingSyncManager.clear",
                () -> FellowshipTrackingSyncManager.clear());
        ServerStep.run("FellowshipDeclines.clear", () -> FellowshipDeclines.clear());
        ServerStep.run("FellowshipQuestProgressCoordinator.clear",
                () -> FellowshipQuestProgressCoordinator.getInstance().clear());
        ServerStep.run("MissiveBoardWatches.clear", () -> MissiveBoardWatches.clear());
        ServerStep.run("LostTalesQuestRegistry.clearServerQuests",
                () -> LostTalesQuestRegistry.clearServerQuests());
        ServerStep.run("ProgressionCatalog.clear", () -> ProgressionCatalog.clear());
        ServerStep.run("CharacterRaceCatalog.clear",
                () -> com.ninuna.losttales.character.server.CharacterRaceCatalog.clear());
        ServerStep.run("LoreCharacterRegistry.clear", () -> LoreCharacterRegistry.clear());
        ServerStep.run("LostTalesMapMarkerFiles.clearServer",
                () -> com.ninuna.losttales.mapmarker.LostTalesMapMarkerFiles.clearServer());
        ServerStep.run("StartingKits.clear",
                () -> com.ninuna.losttales.character.kit.StartingKits.clear());
        ServerStep.run("Accessories.clearServer", () -> Accessories.clearServer());
        ServerStep.run("Armour.clearServer", () -> Armour.clearServer());
        ServerStep.run("MissiveTemplates.clear",
                () -> com.ninuna.losttales.quest.missive.MissiveTemplates.clear());
        ServerStep.run("ProgressionService.clear", () -> ProgressionService.clear());
        ServerStep.run("CountWatch.clear", () -> CountWatch.clear());
        ServerStep.run("PerkRuntime.clearServer", () -> PerkRuntime.clearServer());
        ServerStep.run("PerkCombatEvents.clear", () -> PerkCombatEvents.clear());
        ServerStep.run("PerkAnimalEvents.clear", () -> PerkAnimalEvents.clear());
        ServerStep.run("ArmourEvents.clear", () -> ArmourEvents.clear());
        ServerStep.run("DeedEvents.clear", () -> DeedEvents.clear());
        ServerStep.run("ServerQuestSync.clear", () -> ServerQuestSync.clear());
        ServerStep.run("QuestLogSync.clear", () -> QuestLogSync.clear());
        ServerStep.run("QuestFellows.clear", () -> com.ninuna.losttales.quest.QuestFellows.clear());
        ServerStep.run("QuestOfferPause.clear",
                () -> com.ninuna.losttales.quest.QuestOfferPause.clear());
        ServerStep.run("LostTalesQuestRewardHelper.clear",
                () -> com.ninuna.losttales.quest.LostTalesQuestRewardHelper.clear());
        ServerStep.run("MissiveSeals.clear",
                () -> com.ninuna.losttales.quest.missive.MissiveSeals.clear());
        ServerStep.run("WorldQuests.clear", () -> WorldQuests.clear());
        ServerStep.run("LostTalesMapMarkerSyncManager.clear",
                () -> LostTalesMapMarkerSyncManager.clear());
        ServerStep.run("LostTalesLotrWaystoneTravelAdapter.clear",
                () -> LostTalesLotrWaystoneTravelAdapter.clear());
        ServerStep.run("LostTalesWaystoneVisits.clear",
                () -> com.ninuna.losttales.mapmarker.LostTalesWaystoneVisits.clear());
        ServerStep.run("LostTalesChatRoleRosterWatcher.clear",
                () -> LostTalesChatRoleRosterWatcher.clear());
        ServerStep.run("ChatChannel.resetToBuiltIn", () -> ChatChannel.resetToBuiltIn());
        ServerStep.run("ChatChannelIconCatalog.resetToDefaults",
                () -> ChatChannelIconCatalog.resetToDefaults());
        ServerStep.run("ChatProfanityCatalog.resetToBundled",
                () -> ChatProfanityCatalog.resetToBundled());
        ServerStep.run("ChatRoleCatalog.installServer",
                () -> com.ninuna.losttales.chat.ChatRoleCatalog.installServer(null));
        ServerStep.run("ChatChannelGates.install",
                () -> com.ninuna.losttales.chat.ChatChannelGates.install(null));
        ServerStep.run("LostTalesPermissionCatalog.install",
                () -> com.ninuna.losttales.permission.LostTalesPermissionCatalog.install(null));
        ServerStep.run("ChatCommandText.resetServerNames",
                () -> ChatCommandText.resetServerNames());
        ServerStep.run("ChatMessageIdAllocator.reset", () -> ChatMessageIdAllocator.reset());
        // The save takes the history's last state before the store is
        // cleared: the worlds are saved after this event, and what
        // they write is that snapshot.
        ServerStep.run("ChatHistoryStorage.release", () -> ChatHistoryStorage.release());
        ServerStep.run("ChatHistory.clear", () -> ChatHistory.clear());
        ServerStep.run("ChatConsoleStream.clear", () -> ChatConsoleStream.clear());
        ServerStep.run("ChatReports.clear",
                () -> com.ninuna.losttales.chat.server.ChatReports.clear());
        ServerStep.run("ChatCommandContexts.clear", () -> ChatCommandContexts.clear());
        ServerStep.run("LostTalesChatService.clear", () -> LostTalesChatService.clear());
        ServerStep.run("ChatIdentitySelection.clear", () -> ChatIdentitySelection.clear());
        ServerStep.run("ChatPresenceService.clear", () -> ChatPresenceService.clear());
        ServerStep.run("ChatServerStatus.clear",
                () -> com.ninuna.losttales.chat.server.ChatServerStatus.clear());
        // Missives belong to the world that made them; each
        // player's saved data registers its own again as it loads.
        ServerStep.run("LostTalesQuestRegistry.clearMissives",
                () -> LostTalesQuestRegistry.clearMissives());
        ServerStep.run("LostTalesServerBroadcastHook.clear",
                () -> LostTalesServerBroadcastHook.clear());
        ServerStep.run("ChatArrivals.clear", () -> ChatArrivals.clear());
        ServerStep.run("ChatWelcome.clear", () -> ChatWelcome.clear());
        ServerStep.run("CharacterJoin.clear", () -> CharacterJoin.clear());
        ServerStep.run("CharacterSyncManager.clear",
                () -> com.ninuna.losttales.character.server.CharacterSyncManager.clear());
        ServerStep.run("ChatConsoleCommandHandler.clear", () -> ChatConsoleCommandHandler.clear());
        ServerStep.run("DiscordGameEventRelay.clear", () -> DiscordGameEventRelay.clear());
        ServerStep.run("LostTalesDiscordBridge.resetSession",
                () -> LostTalesDiscordBridge.getInstance().resetSession());
        ServerStep.run("ChatMemberDirectory.clear", () -> ChatMemberDirectory.clear());
        ServerStep.run("ChatMemberWatches.clear",
                () -> com.ninuna.losttales.chat.server.ChatMemberWatches.clear());
        ServerStep.run("ChatAuditLog.onServerStopping", () -> ChatAuditLog.onServerStopping());
        // The clock is forgotten once the stores above are let go: until
        // then whatever asks what is held still gets the world's hold.
        ServerStep.run("WorldClock.reset", () -> WorldClock.reset());
        ServerStep.run("LostTalesMobAggroEventHandler.clearAll",
                () -> LostTalesMobAggroEventHandler.clearAll());
        ServerStep.run("LotrRaceProfileAdapter.clear",
                () -> LotrRaceProfileAdapter.getInstance().clear());
    }
}
