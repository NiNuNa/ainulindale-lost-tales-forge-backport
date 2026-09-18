package com.ninuna.losttales.proxy;

import com.ninuna.losttales.chat.server.ChatIdentitySelection;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import java.io.File;
import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.accessory.AccessoryBootstrap;
import com.ninuna.losttales.accessory.player.AccessoryPlayerEventHandler;
import com.ninuna.losttales.accessory.player.AccessoryInventorySyncManager;
import com.ninuna.losttales.accessory.effect.AccessoryConcealmentEventHandler;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import com.ninuna.losttales.block.ELostTalesBlock;
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
import com.ninuna.losttales.character.server.CharacterStateCheckpointHandler;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.command.ELostTalesCommand;
import com.ninuna.losttales.compat.discord.DiscordGameEventRelay;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.crafting.ELostTalesCrafting;
import com.ninuna.losttales.entity.ELostTalesEntity;
import com.ninuna.losttales.event.LostTalesMobAggroEventHandler;
import com.ninuna.losttales.event.LostTalesQuestObjectiveEventHandler;
import com.ninuna.losttales.event.LostTalesQuestPlayerEventHandler;
import com.ninuna.losttales.faction.ELostTalesFaction;
import com.ninuna.losttales.gui.LostTalesGuiHandler;
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
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.AccessoryInventorySyncPacket;
import com.ninuna.losttales.network.packet.AccessoryEffectSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterAppearanceSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreationCatalogSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterOperationResultPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterSyncPacket;
import com.ninuna.losttales.network.packet.character.LoreCharacterSyncPacket;
import com.ninuna.losttales.network.packet.party.PartyMemberStatusSyncPacket;
import com.ninuna.losttales.network.packet.party.PartyOperationResultPacket;
import com.ninuna.losttales.network.packet.party.PartyStateSyncPacket;
import com.ninuna.losttales.network.packet.party.PartyTrackingSyncPacket;
import com.ninuna.losttales.party.server.PartyMemberStatusSyncManager;
import com.ninuna.losttales.party.server.PartyTrackingSyncManager;
import com.ninuna.losttales.party.server.PartyPlayerEventHandler;
import com.ninuna.losttales.party.server.PartySyncManager;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
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
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import com.ninuna.losttales.chat.ChatChannelIconCatalog;
import com.ninuna.losttales.chat.moderation.ChatAuditLog;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.server.ChatConsoleCommandHandler;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.server.ChatCommandContexts;
import com.ninuna.losttales.chat.server.ChatConsoleStream;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.chat.server.ChatHistoryStorage;
import com.ninuna.losttales.chat.server.ChatMemberDirectory;
import com.ninuna.losttales.chat.server.LostTalesChatRoleRosterWatcher;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.chat.server.LostTalesServerBroadcastHook;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import com.ninuna.losttales.world.room.CharacterRoomWorldHandler;
import com.ninuna.losttales.world.room.CharacterRoomWorldType;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneGenerationHandler;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatPresenceSyncPacket;
import com.ninuna.losttales.chat.server.ChatPresenceService;
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
        LoreCharacterRegistry.load(event.getModConfigurationDirectory());
        GeckoLib.initialize();
        LostTalesNetworkHandler.registerCommonPackets();
        // Before any world can load: a world names its type by name.
        CharacterRoomWorldType.register();
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
        CharacterRaceGameplayHandler characterRaceGameplayHandler = new CharacterRaceGameplayHandler();
        CharacterSpawnOriginHandler characterSpawnOriginHandler = new CharacterSpawnOriginHandler();
        CharacterStateCheckpointHandler characterStateCheckpointHandler =
                new CharacterStateCheckpointHandler();
        PartyPlayerEventHandler partyPlayerEventHandler = new PartyPlayerEventHandler();
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
        LostTalesChatRoleRosterWatcher chatRoleRosterWatcher =
                new LostTalesChatRoleRosterWatcher();
        MinecraftForge.EVENT_BUS.register(questPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(accessoryPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(accessoryConcealmentEventHandler);
        MinecraftForge.EVENT_BUS.register(characterLifecycleStateTracker);
        MinecraftForge.EVENT_BUS.register(characterPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(characterRaceGameplayHandler);
        MinecraftForge.EVENT_BUS.register(characterSpawnOriginHandler);
        MinecraftForge.EVENT_BUS.register(new LotrHiredUnitCustodyHandler());
        MinecraftForge.EVENT_BUS.register(questObjectiveEventHandler);
        MinecraftForge.EVENT_BUS.register(mobAggroEventHandler);
        MinecraftForge.EVENT_BUS.register(projectileAimHandler);
        MinecraftForge.EVENT_BUS.register(chargeService);
        MinecraftForge.EVENT_BUS.register(waystoneGenerationHandler);
        MinecraftForge.EVENT_BUS.register(new CharacterRoomWorldHandler());
        MinecraftForge.EVENT_BUS.register(new ChatConsoleCommandHandler());
        MinecraftForge.TERRAIN_GEN_BUS.register(waystoneGenerationHandler);
        GameRegistry.registerWorldGenerator(
                waystoneGenerationHandler, 1000);
        FMLCommonHandler.instance().bus().register(questPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(accessoryPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(questObjectiveEventHandler);
        FMLCommonHandler.instance().bus().register(mobAggroEventHandler);
        FMLCommonHandler.instance().bus().register(characterLifecycleStateTracker);
        FMLCommonHandler.instance().bus().register(characterPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(characterRaceGameplayHandler);
        FMLCommonHandler.instance().bus().register(characterStateCheckpointHandler);
        FMLCommonHandler.instance().bus().register(partyPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(serverTaskQueue);
        FMLCommonHandler.instance().bus().register(networkPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(chargeService);
        FMLCommonHandler.instance().bus().register(
                waystoneGenerationHandler);
        FMLCommonHandler.instance().bus().register(chatRoleRosterWatcher);

        ELostTalesItem.initAndRegisterItems();
        AccessoryBootstrap.initialize();
        ELostTalesBlock.initAndRegisterBlocks();
        registerTileEntities();
        ELostTalesEntity.initAndRegisterEntities();
        ELostTalesBiome.initAndRegisterBiomes();
        ELostTalesSpawnList.initAndRegisterSpawnLists();
    }

    public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean(
                LostTalesClassTransformer.ACCESSORY_CONTAINER_ACTIVE_PROPERTY)
                || !Boolean.getBoolean(
                LostTalesClassTransformer.ACCESSORY_DEATH_ACTIVE_PROPERTY)) {
            FMLLog.severe("[%s] Accessory lifecycle transformers are incomplete; "
                            + "the ring slot will reject server-side insertion",
                    LostTalesMetaData.MOD_ID);
        }
        NetworkRegistry.INSTANCE.registerGuiHandler(LostTalesMod.instance, new LostTalesGuiHandler());

        ELostTalesStructure.initAndRegisterStructures();
        ELostTalesCrafting.initAndRegisterCrafting();
        ELostTalesFaction.initAndRegisterFactions();
        LostTalesMapOverlay.applyWorldGenerationMap();
        LostTalesMapMarkerWaypointRegistry.initAndRegisterWaypoints();
        ELostTalesAchievement.initAndRegisterAchievements();

        ELostTalesRoad.initAndRegisterRoads();
    }

    public void postInit(FMLPostInitializationEvent event) {
        AccessoryBootstrap.freeze();
        // Run after Lost Tales has registered its additional LOTR factions so
        // the immutable character-creation catalogue includes them as well.
        LotrCharacterAdapter.getInstance().initialize();
    }

    protected void registerTileEntities() {
        // Keep the original short IDs for world-save compatibility. Registering from the
        // common proxy ensures dedicated servers know these tile entities too.
        GameRegistry.registerTileEntity(LostTalesTileEntityUrn.class, "pot");
        GameRegistry.registerTileEntity(LostTalesTileEntityStatue.class, "statue");
        GameRegistry.registerTileEntity(LostTalesTileEntityLamp.class, "lamp");
        GameRegistry.registerTileEntity(LostTalesTileEntityPlushie.class, "plushie");
        GameRegistry.registerTileEntity(LostTalesTileEntityMissiveBoard.class, "missive_board");
        GameRegistry.registerTileEntity(
                LostTalesTileEntityWaystone.class, "losttales_waystone");
    }

    /**
     * Client-only GUI construction hook. The common/server proxy returns null so
     * dedicated servers never load client GUI classes.
     */
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    /**
     * Client-only missive reader hook. The common/server proxy is a no-op so
     * dedicated servers never load client GUI classes.
     */
    public void openMissiveLetterGui(EntityPlayer player, ItemStack stack, int inventorySlot) {}

    /**
     * Client-bound packet hooks. The common/server proxy deliberately does
     * nothing here; the client proxy overrides these methods and updates
     * client-only caches. Packet handlers can safely call through this proxy on
     * either physical side without loading Minecraft client classes on a
     * dedicated server.
     */
    public void handleQuickLootContainerSync(LostTalesQuickLootContainerSyncPacket packet) {}

    public void handleQuestSync(LostTalesQuestSyncPacket packet) {}

    public void handleMobAggroSync(LostTalesMobAggroSyncPacket packet) {}

    public void handleMapMarkerDiscovery(LostTalesMapMarkerDiscoveryPacket packet) {}

    public void handleMapMarkerSnapshot(
            LostTalesMapMarkerSnapshotPacket packet) {}

    public void handleWaystoneState(
            LostTalesWaystoneStatePacket packet) {}

    public void handleChargeTierSync(LostTalesChargeTierSyncPacket packet) {}

    public void handleAccessoryInventorySync(AccessoryInventorySyncPacket packet) {}

    public void handleAccessoryEffectSync(AccessoryEffectSyncPacket packet) {}

    public boolean isAccessoryConcealed(EntityPlayer player) {
        return false;
    }

    /** Queues client-only packet work. The dedicated-server proxy is a no-op. */
    public void scheduleClientTask(Runnable task) {}

    public void handleCharacterRosterSync(CharacterRosterSyncPacket packet) {}

    public void handleCharacterOperationResult(CharacterOperationResultPacket packet) {}

    public void handleCharacterAppearanceSync(CharacterAppearanceSyncPacket packet) {}

    public void handleCharacterCreationCatalogSync(CharacterCreationCatalogSyncPacket packet) {}

    public void handleLoreCharacterSync(LoreCharacterSyncPacket packet) {}

    public void handlePartyStateSync(PartyStateSyncPacket packet) {}

    public void handlePartyOperationResult(PartyOperationResultPacket packet) {}

    public void handlePartyMemberStatusSync(PartyMemberStatusSyncPacket packet) {}

    public void handlePartyTrackingSync(PartyTrackingSyncPacket packet) {}

    public void handleChatIdentity(LostTalesChatIdentitySyncPacket packet) {}

    public void handleChatPresence(LostTalesChatPresenceSyncPacket packet) {}

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

    public void onServerStarting(FMLServerStartingEvent event) {
        // Before the reload, not after: the reload registers the channels
        // this server's config defines, and resetting afterwards would
        // take them straight back out again. Every other static store
        // below holds nothing the reload puts there.
        ChatChannel.resetToBuiltIn();
        // Then the config, so the roles, gates, channels and settings
        // everything below reads are the ones on disk now: the files may
        // have been edited since pre-init, and a client hosting a world
        // installs its catalogue here rather than at pre-init.
        LostTalesConfig.reload();
        CharacterLifecycleStateTracker.markServerStarting();
        initializeLoreCharacterOwnership(event);
        CharacterStateCheckpointHandler.reset();
        CharacterSwitchCoordinator.getInstance().clearAllRuntimeState();
        AccessoryInventorySyncManager.clearAll();
        AccessoryEffectService.clearAll();
        LostTalesServerTaskQueue.startAccepting();
        LostTalesRequestRateLimiter.clear();
        LostTalesThirdPersonAimService.clear();
        LostTalesChargeService.clear();
        PartySyncManager.clear();
        PartyMemberStatusSyncManager.clear();
        PartyTrackingSyncManager.clear();
        LostTalesChatRoleRosterWatcher.clear();
        ChatMessageIdAllocator.reset();
        ChatHistory.clear();
        ChatConsoleStream.clear();
        // After the channels and the config above, since every kept
        // line names its channel by id: the save's recent lines and the
        // console's kept events come back as the live stores, and the
        // save follows them from here.
        ChatHistoryStorage.restore(event.getServer());
        // Straight after the history, whose kept messages decide which of
        // the save's Discord links come back, and before the bridge
        // starts below. Every server gets a map of its own, a character
        // room included, so nothing reaches it from the world before.
        LostTalesDiscordBridge.getInstance().restoreLinks(event.getServer());
        ChatCommandContexts.clear();
        LostTalesChatService.clear();
        ChatIdentitySelection.clear();
        ChatPresenceService.clear();
        // Generated quests belong to the world that made them; each
        // player's saved data registers its own again as it loads.
        LostTalesQuestRegistry.clearRuntimeQuests();
        LostTalesServerBroadcastHook.clear();
        DiscordGameEventRelay.clear();
        ChatMemberDirectory.clear();
        LostTalesChatService.console(ChatConsoleEvent.Kind.SERVER,
                ChatConsoleEvent.Severity.INFO, "Server", "Server started");
        ChatAuditLog.onServerStarting();
        LostTalesMobAggroEventHandler.clearAll();
        LotrRaceProfileAdapter.getInstance().clear();
        ELostTalesCommand.initAndRegisterCommands(event);
        // A character room is a private visit; Discord is not told of it.
        if (!CharacterRoomWorldType.isRoomServer(event.getServer())) {
            LostTalesDiscordBridge.getInstance().start();
        }
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
            } else {
                LoreCharacterTransferCoordinator.getInstance().recoverAll(
                        event.getServer().worldServerForDimension(0));
            }
        } catch (RuntimeException exception) {
            FMLLog.severe("[%s] Failed to initialize lore-character ownership storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
        }
    }

    /**
     * The server accepts players from here on: the OOC line saying so,
     * which the bridge's embed is linked to, and the bridge's clock.
     */
    public void onServerStarted(FMLServerStartedEvent event) {
        if (CharacterRoomWorldType.isRoomServer(MinecraftServer.getServer())) {
            return;
        }
        LostTalesServerBroadcastHook.announceServer(MinecraftServer.getServer(),
                true);
        LostTalesDiscordBridge.getInstance().onServerStarted();
    }

    public void onServerStopping(FMLServerStoppingEvent event) {
        // The Console's entry for the stop, the pair of the one the start
        // records: shown to its readers still online, and kept by the
        // history's snapshot below, so the Console shows it when the
        // server is next up. First, before the ids are reset below.
        LostTalesChatService.console(ChatConsoleEvent.Kind.SERVER,
                ChatConsoleEvent.Severity.INFO, "Server", "Server stopped");
        // The OOC line saying the server is going down, kept by the same
        // snapshot, and the bridge's farewell linked to it; then the
        // offline topic. All are queued before the stop, which gives the
        // worker a bounded moment to send them.
        if (!CharacterRoomWorldType.isRoomServer(MinecraftServer.getServer())) {
            LostTalesServerBroadcastHook.announceServer(
                    MinecraftServer.getServer(), false);
        }
        LostTalesDiscordBridge.getInstance().onServerStopping();
        LostTalesDiscordBridge.getInstance().stop();
        // Once the bridge has stopped adding to them, the links go to the
        // save the worlds are written with after this event, and the live
        // map is emptied for the next world.
        LostTalesDiscordBridge.getInstance().releaseLinks();
        LostTalesServerTaskQueue.stopAcceptingAndClear();
        CharacterLifecycleStateTracker.markServerStopping();
        int checkpointed = CharacterSwitchCoordinator.getInstance()
                .checkpointAllOnlinePlayers(MinecraftServer.getServer());
        FMLLog.info("[%s] Shutdown character checkpoint completed for %d online accounts",
                LostTalesMetaData.MOD_ID, Integer.valueOf(checkpointed));
        CharacterStateCheckpointHandler.reset();
        CharacterSwitchCoordinator.getInstance().clearAllRuntimeState();
        AccessoryInventorySyncManager.clearAll();
        AccessoryEffectService.clearAll();
        LostTalesRequestRateLimiter.clear();
        LostTalesThirdPersonAimService.clear();
        LostTalesChargeService.clear();
        PartySyncManager.clear();
        PartyMemberStatusSyncManager.clear();
        PartyTrackingSyncManager.clear();
        LostTalesChatRoleRosterWatcher.clear();
        ChatChannel.resetToBuiltIn();
        ChatChannelIconCatalog.resetToDefaults();
        ChatProfanityCatalog.resetToBundled();
        ChatMessageIdAllocator.reset();
        // The save takes the history's last state before the store is
        // cleared: the worlds are saved after this event, and what
        // they write is that snapshot.
        ChatHistoryStorage.release();
        ChatHistory.clear();
        ChatConsoleStream.clear();
        ChatCommandContexts.clear();
        LostTalesChatService.clear();
        ChatIdentitySelection.clear();
        ChatPresenceService.clear();
        // Generated quests belong to the world that made them; each
        // player's saved data registers its own again as it loads.
        LostTalesQuestRegistry.clearRuntimeQuests();
        LostTalesServerBroadcastHook.clear();
        DiscordGameEventRelay.clear();
        ChatMemberDirectory.clear();
        ChatAuditLog.onServerStopping();
        LostTalesMobAggroEventHandler.clearAll();
        LotrRaceProfileAdapter.getInstance().clear();
    }
}
