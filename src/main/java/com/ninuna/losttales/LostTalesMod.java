package com.ninuna.losttales;

import com.ninuna.losttales.proxy.LostTalesCommonProxy;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
        modid = LostTalesMetaData.MOD_ID,
        name = LostTalesMetaData.MOD_NAME,
        version = LostTalesMetaData.MOD_VERSION,
        dependencies = LostTalesMetaData.MOD_DEPENDENCIES,
        acceptedMinecraftVersions = LostTalesMetaData.MC_VERSION,
        guiFactory = LostTalesMetaData.GUI_FACTORY_CLASS
)
public class LostTalesMod {

    @Mod.Instance(LostTalesMetaData.MOD_ID)
    public static LostTalesMod instance = new LostTalesMod();

    @SidedProxy(clientSide = LostTalesMetaData.CLIENT_PROXY_CLASS, serverSide = LostTalesMetaData.SERVER_PROXY_CLASS)
    public static LostTalesCommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        proxy.onServerStarting(event);
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        proxy.onServerStopping(event);
    }
}
