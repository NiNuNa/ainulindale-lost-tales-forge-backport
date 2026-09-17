package com.ninuna.losttales.client.event;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.accessory.inventory.LostTalesContainerPlayer;
import com.ninuna.losttales.accessory.player.AccessoryInventory;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.client.LostTalesClientThread;
import com.ninuna.losttales.config.client.ClientServerConfigCache;
import com.ninuna.losttales.client.accessory.ClientAccessoryEffectCache;
import com.ninuna.losttales.client.accessory.WraithWorldVisualEffect;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.camera.ThirdPersonCameraRuntime;
import com.ninuna.losttales.client.camera.ThirdPersonCrosshairRenderer;
import com.ninuna.losttales.client.camera.ThirdPersonExplosionMotionHandler;
import com.ninuna.losttales.client.camera.ThirdPersonHeadRenderHook;
import com.ninuna.losttales.client.camera.ThirdPersonProjectileTrajectoryRenderer;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.character.CharacterClientTaskQueue;
import com.ninuna.losttales.client.character.CharacterTemplateOffer;
import com.ninuna.losttales.client.character.room.CharacterRoomJourneyPrompt;
import com.ninuna.losttales.client.character.room.CharacterRoomLauncher;
import com.ninuna.losttales.client.character.room.CharacterRoomSession;
import com.ninuna.losttales.gui.hud.LostTalesNotificationHud;
import com.ninuna.losttales.client.render.player.LostTalesCharacterFigureRenderer;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterCreationCatalogCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientCharacterRacePhysics;
import com.ninuna.losttales.client.chat.ChatSpeechBubbles;
import com.ninuna.losttales.client.chat.ChatWindowLayout;
import com.ninuna.losttales.client.chat.LostTalesSpeechBubbleRenderer;
import com.ninuna.losttales.client.chat.ClientChatChannelState;
import com.ninuna.losttales.client.chat.ClientChatIgnores;
import com.ninuna.losttales.client.chat.ClientChatReadMarks;
import com.ninuna.losttales.client.chat.ClientChatSession;
import com.ninuna.losttales.client.chat.ClientChatChannelViews;
import com.ninuna.losttales.client.chat.ClientChatDeliveryMarks;
import com.ninuna.losttales.client.chat.ClientChatTypingState;
import com.ninuna.losttales.client.chat.ClientChatShowcaseStore;
import com.ninuna.losttales.client.chat.LostTalesChatPresentation;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.character.CreatorCharacterLight;
import com.ninuna.losttales.client.gui.LostTalesGuiInventory;
import com.ninuna.losttales.client.gui.LostTalesGuiPointerTargets;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimations;
import com.ninuna.losttales.client.gui.LostTalesHudFade;
import com.ninuna.losttales.client.gui.LostTalesHudHidingScreen;
import com.ninuna.losttales.client.gui.LostTalesPlayerListOverlay;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerNotificationStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientWaystoneStateStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientWaystoneTravelContext;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.client.mapmarker.LostTalesMapDecorationRenderer;
import com.ninuna.losttales.client.mapmarker.LostTalesMapTerrainCache;
import com.ninuna.losttales.client.mapmarker.LostTalesMapTerrainRenderer;
import com.ninuna.losttales.client.mapmarker.LostTalesMapViewMemory;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapMarkerIconOverlay;
import com.ninuna.losttales.client.party.ClientPartyMemberStatusCache;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.quest.LostTalesQuestDialogueHooks;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.render.player.LostTalesPlayerCapeRenderHook;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.client.render.player.ChestPhysicsStates;
import com.ninuna.losttales.client.render.player.PlayerAppearanceResolver;
import com.ninuna.losttales.client.skin.LostTalesAccountSkins;
import com.ninuna.losttales.client.render.renderer.item.LostTalesItemRendererHammer;
import com.ninuna.losttales.client.render.renderer.item.LostTalesRendererLargeItems;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.mapmarker.LostTalesMapMarkerHudRenderer;
import com.ninuna.losttales.gui.hud.party.LostTalesPartyHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesWorldQuestMarkerRenderer;
import com.ninuna.losttales.gui.screen.LostTalesQuestJournalGui;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.weapon.LostTalesItemBattleaxe;
import com.ninuna.losttales.item.weapon.LostTalesItemDagger;
import com.ninuna.losttales.item.weapon.LostTalesItemSpear;
import com.ninuna.losttales.item.weapon.LostTalesItemSword;
import com.ninuna.losttales.proxy.LostTalesClientProxy;
import com.ninuna.losttales.world.map.LostTalesMapOverlay;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.client.chat.ClientChatPresence;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.client.MinecraftForgeClient;
import lotr.client.gui.LOTRGuiMap;
import lotr.client.gui.LOTRGuiMiniquestOffer;
import lotr.client.gui.LOTRGuiRedBook;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
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
        LostTalesMapTerrainRenderer.clear();
        WraithWorldVisualEffect.onResourceManagerReload();
        LostTalesInputIconRenderer.onResourceManagerReload(resManager);
        LostTalesMapOverlay.applyClientMap();
        LostTalesClientMapMarkerStore.reloadFromResources(resManager);
        LostTalesClientQuestDefinitionStore.reloadFromResources(resManager);
    }

    /**
     * FML fires this on the network thread; the clears below delete
     * textures and meshes, which only the client thread may do, so the
     * whole of it hops there. The connect handler hops the same way, so
     * a disconnect and the next connect keep their order.
     */
    @SubscribeEvent
    public void onClientDisconnect(ClientDisconnectionFromServerEvent event) {
        LostTalesClientThread.run(new Runnable() {
            @Override
            public void run() {
                clearSessionState();
            }
        });
    }

    private static void clearSessionState() {
        LostTalesClientQuestProgressStore.clear();
        LostTalesClientQuestNotificationStore.clear();
        LostTalesClientQuestDefinitionStore.clearDynamicQuestDefinitions();
        ClientQuestCatalog.forget();
        LostTalesClientMapMarkerNotificationStore.clear();
        LostTalesClientMapMarkerStore.clearDynamicMarkers();
        LostTalesLotrMapMarkerIconOverlay.clearClientState();
        // The map image is per-world, so what was learned about where its
        // water is cannot be carried into the next one — nor may a view of
        // one world's map be restored over another's.
        LostTalesMapDecorationRenderer.clearCache();
        LostTalesMapTerrainCache.clear();
        LostTalesMapTerrainRenderer.clear();
        LostTalesMapViewMemory.clear();
        LostTalesClientWaystoneStateStore.clear();
        LostTalesClientWaystoneTravelContext.clear();
        LostTalesClientMobAggroCache.clear();
        LostTalesClientQuickLootCache.clear();
        ClientCharacterRosterCache.clear();
        CharacterTemplateOffer.clear();
        // A roster sync still queued from this world must not be applied
        // in the next, whose revisions are its own.
        CharacterClientTaskQueue.clear();
        LostTalesCharacterFigureRenderer.clear();
        ClientCharacterAppearanceCache.clear();
        PlayerAppearanceResolver.clear();
        LostTalesAccountSkins.clear();
        ChestPhysicsStates.clear();
        ClientCharacterCreationCatalogCache.clear();
        ClientLoreCharacterCache.clear();
        ClientPartyStateCache.clear();
        ClientPartyMemberStatusCache.clear();
        ClientPartyTrackingCache.clear();
        // Words over a head belong to the world they were spoken in.
        ChatSpeechBubbles.clear();
        ClientServerConfigCache.clear();
        // Chat is the one client state that outlives a disconnect: the
        // game keeps its own message history for as long as it runs, and
        // everything Lost Tales knows about those messages — their tabs,
        // the open conversations, the scroll offsets — has to outlive it
        // too, or rejoining the same server would show a history it
        // could no longer file. It is dropped on arriving somewhere
        // else instead; see onClientConnect. Only what describes the
        // connection itself goes here.
        ClientChatTypingState.clear();
        // What the server said about this session's Discord posts is not
        // said again on the next join.
        ClientChatDeliveryMarks.clear();
        // How far each conversation here was read is written down, so
        // the next join's replay starts its unread run where this one
        // left off.
        ClientChatReadMarks.save();
        // A name learned for an ignored account belongs to this server;
        // on another one it may be somebody else's. The stored accounts
        // themselves persist like every other preference.
        ClientChatIgnores.clearSessionNames();
        ClientAccessoryEffectCache.clear();
        LostTalesHudFade.reset();
        WraithWorldVisualEffect.reset();
        LostTalesQuickLootHudRenderer.resetHud();
        LotrRaceProfileAdapter.getInstance().clear();
        ThirdPersonCameraRuntime.resetSession();
        // Last, after the camera has given the perspective back: a visit
        // to the character room that ended without its own menu is
        // cleaned up here, and that restores the perspective the visit
        // began with.
        CharacterRoomLauncher.onWorldLeft(Minecraft.getMinecraft());
    }

    /**
     * Chat state belongs to a server, not to a connection: arriving back
     * at the same address keeps the history and its tabs, and arriving
     * anywhere else starts clean — the game's own message list included,
     * since nothing here could say which tab those lines belonged to.
     */
    @SubscribeEvent
    public void onClientConnect(ClientConnectedToServerEvent event) {
        LostTalesClientThread.run(new Runnable() {
            @Override
            public void run() {
                beginSession();
            }
        });
    }

    private static void beginSession() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ClientChatSession.resume(minecraft)) {
            if (minecraft != null && minecraft.ingameGUI != null) {
                minecraft.ingameGUI.getChatGUI().clearChatMessages();
            }
            ClientChatChannelState.clear();
            ClientChatChannelViews.clear();
            ChatRoleCatalog.resetToBuiltIn();
            // A channel a server defined is that server's; arriving
            // somewhere else leaves the code's own channels alone.
            ChatChannel.resetToBuiltIn();
            ChatProfanityCatalog.resetToBundled();
            ClientChatPresence.clear();
            ClientChatShowcaseStore.clear();
            LostTalesChatPresentation.clear();
            LostTalesCharacterHeadIconRenderer.clearAccountSkinCache();
        }
        // The place's remembered whisper tabs come back where they were,
        // before anything is replayed into them.
        ChatWindowLayout.restoreConversations(ClientChatSession.currentKey());
    }

    @SubscribeEvent
    public void onClientWorldUnload(WorldEvent.Unload event) {
        if (event != null && event.world != null && event.world.isRemote) {
            LostTalesMapTerrainCache.clear();
            LostTalesMapTerrainRenderer.clear();
            LostTalesClientMobAggroCache.clear();
            ClientPartyTrackingCache.clear();
            ThirdPersonCameraRuntime.resetSession();
            WraithWorldVisualEffect.reset();
        }
    }

    /**
     * Gives up on messages shown early that the server never answered
     * for, so a dropped one is visibly dropped rather than sitting
     * faint in the history looking like it is still on its way.
     */
    @SubscribeEvent
    public void expirePendingChatEchoes(TickEvent.ClientTickEvent event) {
        if (event != null && event.phase == TickEvent.Phase.END) {
            LostTalesChatPresentation.expirePendingEchoes();
        }
    }

    /** Away sets in and lifts on this client's own activity. */
    @SubscribeEvent
    public void updateChatPresence(TickEvent.ClientTickEvent event) {
        if (event != null && event.phase == TickEvent.Phase.END) {
            ClientChatPresence.onClientTick(Minecraft.getMinecraft());
        }
    }

    @SubscribeEvent
    public void updateWraithWorldEffect(TickEvent.ClientTickEvent event) {
        WraithWorldVisualEffect.onClientTick(event);
    }

    /** A visit to the character room: the creator on arrival, then the room. */
    @SubscribeEvent
    public void runCharacterRoomVisit(TickEvent.ClientTickEvent event) {
        CharacterRoomSession.onClientTick(event);
    }

    /** In the character room the pause menu is the room's own menu. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void replaceCharacterRoomPauseMenu(GuiOpenEvent event) {
        CharacterRoomSession.replacePauseMenu(event);
    }

    /** Returns the native pointer as soon as Minecraft leaves its GUI layer. */
    @SubscribeEvent
    public void releaseGuiCursorWhenUnowned(TickEvent.ClientTickEvent event) {
        if (event != null && event.phase == TickEvent.Phase.END) {
            LostTalesMapCursor.releaseIfUnowned(Minecraft.getMinecraft());
        }
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

    /**
     * The Tab player list, drawn as the mod's own so it names characters
     * rather than accounts. Runs last, so a screen that hides the HUD or
     * another mod that claims the list has had its say first.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderPlayerList(RenderGameOverlayEvent.Pre event) {
        if (event == null || event.isCanceled()
                || event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST
                || event.resolution == null) {
            return;
        }
        if (LostTalesPlayerListOverlay.draw(Minecraft.getMinecraft(),
                event.resolution.getScaledWidth())) {
            event.setCanceled(true);
        }
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
        if (appearance != null && appearance.hasCharacter()) {
            event.displayname = PlayableIdentity.formatDisplayName(
                    event.displayname, event.username,
                    appearance.getCharacterName());
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
            LostTalesClientProxy.verifyThirdPersonActionTransformers();
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


    /**
     * Speech over a player's head, drawn where vanilla hangs a
     * nameplate: in the entity's own render pass, so the position
     * arrives with the event instead of being worked out from a camera
     * this mod can move.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderSpeechBubbles(RenderLivingEvent.Specials.Post event) {
        try {
            LostTalesSpeechBubbleRenderer.render(event.entity, event.x,
                    event.y, event.z);
        } catch (Throwable ignored) {
            // Speech over a head is decoration; it never costs a frame.
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
        CreatorCharacterLight.onRenderPlayerPre(event);
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
        CreatorCharacterLight.onRenderPlayerPost(event);
        ThirdPersonHeadRenderHook.onPost(event);
    }

    /**
     * A screen that asks for the bare world gets it: every element of the
     * game's overlay is cancelled one by one, and this mod's own panels
     * below step aside too.
     *
     * <p>Not the overlay as a whole. Forge answers a cancelled whole by
     * returning before it sets up the orthographic projection the screen
     * is then drawn in, so cancelling it leaves the screen itself drawn
     * under the world's perspective, which is to say invisible.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hideHudBehindScreen(RenderGameOverlayEvent.Pre event) {
        if (event != null && event.type != RenderGameOverlayEvent.ElementType.ALL
                && isHudHidden()) {
            event.setCanceled(true);
        }
    }

    private static boolean isHudHidden() {
        return Minecraft.getMinecraft().currentScreen
                instanceof LostTalesHudHidingScreen;
    }

    /**
     * The HUD steps aside while the chat is open, fading out as the chat
     * opens and back in once it closes ({@link LostTalesHudFade}). Asked
     * about every element, cancelled ones included, so the fade finds
     * where its layer begins and ends whatever another mod cancels.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void fadeHudBehindChat(RenderGameOverlayEvent.Pre event) {
        if (!isHudHidden()) {
            LostTalesHudFade.onPre(event);
        }
    }

    /** The overlay is done: a copy of the frame still waiting is laid back. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void endHudFade(RenderGameOverlayEvent.Post event) {
        if (event != null
                && event.type == RenderGameOverlayEvent.ElementType.ALL) {
            LostTalesHudFade.onOverlayEnd();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void replaceLotrMapGui(GuiOpenEvent event) {
        if (event.gui != null
                && event.gui.getClass() == LOTRGuiMiniquestOffer.class) {
            // A quest offered in Middle-earth is talked about in the Lost
            // Tales screen; an offer that cannot be read keeps LOTR's own.
            GuiScreen conversation = LostTalesQuestDialogueHooks
                    .replaceLotrOffer((LOTRGuiMiniquestOffer)event.gui);
            if (conversation != null) {
                event.gui = conversation;
            }
        } else if (event.gui != null
                && event.gui.getClass() == LOTRGuiRedBook.class) {
            event.gui = new LostTalesQuestJournalGui(null);
        } else if (event.gui != null
                && event.gui.getClass() == LOTRGuiMap.class) {
            event.gui = LostTalesLotrMapGui.replace(
                    (LOTRGuiMap)event.gui);
        } else if (event.gui != null
                && event.gui.getClass() == GuiInventory.class
                && Minecraft.getMinecraft().thePlayer != null
                && Minecraft.getMinecraft().thePlayer.inventoryContainer
                instanceof LostTalesContainerPlayer) {
            event.gui = new LostTalesGuiInventory(
                    Minecraft.getMinecraft().thePlayer);
        }
    }

    /**
     * Touching somebody a Lost Tales quest is about opens the
     * conversation. The interaction itself is left alone, so anything
     * else the entity does still happens; the server refuses to start or
     * hand in a quest that is talked about until a reply says so.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void openQuestConversation(EntityInteractEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event == null || event.target == null || minecraft == null
                || minecraft.thePlayer == null
                || event.entityPlayer != minecraft.thePlayer
                || minecraft.currentScreen != null
                || event.entityPlayer.worldObj == null
                || !event.entityPlayer.worldObj.isRemote) {
            return;
        }
        try {
            GuiScreen conversation = LostTalesQuestDialogueHooks.forEntity(
                    event.target);
            if (conversation != null) {
                minecraft.displayGuiScreen(conversation);
            }
        } catch (RuntimeException ignored) {
            // A conversation that cannot be built is no conversation;
            // the interaction itself is untouched.
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

    /** Draws the same pointer used by the LOTR map over every other GUI. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderGuiCursor(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event == null || event.gui == null
                || event.gui instanceof LostTalesLotrMapGui) {
            return;
        }
        LostTalesMapCursor.acquire();
        // One place decides the pose for every screen: the screen's own
        // answer about what is under the pointer, asked at the point its
        // draw and its clicks see — the event carries the window's point,
        // the screen is handed it through the opening animation's
        // inverse. The sprite itself stands on the window's point.
        LostTalesMapCursor.render(
                Minecraft.getMinecraft(), event.mouseX, event.mouseY,
                LostTalesGuiPointerTargets.poseFor(event.gui,
                        LostTalesGuiAnimations.inverseMouseX(event.gui,
                                event.mouseX),
                        LostTalesGuiAnimations.inverseMouseY(event.gui,
                                event.mouseY)));
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
        if (event.type == RenderGameOverlayEvent.ElementType.ALL
                && !isHudHidden()) {
            Minecraft minecraft = Minecraft.getMinecraft();
            // While the chat is open the panels step aside with the rest
            // of the HUD, fading as one layer with it.
            if (!LostTalesHudFade.beginPanels(minecraft)) {
                return;
            }
            try {
                // Every passing notice claims its strip of the one slot,
                // top down, in the order it is drawn here.
                LostTalesNotificationHud.beginFrame();
                LostTalesQuickLootHudRenderer.render(minecraft);
                LostTalesCompassHudRenderer.render(minecraft, event.partialTicks);
                LostTalesMapMarkerHudRenderer.render(minecraft, event.partialTicks);
                LostTalesPartyHudRenderer.render(minecraft, event.partialTicks);
                LostTalesQuestHudRenderer.render(minecraft, event.partialTicks);
                CharacterRoomJourneyPrompt.render(minecraft);
            } finally {
                LostTalesHudFade.endPanels(minecraft);
            }
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
