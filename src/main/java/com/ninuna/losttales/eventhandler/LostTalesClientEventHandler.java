package com.ninuna.losttales.eventhandler;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesWorldQuestMarkerRenderer;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.weapon.LostTalesItemBattleaxe;
import com.ninuna.losttales.item.weapon.LostTalesItemDagger;
import com.ninuna.losttales.item.weapon.LostTalesItemSpear;
import com.ninuna.losttales.item.weapon.LostTalesItemSword;
import com.ninuna.losttales.rendering.renderer.item.LostTalesItemRendererHammer;
import com.ninuna.losttales.rendering.renderer.item.LostTalesRendererLargeItems;
import com.ninuna.losttales.util.LostTalesUtil;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.Arrays;

public class LostTalesClientEventHandler implements IResourceManagerReloadListener {

    public LostTalesClientEventHandler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resManager) {
        LostTalesUtil.setClientMapImage(new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/map/map.png"));
        LostTalesClientMapMarkerStore.reloadFromResources(resManager);
        LostTalesClientQuestDefinitionStore.reloadFromResources(resManager);
    }

    @SubscribeEvent
    public void onClientDisconnect(ClientDisconnectionFromServerEvent event) {
        LostTalesClientQuestProgressStore.clear();
        LostTalesClientQuestNotificationStore.clear();
        LostTalesClientMapMarkerStore.clearDynamicMarkers();
        LostTalesClientMobAggroCache.clear();
        LostTalesClientQuickLootCache.clear();
        LostTalesQuickLootHudRenderer.resetHud();
    }

    @SubscribeEvent
    public void registerIcons(TextureStitchEvent.Pre event) {
        TextureMap map = event.map;

        if (map.getTextureType() == 1) {
            Arrays.stream(ELostTalesItem.values())
                    .forEach(item -> {
                        //Register Large Icons and Item Renderers.
                        if (item.getItem() instanceof LostTalesItemSword || item.getItem() instanceof LostTalesItemDagger || item.getItem() instanceof LostTalesItemSpear || item.getItem() instanceof LostTalesItemBattleaxe) {
                            if (item.getItem().getUnlocalizedName().equals("item.dains_hammer")) {
                                MinecraftForgeClient.registerItemRenderer(item.getItem(), new LostTalesItemRendererHammer());
                            } else {
                                item.setLargeIcon(map.registerIcon(getTexturePath(item.getItem().getUnlocalizedName().substring(5))));
                                MinecraftForgeClient.registerItemRenderer(item.getItem(), new LostTalesRendererLargeItems());
                            }
                        }
                    });
        }
    }


    @SubscribeEvent
    public void renderWorldMarkers(RenderWorldLastEvent event) {
        try {
            LostTalesWorldQuestMarkerRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
        } catch (Throwable ignored) {
            // World-space marker rendering should never crash the client render tick.
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderHud(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            LostTalesQuickLootHudRenderer.render(Minecraft.getMinecraft());
            LostTalesCompassHudRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
            LostTalesQuestHudRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
        }
    }

    private String getTexturePath(String fileName) {
        if (fileName.startsWith("community")) {
            return LostTalesMetaData.MOD_ID + ":community/large/" + fileName;
        } else {
            return LostTalesMetaData.MOD_ID + ":large/" + fileName;
        }
    }
}