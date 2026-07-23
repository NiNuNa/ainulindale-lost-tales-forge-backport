package com.ninuna.losttales.proxy;

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
import com.ninuna.losttales.character.server.CharacterStateCheckpointHandler;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.server.CharacterServerPacketDispatcher;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.command.ELostTalesCommand;
import com.ninuna.losttales.config.LostTalesConfig;
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
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneGenerationHandler;
import software.bernie.geckolib3.GeckoLib;

public class LostTalesCommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        LostTalesConfig.load(event.getSuggestedConfigurationFile());
        LoreCharacterRegistry.load(event.getModConfigurationDirectory());
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
        MinecraftForge.EVENT_BUS.register(questPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(accessoryPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(accessoryConcealmentEventHandler);
        MinecraftForge.EVENT_BUS.register(characterLifecycleStateTracker);
        MinecraftForge.EVENT_BUS.register(characterPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(characterRaceGameplayHandler);
        MinecraftForge.EVENT_BUS.register(characterSpawnOriginHandler);
        MinecraftForge.EVENT_BUS.register(questObjectiveEventHandler);
        MinecraftForge.EVENT_BUS.register(mobAggroEventHandler);
        MinecraftForge.EVENT_BUS.register(projectileAimHandler);
        MinecraftForge.EVENT_BUS.register(chargeService);
        MinecraftForge.EVENT_BUS.register(waystoneGenerationHandler);
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

    public void onServerStarting(FMLServerStartingEvent event) {
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
        CharacterServerPacketDispatcher.clearSecurityState();
        PartySyncManager.clear();
        PartyMemberStatusSyncManager.clear();
        PartyTrackingSyncManager.clear();
        ELostTalesCommand.initAndRegisterCommands(event);
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

    public void onServerStopping(FMLServerStoppingEvent event) {
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
        CharacterServerPacketDispatcher.clearSecurityState();
        PartySyncManager.clear();
        PartyMemberStatusSyncManager.clear();
        PartyTrackingSyncManager.clear();
        LostTalesMobAggroEventHandler.clearAll();
    }
}
