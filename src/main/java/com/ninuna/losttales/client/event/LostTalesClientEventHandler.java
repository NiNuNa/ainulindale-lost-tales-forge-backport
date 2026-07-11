package com.ninuna.losttales.client.event;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.character.CharacterClientTaskQueue;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterCreationCatalogCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientCharacterRacePhysics;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerNotificationStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.render.renderer.item.LostTalesItemRendererHammer;
import com.ninuna.losttales.client.render.renderer.item.LostTalesRendererLargeItems;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.mapmarker.LostTalesMapMarkerHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesWorldQuestMarkerRenderer;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.weapon.LostTalesItemBattleaxe;
import com.ninuna.losttales.item.weapon.LostTalesItemDagger;
import com.ninuna.losttales.item.weapon.LostTalesItemSpear;
import com.ninuna.losttales.item.weapon.LostTalesItemSword;
import com.ninuna.losttales.world.map.LostTalesMapOverlay;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.client.MinecraftForgeClient;
import lotr.client.gui.LOTRGuiMap;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

public class LostTalesClientEventHandler implements IResourceManagerReloadListener {

    public LostTalesClientEventHandler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resManager) {
        LostTalesInputIconRenderer.onResourceManagerReload(resManager);
        LostTalesMapOverlay.applyClientMap();
        LostTalesClientMapMarkerStore.reloadFromResources(resManager);
        LostTalesClientQuestDefinitionStore.reloadFromResources(resManager);
    }

    @SubscribeEvent
    public void onClientDisconnect(ClientDisconnectionFromServerEvent event) {
        LostTalesClientQuestProgressStore.clear();
        LostTalesClientQuestNotificationStore.clear();
        LostTalesClientQuestDefinitionStore.clearDynamicQuestDefinitions();
        LostTalesClientMapMarkerNotificationStore.clear();
        LostTalesClientMapMarkerStore.clearDynamicMarkers();
        LostTalesClientMobAggroCache.clear();
        LostTalesClientQuickLootCache.clear();
        ClientCharacterRosterCache.clear();
        ClientCharacterAppearanceCache.clear();
        ClientCharacterCreationCatalogCache.clear();
        CharacterClientTaskQueue.clear();
        LostTalesQuickLootHudRenderer.resetHud();
        LotrRaceProfileAdapter.getInstance().clear();
    }

    @SubscribeEvent
    public void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || event.player == null || event.player.worldObj == null
                || !event.player.worldObj.isRemote) {
            return;
        }
        ClientCharacterRacePhysics.apply(event.player);
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
    public void replaceLotrMapGui(GuiOpenEvent event) {
        if (event.gui != null && event.gui.getClass() == LOTRGuiMap.class) {
            event.gui = new LostTalesLotrMapGui();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderHud(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            LostTalesQuickLootHudRenderer.render(Minecraft.getMinecraft());
            LostTalesCompassHudRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
            LostTalesMapMarkerHudRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
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