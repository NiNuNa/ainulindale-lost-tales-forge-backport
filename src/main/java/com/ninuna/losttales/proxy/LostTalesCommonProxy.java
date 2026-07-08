package com.ninuna.losttales.proxy;

import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.command.ELostTalesCommand;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.crafting.ELostTalesCrafting;
import com.ninuna.losttales.entity.ELostTalesEntity;
import com.ninuna.losttales.event.LostTalesMobAggroEventHandler;
import com.ninuna.losttales.event.LostTalesQuestObjectiveEventHandler;
import com.ninuna.losttales.event.LostTalesQuestPlayerEventHandler;
import com.ninuna.losttales.faction.ELostTalesFaction;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.world.biome.ELostTalesBiome;
import com.ninuna.losttales.world.map.LostTalesMapOverlay;
import com.ninuna.losttales.world.map.road.ELostTalesRoad;
import com.ninuna.losttales.world.spawning.ELostTalesSpawnList;
import com.ninuna.losttales.world.structure.ELostTalesStructure;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
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
        MinecraftForge.EVENT_BUS.register(questPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(questObjectiveEventHandler);
        FMLCommonHandler.instance().bus().register(questPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(questObjectiveEventHandler);
        FMLCommonHandler.instance().bus().register(mobAggroEventHandler);

        ELostTalesItem.initAndRegisterItems();
        ELostTalesBlock.initAndRegisterBlocks();
        registerTileEntities();
        ELostTalesEntity.initAndRegisterEntities();
        ELostTalesBiome.initAndRegisterBiomes();
        ELostTalesSpawnList.initAndRegisterSpawnLists();
    }

    public void init(FMLInitializationEvent event) {
        ELostTalesStructure.initAndRegisterStructures();
        ELostTalesCrafting.initAndRegisterCrafting();
        ELostTalesFaction.initAndRegisterFactions();
        ELostTalesAchievement.initAndRegisterAchievements();

        LostTalesMapOverlay.applyWorldGenerationMap();
        ELostTalesRoad.initAndRegisterRoads();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    protected void registerTileEntities() {
        // Keep the original short IDs for world-save compatibility. Registering from the
        // common proxy ensures dedicated servers know these tile entities too.
        GameRegistry.registerTileEntity(LostTalesTileEntityUrn.class, "pot");
        GameRegistry.registerTileEntity(LostTalesTileEntityStatue.class, "statue");
        GameRegistry.registerTileEntity(LostTalesTileEntityLamp.class, "lamp");
        GameRegistry.registerTileEntity(LostTalesTileEntityPlushie.class, "plushie");
    }

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

    public void onServerStarting(FMLServerStartingEvent event) {
        ELostTalesCommand.initAndRegisterCommands(event);
    }
}