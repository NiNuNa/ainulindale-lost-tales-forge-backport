package com.ninuna.losttales.proxy;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.character.CharacterClientTaskQueue;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterCreationCatalogCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientCharacterRacePhysics;
import com.ninuna.losttales.client.event.LostTalesClientEventHandler;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerNotificationStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.render.player.LostTalesRaceAwarePlayerRenderer;
import com.ninuna.losttales.client.render.renderer.item.LostTalesItemRendererArmor3D;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererLamp;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererPlushie;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererStatue;
import com.ninuna.losttales.client.render.renderer.tileentity.LostTalesTileEntityRendererUrn;
import com.ninuna.losttales.config.client.LostTalesConfigGuiEventHandler;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneGuard;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneMan;
import com.ninuna.losttales.gui.ELostTalesMapLabels;
import com.ninuna.losttales.gui.LostTalesGuiIds;
import com.ninuna.losttales.gui.screen.LostTalesMissiveBoardGui;
import com.ninuna.losttales.gui.screen.LostTalesMissiveLetterReaderGui;
import com.ninuna.losttales.item.armor.LostTalesItemArmor3D;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerDiscoveryPacket;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterAppearanceSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreationCatalogSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterOperationResultPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterSyncPacket;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.client.resources.IReloadableResourceManager;
import lotr.client.render.entity.LOTRRenderBreeMan;
import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib3.renderers.geo.GeoArmorRenderer;

public class LostTalesClientProxy extends LostTalesCommonProxy {

    private LostTalesClientEventHandler clientEventHandler;
    private LostTalesKeyBindings keyBindings;
    private CharacterClientTaskQueue characterClientTaskQueue;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        clientEventHandler = new LostTalesClientEventHandler();
        keyBindings = new LostTalesKeyBindings();
        characterClientTaskQueue = new CharacterClientTaskQueue();
        keyBindings.register();
        MinecraftForge.EVENT_BUS.register(keyBindings);
        FMLCommonHandler.instance().bus().register(keyBindings);
        FMLCommonHandler.instance().bus().register(clientEventHandler);
        FMLCommonHandler.instance().bus().register(characterClientTaskQueue);
        FMLCommonHandler.instance().bus().register(new LostTalesConfigGuiEventHandler());
        ELostTalesMapLabels.initAndRegisterMapLabels();

        GeoArmorRenderer.registerArmorRenderer(LostTalesItemArmor3D.class, new LostTalesItemRendererArmor3D());

        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        verifyRaceTransformers();
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(clientEventHandler);

        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityUrn.class, new LostTalesTileEntityRendererUrn());
        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityStatue.class, new LostTalesTileEntityRendererStatue());
        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityLamp.class, new LostTalesTileEntityRendererLamp());
        ClientRegistry.bindTileEntitySpecialRenderer(LostTalesTileEntityPlushie.class, new LostTalesTileEntityRendererPlushie());

        RenderingRegistry.registerEntityRenderingHandler(EntityPlayer.class, new LostTalesRaceAwarePlayerRenderer());
        RenderingRegistry.registerEntityRenderingHandler(LostTalesEntityOdaneMan.class, new LOTRRenderBreeMan());
        RenderingRegistry.registerEntityRenderingHandler(LostTalesEntityOdaneGuard.class, new LOTRRenderBreeMan());

        super.init(event);
    }


    private static void verifyRaceTransformers() {
        if (!Boolean.getBoolean("losttales.cameraTransformer.active")) {
            FMLLog.warning("[losttales] Camera transformer is not active; short-race first/third-person camera height will remain vanilla");
        }
        if (!Boolean.getBoolean("losttales.debugHitboxTransformer.active")) {
            FMLLog.warning("[losttales] Debug-hitbox transformer is not active; F3+B may be drawn above roleplay player models");
        }
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == LostTalesGuiIds.MISSIVE_BOARD) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityMissiveBoard) {
                return new LostTalesMissiveBoardGui(player.inventory, (LostTalesTileEntityMissiveBoard) tileEntity);
            }
        }
        return null;
    }


    @Override
    public void openMissiveLetterGui(EntityPlayer player, ItemStack stack, int inventorySlot) {
        if (stack != null) {
            Minecraft.getMinecraft().displayGuiScreen(new LostTalesMissiveLetterReaderGui(stack, inventorySlot));
        }
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
            LostTalesClientMapMarkerStore.setDynamicMarkers(packet.getDynamicMapMarkers());
            LostTalesClientQuestDefinitionStore.setDynamicQuestDefinitions(packet.getDynamicQuestDefinitions());
            LostTalesClientQuestNotificationStore.notifyForIncomingSync(packet.getActiveQuests(), packet.getCompletedQuestIds());
            LostTalesClientQuestProgressStore.update(packet.getActiveQuests(), packet.getCompletedQuestIds(), packet.getFailedQuestIds(), packet.getPinnedQuestIds(), packet.getDiscoveredMarkerIds(), packet.getPinnedMapMarkerId());
        }
    }


    @Override
    public void handleMapMarkerDiscovery(LostTalesMapMarkerDiscoveryPacket packet) {
        if (packet != null) {
            LostTalesClientMapMarkerNotificationStore.showDiscovery(packet.getMarkerId(), packet.getMarkerName());
        }
    }

    @Override
    public void handleMobAggroSync(LostTalesMobAggroSyncPacket packet) {
        if (packet != null && !packet.isMalformed()) {
            LostTalesClientMobAggroCache.accept(packet);
        }
    }

    @Override
    public void scheduleClientTask(Runnable task) {
        CharacterClientTaskQueue.enqueue(task);
    }

    @Override
    public void handleCharacterRosterSync(CharacterRosterSyncPacket packet) {
        if (packet == null || packet.isMalformed() || packet.getSnapshot() == null) {
            ClientCharacterRosterCache.markProtocolError(packet == null ? 0 : packet.getRequestId());
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || !player.getUniqueID().equals(packet.getSnapshot().getOwnerId())) {
            ClientCharacterRosterCache.markProtocolError(packet.getRequestId());
            return;
        }
        ClientCharacterRosterCache.acceptRoster(packet.getRequestId(), packet.getSnapshot());
    }

    @Override
    public void handleCharacterAppearanceSync(CharacterAppearanceSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        if (packet.isReplaceAll()) {
            ClientCharacterAppearanceCache.replaceAll(packet.getAppearances());
        } else {
            ClientCharacterAppearanceCache.apply(packet.getAppearances());
        }
        // Apply dimensions and eye data immediately after the authoritative
        // snapshot rather than waiting for a periodic client tick.
        ClientCharacterRacePhysics.applyAll(Minecraft.getMinecraft());
    }

    @Override
    public void handleCharacterCreationCatalogSync(CharacterCreationCatalogSyncPacket packet) {
        if (packet == null || packet.isMalformed() || packet.getCatalog() == null) {
            ClientCharacterCreationCatalogCache.clear();
            return;
        }
        ClientCharacterCreationCatalogCache.accept(packet.getCatalog());
    }

    @Override
    public void handleCharacterOperationResult(CharacterOperationResultPacket packet) {
        if (packet == null || packet.isMalformed()) {
            ClientCharacterRosterCache.markProtocolError(packet == null ? 0 : packet.getRequestId());
            return;
        }
        ClientCharacterRosterCache.acceptOperation(packet.toFeedback());
    }

}