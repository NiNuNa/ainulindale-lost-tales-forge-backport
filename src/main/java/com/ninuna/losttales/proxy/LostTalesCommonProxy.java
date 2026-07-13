package com.ninuna.losttales.proxy;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.character.server.CharacterPlayerEventHandler;
import com.ninuna.losttales.character.server.CharacterRaceGameplayHandler;
import com.ninuna.losttales.character.server.CharacterSpawnOriginHandler;
import com.ninuna.losttales.character.server.CharacterServerPacketDispatcher;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
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
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerDiscoveryPacket;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterAppearanceSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreationCatalogSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterOperationResultPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterSyncPacket;
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
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib3.GeckoLib;

public class LostTalesCommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        LostTalesConfig.load(event.getSuggestedConfigurationFile());
        GeckoLib.initialize();
        LostTalesNetworkHandler.registerCommonPackets();
        LostTalesQuestRegistry.loadFromClasspath();
        LostTalesQuestPlayerEventHandler questPlayerEventHandler = new LostTalesQuestPlayerEventHandler();
        LostTalesQuestObjectiveEventHandler questObjectiveEventHandler = new LostTalesQuestObjectiveEventHandler();
        LostTalesMobAggroEventHandler mobAggroEventHandler = new LostTalesMobAggroEventHandler();
        CharacterPlayerEventHandler characterPlayerEventHandler = new CharacterPlayerEventHandler();
        CharacterRaceGameplayHandler characterRaceGameplayHandler = new CharacterRaceGameplayHandler();
        CharacterSpawnOriginHandler characterSpawnOriginHandler = new CharacterSpawnOriginHandler();
        PartyPlayerEventHandler partyPlayerEventHandler = new PartyPlayerEventHandler();
        LostTalesServerTaskQueue serverTaskQueue = new LostTalesServerTaskQueue();
        LostTalesNetworkPlayerEventHandler networkPlayerEventHandler = new LostTalesNetworkPlayerEventHandler();
        MinecraftForge.EVENT_BUS.register(questPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(characterPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(characterRaceGameplayHandler);
        MinecraftForge.EVENT_BUS.register(characterSpawnOriginHandler);
        MinecraftForge.EVENT_BUS.register(questObjectiveEventHandler);
        MinecraftForge.EVENT_BUS.register(mobAggroEventHandler);
        FMLCommonHandler.instance().bus().register(questPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(questObjectiveEventHandler);
        FMLCommonHandler.instance().bus().register(mobAggroEventHandler);
        FMLCommonHandler.instance().bus().register(characterPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(characterRaceGameplayHandler);
        FMLCommonHandler.instance().bus().register(partyPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(serverTaskQueue);
        FMLCommonHandler.instance().bus().register(networkPlayerEventHandler);

        ELostTalesItem.initAndRegisterItems();
        ELostTalesBlock.initAndRegisterBlocks();
        registerTileEntities();
        ELostTalesEntity.initAndRegisterEntities();
        ELostTalesBiome.initAndRegisterBiomes();
        ELostTalesSpawnList.initAndRegisterSpawnLists();
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(LostTalesMod.instance, new LostTalesGuiHandler());

        ELostTalesStructure.initAndRegisterStructures();
        ELostTalesCrafting.initAndRegisterCrafting();
        ELostTalesFaction.initAndRegisterFactions();
        LostTalesMapMarkerWaypointRegistry.initAndRegisterWaypoints();
        ELostTalesAchievement.initAndRegisterAchievements();

        LostTalesMapOverlay.applyWorldGenerationMap();
        ELostTalesRoad.initAndRegisterRoads();
    }

    public void postInit(FMLPostInitializationEvent event) {
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

    /** Queues client-only packet work. The dedicated-server proxy is a no-op. */
    public void scheduleClientTask(Runnable task) {}

    public void handleCharacterRosterSync(CharacterRosterSyncPacket packet) {}

    public void handleCharacterOperationResult(CharacterOperationResultPacket packet) {}

    public void handleCharacterAppearanceSync(CharacterAppearanceSyncPacket packet) {}

    public void handleCharacterCreationCatalogSync(CharacterCreationCatalogSyncPacket packet) {}

    public void handlePartyStateSync(PartyStateSyncPacket packet) {}

    public void handlePartyOperationResult(PartyOperationResultPacket packet) {}

    public void handlePartyMemberStatusSync(PartyMemberStatusSyncPacket packet) {}

    public void handlePartyTrackingSync(PartyTrackingSyncPacket packet) {}

    public void onServerStarting(FMLServerStartingEvent event) {
        LostTalesServerTaskQueue.startAccepting();
        LostTalesRequestRateLimiter.clear();
        CharacterServerPacketDispatcher.clearSecurityState();
        PartySyncManager.clear();
        PartyMemberStatusSyncManager.clear();
        PartyTrackingSyncManager.clear();
        ELostTalesCommand.initAndRegisterCommands(event);
    }

    public void onServerStopping(FMLServerStoppingEvent event) {
        LostTalesServerTaskQueue.stopAcceptingAndClear();
        LostTalesRequestRateLimiter.clear();
        CharacterServerPacketDispatcher.clearSecurityState();
        PartySyncManager.clear();
        PartyMemberStatusSyncManager.clear();
        PartyTrackingSyncManager.clear();
        LostTalesMobAggroEventHandler.clearAll();
    }
}
