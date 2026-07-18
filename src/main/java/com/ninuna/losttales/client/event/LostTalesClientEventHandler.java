package com.ninuna.losttales.client.event;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.accessory.inventory.LostTalesContainerPlayer;
import com.ninuna.losttales.accessory.player.AccessoryInventory;
import com.ninuna.losttales.client.accessory.ClientAccessoryEffectCache;
import com.ninuna.losttales.client.accessory.WraithWorldVisualEffect;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.camera.ThirdPersonCameraRuntime;
import com.ninuna.losttales.client.camera.ThirdPersonCrosshairRenderer;
import com.ninuna.losttales.client.camera.ThirdPersonExplosionMotionHandler;
import com.ninuna.losttales.client.camera.ThirdPersonHeadRenderHook;
import com.ninuna.losttales.client.camera.ThirdPersonProjectileTrajectoryRenderer;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.character.CharacterClientTaskQueue;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterCreationCatalogCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientCharacterRacePhysics;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.gui.LostTalesGuiInventory;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerNotificationStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.ninuna.losttales.client.party.ClientPartyMemberStatusCache;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.render.player.LostTalesPlayerCapeRenderHook;
import com.ninuna.losttales.client.render.renderer.item.LostTalesItemRendererHammer;
import com.ninuna.losttales.client.render.renderer.item.LostTalesRendererLargeItems;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.mapmarker.LostTalesMapMarkerHudRenderer;
import com.ninuna.losttales.gui.hud.party.LostTalesPartyHudRenderer;
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
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.client.MinecraftForgeClient;
import lotr.client.gui.LOTRGuiMap;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;

public class LostTalesClientEventHandler implements IResourceManagerReloadListener {

    public LostTalesClientEventHandler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resManager) {
        WraithWorldVisualEffect.onResourceManagerReload();
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
        ClientLoreCharacterCache.clear();
        ClientPartyStateCache.clear();
        ClientPartyMemberStatusCache.clear();
        ClientPartyTrackingCache.clear();
        ClientAccessoryEffectCache.clear();
        WraithWorldVisualEffect.reset();
        CharacterClientTaskQueue.clear();
        LostTalesQuickLootHudRenderer.resetHud();
        LotrRaceProfileAdapter.getInstance().clear();
        ThirdPersonCameraRuntime.resetSession();
    }

    @SubscribeEvent
    public void onClientWorldUnload(WorldEvent.Unload event) {
        if (event != null && event.world != null && event.world.isRemote) {
            LostTalesClientMobAggroCache.clear();
            ClientPartyTrackingCache.clear();
            ThirdPersonCameraRuntime.resetSession();
            WraithWorldVisualEffect.reset();
        }
    }

    @SubscribeEvent
    public void updateWraithWorldEffect(TickEvent.ClientTickEvent event) {
        WraithWorldVisualEffect.onClientTick(event);
    }

    @SubscribeEvent
    public void colorWraithWorldFog(EntityViewRenderEvent.FogColors event) {
        WraithWorldVisualEffect.applyFogColors(event);
    }

    @SubscribeEvent
    public void applyExplosionCameraMotion(PlaySoundEvent17 event) {
        ThirdPersonExplosionMotionHandler.onSound(
                Minecraft.getMinecraft(), event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void useCharacterName(PlayerEvent.NameFormat event) {
        if (event == null || event.entityPlayer == null
                || event.entityPlayer.getUniqueID() == null) {
            return;
        }
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(
                        event.entityPlayer.getUniqueID());
        if (appearance != null && appearance.isPresent()
                && appearance.getCharacterName().length() > 0) {
            String characterName = appearance.getCharacterName();
            String displayName = event.displayname;
            String accountName = event.username;
            int accountNameIndex = displayName == null || accountName == null
                    || accountName.length() == 0
                    ? -1 : displayName.indexOf(accountName);
            event.displayname = accountNameIndex < 0
                    ? characterName
                    : displayName.substring(0, accountNameIndex)
                    + characterName
                    + displayName.substring(
                    accountNameIndex + accountName.length());
        }
    }

    @SubscribeEvent
    public void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || event.player == null || event.player.worldObj == null
                || !event.player.worldObj.isRemote) {
            return;
        }
        if (event.player == Minecraft.getMinecraft().thePlayer) {
            LostTalesClientMobAggroCache.validateContext(event.player);
            ThirdPersonCameraRuntime.onClientTick(
                    Minecraft.getMinecraft());
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
    public void renderProjectilePrediction(RenderWorldLastEvent event) {
        ThirdPersonProjectileTrajectoryRenderer.render(
                Minecraft.getMinecraft(), event.partialTicks);
    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderRaceAdjustedCape(RenderPlayerEvent.Specials.Pre event) {
        LostTalesPlayerCapeRenderHook.onSpecialsPre(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void applyThirdPersonHeadPitch(RenderPlayerEvent.Pre event) {
        ThirdPersonHeadRenderHook.onPre(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hideConcealedPlayer(RenderPlayerEvent.Pre event) {
        if (event != null && event.entityPlayer != null
                && ClientAccessoryEffectCache.isConcealed(
                event.entityPlayer)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void restoreThirdPersonHeadPitch(RenderPlayerEvent.Post event) {
        ThirdPersonHeadRenderHook.onPost(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void replaceLotrMapGui(GuiOpenEvent event) {
        if (event.gui != null && event.gui.getClass() == LOTRGuiMap.class) {
            event.gui = new LostTalesLotrMapGui();
        } else if (event.gui != null
                && event.gui.getClass() == GuiInventory.class
                && Minecraft.getMinecraft().thePlayer != null
                && Minecraft.getMinecraft().thePlayer.inventoryContainer
                instanceof LostTalesContainerPlayer) {
            event.gui = new LostTalesGuiInventory(
                    Minecraft.getMinecraft().thePlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void positionCreativeAccessorySlot(
            GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.gui instanceof GuiContainerCreative)) {
            return;
        }
        GuiContainerCreative gui = (GuiContainerCreative)event.gui;
        for (Object value : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot)value;
            if (slot.inventory instanceof AccessoryInventory) {
                slot.xDisplayPosition = 126;
                slot.yDisplayPosition = 20;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderThirdPersonCrosshair(
            RenderGameOverlayEvent.Pre event) {
        ThirdPersonCrosshairRenderer.render(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void renderWraithWorldEffect(RenderGameOverlayEvent.Pre event) {
        WraithWorldVisualEffect.render(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderHud(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            LostTalesQuickLootHudRenderer.render(Minecraft.getMinecraft());
            LostTalesCompassHudRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
            LostTalesMapMarkerHudRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
            LostTalesPartyHudRenderer.render(Minecraft.getMinecraft(), event.partialTicks);
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
