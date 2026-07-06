package com.ninuna.losttales.proxy;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.entity.ELostTalesEntity;
import com.ninuna.losttales.faction.ELostTalesFaction;
import com.ninuna.losttales.eventhandler.LostTalesQuestPlayerEventHandler;
import com.ninuna.losttales.eventhandler.LostTalesQuestObjectiveEventHandler;
import com.ninuna.losttales.world.biome.ELostTalesBiome;
import com.ninuna.losttales.world.spawning.ELostTalesSpawnList;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.command.ELostTalesCommand;
import com.ninuna.losttales.crafting.ELostTalesCrafting;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.util.LostTalesUtil;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.util.ResourceLocation;
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
        MinecraftForge.EVENT_BUS.register(questPlayerEventHandler);
        MinecraftForge.EVENT_BUS.register(questObjectiveEventHandler);
        FMLCommonHandler.instance().bus().register(questPlayerEventHandler);
        FMLCommonHandler.instance().bus().register(questObjectiveEventHandler);

        ELostTalesItem.initAndRegisterItems();
        ELostTalesBlock.initAndRegisterBlocks();
        registerTileEntities();
        ELostTalesEntity.initAndRegisterEntities();
        ELostTalesBiome.initAndRegisterBiomes();
        ELostTalesSpawnList.initAndRegisterSpawnLists();
    }

    public void init(FMLInitializationEvent event) {
        ELostTalesCrafting.initAndRegisterCrafting();
        ELostTalesFaction.initAndRegisterFactions();
        ELostTalesAchievement.initAndRegisterAchievements();

        LostTalesUtil.setWorldGenMapImage(new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/map/map.png"));
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

    public void onServerStarting(FMLServerStartingEvent event) {
        ELostTalesCommand.initAndRegisterCommands(event);
    }
}