package com.ninuna.losttales.proxy;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.event.LostTalesClientEventHandler;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.render.renderer.item.LostTalesItemRendererArmor3D;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererLamp;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererPlushie;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererStatue;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererUrn;
import com.ninuna.losttales.config.client.LostTalesConfigGuiEventHandler;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneGuard;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneMan;
import com.ninuna.losttales.gui.ELostTalesMapLabels;
import com.ninuna.losttales.item.armor.LostTalesItemArmor3D;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import lotr.client.render.entity.LOTRRenderBreeMan;
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

        RenderingRegistry.registerEntityRenderingHandler(LostTalesEntityOdaneMan.class, new LOTRRenderBreeMan());
        RenderingRegistry.registerEntityRenderingHandler(LostTalesEntityOdaneGuard.class, new LOTRRenderBreeMan());

        super.init(event);
    }

    @Override
    public void handleQuickLootContainerSync(LostTalesQuickLootContainerSyncPacket packet) {
        if (packet != null) {
            LostTalesClientQuickLootCache.update(packet.getX(), packet.getY(), packet.getZ(), packet.getTitle(), packet.isSealed(), packet.getItems());
        }
    }

    @Override
    public void handleQuestSync(LostTalesQuestSyncPacket packet) {
        if (packet != null) {
            LostTalesClientQuestNotificationStore.notifyForIncomingSync(packet.getActiveQuests(), packet.getCompletedQuestIds());
            LostTalesClientMapMarkerStore.setDynamicMarkers(packet.getDynamicMapMarkers());
            LostTalesClientQuestProgressStore.update(packet.getActiveQuests(), packet.getCompletedQuestIds(), packet.getPinnedQuestIds(), packet.getDiscoveredMarkerIds(), packet.getPinnedMapMarkerId());
        }
    }

    @Override
    public void handleMobAggroSync(LostTalesMobAggroSyncPacket packet) {
        if (packet != null) {
            LostTalesClientMobAggroCache.accept(packet.getEntityIds());
        }
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}