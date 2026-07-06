package com.ninuna.losttales.proxy;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.config.client.LostTalesConfigGuiEventHandler;
import com.ninuna.losttales.eventhandler.LostTalesClientEventHandler;
import com.ninuna.losttales.gui.ELostTalesMapLabels;
import com.ninuna.losttales.item.armor.LostTalesItemArmor3D;
import com.ninuna.losttales.rendering.renderer.tileentity.LostTalesTileEntityRendererLamp;
import com.ninuna.losttales.rendering.renderer.tileentity.LostTalesTileEntityRendererPlushie;
import com.ninuna.losttales.rendering.renderer.tileentity.LostTalesTileEntityRendererUrn;
import com.ninuna.losttales.rendering.renderer.item.LostTalesItemRendererArmor3D;
import com.ninuna.losttales.rendering.renderer.tileentity.LostTalesTileEntityRendererStatue;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.client.network.LostTalesQuickLootContainerSyncHandler;
import com.ninuna.losttales.client.network.LostTalesQuestSyncHandler;
import com.ninuna.losttales.client.network.LostTalesMobAggroSyncHandler;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib3.renderers.geo.GeoArmorRenderer;

public class LostTalesClientProxy extends LostTalesCommonProxy {

    private LostTalesClientEventHandler clientEventHandler;
    private LostTalesKeyBindings keyBindings;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        clientEventHandler = new LostTalesClientEventHandler();
        keyBindings = new LostTalesKeyBindings();
        keyBindings.register();
        MinecraftForge.EVENT_BUS.register(keyBindings);
        FMLCommonHandler.instance().bus().register(keyBindings);
        FMLCommonHandler.instance().bus().register(clientEventHandler);
        FMLCommonHandler.instance().bus().register(new LostTalesConfigGuiEventHandler());
        LostTalesNetworkHandler.CHANNEL.registerMessage(LostTalesQuickLootContainerSyncHandler.class, LostTalesQuickLootContainerSyncPacket.class, 2, Side.CLIENT);
        LostTalesNetworkHandler.CHANNEL.registerMessage(LostTalesQuestSyncHandler.class, LostTalesQuestSyncPacket.class, 3, Side.CLIENT);
        LostTalesNetworkHandler.CHANNEL.registerMessage(LostTalesMobAggroSyncHandler.class, LostTalesMobAggroSyncPacket.class, 5, Side.CLIENT);
        ELostTalesMapLabels.initAndRegisterMapLabels();

        GeoArmorRenderer.registerArmorRenderer(LostTalesItemArmor3D.class, new LostTalesItemRendererArmor3D());

        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(clientEventHandler);

        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityUrn.class, new LostTalesTileEntityRendererUrn());
        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityStatue.class, new LostTalesTileEntityRendererStatue());
        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityLamp.class, new LostTalesTileEntityRendererLamp());
        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityPlushie.class, new LostTalesTileEntityRendererPlushie());

        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}