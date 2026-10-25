package com.ninuna.losttales.core;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies narrowly scoped compatibility hooks where public Forge 1.7.10 events
 * do not expose the required camera, debug-box, identity, and LOTR behavior.
 * Every call a patch injects is wrapped in one catch as its class is written
 * ({@link HookGuard}, DATA-08), so a fault of the mod's never reaches
 * vanilla's or LOTR's code.
 */
public final class LostTalesClassTransformer implements IClassTransformer {
    /**
     * What each call the patch being tried on this thread injects answers
     * should its hook fail ({@link #hookCall}, {@link #redirect}).
     */
    private static final ThreadLocal<Map<AbstractInsnNode, HookFallback>> DECLARED =
            new ThreadLocal<Map<AbstractInsnNode, HookFallback>>() {
                @Override
                protected Map<AbstractInsnNode, HookFallback> initialValue() {
                    return new IdentityHashMap<AbstractInsnNode, HookFallback>();
                }
            };
    /**
     * The active flags the patch being tried on this thread has set, each
     * with what it held before ({@link #activate}, {@link #rollBack}).
     */
    private static final ThreadLocal<List<String[]>> ACTIVATED =
            new ThreadLocal<List<String[]>>() {
                @Override
                protected List<String[]> initialValue() {
                    return new ArrayList<String[]>();
                }
            };
    public static final String LOTR_MAP_SCENE_ACTIVE_PROPERTY =
            "losttales.lotrMapSceneTransformer.active";
    public static final String LOTR_MAP_BACKGROUND_ACTIVE_PROPERTY =
            "losttales.lotrMapBackgroundTransformer.active";
    private static final String LOTR_MAP_SCENE_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/LostTalesMapScene";
    public static final String WINDOW_DEFAULTS_ACTIVE_PROPERTY =
            "losttales.windowDefaultsTransformer.active";
    private static final String WINDOW_DEFAULTS_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/LostTalesWindowDefaults";
    public static final String MAIN_MENU_TEXT_ACTIVE_PROPERTY =
            "losttales.mainMenuTextTransformer.active";
    public static final String LOTR_MAIN_MENU_TEXT_ACTIVE_PROPERTY =
            "losttales.lotrMainMenuTextTransformer.active";
    private static final String GUI_MAIN_MENU = "net.minecraft.client.gui.GuiMainMenu";
    private static final String LOTR_GUI_MAIN_MENU = "lotr.client.gui.LOTRGuiMainMenu";
    private static final String MAIN_MENU_TEXT_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/LostTalesMainMenuTextStyle";
    public static final String MAIN_MENU_TITLE_ACTIVE_PROPERTY =
            "losttales.mainMenuTitleTransformer.active";
    public static final String LOTR_MAIN_MENU_TITLE_ACTIVE_PROPERTY =
            "losttales.lotrMainMenuTitleTransformer.active";
    private static final String MAIN_MENU_TITLE_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/LostTalesMainMenuTitle";

    public static final String CAMERA_ACTIVE_PROPERTY =
            "losttales.cameraTransformer.active";
    public static final String THIRD_PERSON_CAMERA_ACTIVE_PROPERTY =
            "losttales.thirdPersonCameraTransformer.active";
    public static final String THIRD_PERSON_TARGETING_ACTIVE_PROPERTY =
            "losttales.thirdPersonTargetingTransformer.active";
    public static final String THIRD_PERSON_ENTITY_ACTION_ACTIVE_PROPERTY =
            "losttales.thirdPersonEntityActionTransformer.active";
    public static final String THIRD_PERSON_BLOCK_ACTION_ACTIVE_PROPERTY =
            "losttales.thirdPersonBlockActionTransformer.active";
    public static final String DEBUG_BOX_ACTIVE_PROPERTY =
            "losttales.debugHitboxTransformer.active";
    public static final String FAST_TRAVEL_ACTIVE_PROPERTY =
            "losttales.fastTravelTransformer.active";
    public static final String LOTR_BOUNTY_ACTIVE_PROPERTY =
            "losttales.lotrBountyTransformer.active";
    public static final String LOTR_SPEECH_ACTIVE_PROPERTY =
            "losttales.lotrSpeechTransformer.active";
    public static final String NPC_CHAT_ACTIVE_PROPERTY =
            "losttales.npcChatTransformer.active";
    public static final String NPC_SPEECH_RENDER_ACTIVE_PROPERTY =
            "losttales.npcSpeechRenderTransformer.active";
    public static final String NAMEPLATE_ACTIVE_PROPERTY =
            "losttales.nameplateTransformer.active";
    public static final String ALIGNMENT_LIFT_ACTIVE_PROPERTY =
            "losttales.alignmentLiftTransformer.active";
    public static final String ACCESSORY_CONTAINER_ACTIVE_PROPERTY =
            "losttales.accessoryContainerTransformer.active";
    public static final String ACCESSORY_PICK_BLOCK_ACTIVE_PROPERTY =
            "losttales.accessoryPickBlockTransformer.active";
    public static final String ACCESSORY_CREATIVE_ACTIVE_PROPERTY =
            "losttales.accessoryCreativeTransformer.active";
    public static final String ACCESSORY_DEATH_ACTIVE_PROPERTY =
            "losttales.accessoryDeathTransformer.active";
    public static final String ACCESSORY_COLLISION_ACTIVE_PROPERTY =
            "losttales.accessoryCollisionTransformer.active";
    public static final String ACCESSORY_INVISIBILITY_ACTIVE_PROPERTY =
            "losttales.accessoryInvisibilityTransformer.active";
    public static final String ACCESSORY_AI_TARGET_ACTIVE_PROPERTY =
            "losttales.accessoryAiTargetTransformer.active";
    public static final String ACCESSORY_SIGHT_ACTIVE_PROPERTY =
            "losttales.accessorySightTransformer.active";
    public static final String ACCESSORY_LOTR_MAP_ACTIVE_PROPERTY =
            "losttales.accessoryLotrMapTransformer.active";
    public static final String LOTR_MAP_FULLSCREEN_ACTIVE_PROPERTY =
            "losttales.lotrMapFullscreenTransformer.active";
    public static final String LOTR_MAP_CONTROL_BAR_ACTIVE_PROPERTY =
            "losttales.lotrMapControlBarTransformer.active";
    public static final String LOTR_MAP_MINIQUEST_FILTER_ACTIVE_PROPERTY =
            "losttales.lotrMapMiniquestFilterTransformer.active";
    public static final String LOTR_QUEST_TRACKER_ACTIVE_PROPERTY =
            "losttales.lotrQuestTrackerTransformer.active";
    public static final String LOTR_MAP_ROTATION_ACTIVE_PROPERTY =
            "losttales.lotrMapRotationTransformer.active";
    public static final String LOTR_MAP_WINDOW_CLIP_ACTIVE_PROPERTY =
            "losttales.lotrMapWindowClipTransformer.active";
    public static final String LOTR_MAP_WINDOW_NEWS_ACTIVE_PROPERTY =
            "losttales.lotrMapWindowNewsTransformer.active";
    public static final String GUI_ANIMATION_ACTIVE_PROPERTY =
            "losttales.guiAnimationTransformer.active";
    public static final String SMOOTH_INVENTORY_ACTIVE_PROPERTY =
            "losttales.smoothInventoryTransformer.active";
    private static final String GUI_ANIMATION_DRAW_ACTIVE_PROPERTY =
            "losttales.guiAnimationDrawTransformer.active";
    private static final String GUI_ANIMATION_INPUT_ACTIVE_PROPERTY =
            "losttales.guiAnimationInputTransformer.active";
    private static final String GUI_ANIMATION_BACKGROUND_ACTIVE_PROPERTY =
            "losttales.guiAnimationBackgroundTransformer.active";
    public static final String TOOLTIP_ICON_ACTIVE_PROPERTY =
            "losttales.tooltipIconTransformer.active";
    public static final String TOOLTIP_SMOOTHING_ACTIVE_PROPERTY =
            "losttales.tooltipSmoothingTransformer.active";
    public static final String CHAT_HIT_TEST_ACTIVE_PROPERTY =
            "losttales.chatHitTestTransformer.active";
    public static final String CHAT_WRAP_ACTIVE_PROPERTY =
            "losttales.chatWrapTransformer.active";
    public static final String LOTR_FAST_TRAVEL_ARRIVAL_ACTIVE_PROPERTY =
            "losttales.lotrFastTravelArrivalTransformer.active";
    public static final String SERVER_BROADCAST_ACTIVE_PROPERTY =
            "losttales.serverBroadcastTransformer.active";
    public static final String PLAYER_LINE_ACTIVE_PROPERTY =
            "losttales.playerLineTransformer.active";
    public static final String DEATH_MESSAGE_ACTIVE_PROPERTY =
            "losttales.deathMessageTransformer.active";
    public static final String LOTR_HIRED_UNIT_ACTIVE_PROPERTY =
            "losttales.lotrHiredUnitTransformer.active";
    public static final String LOTR_FELLOWSHIP_REQUESTS_ACTIVE_PROPERTY =
            "losttales.lotrFellowshipRequestsTransformer.active";
    public static final String LOTR_FELLOWSHIP_CREATE_ACTIVE_PROPERTY =
            "losttales.lotrFellowshipCreateTransformer.active";
    public static final String LOTR_FELLOWSHIP_SHARE_ACTIVE_PROPERTY =
            "losttales.lotrFellowshipShareTransformer.active";
    public static final String LOTR_FELLOWSHIP_SHARE_ROW_ACTIVE_PROPERTY =
            "losttales.lotrFellowshipShareRowTransformer.active";
    public static final String LOTR_TRADER_NOTICE_ACTIVE_PROPERTY =
            "losttales.lotrTraderNoticeTransformer.active";
    public static final String LOTR_ACHIEVEMENT_HOVER_ACTIVE_PROPERTY =
            "losttales.lotrAchievementHoverTransformer.active";
    public static final String LOTR_SHARED_WAYPOINT_NAME_ACTIVE_PROPERTY =
            "losttales.lotrSharedWaypointNameTransformer.active";
    /**
     * Set once the chat's line replacement is guarded against the
     * history laying itself out again; the chat refreshes its drawn
     * lines only while this holds.
     */
    public static final String CHAT_DELETE_ACTIVE_PROPERTY =
            "losttales.chatDelete.active";
    /** Set once the game's chat log writes every line on one line of the log. */
    public static final String CHAT_LOG_ACTIVE_PROPERTY =
            "losttales.chatLogTransformer.active";
    /** Friend of the Wise's larger alignment gains (PROG-06). */
    public static final String PERK_ALIGNMENT_ACTIVE_PROPERTY =
            "losttales.perkAlignmentTransformer.active";
    /** Daggerwork's sooner strikes on LOTR's server. */
    public static final String PERK_ATTACK_SERVER_ACTIVE_PROPERTY =
            "losttales.perkAttackServerTransformer.active";
    /** Daggerwork's sooner strikes in LOTR's client timer. */
    public static final String PERK_ATTACK_CLIENT_ACTIVE_PROPERTY =
            "losttales.perkAttackClientTransformer.active";
    /** Fair Dealer's prices where LOTR checks and takes coins. */
    public static final String PERK_TRADE_ACTIVE_PROPERTY =
            "losttales.perkTradeTransformer.active";
    /** Fair Dealer's prices on LOTR's trade screen. */
    public static final String PERK_TRADE_SCREEN_ACTIVE_PROPERTY =
            "losttales.perkTradeScreenTransformer.active";
    /** Fair Dealer's sales as LOTR pays them. */
    public static final String PERK_SELL_ACTIVE_PROPERTY =
            "losttales.perkSellTransformer.active";
    /** Repair and Master Smith at LOTR's anvil. */
    public static final String PERK_ANVIL_ACTIVE_PROPERTY =
            "losttales.perkAnvilTransformer.active";
    /** Repairs taken from LOTR's anvil counting toward Smithing. */
    public static final String PERK_ANVIL_TAKE_ACTIVE_PROPERTY =
            "losttales.perkAnvilTakeTransformer.active";

    private static final String ENTITY_RENDERER =
            "net.minecraft.client.renderer.EntityRenderer";
    private static final String GUI_SCREEN =
            "net.minecraft.client.gui.GuiScreen";
    private static final String GUI_NEW_CHAT =
            "net.minecraft.client.gui.GuiNewChat";
    private static final String GUI_CONTAINER =
            "net.minecraft.client.gui.inventory.GuiContainer";
    private static final String MINECRAFT =
            "net.minecraft.client.Minecraft";
    private static final String PLAYER_CONTROLLER =
            "net.minecraft.client.multiplayer.PlayerControllerMP";
    private static final String ENTITY_PLAYER_MP =
            "net.minecraft.entity.player.EntityPlayerMP";
    private static final String ENTITY_PLAYER =
            "net.minecraft.entity.player.EntityPlayer";
    private static final String ENTITY =
            "net.minecraft.entity.Entity";
    private static final String ENTITY_LIVING_BASE =
            "net.minecraft.entity.EntityLivingBase";
    private static final String ENTITY_AI_TARGET =
            "net.minecraft.entity.ai.EntityAITarget";
    private static final String NET_HANDLER_PLAY_SERVER =
            "net.minecraft.network.NetHandlerPlayServer";
    private static final String RENDER_MANAGER =
            "net.minecraft.client.renderer.entity.RenderManager";
    private static final String LOTR_FAST_TRAVEL_HANDLER =
            "lotr.common.network.LOTRPacketFastTravel$Handler";
    private static final String LOTR_FACTION_BOUNTIES =
            "lotr.common.fac.LOTRFactionBounties";
    private static final String LOTR_FACTION_BOUNTY_PLAYER_DATA =
            "lotr.common.fac.LOTRFactionBounties$PlayerData";
    private static final String LOTR_SPEECH =
            "lotr.common.entity.npc.LOTRSpeech";
    private static final String LOTR_NPC_SPEECH_HANDLER =
            "lotr.common.network.LOTRPacketNPCSpeech$Handler";
    private static final String LOTR_RENDER_ALIGNMENT_BONUS =
            "lotr.client.render.entity.LOTRRenderAlignmentBonus";
    private static final String RENDERER_LIVING_ENTITY =
            "net.minecraft.client.renderer.entity.RendererLivingEntity";
    private static final String LOTR_TICK_HANDLER_CLIENT =
            "lotr.client.LOTRTickHandlerClient";
    private static final String LOTR_NPC_RENDERING =
            "lotr/client/render/entity/LOTRNPCRendering";
    private static final String LOTR_GUI_MAP =
            "lotr.client.gui.LOTRGuiMap";
    private static final String LOTR_CLIENT_PROXY =
            "lotr.client.LOTRClientProxy";
    private static final String LOTR_MAP_PAGE_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/LostTalesMapPage";
    private static final String LOTR_GUI_MINIQUEST_TRACKER =
            "lotr.client.gui.LOTRGuiMiniquestTracker";
    private static final String LOTR_LEVEL_DATA =
            "lotr.common.LOTRLevelData";

    private static final String CAMERA_MCP = "orientCamera";
    private static final String CAMERA_SRG = "func_78467_g";
    private static final String FOV_MCP = "getFOVModifier";
    private static final String FOV_SRG = "func_78481_a";
    private static final String MOUSE_OVER_MCP = "getMouseOver";
    private static final String MOUSE_OVER_SRG = "func_78473_a";
    private static final String ATTACK_ENTITY_MCP = "attackEntity";
    private static final String ATTACK_ENTITY_SRG = "func_78764_a";
    private static final String INTERACT_ENTITY_MCP =
            "interactWithEntitySendPacket";
    private static final String INTERACT_ENTITY_SRG = "func_78768_b";
    private static final String RIGHT_CLICK_BLOCK_MCP =
            "onPlayerRightClick";
    private static final String RIGHT_CLICK_BLOCK_SRG = "func_78760_a";
    private static final String DEBUG_BOX_MCP = "renderDebugBoundingBox";
    private static final String DEBUG_BOX_SRG = "func_85094_b";

    private static final String CAMERA_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterCameraHook";
    private static final String CAMERA_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;F)F";
    private static final String THIRD_PERSON_CAMERA_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonCameraHooks";
    private static final String CAMERA_DISTANCE_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;DF)D";
    private static final String CAMERA_OFFSET_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;FD)V";
    private static final String CAMERA_FOV_HOOK_DESC = "(FZ)F";
    private static final String TARGETING_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonTargetingHooks";
    private static final String TARGETING_HOOK_DESC = "(F)V";
    private static final String ENTITY_ACTION_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonEntityActionHooks";
    private static final String ENTITY_ACTION_PREDICATE_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/entity/Entity;)Z";
    private static final String BLOCK_ACTION_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonBlockActionHooks";
    private static final String BLOCK_ACTION_SEND_DESC =
            "(Lnet/minecraft/client/network/NetHandlerPlayClient;"
                    + "Lnet/minecraft/network/Packet;)V";
    private static final String DEBUG_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterDebugHitboxHook";
    private static final String DEBUG_HOOK_DESC =
            "(Lnet/minecraft/entity/Entity;D)D";
    private static final String FAST_TRAVEL_HOOK_OWNER =
            "com/ninuna/losttales/world/map/waypoint/"
                    + "LostTalesWaypointFastTravelPolicy";
    private static final String FAST_TRAVEL_HOOK_DESC =
            "(Llotr/common/LOTRPlayerData;"
                    + "Llotr/common/world/map/LOTRAbstractWaypoint;"
                    + "Lnet/minecraft/entity/player/EntityPlayerMP;)V";
    private static final String NPC_CHAT_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesNpcChatHook";
    private static final String NPC_CHAT_HOOK_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/IChatComponent;"
                    + "Llotr/common/entity/npc/LOTREntityNPC;)V";
    private static final String NAMEPLATE_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/"
                    + "LostTalesSpeechBubbleRenderer";
    private static final String NAMEPLATE_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;)Z";
    private static final String ALIGNMENT_LIFT_DESC = "(D)D";
    private static final String NPC_SPEECH_RENDER_DESC =
            "(Lnet/minecraft/client/Minecraft;"
                    + "Lnet/minecraft/world/World;F)V";
    private static final String NPC_IMMERSIVE_HOOK_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Llotr/common/entity/npc/LOTREntityNPC;"
                    + "Ljava/lang/String;)V";
    private static final String ROLEPLAY_IDENTITY_HOOK_OWNER =
            "com/ninuna/losttales/character/identity/"
                    + "RoleplayCharacterIdentityHook";
    private static final String GAMEPLAY_ID_HOOK_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;)Ljava/util/UUID;";
    private static final String ROLEPLAY_NAME_HOOK_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;)Ljava/lang/String;";
    private static final String GAMEPLAY_NAME_HOOK_DESC =
            "(Ljava/util/UUID;)Ljava/lang/String;";
    private static final String DEATH_MESSAGE_HOOK_DESC =
            "(Lnet/minecraft/util/IChatComponent;"
                    + "Lnet/minecraft/entity/player/EntityPlayerMP;)"
                    + "Lnet/minecraft/util/IChatComponent;";
    private static final String CLIENT_IDENTITY_HOOK_OWNER =
            "com/ninuna/losttales/client/character/"
                    + "ClientRoleplayCharacterIdentityHook";
    private static final String MAP_PLAYER_NAME_HOOK_DESC =
            "(Lcom/mojang/authlib/GameProfile;)Ljava/lang/String;";
    private static final String ACCESSORY_CONTAINER_HOOK_OWNER =
            "com/ninuna/losttales/accessory/inventory/"
                    + "AccessoryContainerHooks";
    private static final String ACCESSORY_CREATIVE_HOOK_OWNER =
            "com/ninuna/losttales/accessory/inventory/"
                    + "AccessoryCreativeInventoryHook";
    private static final String ACCESSORY_DEATH_HOOK_OWNER =
            "com/ninuna/losttales/accessory/player/AccessoryDeathHooks";
    private static final String ACCESSORY_CONCEALMENT_HOOK_OWNER =
            "com/ninuna/losttales/accessory/effect/"
                    + "AccessoryConcealmentHooks";
    private static final String ACCESSORY_LOTR_MAP_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/LotrAccessoryMapHooks";
    private static final String LOTR_MAP_EDGE_FILL_HOOK_OWNER =
            "com/ninuna/losttales/client/map/"
                    + "LostTalesLotrMapEdgeRenderer";
    private static final String LOTR_MAP_LAYOUT_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/"
                    + "LostTalesLotrMapLayout";
    private static final String LOTR_MAP_LEGEND_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/"
                    + "LostTalesMapLegendRegistry";
    private static final String LOTR_MAP_MARKER_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/"
                    + "LostTalesLotrMapMarkerIconOverlay";
    private static final String LOTR_MAP_LABEL_STYLE_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/"
                    + "LostTalesLotrMapLabelStyle";
    /**
     * LOTR's white, as the literal its map strip hands the font:
     * recognised to route it through the label style, never drawn here.
     */
    private static final int LOTR_MAP_TEXT_WHITE = 0x00FFFFFF;
    private static final String MAP_LABEL_DRAW_HOOK_DESC =
            "(Lnet/minecraft/client/gui/FontRenderer;"
                    + "Ljava/lang/String;III)I";
    private static final String LOTR_MAP_ROTATION_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/"
                    + "LostTalesLotrMapRotation";
    private static final String LOTR_MAP_COMPASS_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/"
                    + "LostTalesLotrMapCompass";
    private static final String GUI_ANIMATION_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/animation/"
                    + "LostTalesGuiAnimationHooks";
    private static final String SMOOTH_INVENTORY_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/inventory/"
                    + "LostTalesSmoothInventoryHooks";
    private static final String TOOLTIP_SMOOTHING_OWNER =
            "com/ninuna/losttales/client/gui/tooltip/"
                    + "LostTalesTooltipSmoothing";
    private static final String TOOLTIP_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/tooltip/"
                    + "LostTalesTooltipHooks";
    private static final String CHAT_HIT_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesChatHitHooks";
    private static final String CHAT_WRAP_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesChatWrapHooks";
    private static final String CHAT_HISTORY_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesChatHistoryHooks";
    private static final String CHAT_HISTORY_ACTIVE_PROPERTY =
            "losttales.chatHistory.active";
    private static final String CHAT_LOG_HOOK_OWNER =
            "com/ninuna/losttales/chat/ChatMessageValidator";
    private static final String LOTR_GUI_ACHIEVEMENT_HOVER =
            "lotr.client.gui.LOTRGuiAchievementHoverEvent";
    private static final String LOTR_ACHIEVEMENT_HOVER_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesLotrAchievementHoverHook";
    /** Vanilla's history limit, as the literal its trimming loops test. */
    private static final int VANILLA_CHAT_HISTORY = 100;
    private static final String MENU_FRAMERATE_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/animation/"
                    + "LostTalesMenuFramerateHook";
    public static final String MENU_FRAMERATE_ACTIVE_PROPERTY =
            "losttales.menuFramerate.active";
    /** Vanilla's menu framerate cap, as the literal the getter returns. */
    private static final int VANILLA_MENU_FRAMERATE = 30;
    private static final String LOTR_PLAYER_DATA =
            "lotr.common.LOTRPlayerData";
    private static final String LOTR_ADD_ALIGNMENT_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Llotr/common/fac/LOTRAlignmentValues$AlignmentBonus;"
                    + "Llotr/common/fac/LOTRFaction;Ljava/util/List;DDD)"
                    + "Llotr/common/fac/LOTRAlignmentBonusMap;";
    private static final String LOTR_NET_HANDLER_PLAY_SERVER =
            "lotr.common.LOTRNetHandlerPlayServer";
    private static final String LOTR_ATTACK_TIMING = "lotr.client.LOTRAttackTiming";
    private static final String LOTR_SLOT_TRADE = "lotr.common.inventory.LOTRSlotTrade";
    private static final String LOTR_GUI_TRADE = "lotr.client.gui.LOTRGuiTrade";
    private static final String LOTR_PACKET_SELL_HANDLER =
            "lotr.common.network.LOTRPacketSell$Handler";
    private static final String LOTR_CONTAINER_ANVIL =
            "lotr.common.inventory.LOTRContainerAnvil";
    private static final String LOTR_SLOT_ANVIL_OUTPUT =
            "lotr.common.inventory.LOTRSlotAnvilOutput";
    private static final String PERK_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/LostTalesLotrPerkPatchHooks";
    private static final String CLIENT_PERK_HOOK_OWNER =
            "com/ninuna/losttales/client/progression/ClientPerkHooks";
    private static final String LOTR_HIRED_NPC_INFO =
            "lotr.common.entity.npc.LOTRHiredNPCInfo";
    private static final String LOTR_CUSTOM_WAYPOINT =
            "lotr.common.world.map.LOTRCustomWaypoint";
    private static final String LOTR_SHARED_WAYPOINT_NAME_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/LostTalesLotrSharedWaypointNames";
    private static final String LOTR_FELLOWSHIP_DO =
            "lotr.common.network.LOTRPacketFellowshipDo";
    private static final String LOTR_FELLOWSHIP_CREATE_HANDLER =
            "lotr.common.network.LOTRPacketFellowshipCreate$Handler";
    private static final String LOTR_FELLOWSHIP_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/LostTalesLotrFellowshipRequestHook";
    private static final String LOTR_FELLOWSHIP_DESC =
            "Llotr/common/fellowship/LOTRFellowship;";
    private static final String LOTR_FELLOWSHIP_CREATE_DESC =
            "(Llotr/common/network/LOTRPacketFellowshipCreate;"
                    + "Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)"
                    + "Lcpw/mods/fml/common/network/simpleimpl/IMessage;";
    private static final String LOTR_SHARE_CWP_HANDLER =
            "lotr.common.network.LOTRPacketShareCWP$Handler";
    private static final String LOTR_SHARE_CWP_DESC =
            "(Llotr/common/network/LOTRPacketShareCWP;"
                    + "Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)"
                    + "Lcpw/mods/fml/common/network/simpleimpl/IMessage;";
    private static final String LOTR_FELLOWSHIP_SHARE_ROW_HOOK_OWNER =
            "com/ninuna/losttales/client/fellowship/LostTalesLotrFellowshipShareRows";
    private static final String LOTR_TRAVELLING_TRADER_INFO =
            "lotr.common.entity.npc.LOTRTravellingTraderInfo";
    private static final String LOTR_TRADER_NOTICE_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/LostTalesLotrTraderNoticeHook";
    private static final String LOTR_MESSAGE_ALL_PLAYERS_DESC =
            "(Lnet/minecraft/world/World;Lnet/minecraft/util/IChatComponent;)V";
    private static final String LOTR_HIRED_UNIT_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/hired/LostTalesLotrHiredUnitHook";
    private static final String LOTR_HIRE_UNIT_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;Z"
                    + "Llotr/common/fac/LOTRFaction;"
                    + "Llotr/common/entity/npc/LOTRUnitTradeEntry;"
                    + "Ljava/lang/String;Lnet/minecraft/entity/Entity;)V";
    private static final String FAST_TRAVEL_ARRIVAL_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/"
                    + "LostTalesLotrFastTravelArrivalHook";
    private static final String LOTR_QUEST_PRESENTATION_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/"
                    + "LotrQuestPresentationHooks";
    private static final String SERVER_CONFIGURATION_MANAGER =
            "net.minecraft.server.management.ServerConfigurationManager";
    private static final String SERVER_BROADCAST_HOOK_OWNER =
            "com/ninuna/losttales/chat/server/LostTalesServerBroadcastHook";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (SERVER_CONFIGURATION_MANAGER.equals(transformedName)) {
            return transformServerBroadcast(basicClass);
        }
        if (ENTITY_RENDERER.equals(transformedName)) {
            return transformGuiScreenDraw(transformCamera(basicClass));
        }
        if (GUI_SCREEN.equals(transformedName)) {
            return transformGuiScreenTooltip(
                    transformGuiScreenBackground(
                            transformGuiScreenInput(basicClass)));
        }
        if (GUI_MAIN_MENU.equals(transformedName)
                || LOTR_GUI_MAIN_MENU.equals(transformedName)) {
            return transformMainMenuTitle(transformMainMenuText(basicClass,
                    LOTR_GUI_MAIN_MENU.equals(transformedName)
                            ? LOTR_MAIN_MENU_TEXT_ACTIVE_PROPERTY
                            : MAIN_MENU_TEXT_ACTIVE_PROPERTY),
                    LOTR_GUI_MAIN_MENU.equals(transformedName));
        }
        if (GUI_NEW_CHAT.equals(transformedName)) {
            return transformGuiNewChatLog(transformGuiNewChatDelete(
                    transformGuiNewChatHistory(transformGuiNewChatWrap(
                            transformGuiNewChatHitTest(basicClass)))));
        }
        if (LOTR_PLAYER_DATA.equals(transformedName)) {
            return transformLotrAlignmentGain(
                    transformLotrFastTravelArrival(basicClass));
        }
        if (LOTR_NET_HANDLER_PLAY_SERVER.equals(transformedName)) {
            return transformLotrServerAttackTime(basicClass);
        }
        if (LOTR_ATTACK_TIMING.equals(transformedName)) {
            return transformLotrClientAttackTime(basicClass);
        }
        if (LOTR_SLOT_TRADE.equals(transformedName)) {
            return transformLotrSlotTrade(basicClass);
        }
        if (LOTR_GUI_TRADE.equals(transformedName)) {
            return transformLotrGuiTrade(basicClass);
        }
        if (LOTR_PACKET_SELL_HANDLER.equals(transformedName)) {
            return transformLotrPacketSell(basicClass);
        }
        if (LOTR_CONTAINER_ANVIL.equals(transformedName)) {
            return transformLotrContainerAnvil(basicClass);
        }
        if (LOTR_SLOT_ANVIL_OUTPUT.equals(transformedName)) {
            return transformLotrSlotAnvilOutput(basicClass);
        }
        if (LOTR_HIRED_NPC_INFO.equals(transformedName)) {
            return transformLotrHiredUnit(basicClass);
        }
        if (LOTR_CUSTOM_WAYPOINT.equals(transformedName)) {
            return transformLotrSharedWaypointName(basicClass);
        }
        if (LOTR_FELLOWSHIP_DO.equals(transformedName)) {
            return transformLotrFellowshipRequests(basicClass);
        }
        if (LOTR_FELLOWSHIP_CREATE_HANDLER.equals(transformedName)) {
            return transformLotrFellowshipCreate(basicClass);
        }
        if (LOTR_SHARE_CWP_HANDLER.equals(transformedName)) {
            return transformLotrFellowshipShare(basicClass);
        }
        if (GUI_CONTAINER.equals(transformedName)) {
            return transformGuiContainer(basicClass);
        }
        if (MINECRAFT.equals(transformedName)) {
            return transformMinecraftWindowDefaults(transformMinecraftMenuFramerate(
                    transformMinecraftPickBlock(basicClass)));
        }
        if (PLAYER_CONTROLLER.equals(transformedName)) {
            return transformPlayerController(basicClass);
        }
        if (ENTITY_PLAYER_MP.equals(transformedName)) {
            return transformEntityPlayerMpChatLine(
                    transformEntityPlayerMpDeathMessage(basicClass));
        }
        if (ENTITY_PLAYER.equals(transformedName)) {
            return transformEntityPlayerContainer(basicClass);
        }
        if (ENTITY.equals(transformedName)) {
            return transformEntityInvisibility(basicClass);
        }
        if (ENTITY_LIVING_BASE.equals(transformedName)) {
            return transformEntityLivingBaseSight(basicClass);
        }
        if (ENTITY_AI_TARGET.equals(transformedName)) {
            return transformEntityAiTarget(basicClass);
        }
        if (NET_HANDLER_PLAY_SERVER.equals(transformedName)) {
            return transformCreativeInventoryAction(basicClass);
        }
        if (RENDER_MANAGER.equals(transformedName)) {
            return transformDebugBox(basicClass);
        }
        if (LOTR_FAST_TRAVEL_HANDLER.equals(transformedName)) {
            return transformLotrFastTravelHandler(basicClass);
        }
        if (LOTR_FACTION_BOUNTIES.equals(transformedName)) {
            return transformLotrFactionBounties(basicClass);
        }
        if (LOTR_FACTION_BOUNTY_PLAYER_DATA.equals(transformedName)) {
            return transformLotrFactionBountyPlayerData(basicClass);
        }
        if (LOTR_SPEECH.equals(transformedName)) {
            return transformLotrSpeech(basicClass);
        }
        if (LOTR_TRAVELLING_TRADER_INFO.equals(transformedName)) {
            return transformLotrTraderNotice(basicClass);
        }
        if (LOTR_GUI_ACHIEVEMENT_HOVER.equals(transformedName)) {
            return transformLotrAchievementHover(basicClass);
        }
        if (LOTR_NPC_SPEECH_HANDLER.equals(transformedName)) {
            return transformLotrNpcSpeechHandler(basicClass);
        }
        if (LOTR_TICK_HANDLER_CLIENT.equals(transformedName)) {
            return transformLotrNpcSpeechRendering(basicClass);
        }
        if (RENDERER_LIVING_ENTITY.equals(transformedName)) {
            return transformLivingLabel(basicClass);
        }
        if (LOTR_RENDER_ALIGNMENT_BONUS.equals(transformedName)) {
            return transformLotrAlignmentLift(basicClass);
        }
        if (LOTR_GUI_MAP.equals(transformedName)) {
            return transformLotrFellowshipShareRow(
                    transformLotrMapScene(transformLotrGuiMap(basicClass), false));
        }
        if (LOTR_GUI_MINIQUEST_TRACKER.equals(transformedName)) {
            return transformLotrQuestTracker(basicClass);
        }
        if (LOTR_CLIENT_PROXY.equals(transformedName)) {
            return transformLotrClientProxy(basicClass);
        }
        if ("lotr.client.gui.LOTRGuiRendererMap".equals(transformedName)) {
            return transformLotrMapScene(basicClass, true);
        }
        if (LOTR_LEVEL_DATA.equals(transformedName)) {
            return transformLotrPlayerLocations(basicClass);
        }
        return basicClass;
    }

    private static byte[] transformLotrQuestTracker(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"drawTracker".equals(method.name)
                        || !"(Lnet/minecraft/client/Minecraft;"
                                .concat("Lnet/minecraft/entity/player/EntityPlayer;)V")
                                .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method,
                        LOTR_QUEST_PRESENTATION_HOOK_OWNER,
                        "shouldRenderNativeTracker")) {
                    activate(LOTR_QUEST_TRACKER_ACTIVE_PROPERTY);
                    return basicClass;
                }
                LabelNode render = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(hookCall(LOTR_QUEST_PRESENTATION_HOOK_OWNER,
                        "shouldRenderNativeTracker", "()Z", HookFallback.TRUE));
                guard.add(new JumpInsnNode(Opcodes.IFNE, render));
                guard.add(new InsnNode(Opcodes.RETURN));
                guard.add(render);
                method.instructions.insert(guard);
                activate(LOTR_QUEST_TRACKER_ACTIVE_PROPERTY);
                info("Patched the native LOTR quest tracker behind the Lost Tales fallback option");
                return write(owner);
            }
            warn("Could not locate LOTRGuiMiniquestTracker#drawTracker; keeping LOTR's tracker");
            return basicClass;
        } catch (Throwable failure) {
            rollBack();
            warn("Failed to guard the LOTR quest tracker: " + failure);
            return basicClass;
        }
    }

    /** Shares ground layers across gameplay, embedded and animated LOTR maps. */
    private static byte[] transformLotrMapScene(byte[] basicClass, boolean background) {
        String property = background ? LOTR_MAP_BACKGROUND_ACTIVE_PROPERTY : LOTR_MAP_SCENE_ACTIVE_PROPERTY;
        String name = background ? "renderMap" : "renderMapAndOverlay";
        String descriptor = background
                ? "(Lnet/minecraft/client/gui/GuiScreen;Llotr/client/gui/LOTRGuiMap;FIIII)V" : "(ZFZ)V";
        String hookName = background ? "renderBackground" : "renderMap";
        try {
            ClassNode owner = read(basicClass);
            if (background) {
                boolean found = false;
                for (Object value : owner.fields) {
                    org.objectweb.asm.tree.FieldNode field = (org.objectweb.asm.tree.FieldNode)value;
                    found |= "sepia".equals(field.name) && "Z".equals(field.desc)
                            && (field.access & Opcodes.ACC_STATIC) == 0;
                }
                if (!found) {
                    warn("Shared background map requires LOTRGuiRendererMap.sepia; keeping LOTR rendering");
                    return basicClass;
                }
            }
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                // LOTR's own names are identical in development and production.
                if (!name.equals(method.name) || !descriptor.equals(method.desc)) continue;
                if (containsHook(method, LOTR_MAP_SCENE_HOOK_OWNER, hookName)) {
                    activate(property);
                    return basicClass;
                }
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                if (background) {
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    hook.add(new VarInsnNode(Opcodes.FLOAD, 3));
                    for (int slot = 4; slot <= 7; slot++) hook.add(new VarInsnNode(Opcodes.ILOAD, slot));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new FieldInsnNode(Opcodes.GETFIELD, owner.name, "sepia", "Z"));
                } else {
                    hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    hook.add(new VarInsnNode(Opcodes.FLOAD, 2));
                    hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
                }
                hook.add(hookCall(LOTR_MAP_SCENE_HOOK_OWNER,
                        hookName, background
                        ? "(Llotr/client/gui/LOTRGuiRendererMap;Lnet/minecraft/client/gui/GuiScreen;Llotr/client/gui/LOTRGuiMap;FIIIIZ)Z"
                        : "(Llotr/client/gui/LOTRGuiMap;ZFZ)Z", HookFallback.FALSE));
                LabelNode fallback = new LabelNode();
                hook.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
                hook.add(new InsnNode(Opcodes.RETURN));
                hook.add(fallback);
                method.instructions.insert(hook);
                byte[] transformed = write(owner);
                activate(property);
                info("Patched shared map scene: " + owner.name + "#" + name);
                return transformed;
            }
            warn("Could not locate shared map target " + name + descriptor + "; keeping LOTR rendering");
        } catch (Throwable failure) {
            rollBack();
            warn("Failed to patch shared map scene: " + failure);
        }
        return basicClass;
    }

    /** Replaces only the title blits, keeping each menu's background renderer. */
    private static byte[] transformMainMenuTitle(byte[] basicClass, boolean lotr) {
        String property = lotr ? LOTR_MAIN_MENU_TITLE_ACTIVE_PROPERTY : MAIN_MENU_TITLE_ACTIVE_PROPERTY;
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode) value;
                if (!("drawScreen".equals(method.name) || "func_73863_a".equals(method.name))
                        || !"(IIF)V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, MAIN_MENU_TITLE_HOOK_OWNER, "drawPiece")) {
                    activate(property);
                    return basicClass;
                }
                FieldInsnNode buttons = null;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        if (field.getOpcode() == Opcodes.GETFIELD
                                && ("buttonList".equals(field.name) || "field_146292_n".equals(field.name))
                                && "Ljava/util/List;".equals(field.desc)) {
                            buttons = field;
                            break;
                        }
                    }
                }
                if (buttons == null) {
                    break;
                }
                int pieces = 0;
                int subtitles = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && ("drawTexturedModalRect".equals(call.name) || "func_73729_b".equals(call.name))
                            && "(IIIIII)V".equals(call.desc)
                            && (owner.name.equals(call.owner) || "net/minecraft/client/gui/Gui".equals(call.owner)
                            || "net/minecraft/client/gui/GuiMainMenu".equals(call.owner))) {
                        InsnList arguments = new InsnList();
                        arguments.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        arguments.add(new FieldInsnNode(Opcodes.GETFIELD, buttons.owner, buttons.name, buttons.desc));
                        arguments.add(new InsnNode(lotr ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                        method.instructions.insertBefore(call, arguments);
                        redirect(call, MAIN_MENU_TITLE_HOOK_OWNER, "drawPiece",
                                "(Lnet/minecraft/client/gui/GuiScreen;IIIIIILjava/util/List;Z)V");
                        pieces++;
                    } else if (lotr && HookGuard.calls(call, MAIN_MENU_TEXT_HOOK_OWNER,
                            "drawString")) {
                        AbstractInsnNode y = previousCode(previousCode(call));
                        if (y instanceof IntInsnNode && ((IntInsnNode) y).operand == 80) {
                            InsnList arguments = new InsnList();
                            arguments.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            arguments.add(new FieldInsnNode(Opcodes.GETFIELD, buttons.owner, buttons.name, buttons.desc));
                            arguments.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            arguments.add(hookCall(MAIN_MENU_TITLE_HOOK_OWNER, "subtitleY",
                                    "(ILjava/util/List;Lnet/minecraft/client/gui/GuiScreen;)I",
                                    HookFallback.argument(0)));
                            method.instructions.insert(y, arguments);
                            subtitles++;
                        }
                    }
                }
                // All original pieces must be replaced together, including
                // vanilla's rare alternate logo; otherwise keep the old title.
                if (pieces == (lotr ? 2 : 7) && subtitles == (lotr ? 1 : 0)) {
                    byte[] result = write(owner);
                    activate(property);
                    info("Patched custom main-menu title: " + owner.name);
                    return result;
                }
            }
            warn("Could not patch main-menu title: " + property);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch main-menu title: " + property + ": " + throwable);
        }
        return basicClass;
    }

    /** Main menus have no per-label Forge event; redirect their text calls. */
    private static byte[] transformMainMenuText(byte[] basicClass, String activeProperty) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode) value;
                if (!("drawScreen".equals(method.name)
                        || "func_73863_a".equals(method.name))
                        || !"(IIF)V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, MAIN_MENU_TEXT_HOOK_OWNER, "drawString")) {
                    activate(activeProperty);
                    return basicClass;
                }
                boolean patched = false;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                            || !"(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"
                            .equals(call.desc)
                            || !(owner.name.equals(call.owner)
                            || "net/minecraft/client/gui/Gui".equals(call.owner)
                            || "net/minecraft/client/gui/GuiScreen".equals(call.owner)
                            || "net/minecraft/client/gui/GuiMainMenu".equals(call.owner))) {
                        continue;
                    }
                    boolean centered = "drawCenteredString".equals(call.name)
                            || "func_73732_a".equals(call.name);
                    if (!centered && !"drawString".equals(call.name)
                            && !"func_73731_b".equals(call.name)) {
                        continue;
                    }
                    redirect(call, MAIN_MENU_TEXT_HOOK_OWNER,
                            centered ? "drawCenteredString" : "drawString",
                            "(Lnet/minecraft/client/gui/Gui;"
                                    + "Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V");
                    patched = true;
                }
                if (patched) {
                    byte[] result = write(owner);
                    activate(activeProperty);
                    info("Patched main-menu text onto the Lost Tales palette: " + owner.name);
                    return result;
                }
            }
            warn("Could not patch main-menu text: " + activeProperty);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch main-menu text: " + activeProperty + ": " + throwable);
        }
        return basicClass;
    }

    /**
     * Makes the player's constructor build the accessory-aware container
     * through {@code AccessoryContainerHooks.playerContainer}: vanilla's
     * {@code NEW} and {@code DUP} go, its constructor call becomes the
     * hook's, and the frames that held the unbuilt container lose it. All
     * of it or none: an allocation without its matching call stays
     * vanilla. Should the hook fail, its guard builds vanilla's container.
     * Answers whether the constructor builds it now.
     */
    static boolean patchPlayerContainer(MethodNode constructor) {
        String vanilla = "net/minecraft/inventory/ContainerPlayer";
        String desc = "(Lnet/minecraft/entity/player/InventoryPlayer;"
                + "ZLnet/minecraft/entity/player/EntityPlayer;)V";
        TypeInsnNode allocation = null;
        MethodInsnNode construction = null;
        int allocations = 0;
        int constructions = 0;
        for (AbstractInsnNode instruction = constructor.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode && HookGuard.calls(
                    (MethodInsnNode)instruction, ACCESSORY_CONTAINER_HOOK_OWNER,
                    "playerContainer")) {
                return true;
            }
            if (instruction instanceof TypeInsnNode
                    && instruction.getOpcode() == Opcodes.NEW
                    && vanilla.equals(((TypeInsnNode)instruction).desc)) {
                allocation = (TypeInsnNode)instruction;
                allocations++;
            } else if (instruction instanceof MethodInsnNode
                    && instruction.getOpcode() == Opcodes.INVOKESPECIAL
                    && "<init>".equals(((MethodInsnNode)instruction).name)
                    && desc.equals(((MethodInsnNode)instruction).desc)
                    && vanilla.equals(((MethodInsnNode)instruction).owner)) {
                construction = (MethodInsnNode)instruction;
                constructions++;
            }
        }
        if (allocations != 1 || constructions != 1) {
            return false;
        }
        AbstractInsnNode duplicate = nextCode(allocation);
        if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) {
            return false;
        }
        List<LabelNode> unbuilt = labelsBefore(allocation);
        constructor.instructions.remove(duplicate);
        constructor.instructions.remove(allocation);
        constructor.instructions.set(construction, hookCall(
                ACCESSORY_CONTAINER_HOOK_OWNER, "playerContainer",
                "(Lnet/minecraft/entity/player/InventoryPlayer;"
                        + "ZLnet/minecraft/entity/player/EntityPlayer;)"
                        + "Lnet/minecraft/inventory/ContainerPlayer;",
                HookFallback.construct(vanilla, desc)));
        forgetUnbuilt(constructor, unbuilt);
        return true;
    }

    /** The labels that stand right before an instruction: what a frame names it by. */
    private static List<LabelNode> labelsBefore(AbstractInsnNode instruction) {
        List<LabelNode> labels = new ArrayList<LabelNode>();
        for (AbstractInsnNode cursor = instruction.getPrevious();
             cursor != null && cursor.getOpcode() < 0; cursor = cursor.getPrevious()) {
            if (cursor instanceof LabelNode) {
                labels.add((LabelNode)cursor);
            }
        }
        return labels;
    }

    /**
     * Takes an allocation that is gone out of every frame's stack: a frame
     * names an object not yet built by the label of its {@code NEW}.
     */
    private static void forgetUnbuilt(MethodNode method, List<LabelNode> unbuilt) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FrameNode)) {
                continue;
            }
            FrameNode frame = (FrameNode)instruction;
            if (frame.stack == null) {
                continue;
            }
            // The list a frame is read with cannot be changed in place.
            List<Object> stack = new ArrayList<Object>(frame.stack);
            if (!stack.removeAll(unbuilt)) {
                continue;
            }
            frame.stack = stack;
            if (frame.type == Opcodes.F_SAME1 && frame.stack.isEmpty()) {
                frame.type = Opcodes.F_SAME;
            }
        }
    }

    /** Installs the player container and the Forge drop-capture transaction hook. */
    private static byte[] transformEntityPlayerContainer(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean containerPatched = false;
            boolean deathPatched = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if ("<init>".equals(method.name)
                        && "(Lnet/minecraft/world/World;"
                        .concat("Lcom/mojang/authlib/GameProfile;)V")
                        .equals(method.desc)) {
                    containerPatched = patchPlayerContainer(method);
                }

                if (("onDeath".equals(method.name)
                        || "func_70645_a".equals(method.name))
                        && "(Lnet/minecraft/util/DamageSource;)V".equals(
                        method.desc)) {
                    if (containsHook(method, ACCESSORY_DEATH_HOOK_OWNER,
                            "captureAccessoryDrop")) {
                        deathPatched = true;
                        continue;
                    }
                    for (AbstractInsnNode instruction =
                         method.instructions.getFirst();
                         instruction != null;
                         instruction = instruction.getNext()) {
                        if (!(instruction instanceof FieldInsnNode)
                                || instruction.getOpcode() != Opcodes.PUTFIELD) {
                            continue;
                        }
                        FieldInsnNode field = (FieldInsnNode)instruction;
                        AbstractInsnNode zero = previousCode(field);
                        AbstractInsnNode playerLoad = previousCode(zero);
                        if (!"captureDrops".equals(field.name)
                                || !"Z".equals(field.desc)
                                || zero == null
                                || zero.getOpcode() != Opcodes.ICONST_0
                                || !(playerLoad instanceof VarInsnNode)
                                || playerLoad.getOpcode() != Opcodes.ALOAD
                                || ((VarInsnNode)playerLoad).var != 0) {
                            continue;
                        }
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        hook.add(hookCall(
                                ACCESSORY_DEATH_HOOK_OWNER,
                                "captureAccessoryDrop",
                                "(Lnet/minecraft/entity/player/EntityPlayer;)V",
                                HookFallback.NOTHING));
                        method.instructions.insertBefore(playerLoad, hook);
                        deathPatched = true;
                        break;
                    }
                }

            }
            byte[] result = containerPatched || deathPatched
                    ? write(owner) : basicClass;
            if (containerPatched) {
                activate(ACCESSORY_CONTAINER_ACTIVE_PROPERTY);
                info("Installed accessory-aware player container");
            } else {
                warn("Could not patch EntityPlayer player-container construction; "
                        + "accessory slots will remain unavailable");
            }
            if (deathPatched) {
                activate(ACCESSORY_DEATH_ACTIVE_PROPERTY);
                info("Installed accessory death-drop capture hook");
            } else {
                warn("Could not patch EntityPlayer death-drop capture; "
                        + "accessory insertion must remain disabled");
            }
            return result;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch EntityPlayer accessory integration: "
                    + throwable);
            return basicClass;
        }
    }

    /** Rejects concealed players before vanilla and LOTR target suitability. */
    private static byte[] transformEntityAiTarget(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("isSuitableTarget".equals(method.name)
                        || "func_75296_a".equals(method.name))
                        || !"(Lnet/minecraft/entity/EntityLivingBase;Z)Z"
                        .equals(method.desc)) {
                    continue;
                }
                if (!containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    injectConcealmentGuard(method, 1);
                }
                if (containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    activate(ACCESSORY_AI_TARGET_ACTIVE_PROPERTY);
                    info("Installed accessory AI target-suitability hook");
                    return write(owner);
                }
            }
            warn("Could not patch EntityAITarget target suitability");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch EntityAITarget target suitability: "
                    + throwable);
            return basicClass;
        }
    }

    /** Makes vanilla rendering and visibility consumers treat the wearer as vanished. */
    private static byte[] transformEntityInvisibility(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("isInvisible".equals(method.name)
                        || "func_82150_aj".equals(method.name))
                        || !"()Z".equals(method.desc)) {
                    continue;
                }
                if (!containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    injectBooleanTrueGuard(method, 0);
                }
                if (containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    activate(ACCESSORY_INVISIBILITY_ACTIVE_PROPERTY);
                    info("Installed accessory vanilla-invisibility hook");
                    return write(owner);
                }
            }
            warn("Could not patch Entity invisibility predicate");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch Entity invisibility predicate: "
                    + throwable);
            return basicClass;
        }
    }

    /** Makes living sight and collision checks fail for concealed players. */
    private static byte[] transformEntityLivingBaseSight(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean sightPatched = false;
            boolean collisionPatched = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (("canEntityBeSeen".equals(method.name)
                        || "func_70685_l".equals(method.name))
                        && "(Lnet/minecraft/entity/Entity;)Z".equals(
                        method.desc)) {
                    if (!containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed")) {
                        injectConcealmentGuard(method, 1);
                    }
                    sightPatched = containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed");
                }
                if (("canBeCollidedWith".equals(method.name)
                        || "func_70067_L".equals(method.name))
                        && "()Z".equals(method.desc)) {
                    if (!containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed")) {
                        injectConcealmentGuard(method, 0);
                    }
                    collisionPatched = containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed");
                }
            }
            if (sightPatched) {
                activate(ACCESSORY_SIGHT_ACTIVE_PROPERTY);
            } else {
                warn("Could not patch EntityLivingBase sight checks");
            }
            if (collisionPatched) {
                activate(ACCESSORY_COLLISION_ACTIVE_PROPERTY);
            } else {
                warn("Could not patch EntityLivingBase collision targeting");
            }
            if (sightPatched || collisionPatched) {
                info("Installed accessory living sight/collision hooks");
                return write(owner);
            }
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch EntityLivingBase sight checks: "
                    + throwable);
            return basicClass;
        }
    }

    private static void injectConcealmentGuard(
            MethodNode method, int entityLocal) {
        LabelNode vanilla = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
        guard.add(hookCall(
                ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed",
                "(Lnet/minecraft/entity/Entity;)Z",
                HookFallback.FALSE));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
        guard.add(vanilla);
        method.instructions.insert(guard);
    }

    private static void injectBooleanTrueGuard(
            MethodNode method, int entityLocal) {
        LabelNode vanilla = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
        guard.add(hookCall(
                ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed",
                "(Lnet/minecraft/entity/Entity;)Z",
                HookFallback.FALSE));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
        guard.add(vanilla);
        method.instructions.insert(guard);
    }

    /** Supplies the window default before display creation and Forge initialization. */
    private static byte[] transformMinecraftWindowDefaults(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            // Constructor names and parameter slots are identical in MCP and SRG.
            String descriptor = "(Lnet/minecraft/util/Session;IIZZLjava/io/File;"
                    + "Ljava/io/File;Ljava/io/File;Ljava/net/Proxy;Ljava/lang/String;"
                    + "Lcom/google/common/collect/Multimap;Ljava/lang/String;)V";
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode) value;
                if (!"<init>".equals(method.name) || !descriptor.equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, WINDOW_DEFAULTS_HOOK_OWNER, "width")) {
                    activate(WINDOW_DEFAULTS_ACTIVE_PROPERTY);
                    return basicClass;
                }
                // Rewrite only the dimension arguments. Vanilla stores these in
                // displayWidth/Height and tempDisplayWidth/Height for F11 restore.
                // Its subsequent options.txt overrides still take precedence.
                // Both are worked out from the pair as given: the height waits
                // on the stack while the width is stored. A hook that fails
                // keeps the size it was handed.
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ILOAD, 2));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
                hook.add(hookCall(WINDOW_DEFAULTS_HOOK_OWNER, "height", "(II)I",
                        HookFallback.argument(1)));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 2));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
                hook.add(hookCall(WINDOW_DEFAULTS_HOOK_OWNER, "width", "(II)I",
                        HookFallback.argument(0)));
                hook.add(new VarInsnNode(Opcodes.ISTORE, 2));
                hook.add(new VarInsnNode(Opcodes.ISTORE, 3));
                method.instructions.insert(hook);
                byte[] transformed = write(owner);
                activate(WINDOW_DEFAULTS_ACTIVE_PROPERTY);
                info("Patched default startup window dimensions");
                return transformed;
            }
            warn("Could not locate Minecraft constructor; keeping vanilla window dimensions");
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch startup window dimensions: " + throwable);
        }
        return basicClass;
    }

    /** Preserves vanilla hotbar slot IDs after appending the accessory slot. */
    private static byte[] transformMinecraftPickBlock(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("middleClickMouse".equals(method.name)
                        || "func_147112_ai".equals(method.name))
                        || !"()V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method,
                        ACCESSORY_CONTAINER_HOOK_OWNER,
                        "resolveVanillaInventorySlotCount")) {
                    activate(ACCESSORY_PICK_BLOCK_ACTIVE_PROPERTY);
                    return basicClass;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                            && "java/util/List".equals(call.owner)
                            && "size".equals(call.name)
                            && "()I".equals(call.desc)) {
                        // The redirect clears the interface-owner flag the
                        // list's call carried: left set, it writes an
                        // InterfaceMethodref for a class method, which Java 8
                        // rejects during Minecraft verification.
                        redirect(call, ACCESSORY_CONTAINER_HOOK_OWNER,
                                "resolveVanillaInventorySlotCount", "(Ljava/util/List;)I");
                        activate(ACCESSORY_PICK_BLOCK_ACTIVE_PROPERTY);
                        info("Patched creative pick-block hotbar indexing");
                        return write(owner);
                    }
                }
            }
            warn("Could not patch Minecraft creative pick-block indexing");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch creative pick-block indexing: "
                    + throwable);
            return basicClass;
        }
    }

    /** Routes creative slot 45 through the accessory validator. */
    private static byte[] transformCreativeInventoryAction(
            byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("processCreativeInventoryAction".equals(method.name)
                        || "func_147344_a".equals(method.name))
                        || !"(Lnet/minecraft/network/play/client/"
                        .concat("C10PacketCreativeInventoryAction;)V")
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, ACCESSORY_CREATIVE_HOOK_OWNER,
                        "handle")) {
                    activate(ACCESSORY_CREATIVE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                String playerField = findPlayerEntityField(method);
                if (playerField == null) {
                    warn("Could not locate NetHandlerPlayServer player field "
                            + "for accessory creative validation");
                    return basicClass;
                }
                LabelNode vanilla = new LabelNode();
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new FieldInsnNode(
                        Opcodes.GETFIELD,
                        "net/minecraft/network/NetHandlerPlayServer",
                        playerField,
                        "Lnet/minecraft/entity/player/EntityPlayerMP;"));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(hookCall(
                        ACCESSORY_CREATIVE_HOOK_OWNER,
                        "handle",
                        "(Lnet/minecraft/entity/player/EntityPlayerMP;"
                                + "Lnet/minecraft/network/play/client/"
                                + "C10PacketCreativeInventoryAction;)Z",
                        HookFallback.FALSE));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
                hook.add(new org.objectweb.asm.tree.InsnNode(
                        Opcodes.RETURN));
                hook.add(vanilla);
                method.instructions.insertBefore(
                        method.instructions.getFirst(), hook);
                activate(ACCESSORY_CREATIVE_ACTIVE_PROPERTY);
                info("Installed server accessory creative-slot validation");
                return write(owner);
            }
            warn("Could not patch creative inventory action handling");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch creative inventory action handling: "
                    + throwable);
            return basicClass;
        }
    }

    private static String findPlayerEntityField(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            if ("net/minecraft/network/NetHandlerPlayServer"
                    .equals(field.owner)
                    && "Lnet/minecraft/entity/player/EntityPlayerMP;"
                    .equals(field.desc)) {
                return field.name;
            }
        }
        return null;
    }

    /** Rewrites only the already-generated player death chat component. */
    private static byte[] transformEntityPlayerMpDeathMessage(
            byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("onDeath".equals(method.name)
                        || "func_70645_a".equals(method.name))
                        || !"(Lnet/minecraft/util/DamageSource;)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(
                        method, ROLEPLAY_IDENTITY_HOOK_OWNER,
                        "resolveDeathMessage")) {
                    activate(DEATH_MESSAGE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode invocation =
                            (MethodInsnNode)instruction;
                    if (!"net/minecraft/util/CombatTracker"
                            .equals(invocation.owner)
                            || !("func_151521_b".equals(invocation.name)
                            || "getDeathMessage".equals(invocation.name))
                            || !"()Lnet/minecraft/util/IChatComponent;"
                            .equals(invocation.desc)) {
                        continue;
                    }
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(hookCall(
                            ROLEPLAY_IDENTITY_HOOK_OWNER,
                            "resolveDeathMessage",
                            DEATH_MESSAGE_HOOK_DESC,
                            HookFallback.argument(0)));
                    method.instructions.insert(invocation, hook);
                    activate(DEATH_MESSAGE_ACTIVE_PROPERTY);
                    info("Patched player death messages with roleplay names");
                    return write(owner);
                }
            }
            warn("Could not patch player death-message broadcast");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch player death messages: " + throwable);
            return basicClass;
        }
    }

    /** Applies client map rendering hooks without changing LOTR map data. */
    private static byte[] transformLotrGuiMap(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean changed = false;
            boolean playerNameHookPresent = false;
            boolean playerRenderHookPresent = false;
            boolean edgeFillHookPresent = false;
            boolean fullscreenBoundsHookPresent = false;
            boolean frameSuppressionHookPresent = false;
            boolean subtitleLayoutHookPresent = false;
            boolean tooltipLayoutHookPresent = false;
            boolean miniQuestFilterHookPresent = false;
            boolean rotationHookPresent = false;
            boolean labelRotationHookPresent = false;
            boolean compassHookPresent = false;
            boolean windowClipHookPresent = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if ("renderPlayers".equals(method.name)
                        && "(II)V".equals(method.desc)) {
                    playerRenderHookPresent = containsHook(
                            method, LOTR_MAP_MARKER_HOOK_OWNER,
                            "shouldSuppressNativePlayerRendering");
                    if (!playerRenderHookPresent) {
                        playerRenderHookPresent =
                                injectLotrMapPlayerRenderingGuard(method);
                        changed |= playerRenderHookPresent;
                    }
                    playerNameHookPresent = containsHook(
                            method, CLIENT_IDENTITY_HOOK_OWNER,
                            "resolveMapPlayerName");
                    if (!playerNameHookPresent) {
                        for (AbstractInsnNode instruction =
                             method.instructions.getFirst();
                             instruction != null;
                             instruction = instruction.getNext()) {
                            if (!(instruction instanceof MethodInsnNode)) {
                                continue;
                            }
                            MethodInsnNode invocation =
                                    (MethodInsnNode)instruction;
                            if (invocation.getOpcode()
                                    != Opcodes.INVOKEVIRTUAL
                                    || !"com/mojang/authlib/GameProfile"
                                    .equals(invocation.owner)
                                    || !"getName".equals(invocation.name)
                                    || !"()Ljava/lang/String;"
                                    .equals(invocation.desc)) {
                                continue;
                            }
                            redirect(invocation, CLIENT_IDENTITY_HOOK_OWNER,
                                    "resolveMapPlayerName", MAP_PLAYER_NAME_HOOK_DESC);
                            playerNameHookPresent = true;
                            changed = true;
                            info("Patched LOTR map player tooltips with "
                                    + "roleplay names");
                            break;
                        }
                    }
                } else if ("renderMapAndOverlay".equals(method.name)
                        && "(ZFZ)V".equals(method.desc)) {
                    edgeFillHookPresent = containsHook(
                            method, LOTR_MAP_EDGE_FILL_HOOK_OWNER,
                            "fillClippedMapBackground");
                    if (!edgeFillHookPresent) {
                        edgeFillHookPresent = injectLotrMapEdgeFill(method);
                        changed |= edgeFillHookPresent;
                    }
                } else if ("setupMapDimensions".equals(method.name)
                        && "()V".equals(method.desc)) {
                    fullscreenBoundsHookPresent = containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "applyFullscreenBounds");
                    if (!fullscreenBoundsHookPresent) {
                        fullscreenBoundsHookPresent =
                                injectLotrMapFullscreenBounds(method);
                        changed |= fullscreenBoundsHookPresent;
                    }
                } else if ("renderGraduatedRects".equals(method.name)
                        && "(IIIIIII)V".equals(method.desc)) {
                    frameSuppressionHookPresent = containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "shouldSuppressMapFrame");
                    if (!frameSuppressionHookPresent) {
                        frameSuppressionHookPresent =
                                injectLotrMapFrameSuppression(method);
                        changed |= frameSuppressionHookPresent;
                    }
                } else if ("renderFullscreenSubtitles".equals(method.name)
                        && "([Ljava/lang/String;)V".equals(method.desc)) {
                    subtitleLayoutHookPresent = containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "beginFullscreenSubtitles")
                            && containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "endFullscreenSubtitles")
                            && containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "filterFullscreenSubtitles");
                    if (!subtitleLayoutHookPresent) {
                        subtitleLayoutHookPresent =
                                injectLotrMapSubtitleLayout(method);
                        changed |= subtitleLayoutHookPresent;
                    }
                    if (!containsHook(method,
                            LOTR_MAP_LABEL_STYLE_HOOK_OWNER,
                            "restyleMapSubtitle")) {
                        changed |= restyleLotrMapSubtitles(method);
                    }
                } else if ("renderWaypointTooltip".equals(method.name)
                        && "(Llotr/common/world/map/LOTRAbstractWaypoint;ZII)V"
                        .equals(method.desc)) {
                    tooltipLayoutHookPresent = containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "beginMapTooltip")
                            && containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "endMapTooltip");
                    if (!tooltipLayoutHookPresent) {
                        tooltipLayoutHookPresent =
                                injectLotrMapTooltipLayout(method);
                        changed |= tooltipLayoutHookPresent;
                    }
                } else if ("renderMiniQuests".equals(method.name)
                        && "(Lnet/minecraft/entity/player/EntityPlayer;II)V"
                        .equals(method.desc)) {
                    miniQuestFilterHookPresent = containsHook(
                            method, LOTR_MAP_LEGEND_HOOK_OWNER,
                            "shouldRenderLotrMiniQuests");
                    if (!miniQuestFilterHookPresent) {
                        miniQuestFilterHookPresent =
                                injectLotrMapMiniQuestFilter(method);
                        changed |= miniQuestFilterHookPresent;
                    }
                } else if (isNamed(owner, method, "drawScreen", "func_73863_a")
                        && "(IIF)V".equals(method.desc)) {
                    compassHookPresent = containsHook(
                            method, LOTR_MAP_COMPASS_HOOK_OWNER,
                            "drawMapCompass");
                    if (!compassHookPresent) {
                        compassHookPresent =
                                injectLotrMapCompass(method);
                        changed |= compassHookPresent;
                    }
                } else if ("renderLabels".equals(method.name)
                        && "()V".equals(method.desc)) {
                    labelRotationHookPresent = containsHook(
                            method, LOTR_MAP_ROTATION_HOOK_OWNER,
                            "beginSheetPass");
                    if (!labelRotationHookPresent) {
                        labelRotationHookPresent =
                                injectLotrMapUnrotatedLabels(method);
                        changed |= labelRotationHookPresent;
                    }
                    if (!containsHook(method,
                            LOTR_MAP_LABEL_STYLE_HOOK_OWNER,
                            "drawMapLabel")) {
                        changed |= restyleLotrMapLabels(method);
                    }
                } else if ("setupMapClipping".equals(method.name)
                        && "()V".equals(method.desc)) {
                    windowClipHookPresent = containsHook(
                            method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                            "lotrScissor");
                    if (!windowClipHookPresent) {
                        windowClipHookPresent =
                                redirectLotrMapScissor(method);
                        changed |= windowClipHookPresent;
                    }
                } else if ("transformMapCoords".equals(method.name)
                        && "(FF)[F".equals(method.desc)) {
                    rotationHookPresent = containsHook(
                            method, LOTR_MAP_ROTATION_HOOK_OWNER,
                            "rotate");
                    if (!rotationHookPresent) {
                        rotationHookPresent =
                                injectLotrMapRotation(method);
                        changed |= rotationHookPresent;
                    }
                }
            }
            if (!playerNameHookPresent) {
                warn("Could not patch LOTR map player tooltip names");
            }
            if (!playerRenderHookPresent) {
                warn("Could not patch LOTR map player rendering; "
                        + "native player tooltip motion will remain active");
            }
            if (!edgeFillHookPresent) {
                warn("Could not patch LOTR clipped map background");
            }
            if (!compassHookPresent) {
                warn("Could not move the LOTR map compass; it will stay in "
                        + "the corner and will not follow map rotation");
            }
            boolean fullscreenLayoutReady = fullscreenBoundsHookPresent
                    && frameSuppressionHookPresent
                    && subtitleLayoutHookPresent;
            byte[] transformed = changed ? write(owner) : basicClass;
            if (fullscreenLayoutReady) {
                activate(LOTR_MAP_FULLSCREEN_ACTIVE_PROPERTY);
            } else {
                System.clearProperty(LOTR_MAP_FULLSCREEN_ACTIVE_PROPERTY);
                if (!fullscreenBoundsHookPresent) {
                    warn("Could not patch LOTR fullscreen map bounds");
                }
                if (!frameSuppressionHookPresent) {
                    warn("Could not patch LOTR map frame rendering");
                }
                if (!subtitleLayoutHookPresent) {
                    warn("Could not patch LOTR fullscreen map subtitles");
                }
            }
            if (fullscreenLayoutReady && tooltipLayoutHookPresent) {
                activate(LOTR_MAP_CONTROL_BAR_ACTIVE_PROPERTY);
            } else {
                System.clearProperty(LOTR_MAP_CONTROL_BAR_ACTIVE_PROPERTY);
                if (!tooltipLayoutHookPresent) {
                    warn("Could not patch LOTR map tooltip bounds");
                }
            }
            if (rotationHookPresent && labelRotationHookPresent) {
                activate(LOTR_MAP_ROTATION_ACTIVE_PROPERTY);
            } else {
                System.clearProperty(LOTR_MAP_ROTATION_ACTIVE_PROPERTY);
                warn("Could not patch LOTR map coordinates; "
                        + "map rotation will stay disabled");
            }
            if (windowClipHookPresent) {
                activate(LOTR_MAP_WINDOW_CLIP_ACTIVE_PROPERTY);
            } else {
                System.clearProperty(LOTR_MAP_WINDOW_CLIP_ACTIVE_PROPERTY);
                warn("Could not patch LOTR map clipping; the map keeps a "
                        + "screen of its own instead of a window");
            }
            if (miniQuestFilterHookPresent) {
                activate(LOTR_MAP_MINIQUEST_FILTER_ACTIVE_PROPERTY);
            } else {
                System.clearProperty(
                        LOTR_MAP_MINIQUEST_FILTER_ACTIVE_PROPERTY);
                warn("Could not patch LOTR map miniquest rendering; "
                        + "native miniquest markers will remain visible");
            }
            return transformed;
        } catch (Throwable throwable) {
            rollBack();
            System.clearProperty(LOTR_MAP_FULLSCREEN_ACTIVE_PROPERTY);
            System.clearProperty(LOTR_MAP_CONTROL_BAR_ACTIVE_PROPERTY);
            System.clearProperty(
                    LOTR_MAP_MINIQUEST_FILTER_ACTIVE_PROPERTY);
            System.clearProperty(LOTR_MAP_ROTATION_ACTIVE_PROPERTY);
            System.clearProperty(LOTR_MAP_WINDOW_CLIP_ACTIVE_PROPERTY);
            warn("Failed to patch LOTR map rendering: "
                    + throwable);
            return basicClass;
        }
    }

    private static boolean injectLotrMapPlayerRenderingGuard(
            MethodNode method) {
        LabelNode renderNativePlayers = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(hookCall(
                LOTR_MAP_MARKER_HOOK_OWNER,
                "shouldSuppressNativePlayerRendering",
                "(Llotr/client/gui/LOTRGuiMap;)Z",
                HookFallback.FALSE));
        hook.add(new JumpInsnNode(
                Opcodes.IFEQ, renderNativePlayers));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(renderNativePlayers);
        method.instructions.insert(hook);
        info("Patched LOTR map player icons with smooth Lost Tales rendering");
        return true;
    }

    /**
     * Sends LOTR's map scissor through the one that knows where the map
     * stands, so a map drawn in a window's box is cut to the box. On a
     * screen of its own the scissor is LOTR's, unchanged.
     */
    private static boolean redirectLotrMapScissor(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !"org/lwjgl/opengl/GL11".equals(call.owner)
                    || !"glScissor".equals(call.name)
                    || !"(IIII)V".equals(call.desc)) {
                continue;
            }
            redirect(call, LOTR_MAP_LAYOUT_HOOK_OWNER, "lotrScissor", call.desc);
            info("Patched the LOTR map scissor to follow its window");
            return true;
        }
        return false;
    }

    /**
     * LOTR tells the open map whether the player is an operator, and why a
     * waypoint cannot stand, only while the map is the screen. Where it
     * reads the screen, it is handed the map in a window instead, while
     * the window screen shows one.
     */
    private static byte[] transformLotrClientProxy(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean changed = false;
            boolean isOpPatched = false;
            boolean protectionPatched = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                boolean isOp = "setMapIsOp".equals(method.name)
                        && "(Z)V".equals(method.desc);
                boolean protection = "setMapCWPProtectionMessage"
                        .equals(method.name)
                        && "(Lnet/minecraft/util/IChatComponent;)V"
                        .equals(method.desc);
                if (!isOp && !protection) {
                    continue;
                }
                boolean patched = containsHook(method,
                        LOTR_MAP_PAGE_HOOK_OWNER, "screenOf");
                if (!patched) {
                    patched = redirectCurrentScreen(method);
                    changed |= patched;
                }
                if (isOp) {
                    isOpPatched = patched;
                } else {
                    protectionPatched = patched;
                }
            }
            if (isOpPatched && protectionPatched) {
                activate(LOTR_MAP_WINDOW_NEWS_ACTIVE_PROPERTY);
                info("Patched LOTR's map news to reach the map in a window");
            } else {
                System.clearProperty(LOTR_MAP_WINDOW_NEWS_ACTIVE_PROPERTY);
                warn("Could not patch LOTR's map news; a map in a window "
                        + "will not hear of operator teleports or protected "
                        + "waypoints");
            }
            return changed ? write(owner) : basicClass;
        } catch (Throwable throwable) {
            rollBack();
            System.clearProperty(LOTR_MAP_WINDOW_NEWS_ACTIVE_PROPERTY);
            warn("Failed to patch LOTR's map news: " + throwable);
            return basicClass;
        }
    }

    /** Reads the screen a map's news goes to where the method reads the current screen. */
    private static boolean redirectCurrentScreen(MethodNode method) {
        boolean redirected = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction instanceof FieldInsnNode
                    && instruction.getOpcode() == Opcodes.GETFIELD) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if ("net/minecraft/client/Minecraft".equals(field.owner)
                        && ("currentScreen".equals(field.name)
                        || "field_71462_r".equals(field.name))
                        && "Lnet/minecraft/client/gui/GuiScreen;"
                        .equals(field.desc)) {
                    method.instructions.set(instruction, hookCall(
                            LOTR_MAP_PAGE_HOOK_OWNER, "screenOf",
                            "(Lnet/minecraft/client/Minecraft;)"
                                    + "Lnet/minecraft/client/gui/GuiScreen;",
                            HookFallback.original(field)));
                    redirected = true;
                }
            }
            instruction = next;
        }
        return redirected;
    }

    private static boolean injectLotrMapEdgeFill(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !"lotr/client/LOTRTextures".equals(call.owner)
                    || !"drawMap".equals(call.name)) {
                continue;
            }

            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMin");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMax");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMin");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMax");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMin_W");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMax_W");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMin_W");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMax_W");
            hook.add(hookCall(
                    LOTR_MAP_EDGE_FILL_HOOK_OWNER,
                    "fillClippedMapBackground",
                    "(ZIIIIIIII)V", HookFallback.NOTHING));
            method.instructions.insertBefore(call, hook);
            info("Patched clipped LOTR map previews with ocean padding");
            return true;
        }
        return false;
    }

    /**
     * Turns the answer of LOTR's own map-space conversion.
     *
     * <p>Every position LOTR draws on the map — roads, waypoints, region
     * labels, players, quest markers — comes out of this one method, so
     * rotating what it returns turns the whole map at once and leaves each
     * sprite and label to be drawn upright at its new place. The alternative,
     * rotating the projection LOTR draws through, would have tilted all of
     * that artwork with it.</p>
     */
    /**
     * Puts the map's compass rose where Lost Tales wants it, and lets it turn.
     *
     * <p>One call, redirected: LOTR still draws its own sprite through its own
     * routine, and only the placement and the angle are decided elsewhere.</p>
     */
    private static boolean injectLotrMapCompass(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !"lotr/client/LOTRTextures".equals(call.owner)
                    || !"drawMapCompassBottomLeft".equals(call.name)
                    || !"(DDDD)V".equals(call.desc)) {
                continue;
            }
            redirect(call, LOTR_MAP_COMPASS_HOOK_OWNER, "drawMapCompass", call.desc);
            info("Patched the LOTR map compass onto the Lost Tales strip");
            return true;
        }
        return false;
    }

    /**
     * Draws LOTR's region names as part of the map sheet.
     *
     * <p>They are written across the paper rather than pinned to a place on
     * it, so they have to turn and lean with it, letters and all, instead of
     * pivoting upright the way a marker's label does. Bracketing the pass is
     * enough: it is drawn under the sheet's own matrix, and the coordinate
     * transform stands down for the length of the call so nothing is moved
     * twice.</p>
     */
    private static boolean injectLotrMapUnrotatedLabels(MethodNode method) {
        boolean injected = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.RETURN) {
                method.instructions.insertBefore(instruction,
                        hookCall(LOTR_MAP_ROTATION_HOOK_OWNER,
                                "endSheetPass", "()V", HookFallback.NOTHING));
                injected = true;
            }
            instruction = next;
        }
        if (injected) {
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(hookCall(
                    LOTR_MAP_ROTATION_HOOK_OWNER,
                    "beginSheetPass",
                    "(Llotr/client/gui/LOTRGuiMap;)V", HookFallback.NOTHING));
            method.instructions.insert(hook);
            info("Patched LOTR map labels to turn with the map sheet");
        }
        return injected;
    }

    /**
     * Sends LOTR's region names through the mod's own label colour.
     *
     * <p>Every string that pass draws is redirected; which of them are
     * actually restyled is decided at runtime by the colour the base mod
     * asked for, so a shadow stays a shadow and only plain white becomes the
     * interface's ivory.</p>
     */
    private static boolean restyleLotrMapLabels(MethodNode method) {
        boolean injected = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"net/minecraft/client/gui/FontRenderer"
                    .equals(call.owner)
                    || !("drawString".equals(call.name)
                    || "func_78276_b".equals(call.name))
                    || !"(Ljava/lang/String;III)I".equals(call.desc)) {
                continue;
            }
            redirect(call, LOTR_MAP_LABEL_STYLE_HOOK_OWNER, "drawMapLabel",
                    MAP_LABEL_DRAW_HOOK_DESC);
            injected = true;
        }
        if (injected) {
            info("Patched LOTR map region names onto the Lost Tales palette");
        }
        return injected;
    }

    /** Sends the fullscreen biome and coordinates through the same ivory. */
    private static boolean restyleLotrMapSubtitles(MethodNode method) {
        boolean injected = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof LdcInsnNode)
                    || !Integer.valueOf(LOTR_MAP_TEXT_WHITE).equals(
                            ((LdcInsnNode)instruction).cst)) {
                continue;
            }
            method.instructions.insert(instruction, hookCall(
                    LOTR_MAP_LABEL_STYLE_HOOK_OWNER,
                    "restyleMapSubtitle", "(I)I", HookFallback.argument(0)));
            injected = true;
        }
        if (injected) {
            info("Patched LOTR map subtitles onto the Lost Tales palette");
        }
        return injected;
    }

    private static boolean injectLotrMapRotation(MethodNode method) {
        boolean injected = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.ARETURN) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(hookCall(
                        LOTR_MAP_ROTATION_HOOK_OWNER,
                        "rotate",
                        "([FLlotr/client/gui/LOTRGuiMap;)[F",
                        HookFallback.argument(0)));
                method.instructions.insertBefore(instruction, hook);
                injected = true;
            }
            instruction = next;
        }
        if (injected) {
            info("Patched LOTR map coordinates with Lost Tales rotation");
        }
        return injected;
    }

    private static boolean injectLotrMapFullscreenBounds(MethodNode method) {
        boolean injected = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.RETURN) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(hookCall(
                        LOTR_MAP_LAYOUT_HOOK_OWNER,
                        "applyFullscreenBounds",
                        "(Llotr/client/gui/LOTRGuiMap;)V", HookFallback.NOTHING));
                method.instructions.insertBefore(instruction, hook);
                injected = true;
            }
            instruction = next;
        }
        if (injected) {
            info("Patched LOTR map viewport with Lost Tales fullscreen bounds");
        }
        return injected;
    }

    private static boolean injectLotrMapFrameSuppression(MethodNode method) {
        LabelNode renderFrame = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(hookCall(
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "shouldSuppressMapFrame",
                "(Llotr/client/gui/LOTRGuiMap;)Z", HookFallback.FALSE));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, renderFrame));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(renderFrame);
        method.instructions.insert(hook);
        info("Patched LOTR map frame for Lost Tales fullscreen maps");
        return true;
    }

    private static boolean injectLotrMapSubtitleLayout(MethodNode method) {
        boolean beginPresent = containsHook(
                method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                "beginFullscreenSubtitles");
        boolean endPresent = containsHook(
                method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                "endFullscreenSubtitles");
        boolean filterPresent = containsHook(
                method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                "filterFullscreenSubtitles");
        if (!beginPresent) {
            InsnList begin = new InsnList();
            begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
            begin.add(hookCall(
                    LOTR_MAP_LAYOUT_HOOK_OWNER,
                    "beginFullscreenSubtitles",
                    "(Llotr/client/gui/LOTRGuiMap;)V", HookFallback.NOTHING));
            method.instructions.insert(begin);
            beginPresent = true;
        }
        if (!filterPresent) {
            // Rewrites the varargs parameter itself, so the whole body draws
            // the filtered lines rather than only the first use of them.
            InsnList filter = new InsnList();
            filter.add(new VarInsnNode(Opcodes.ALOAD, 1));
            filter.add(hookCall(
                    LOTR_MAP_LAYOUT_HOOK_OWNER,
                    "filterFullscreenSubtitles",
                    "([Ljava/lang/String;)[Ljava/lang/String;",
                    HookFallback.argument(0)));
            filter.add(new VarInsnNode(Opcodes.ASTORE, 1));
            method.instructions.insert(filter);
            filterPresent = true;
        }
        if (!endPresent) {
            boolean foundReturn = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    method.instructions.insertBefore(
                            instruction,
                            hookCall(LOTR_MAP_LAYOUT_HOOK_OWNER,
                                    "endFullscreenSubtitles",
                                    "()V", HookFallback.NOTHING));
                    foundReturn = true;
                }
                instruction = next;
            }
            endPresent = foundReturn;
        }
        if (beginPresent && endPresent && filterPresent) {
            info("Patched LOTR fullscreen subtitle positioning");
        }
        return beginPresent && endPresent && filterPresent;
    }

    private static boolean injectLotrMapTooltipLayout(MethodNode method) {
        boolean beginPresent = containsHook(
                method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                "beginMapTooltip");
        boolean endPresent = containsHook(
                method, LOTR_MAP_LAYOUT_HOOK_OWNER,
                "endMapTooltip");
        if (!beginPresent) {
            InsnList begin = new InsnList();
            begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
            begin.add(hookCall(
                    LOTR_MAP_LAYOUT_HOOK_OWNER,
                    "beginMapTooltip",
                    "(Llotr/client/gui/LOTRGuiMap;)V", HookFallback.NOTHING));
            method.instructions.insert(begin);
            beginPresent = true;
        }
        if (!endPresent) {
            boolean foundReturn = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    method.instructions.insertBefore(
                            instruction,
                            hookCall(LOTR_MAP_LAYOUT_HOOK_OWNER,
                                    "endMapTooltip",
                                    "()V", HookFallback.NOTHING));
                    foundReturn = true;
                }
                instruction = next;
            }
            endPresent = foundReturn;
        }
        if (beginPresent && endPresent) {
            info("Patched LOTR map tooltips for the bottom control bar");
        }
        return beginPresent && endPresent;
    }

    private static boolean injectLotrMapMiniQuestFilter(MethodNode method) {
        LabelNode renderMiniQuests = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(hookCall(
                LOTR_MAP_LEGEND_HOOK_OWNER,
                "shouldRenderLotrMiniQuests",
                "(Llotr/client/gui/LOTRGuiMap;)Z", HookFallback.TRUE));
        hook.add(new JumpInsnNode(Opcodes.IFNE, renderMiniQuests));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(renderMiniQuests);
        method.instructions.insert(hook);
        info("Patched LOTR map miniquest rendering with map-only filters");
        return true;
    }

    private static void addStaticIntField(
            InsnList instructions, String owner, String name) {
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, owner.replace('.', '/'), name, "I"));
    }

    /** Filters concealed players before LOTR serializes map coordinates. */
    private static byte[] transformLotrPlayerLocations(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"sendPlayerLocationsToPlayer".equals(method.name)
                        || !"(Lnet/minecraft/entity/player/EntityPlayer;"
                        .concat("Lnet/minecraft/world/World;)V")
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method,
                        ACCESSORY_LOTR_MAP_HOOK_OWNER,
                        "addPlayerLocationIfVisible")) {
                    activate(ACCESSORY_LOTR_MAP_ACTIVE_PROPERTY);
                    return basicClass;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                            || !"lotr/common/network/"
                            .concat("LOTRPacketUpdatePlayerLocations")
                            .equals(call.owner)
                            || !"addPlayerLocation".equals(call.name)
                            || !"(Lcom/mojang/authlib/GameProfile;DD)V"
                            .equals(call.desc)) {
                        continue;
                    }
                    redirect(call, ACCESSORY_LOTR_MAP_HOOK_OWNER,
                            "addPlayerLocationIfVisible",
                            "(Llotr/common/network/"
                                    + "LOTRPacketUpdatePlayerLocations;"
                                    + "Lcom/mojang/authlib/GameProfile;DD)V");
                    activate(ACCESSORY_LOTR_MAP_ACTIVE_PROPERTY);
                    info("Patched LOTR map locations for accessory concealment");
                    return write(owner);
                }
            }
            warn("Could not patch LOTR player-location packet construction");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR player-location packets: "
                    + throwable);
            return basicClass;
        }
    }

    /**
     * Replaces only active offset-camera action packets. Forge 1.7.10 has no
     * cancelable hook between PlayerControllerMP's held-item synchronization
     * and its C02/C08 packets, so the original paths remain the fallbacks.
     */
    private static byte[] transformPlayerController(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            MethodNode attack = findPlayerControllerAttackMethod(owner);
            MethodNode interact =
                    findPlayerControllerInteractionMethod(owner);
            MethodNode block = findPlayerControllerBlockActionMethod(owner);
            if (attack == null || interact == null) {
                warn("Could not locate PlayerControllerMP entity actions; "
                        + "vanilla C02 entity packets will remain active");
            }
            if (block == null) {
                warn("Could not locate PlayerControllerMP block action; "
                        + "vanilla C08 block packets will remain active");
            }

            boolean changed = false;
            if (attack != null && !containsHook(
                    attack, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityAttack")) {
                changed |= injectEntityAttackHook(attack);
            }
            if (interact != null && !containsHook(
                    interact, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityInteraction")) {
                changed |= injectEntityInteractionHook(interact);
            }
            if (block != null && !containsHook(
                    block, BLOCK_ACTION_HOOK_OWNER,
                    "sendBlockActionOrVanilla")) {
                changed |= replaceBlockActionSend(block);
            }

            boolean entityActive = attack != null && interact != null
                    && containsHook(
                    attack, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityAttack")
                    && containsHook(
                    attack, ENTITY_ACTION_HOOK_OWNER, "handleAttack")
                    && containsHook(
                    interact, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityInteraction")
                    && containsHook(
                    interact, ENTITY_ACTION_HOOK_OWNER,
                    "handleInteraction");
            if (entityActive) {
                activate(THIRD_PERSON_ENTITY_ACTION_ACTIVE_PROPERTY);
            }
            boolean blockActive = block != null && containsHook(
                    block, BLOCK_ACTION_HOOK_OWNER,
                    "sendBlockActionOrVanilla");
            if (blockActive) {
                activate(THIRD_PERSON_BLOCK_ACTION_ACTIVE_PROPERTY);
            }
            if (changed) {
                info("Patched PlayerControllerMP third-person actions "
                        + "with vanilla fallbacks");
                return write(owner);
            }
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch PlayerControllerMP entity actions: "
                    + throwable);
            return basicClass;
        }
    }

    /** Makes LOTR's faction-bounty map use the active character UUID. */
    private static byte[] transformLotrFactionBounties(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"forPlayer".equals(method.name)
                        || !"(Lnet/minecraft/entity/player/EntityPlayer;)"
                        .concat("Llotr/common/fac/LOTRFactionBounties$PlayerData;")
                        .equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (HookGuard.calls(call, ROLEPLAY_IDENTITY_HOOK_OWNER,
                            "resolveGameplayId")) {
                        activateLotrBountyTransformer();
                        return basicClass;
                    }
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && "net/minecraft/entity/player/EntityPlayer"
                            .equals(call.owner)
                            && "()Ljava/util/UUID;".equals(call.desc)) {
                        redirect(call, ROLEPLAY_IDENTITY_HOOK_OWNER,
                                "resolveGameplayId", GAMEPLAY_ID_HOOK_DESC);
                        activateLotrBountyTransformer();
                        info("Bound LOTR faction bounties "
                                + "to active roleplay-character UUIDs");
                        return write(owner);
                    }
                }
            }
            warn("Could not patch LOTRFactionBounties#forPlayer; faction "
                    + "bounties will remain account-bound");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to bind LOTR faction bounties to characters: "
                    + throwable);
            return basicClass;
        }
    }

    /** Resolves bounty target labels from roleplay-character UUIDs. */
    private static byte[] transformLotrFactionBountyPlayerData(
            byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"findUsername".equals(method.name)
                        || !"()Ljava/lang/String;".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, ROLEPLAY_IDENTITY_HOOK_OWNER,
                        "resolveGameplayName")) {
                    activateLotrBountyTransformer();
                    return basicClass;
                }

                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) {
                    method.localVariables.clear();
                }
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new FieldInsnNode(
                        Opcodes.GETFIELD,
                        "lotr/common/fac/LOTRFactionBounties$PlayerData",
                        "playerID",
                        "Ljava/util/UUID;"));
                // LOTR's own lookup may ask Mojang; a hook that fails names
                // nobody rather than send the server thread out.
                method.instructions.add(hookCall(
                        ROLEPLAY_IDENTITY_HOOK_OWNER,
                        "resolveGameplayName",
                        GAMEPLAY_NAME_HOOK_DESC, HookFallback.constant("")));
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(
                        Opcodes.ARETURN));
                activateLotrBountyTransformer();
                info("Patched LOTR bounty target names "
                        + "for roleplay-character UUIDs");
                return write(owner);
            }
            warn("Could not patch LOTR faction bounty target names");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR faction bounty target names: "
                    + throwable);
            return basicClass;
        }
    }

    /** Changes only LOTR speech's # placeholder, not the player's account name. */
    private static byte[] transformLotrSpeech(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"formatSpeech".equals(method.name)
                        || !"(Ljava/lang/String;"
                        .concat("Lnet/minecraft/entity/player/EntityPlayer;")
                        .concat("Ljava/lang/String;Ljava/lang/String;)")
                        .concat("Ljava/lang/String;")
                        .equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (HookGuard.calls(call, ROLEPLAY_IDENTITY_HOOK_OWNER,
                            "resolveRoleplayName")) {
                        activate(LOTR_SPEECH_ACTIVE_PROPERTY);
                        return basicClass;
                    }
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && "net/minecraft/entity/player/EntityPlayer"
                            .equals(call.owner)
                            && "()Ljava/lang/String;".equals(call.desc)
                            && ("getCommandSenderName".equals(call.name)
                            || "func_70005_c_".equals(call.name))) {
                        redirect(call, ROLEPLAY_IDENTITY_HOOK_OWNER,
                                "resolveRoleplayName", ROLEPLAY_NAME_HOOK_DESC);
                        activate(LOTR_SPEECH_ACTIVE_PROPERTY);
                        info("Patched LOTR NPC speech to "
                                + "use active roleplay-character names");
                        return write(owner);
                    }
                }
            }
            warn("Could not patch LOTRSpeech#formatSpeech; NPC speech will "
                    + "use account names");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR NPC speech names: " + throwable);
            return basicClass;
        }
    }

    /**
     * Reroutes the client-side chat print of NPC speech through the Lost
     * Tales chat presentation, and adds a call after LOTR's immersive
     * floating speech so the same words are filed into the NPC's
     * conversation tab even while LOTR prints no chat line at all. Only
     * the final addChatMessage call is replaced; LOTR's immersive
     * floating speech, recipients, and speech content are untouched. The
     * chat hook falls back to the original component, so a partial
     * failure degrades to LOTR's plain yellow line; the immersive hook
     * is additive and its absence only costs the conversation copy.
     */
    private static byte[] transformLotrNpcSpeechHandler(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"onMessage".equals(method.name)
                        || !method.desc.startsWith(
                        "(Llotr/common/network/LOTRPacketNPCSpeech;")) {
                    continue;
                }
                MethodInsnNode chatCall = null;
                for (AbstractInsnNode instruction =
                        method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (HookGuard.calls(call, NPC_CHAT_HOOK_OWNER,
                            "addNpcChatMessage")) {
                        activate(NPC_CHAT_ACTIVE_PROPERTY);
                        return basicClass;
                    }
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && "net/minecraft/entity/player/EntityPlayer"
                            .equals(call.owner)
                            && "(Lnet/minecraft/util/IChatComponent;)V"
                            .equals(call.desc)
                            && ("addChatMessage".equals(call.name)
                            || "func_145747_a".equals(call.name))) {
                        chatCall = call;
                        break;
                    }
                }
                if (chatCall == null) {
                    continue;
                }
                int npcLocal = findNpcSpeechHandlerNpcLocal(method);
                if (npcLocal < 0) {
                    warn("Could not identify LOTR NPC speech handler NPC "
                            + "local; NPC chat will keep LOTR's style");
                    return basicClass;
                }
                method.instructions.insertBefore(
                        chatCall,
                        new VarInsnNode(Opcodes.ALOAD, npcLocal));
                redirect(chatCall, NPC_CHAT_HOOK_OWNER, "addNpcChatMessage",
                        NPC_CHAT_HOOK_DESC);
                insertImmersiveSpeechHook(method, npcLocal);
                activate(NPC_CHAT_ACTIVE_PROPERTY);
                info("Patched LOTR NPC speech chat lines into the "
                        + "Lost Tales chat presentation");
                return write(owner);
            }
            warn("Could not locate LOTRPacketNPCSpeech$Handler#onMessage; "
                    + "NPC chat will keep LOTR's style");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR NPC speech chat: " + throwable);
            return basicClass;
        }
    }

    /**
     * Adds, directly after {@code clientReceiveSpeech(npc, speech)}, a
     * call filing the same speech into the Lost Tales conversation tab:
     * with LOTR's immersive speech on and its chat log off, the floating
     * text is the only delivery LOTR makes, and this is the only point
     * the words pass through. The speech string is re-read through the
     * same synthetic accessor LOTR itself calls one instruction earlier;
     * a handler shaped differently simply keeps the chat patch alone.
     */
    /**
     * Sends LOTR's pass over every NPC's floating speech through
     * {@code LostTalesNpcChatHook#renderNpcSpeeches} instead, so the
     * words over an NPC's head are drawn in the chat's own style beside
     * the players' — under a name in its faction's colour, the way the
     * conversation shows it. The hook keeps LOTR's call and makes it
     * when the styling is off, so turning the feature off restores
     * LOTR's speech exactly; a failure to patch leaves LOTR's own
     * drawing in place and costs only the restyling.
     */
    /**
     * Lets Lost Tales hold back one entity's floating name. A speaking
     * player wears the name the chat signs them with, in its own colour
     * and over their words, and vanilla's plain account name in the same
     * place would only double it — but the pass that draws it also
     * carries the speech itself and LOTR's alignment, so cancelling the
     * whole pass is not the way. The guard goes on the label draw alone:
     * everything else in that pass, ours included, still runs.
     */
    /**
     * Lifts LOTR's floating alignment clear of what a speaker wears.
     * It is a spawned effect rather than part of any nameplate pass, so
     * its own renderer is where its height is decided; the hook raises
     * the y it is drawn at before anything is drawn there.
     */
    private static byte[] transformLotrAlignmentLift(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!isNamed(owner, method, "doRender", "func_76986_a")
                        || !method.desc.startsWith(
                                "(Lnet/minecraft/entity/Entity;DDD")) {
                    continue;
                }
                if (containsHook(method, NAMEPLATE_HOOK_OWNER,
                        "liftAlignment")) {
                    activate(ALIGNMENT_LIFT_ACTIVE_PROPERTY);
                    return basicClass;
                }
                // doRender(this, entity, x, y, z, ...): y is the double
                // at slot four, raised in place before its first use.
                InsnList lift = new InsnList();
                lift.add(new VarInsnNode(Opcodes.DLOAD, 4));
                lift.add(hookCall(NAMEPLATE_HOOK_OWNER, "liftAlignment",
                        ALIGNMENT_LIFT_DESC, HookFallback.argument(0)));
                lift.add(new VarInsnNode(Opcodes.DSTORE, 4));
                method.instructions.insert(lift);
                activate(ALIGNMENT_LIFT_ACTIVE_PROPERTY);
                info("Patched LOTR floating alignment to stand above a "
                        + "speaker's name");
                return write(owner);
            }
            warn("Could not locate LOTRRenderAlignmentBonus#doRender; "
                    + "floating alignment will keep LOTR's height");
            return basicClass;
        } catch (Throwable failure) {
            rollBack();
            warn("Failed to lift LOTR floating alignment: " + failure);
            return basicClass;
        }
    }

    private static byte[] transformLivingLabel(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"renderLivingLabel".equals(method.name)
                        && !"func_96449_a".equals(method.name)) {
                    continue;
                }
                if (!method.desc.startsWith(
                        "(Lnet/minecraft/entity/EntityLivingBase;DDD"
                                + "Ljava/lang/String;")) {
                    continue;
                }
                if (containsHook(method, NAMEPLATE_HOOK_OWNER,
                        "hidesNameplate")) {
                    activate(NAMEPLATE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                LabelNode carryOn = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                guard.add(hookCall(NAMEPLATE_HOOK_OWNER, "hidesNameplate",
                        NAMEPLATE_HOOK_DESC, HookFallback.FALSE));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, carryOn));
                guard.add(new InsnNode(Opcodes.RETURN));
                guard.add(carryOn);
                method.instructions.insert(guard);
                activate(NAMEPLATE_ACTIVE_PROPERTY);
                info("Patched entity nameplates so a speaking player "
                        + "wears the chat's own name");
                return write(owner);
            }
            warn("Could not locate RendererLivingEntity#renderLivingLabel; "
                    + "a speaking player will wear both names");
            return basicClass;
        } catch (Throwable failure) {
            rollBack();
            warn("Failed to patch entity nameplates: " + failure);
            return basicClass;
        }
    }

    private static byte[] transformLotrNpcSpeechRendering(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                for (AbstractInsnNode instruction =
                        method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() != Opcodes.INVOKESTATIC
                            || !NPC_SPEECH_RENDER_DESC.equals(call.desc)) {
                        continue;
                    }
                    if (HookGuard.calls(call, NPC_CHAT_HOOK_OWNER,
                            "renderNpcSpeeches")) {
                        activate(NPC_SPEECH_RENDER_ACTIVE_PROPERTY);
                        return basicClass;
                    }
                    if (LOTR_NPC_RENDERING.equals(call.owner)
                            && "renderAllNPCSpeeches".equals(call.name)) {
                        redirect(call, NPC_CHAT_HOOK_OWNER, "renderNpcSpeeches",
                                call.desc);
                        activate(NPC_SPEECH_RENDER_ACTIVE_PROPERTY);
                        info("Patched LOTR NPC floating speech into the "
                                + "Lost Tales chat style");
                        return write(owner);
                    }
                }
            }
            warn("Could not locate LOTRNPCRendering#renderAllNPCSpeeches; "
                    + "NPC floating speech will keep LOTR's style");
            return basicClass;
        } catch (Throwable failure) {
            rollBack();
            warn("Failed to patch LOTR NPC floating speech: " + failure);
            return basicClass;
        }
    }

    private static void insertImmersiveSpeechHook(MethodNode method,
                                                  int npcLocal) {
        int playerLocal = findNpcSpeechHandlerPlayerLocal(method);
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"clientReceiveSpeech".equals(call.name)) {
                continue;
            }
            AbstractInsnNode before = previousCode(call);
            if (playerLocal < 0 || !(before instanceof MethodInsnNode)) {
                break;
            }
            MethodInsnNode speechAccessor = (MethodInsnNode)before;
            if (speechAccessor.getOpcode() != Opcodes.INVOKESTATIC
                    || !speechAccessor.desc.endsWith(
                            ")Ljava/lang/String;")) {
                break;
            }
            InsnList added = new InsnList();
            added.add(new VarInsnNode(Opcodes.ALOAD, playerLocal));
            added.add(new VarInsnNode(Opcodes.ALOAD, npcLocal));
            added.add(new VarInsnNode(Opcodes.ALOAD, 1));
            added.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    speechAccessor.owner, speechAccessor.name,
                    speechAccessor.desc, false));
            added.add(hookCall(NPC_CHAT_HOOK_OWNER, "addImmersiveSpeech",
                    NPC_IMMERSIVE_HOOK_DESC, HookFallback.NOTHING));
            method.instructions.insert(call, added);
            return;
        }
        warn("Could not add the immersive NPC speech hook; floating "
                + "speech will not reach the conversation tab");
    }

    private static int findNpcSpeechHandlerPlayerLocal(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (!"getClientPlayer".equals(call.name)) {
                continue;
            }
            AbstractInsnNode store = nextCode(instruction);
            if (store instanceof VarInsnNode
                    && store.getOpcode() == Opcodes.ASTORE) {
                return ((VarInsnNode)store).var;
            }
        }
        return -1;
    }

    private static int findNpcSpeechHandlerNpcLocal(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof TypeInsnNode)
                    || instruction.getOpcode() != Opcodes.CHECKCAST) {
                continue;
            }
            if (!"lotr/common/entity/npc/LOTREntityNPC".equals(
                    ((TypeInsnNode)instruction).desc)) {
                continue;
            }
            AbstractInsnNode store = nextCode(instruction);
            if (store instanceof VarInsnNode
                    && store.getOpcode() == Opcodes.ASTORE) {
                return ((VarInsnNode)store).var;
            }
        }
        return -1;
    }

    private static void activateLotrBountyTransformer() {
        activate(LOTR_BOUNTY_ACTIVE_PROPERTY);
    }

    private static byte[] transformLotrFastTravelHandler(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"onMessage".equals(method.name)
                        || !method.desc.startsWith(
                        "(Llotr/common/network/LOTRPacketFastTravel;")) {
                    continue;
                }
                MethodInsnNode targetCall = findLotrFastTravelTargetCall(method);
                if (targetCall == null) {
                    continue;
                }
                if (HookGuard.calls(targetCall, FAST_TRAVEL_HOOK_OWNER,
                        "setTargetIfAllowed")) {
                    activate(FAST_TRAVEL_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int playerLocal = findLotrHandlerPlayerLocal(method);
                if (playerLocal < 0) {
                    warn("Could not identify LOTR fast-travel handler player local; "
                            + "the per-tick fallback will remain active");
                    return basicClass;
                }
                method.instructions.insertBefore(
                        targetCall,
                        new VarInsnNode(Opcodes.ALOAD, playerLocal));
                redirect(targetCall, FAST_TRAVEL_HOOK_OWNER, "setTargetIfAllowed",
                        FAST_TRAVEL_HOOK_DESC);
                activate(FAST_TRAVEL_ACTIVE_PROPERTY);
                info("Patched LOTR fast travel for "
                        + "character-specific marker discovery");
                return write(owner);
            }
            warn("Could not locate LOTRPacketFastTravel$Handler#onMessage; "
                    + "the per-tick fallback will remain active");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR fast travel: " + throwable);
            return basicClass;
        }
    }

    private static byte[] transformCamera(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            MethodNode cameraMethod = findCameraMethod(owner);
            if (cameraMethod == null) {
                warn("Could not locate EntityRenderer#orientCamera; race camera height will remain vanilla");
                return basicClass;
            }

            boolean changed = false;
            VarInsnNode offsetStore = findVanillaCameraOffsetStore(
                    cameraMethod);
            if (offsetStore == null) {
                warn("Could not locate EntityRenderer's vanilla yOffset subtraction; race camera height will remain vanilla");
                return basicClass;
            }
            int viewEntityLocal = findViewEntityLocal(offsetStore);
            if (viewEntityLocal < 0) {
                warn("Could not identify EntityRenderer's view-entity local; race camera height will remain vanilla");
                return basicClass;
            }

            if (!containsHook(
                    cameraMethod, CAMERA_HOOK_OWNER,
                    "resolveCameraOffset")) {
                InsnList raceOffsetHook = new InsnList();
                raceOffsetHook.add(new VarInsnNode(
                        Opcodes.ALOAD, viewEntityLocal));
                raceOffsetHook.add(new VarInsnNode(
                        Opcodes.FLOAD, offsetStore.var));
                raceOffsetHook.add(hookCall(
                        CAMERA_HOOK_OWNER,
                        "resolveCameraOffset",
                        CAMERA_HOOK_DESC, HookFallback.argument(1)));
                raceOffsetHook.add(new VarInsnNode(
                        Opcodes.FSTORE, offsetStore.var));
                cameraMethod.instructions.insert(
                        offsetStore, raceOffsetHook);
                changed = true;
            }

            activate(CAMERA_ACTIVE_PROPERTY);

            VarInsnNode distanceStore = findThirdPersonDistanceStore(
                    cameraMethod);
            if (distanceStore == null) {
                warn("Could not locate EntityRenderer's third-person distance local; the optional camera overhaul will remain inactive");
            } else {
                if (!containsHook(
                        cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                        "resolveDistance")) {
                    InsnList distanceHook = new InsnList();
                    distanceHook.add(new VarInsnNode(
                            Opcodes.ALOAD, viewEntityLocal));
                    distanceHook.add(new VarInsnNode(
                            Opcodes.DLOAD, distanceStore.var));
                    distanceHook.add(new VarInsnNode(Opcodes.FLOAD, 1));
                    distanceHook.add(hookCall(
                            THIRD_PERSON_CAMERA_HOOK_OWNER,
                            "resolveDistance",
                            CAMERA_DISTANCE_HOOK_DESC, HookFallback.argument(1)));
                    distanceHook.add(new VarInsnNode(
                            Opcodes.DSTORE, distanceStore.var));
                    cameraMethod.instructions.insert(
                            distanceStore, distanceHook);
                    changed = true;
                }

                if (!containsHook(
                        cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                        "applyCameraOffset")) {
                    MethodInsnNode distanceTranslation =
                            findThirdPersonDistanceTranslation(
                            cameraMethod, distanceStore.var);
                    if (distanceTranslation == null) {
                        warn("Could not locate EntityRenderer's normal third-person translation; shoulder offsets will remain inactive");
                    } else {
                        InsnList offsetHook = new InsnList();
                        offsetHook.add(new VarInsnNode(
                                Opcodes.ALOAD, viewEntityLocal));
                        offsetHook.add(new VarInsnNode(
                                Opcodes.FLOAD, 1));
                        offsetHook.add(new VarInsnNode(
                                Opcodes.DLOAD, distanceStore.var));
                        offsetHook.add(hookCall(
                                THIRD_PERSON_CAMERA_HOOK_OWNER,
                                "applyCameraOffset",
                                CAMERA_OFFSET_HOOK_DESC, HookFallback.NOTHING));
                        cameraMethod.instructions.insertBefore(
                                distanceTranslation, offsetHook);
                        changed = true;
                    }
                }
            }

            MethodNode fovMethod = findFovMethod(owner);
            if (fovMethod == null) {
                warn("Could not locate EntityRenderer#getFOVModifier; camera FOV profiles will remain inactive");
            } else if (!containsHook(
                    fovMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveFov")) {
                changed |= injectFovHooks(fovMethod);
            }

            boolean cameraOverhaulActive = distanceStore != null
                    && containsHook(
                    cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveDistance")
                    && containsHook(
                    cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "applyCameraOffset")
                    && fovMethod != null
                    && containsHook(
                    fovMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveFov");
            if (cameraOverhaulActive) {
                activate(THIRD_PERSON_CAMERA_ACTIVE_PROPERTY);
            }
            MethodNode mouseOverMethod = findMouseOverMethod(owner);
            if (mouseOverMethod == null) {
                warn("Could not locate EntityRenderer#getMouseOver; "
                        + "camera-intent targeting will remain inactive");
            } else {
                if (!containsHook(
                        mouseOverMethod, TARGETING_HOOK_OWNER,
                        "resolveMouseOver")) {
                    changed |= injectMouseOverHooks(mouseOverMethod);
                }
                if (containsHook(
                        mouseOverMethod, TARGETING_HOOK_OWNER,
                        "resolveMouseOver")) {
                    activate(THIRD_PERSON_TARGETING_ACTIVE_PROPERTY);
                }
            }
            if (changed) {
                info("Patched EntityRenderer race origin, third-person "
                        + "camera, and targeting hooks");
                return write(owner);
            }
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch EntityRenderer camera origin: " + throwable);
            return basicClass;
        }
    }

    /**
     * Routes EntityRenderer's single screen draw through a coordinate bridge.
     * The Forge pre/post events still own the GL transform; this only supplies
     * the inverse pointer position to hover and tooltip logic.
     */
    private static byte[] transformGuiScreenDraw(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("updateCameraAndRender".equals(method.name)
                        || "func_78480_b".equals(method.name))
                        || !("(F)V".equals(method.desc)
                        || "(FJ)V".equals(method.desc))) {
                    continue;
                }
                if (containsHook(method, GUI_ANIMATION_HOOK_OWNER,
                        "drawScreen")) {
                    activateGuiAnimationPart(
                            GUI_ANIMATION_DRAW_ACTIVE_PROPERTY);
                    return basicClass;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                            || !"net/minecraft/client/gui/GuiScreen"
                            .equals(call.owner)
                            || !"(IIF)V".equals(call.desc)
                            || !("drawScreen".equals(call.name)
                            || "func_73863_a".equals(call.name))) {
                        continue;
                    }
                    redirect(call, GUI_ANIMATION_HOOK_OWNER, "drawScreen",
                            "(Lnet/minecraft/client/gui/GuiScreen;IIF)V");
                    activateGuiAnimationPart(
                            GUI_ANIMATION_DRAW_ACTIVE_PROPERTY);
                    info("Patched GUI draw coordinates for animated screens");
                    return write(owner);
                }
            }
            warn("Could not locate EntityRenderer's GuiScreen draw call; "
                    + "GUI animation hover coordinates will remain vanilla");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch animated GUI draw coordinates: "
                    + throwable);
            return basicClass;
        }
    }

    /** Keeps clicks and drag callbacks aligned with the rendered transform. */
    private static byte[] transformGuiScreenInput(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("handleMouseInput".equals(method.name)
                        || "func_146274_d".equals(method.name))
                        || !"()V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, GUI_ANIMATION_HOOK_OWNER,
                        "inverseMouseX")
                        && containsHook(method, GUI_ANIMATION_HOOK_OWNER,
                        "inverseMouseY")) {
                    activateGuiAnimationPart(
                            GUI_ANIMATION_INPUT_ACTIVE_PROPERTY);
                    return basicClass;
                }

                MethodInsnNode buttonCall = null;
                int olderStore = -1;
                int newerStore = -1;
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (instruction.getOpcode() == Opcodes.ISTORE) {
                        olderStore = newerStore;
                        newerStore = ((VarInsnNode)instruction).var;
                    }
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)instruction;
                        if (call.getOpcode() == Opcodes.INVOKESTATIC
                                && "org/lwjgl/input/Mouse".equals(call.owner)
                                && "getEventButton".equals(call.name)
                                && "()I".equals(call.desc)) {
                            buttonCall = call;
                            break;
                        }
                    }
                }
                if (buttonCall == null || olderStore < 0
                        || newerStore < 0 || olderStore == newerStore) {
                    continue;
                }

                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ILOAD, olderStore));
                hook.add(hookCall(
                        GUI_ANIMATION_HOOK_OWNER,
                        "inverseMouseX",
                        "(Lnet/minecraft/client/gui/GuiScreen;I)I",
                        HookFallback.argument(1)));
                hook.add(new VarInsnNode(Opcodes.ISTORE, olderStore));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ILOAD, newerStore));
                hook.add(hookCall(
                        GUI_ANIMATION_HOOK_OWNER,
                        "inverseMouseY",
                        "(Lnet/minecraft/client/gui/GuiScreen;I)I",
                        HookFallback.argument(1)));
                hook.add(new VarInsnNode(Opcodes.ISTORE, newerStore));
                method.instructions.insertBefore(buttonCall, hook);
                activateGuiAnimationPart(
                        GUI_ANIMATION_INPUT_ACTIVE_PROPERTY);
                info("Patched GuiScreen mouse coordinates for animated screens");
                return write(owner);
            }
            warn("Could not locate GuiScreen#handleMouseInput; GUI animation "
                    + "click coordinates will remain vanilla");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch animated GUI input coordinates: "
                    + throwable);
            return basicClass;
        }
    }

    /** Replaces GuiScreen's veil with the handler's stationary fade/blur pass. */
    private static byte[] transformGuiScreenBackground(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("drawWorldBackground".equals(method.name)
                        || "func_146270_b".equals(method.name))
                        || !"(I)V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, GUI_ANIMATION_HOOK_OWNER,
                        "beginVanillaBackground")
                        && containsHook(method, GUI_ANIMATION_HOOK_OWNER,
                        "endVanillaBackground")) {
                    activateGuiAnimationPart(
                            GUI_ANIMATION_BACKGROUND_ACTIVE_PROPERTY);
                    return basicClass;
                }
                InsnList begin = new InsnList();
                begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
                begin.add(hookCall(
                        GUI_ANIMATION_HOOK_OWNER,
                        "beginVanillaBackground",
                        "(Lnet/minecraft/client/gui/GuiScreen;)V",
                        HookFallback.NOTHING));
                method.instructions.insert(begin);
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (instruction.getOpcode() != Opcodes.RETURN) {
                        continue;
                    }
                    method.instructions.insertBefore(
                            instruction, hookCall(
                            GUI_ANIMATION_HOOK_OWNER,
                            "endVanillaBackground", "()V", HookFallback.NOTHING));
                }
                activateGuiAnimationPart(
                        GUI_ANIMATION_BACKGROUND_ACTIVE_PROPERTY);
                info("Patched GuiScreen backdrop for stationary GUI fades");
                return write(owner);
            }
            warn("Could not locate GuiScreen#drawWorldBackground; animated "
                    + "GUI backdrops will remain attached to the content");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch stationary GUI backdrops: " + throwable);
            return basicClass;
        }
    }

    /**
     * Routes vanilla's chat hit test through the Lost Tales chat layout.
     *
     * <p>{@code GuiNewChat.func_146236_a} maps a raw mouse position to the
     * chat component under it with vanilla's nine-pixel line stride, which
     * is not what Lost Tales draws. Everything that asks vanilla — LOTR's
     * achievement hover card drawn after every chat screen, other mods, and
     * vanilla's own click handling — would otherwise resolve against a
     * layout that is not on screen. While the Lost Tales chat screen is
     * open the hook answers from the lines actually drawn and reports
     * nothing under a popup; at any other time vanilla proceeds unchanged.
     * A failure here leaves third-party chat hovers on vanilla's geometry.</p>
     */
    private static byte[] transformGuiNewChatHitTest(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"func_146236_a".equals(method.name)
                        || !"(II)Lnet/minecraft/util/IChatComponent;"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, CHAT_HIT_HOOK_OWNER,
                        "componentAt")) {
                    activate(CHAT_HIT_TEST_ACTIVE_PROPERTY);
                    return basicClass;
                }
                LabelNode vanilla = new LabelNode();
                InsnList head = new InsnList();
                head.add(new VarInsnNode(Opcodes.ALOAD, 0));
                head.add(hookCall(
                        CHAT_HIT_HOOK_OWNER,
                        "isActive",
                        "(Lnet/minecraft/client/gui/GuiNewChat;)Z",
                        HookFallback.FALSE));
                head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
                head.add(new VarInsnNode(Opcodes.ALOAD, 0));
                head.add(new VarInsnNode(Opcodes.ILOAD, 1));
                head.add(new VarInsnNode(Opcodes.ILOAD, 2));
                head.add(hookCall(
                        CHAT_HIT_HOOK_OWNER,
                        "componentAt",
                        "(Lnet/minecraft/client/gui/GuiNewChat;II)"
                                + "Lnet/minecraft/util/IChatComponent;",
                        HookFallback.NULL));
                head.add(new InsnNode(Opcodes.ARETURN));
                head.add(vanilla);
                method.instructions.insert(head);
                activate(CHAT_HIT_TEST_ACTIVE_PROPERTY);
                info("Patched GuiNewChat hit testing for the Lost Tales chat layout");
                return write(owner);
            }
            warn("Could not locate GuiNewChat#func_146236_a; third-party chat "
                    + "hover cards will use vanilla's line geometry");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch chat hit testing: " + throwable);
            return basicClass;
        }
    }

    /**
     * Lets Lost Tales lay out its own chat lines.
     *
     * <p>{@code GuiNewChat.func_146237_a} wraps a message into drawn lines
     * inline and then files them into the history. The hook is inserted
     * at the seam between the two — after the last wrapped line has been
     * added to the local list and before {@code getChatOpen()} starts the
     * filing loop — and replaces that list for Lost Tales messages, so
     * continuation lines indent under the message body while vanilla's
     * history, scroll, trimming and resize re-wrap stay untouched. The
     * list local is found from the {@code ArrayList.add} call that
     * precedes the seam rather than assumed by index. Without the patch
     * wrapped lines simply return to the left edge.</p>
     */
    private static byte[] transformGuiNewChatWrap(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"func_146237_a".equals(method.name)
                        || !"(Lnet/minecraft/util/IChatComponent;IIZ)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, CHAT_WRAP_HOOK_OWNER, "wrap")) {
                    activate(CHAT_WRAP_ACTIVE_PROPERTY);
                    return basicClass;
                }
                MethodInsnNode chatOpen = findChatOpenCall(method);
                AbstractInsnNode receiver = chatOpen == null
                        ? null : previousCode(chatOpen);
                VarInsnNode lines = receiver == null
                        ? null : findWrappedLinesLocal(receiver);
                if (chatOpen == null || lines == null
                        || !(receiver instanceof VarInsnNode)
                        || receiver.getOpcode() != Opcodes.ALOAD
                        || ((VarInsnNode)receiver).var != 0) {
                    warn("Could not locate the wrapped-line seam in "
                            + "GuiNewChat#func_146237_a; wrapped chat lines "
                            + "will not indent under the message body");
                    return basicClass;
                }
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, lines.var));
                hook.add(hookCall(
                        CHAT_WRAP_HOOK_OWNER,
                        "wrap",
                        "(Lnet/minecraft/client/gui/GuiNewChat;"
                                + "Lnet/minecraft/util/IChatComponent;"
                                + "Ljava/util/ArrayList;)Ljava/util/ArrayList;",
                        HookFallback.argument(2)));
                hook.add(new VarInsnNode(Opcodes.ASTORE, lines.var));
                method.instructions.insertBefore(receiver, hook);
                activate(CHAT_WRAP_ACTIVE_PROPERTY);
                info("Patched GuiNewChat line wrapping for the Lost Tales chat layout");
                return write(owner);
            }
            warn("Could not locate GuiNewChat#func_146237_a; wrapped chat "
                    + "lines will not indent under the message body");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch chat line wrapping: " + throwable);
            return basicClass;
        }
    }

    /**
     * Writes every chat line on one line of the game's log.
     *
     * <p>{@code GuiNewChat.printChatMessageWithOptionalDeletion} logs
     * {@code "[CHAT] "} and the line's words as they are, so a message
     * with a line break in it would start a line of its own in the log,
     * one that could read as any other line there. The string handed to
     * the logger goes through {@code ChatMessageValidator.logged} first,
     * which writes the breaks out.</p>
     */
    private static byte[] transformGuiNewChatLog(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"printChatMessageWithOptionalDeletion".equals(method.name)
                        && !"func_146234_a".equals(method.name)
                        || !"(Lnet/minecraft/util/IChatComponent;I)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, CHAT_LOG_HOOK_OWNER, "logged")) {
                    activate(CHAT_LOG_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int patched = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() != Opcodes.INVOKEINTERFACE) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (!"org/apache/logging/log4j/Logger".equals(call.owner)
                            || !"info".equals(call.name)
                            || !"(Ljava/lang/String;)V".equals(call.desc)) {
                        continue;
                    }
                    method.instructions.insertBefore(call, hookCall(
                            CHAT_LOG_HOOK_OWNER, "logged",
                            "(Ljava/lang/String;)Ljava/lang/String;",
                            HookFallback.argument(0)));
                    patched++;
                }
                if (patched != 1) {
                    warn("Expected one log call in GuiNewChat#"
                            + "printChatMessageWithOptionalDeletion, found "
                            + patched + "; a chat line with a line break"
                            + " starts a line of its own in the log");
                    return basicClass;
                }
                activate(CHAT_LOG_ACTIVE_PROPERTY);
                info("Patched GuiNewChat logging to keep each chat line on one"
                        + " log line");
                return write(owner);
            }
            warn("Could not locate GuiNewChat#printChatMessageWithOptionalDeletion;"
                    + " a chat line with a line break starts a line of its own"
                    + " in the log");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch the chat log: " + throwable);
            return basicClass;
        }
    }

    /**
     * Keeps the chat history from deleting itself as it is laid out
     * again.
     *
     * <p>{@code GuiNewChat.func_146237_a} opens by deleting any line
     * that carries the id it was given, which is how a line is replaced.
     * That delete also drops the message from the unwrapped history,
     * and {@code refreshChat} calls the method for every entry of that
     * history while walking it — so with lines that carry an id, which
     * every Lost Tales line does, a re-wrap ate the history. The call
     * becomes {@code LostTalesChatHistoryHooks.deleteUnlessRefreshing},
     * which does exactly what vanilla did except while refreshing.</p>
     */
    private static byte[] transformGuiNewChatDelete(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"func_146237_a".equals(method.name)
                        || !"(Lnet/minecraft/util/IChatComponent;IIZ)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, CHAT_HISTORY_HOOK_OWNER,
                        "deleteUnlessRefreshing")) {
                    activate(CHAT_DELETE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int replaced = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (!"net/minecraft/client/gui/GuiNewChat".equals(call.owner)
                            || !"(I)V".equals(call.desc)
                            || (!"deleteChatLine".equals(call.name)
                                    && !"func_146242_c".equals(call.name))) {
                        continue;
                    }
                    // The refresh flag is the method's fourth argument.
                    method.instructions.insertBefore(call,
                            new VarInsnNode(Opcodes.ILOAD, 4));
                    MethodInsnNode hook = hookCall(CHAT_HISTORY_HOOK_OWNER,
                            "deleteUnlessRefreshing",
                            "(Lnet/minecraft/client/gui/GuiNewChat;IZ)V",
                            HookFallback.original(call));
                    method.instructions.set(call, hook);
                    instruction = hook;
                    replaced++;
                }
                if (replaced != 1) {
                    warn("Expected one line deletion in "
                            + "GuiNewChat#func_146237_a, found " + replaced
                            + "; re-wrapping the chat will lose its history");
                    return basicClass;
                }
                activate(CHAT_DELETE_ACTIVE_PROPERTY);
                info("Patched GuiNewChat line replacement for re-wrapping");
                return write(owner);
            }
            warn("Could not locate GuiNewChat#func_146237_a; re-wrapping "
                    + "the chat will lose its history");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch chat line replacement: " + throwable);
            return basicClass;
        }
    }

    /**
     * Lets the chat history keep more than vanilla's hundred.
     *
     * <p>{@code GuiNewChat.func_146237_a} files a message into the
     * wrapped-line list and the message list and trims each back to a
     * literal hundred. Every {@code bipush 100} in the method becomes a
     * call to {@code LostTalesChatHistoryHooks.capacity()}, so the
     * configured size applies to both lists and to nothing else; the
     * resize re-wrap reads the same lists and needs no change. Without
     * the patch vanilla's hundred stays.</p>
     */
    private static byte[] transformGuiNewChatHistory(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"func_146237_a".equals(method.name)
                        || !"(Lnet/minecraft/util/IChatComponent;IIZ)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, CHAT_HISTORY_HOOK_OWNER,
                        "capacity")) {
                    activate(CHAT_HISTORY_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int replaced = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() == Opcodes.BIPUSH
                            && ((IntInsnNode)instruction).operand
                                    == VANILLA_CHAT_HISTORY) {
                        MethodInsnNode hook = hookCall(
                                CHAT_HISTORY_HOOK_OWNER, "capacity", "()I",
                                HookFallback.constant(Integer.valueOf(VANILLA_CHAT_HISTORY)));
                        method.instructions.set(instruction, hook);
                        instruction = hook;
                        replaced++;
                    }
                }
                if (replaced != 2) {
                    warn("Expected two history limits in "
                            + "GuiNewChat#func_146237_a, found " + replaced
                            + "; the chat history keeps vanilla's hundred");
                    return basicClass;
                }
                activate(CHAT_HISTORY_ACTIVE_PROPERTY);
                info("Patched GuiNewChat history capacity");
                return write(owner);
            }
            warn("Could not locate GuiNewChat#func_146237_a; the chat "
                    + "history keeps vanilla's hundred");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch chat history capacity: " + throwable);
            return basicClass;
        }
    }

    /**
     * Frees the menus from vanilla's thirty-frame cap.
     *
     * <p>{@code Minecraft.getLimitFramerate} answers a literal thirty
     * while no world is loaded and a screen is open — the main menu and
     * every screen reached from it. That literal becomes a call to
     * {@code LostTalesMenuFramerateHook.menuFramerateLimit()}, which
     * answers the player's own framerate limit, so the pointer and the
     * animated screens move as smoothly in the menus as in game. Without
     * the patch vanilla's thirty stays.</p>
     */
    private static byte[] transformMinecraftMenuFramerate(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("getLimitFramerate".equals(method.name)
                        || "func_90020_K".equals(method.name))
                        || !"()I".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, MENU_FRAMERATE_HOOK_OWNER,
                        "menuFramerateLimit")) {
                    activate(MENU_FRAMERATE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int replaced = 0;
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (instruction.getOpcode() == Opcodes.BIPUSH
                            && ((IntInsnNode)instruction).operand
                                    == VANILLA_MENU_FRAMERATE) {
                        MethodInsnNode hook = hookCall(
                                MENU_FRAMERATE_HOOK_OWNER,
                                "menuFramerateLimit", "()I",
                                HookFallback.constant(Integer.valueOf(VANILLA_MENU_FRAMERATE)));
                        method.instructions.set(instruction, hook);
                        instruction = hook;
                        replaced++;
                    }
                }
                if (replaced != 1) {
                    warn("Expected one menu framerate literal in "
                            + "Minecraft#getLimitFramerate, found "
                            + replaced + "; the menus keep vanilla's "
                            + "thirty frames");
                    return basicClass;
                }
                activate(MENU_FRAMERATE_ACTIVE_PROPERTY);
                info("Patched menu framerate cap");
                return write(owner);
            }
            warn("Could not locate Minecraft#getLimitFramerate; the menus "
                    + "keep vanilla's thirty frames");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch the menu framerate cap: " + throwable);
            return basicClass;
        }
    }

    /**
     * Reports completed LOTR fast travel.
     *
     * <p>LOTR finishes a fast travel in
     * {@code LOTRPlayerData.receiveFTBouncePacket}: the client bounces the
     * countdown back and the server calls the private
     * {@code fastTravelTo(waypoint)}. The receiver and argument are
     * duplicated on the stack before that call and handed to the arrival
     * hook after it, so the hook sees exactly the waypoint that was
     * travelled to, only once the teleport has run, and never for a
     * request that was refused. Without the patch arrivals are not
     * reported and the chat picker's recent-destinations list stays
     * empty.</p>
     */
    private static byte[] transformLotrFastTravelArrival(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"receiveFTBouncePacket".equals(method.name)
                        || !"()V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, FAST_TRAVEL_ARRIVAL_HOOK_OWNER,
                        "onArrived")) {
                    activate(LOTR_FAST_TRAVEL_ARRIVAL_ACTIVE_PROPERTY);
                    return basicClass;
                }
                MethodInsnNode travel = null;
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == Opcodes.INVOKESPECIAL
                            && "lotr/common/LOTRPlayerData".equals(call.owner)
                            && "fastTravelTo".equals(call.name)
                            && "(Llotr/common/world/map/LOTRAbstractWaypoint;)V"
                            .equals(call.desc)) {
                        travel = call;
                        break;
                    }
                }
                if (travel == null) {
                    warn("Could not locate LOTRPlayerData#fastTravelTo inside "
                            + "receiveFTBouncePacket; fast-travel arrivals "
                            + "will not be reported");
                    return basicClass;
                }
                method.instructions.insertBefore(
                        travel, new InsnNode(Opcodes.DUP2));
                method.instructions.insert(travel, hookCall(
                        FAST_TRAVEL_ARRIVAL_HOOK_OWNER,
                        "onArrived",
                        "(Llotr/common/LOTRPlayerData;"
                                + "Llotr/common/world/map/LOTRAbstractWaypoint;)V",
                        HookFallback.NOTHING));
                activate(LOTR_FAST_TRAVEL_ARRIVAL_ACTIVE_PROPERTY);
                info("Patched LOTR fast travel completion to report arrivals");
                return write(owner);
            }
            warn("Could not locate LOTRPlayerData#receiveFTBouncePacket; "
                    + "fast-travel arrivals will not be reported");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR fast travel completion: " + throwable);
            return basicClass;
        }
    }

    /**
     * Tags a unit with the identity that hired it.
     *
     * <p>{@code LOTRHiredNPCInfo.hireUnit} is where every hire ends, with
     * the info and the hiring player on hand. The info and the player are
     * handed to {@code LostTalesLotrHiredUnitHook.onHired} just before
     * the method returns, once LOTR has finished the hire, so the unit is
     * marked with the character or account that hired it and a later
     * switch knows whose it is. Without the patch a unit is tagged when
     * its owner first switches away instead.</p>
     */
    private static byte[] transformLotrHiredUnit(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"hireUnit".equals(method.name)
                        || !LOTR_HIRE_UNIT_DESC.equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, LOTR_HIRED_UNIT_HOOK_OWNER, "onHired")) {
                    activate(LOTR_HIRED_UNIT_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int patched = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() != Opcodes.RETURN) {
                        continue;
                    }
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    call.add(hookCall(LOTR_HIRED_UNIT_HOOK_OWNER, "onHired",
                            "(Llotr/common/entity/npc/LOTRHiredNPCInfo;"
                                    + "Lnet/minecraft/entity/player/EntityPlayer;)V",
                            HookFallback.NOTHING));
                    method.instructions.insertBefore(instruction, call);
                    patched++;
                }
                if (patched == 0) {
                    warn("LOTRHiredNPCInfo#hireUnit has no return to patch; "
                            + "hired units will be tagged on the first switch instead");
                    return basicClass;
                }
                activate(LOTR_HIRED_UNIT_ACTIVE_PROPERTY);
                info("Patched LOTR unit hiring to tag units with the hiring identity");
                return write(owner);
            }
            warn("Could not locate LOTRHiredNPCInfo#hireUnit; hired units will "
                    + "be tagged on the first switch instead");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR unit hiring: " + throwable);
            return basicClass;
        }
    }

    /**
     * Names who shared a waypoint after the character they play.
     *
     * <p>{@code LOTRCustomWaypoint.setSharingPlayerID} is where every copy
     * of a shared waypoint learns its sharer, and LOTR writes the sharing
     * account's name there. The waypoint is handed to
     * {@code LostTalesLotrSharedWaypointNames.name} just before the method
     * returns, which writes the name of the character the account plays.
     * Without the patch LOTR's copies name the account.</p>
     */
    private static byte[] transformLotrSharedWaypointName(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"setSharingPlayerID".equals(method.name)
                        || !"(Ljava/util/UUID;)V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, LOTR_SHARED_WAYPOINT_NAME_HOOK_OWNER, "name")) {
                    activate(LOTR_SHARED_WAYPOINT_NAME_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int patched = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() != Opcodes.RETURN) {
                        continue;
                    }
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    call.add(hookCall(LOTR_SHARED_WAYPOINT_NAME_HOOK_OWNER, "name",
                            "(Llotr/common/world/map/LOTRCustomWaypoint;)V",
                            HookFallback.NOTHING));
                    method.instructions.insertBefore(instruction, call);
                    patched++;
                }
                if (patched == 0) {
                    warn("LOTRCustomWaypoint#setSharingPlayerID has no return to patch; "
                            + "shared waypoints will name the account");
                    return basicClass;
                }
                activate(LOTR_SHARED_WAYPOINT_NAME_ACTIVE_PROPERTY);
                info("Patched LOTR shared waypoints to name the sharing character");
                return write(owner);
            }
            warn("Could not locate LOTRCustomWaypoint#setSharingPlayerID; "
                    + "shared waypoints will name the account");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR shared waypoint names: " + throwable);
            return basicClass;
        }
    }

    /**
     * Refuses LOTR's own requests about a fellowship.
     *
     * <p>Every request of LOTR's fellowship screen but the one that makes a
     * fellowship finds its fellowship through
     * {@code LOTRPacketFellowshipDo.getActiveFellowship} or
     * {@code getActiveOrDisbandedFellowship}, and does nothing when that
     * finds none. Each value either returns is handed to
     * {@code LostTalesLotrFellowshipRequestHook.refuse}, which finds none:
     * the fellowships are the mod's, managed on its own page. Without the
     * patch a changed client could still rename, invite or hand on the lead
     * in LOTR's fellowship behind one of ours, until the mod next sets it
     * right.</p>
     */
    private static byte[] transformLotrFellowshipRequests(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            int methods = 0;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("getActiveFellowship".equals(method.name)
                        || "getActiveOrDisbandedFellowship".equals(method.name))
                        || !("()" + LOTR_FELLOWSHIP_DESC).equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, LOTR_FELLOWSHIP_HOOK_OWNER, "refuse")) {
                    methods++;
                    continue;
                }
                int patched = 0;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() != Opcodes.ARETURN) {
                        continue;
                    }
                    method.instructions.insertBefore(instruction, hookCall(
                            LOTR_FELLOWSHIP_HOOK_OWNER, "refuse",
                            "(" + LOTR_FELLOWSHIP_DESC + ")" + LOTR_FELLOWSHIP_DESC,
                            HookFallback.argument(0)));
                    patched++;
                }
                if (patched > 0) {
                    methods++;
                }
            }
            if (methods != 2) {
                warn("Could not locate both fellowship lookups of LOTRPacketFellowshipDo; "
                        + "LOTR's own fellowship requests are not refused");
                return basicClass;
            }
            activate(LOTR_FELLOWSHIP_REQUESTS_ACTIVE_PROPERTY);
            info("Patched LOTR's fellowship requests to be refused");
            return write(owner);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's fellowship requests: " + throwable);
            return basicClass;
        }
    }

    /**
     * Refuses LOTR's own request to make a fellowship: before its handler
     * does anything, {@code LostTalesLotrFellowshipRequestHook.refusesCreation}
     * is asked, and a yes returns at once. Fellowships are made on the
     * mod's page; LOTR's {@code /fellowship} is removed at server start.
     */
    private static byte[] transformLotrFellowshipCreate(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"onMessage".equals(method.name)
                        || !LOTR_FELLOWSHIP_CREATE_DESC.equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, LOTR_FELLOWSHIP_HOOK_OWNER, "refusesCreation")) {
                    activate(LOTR_FELLOWSHIP_CREATE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                LabelNode go = new LabelNode();
                InsnList refusal = new InsnList();
                refusal.add(hookCall(LOTR_FELLOWSHIP_HOOK_OWNER, "refusesCreation", "()Z",
                        HookFallback.FALSE));
                refusal.add(new JumpInsnNode(Opcodes.IFEQ, go));
                refusal.add(new InsnNode(Opcodes.ACONST_NULL));
                refusal.add(new InsnNode(Opcodes.ARETURN));
                refusal.add(go);
                method.instructions.insert(refusal);
                activate(LOTR_FELLOWSHIP_CREATE_ACTIVE_PROPERTY);
                info("Patched LOTR's request to make a fellowship to be refused");
                return write(owner);
            }
            warn("Could not locate LOTRPacketFellowshipCreate.Handler#onMessage; "
                    + "LOTR's own requests to make a fellowship are not refused");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's request to make a fellowship: " + throwable);
            return basicClass;
        }
    }

    /**
     * LOTR's request to share a waypoint finds the fellowship it names
     * through {@code LOTRPlayerData.getFellowshipByName}; that call is
     * handed to {@code LostTalesLotrFellowshipRequestHook.shareInto}, which
     * passes over a fellowship the account holds for the record only
     * (FEL-02). The call takes and gives the same, so nothing else moves.
     */
    private static byte[] transformLotrFellowshipShare(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"onMessage".equals(method.name) || !LOTR_SHARE_CWP_DESC.equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, LOTR_FELLOWSHIP_HOOK_OWNER, "shareInto")) {
                    activate(LOTR_FELLOWSHIP_SHARE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                if (!redirectPlayerDataCall(method, "getFellowshipByName",
                        "(Ljava/lang/String;)" + LOTR_FELLOWSHIP_DESC,
                        LOTR_FELLOWSHIP_HOOK_OWNER, "shareInto")) {
                    break;
                }
                activate(LOTR_FELLOWSHIP_SHARE_ACTIVE_PROPERTY);
                info("Patched LOTR's waypoint share to pass over a fellowship held for the record");
                return write(owner);
            }
            warn("Could not locate the fellowship lookup of LOTRPacketShareCWP.Handler#onMessage; "
                    + "a fellowship held for the record takes shared waypoints");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's waypoint share: " + throwable);
            return basicClass;
        }
    }

    /**
     * LOTR's map finds the fellowship a waypoint is shared into, by the name
     * typed, in its private {@code getFellowshipByName} through
     * {@code LOTRPlayerData.getClientFellowshipByName}; that call is handed
     * to {@code LostTalesLotrFellowshipShareRows.fellowshipByName}, which
     * offers only the fellowships behind the character played (FEL-02).
     */
    private static byte[] transformLotrFellowshipShareRow(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"getFellowshipByName".equals(method.name)
                        || !"(Ljava/lang/String;)Llotr/common/fellowship/LOTRFellowshipClient;"
                                .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, LOTR_FELLOWSHIP_SHARE_ROW_HOOK_OWNER,
                        "fellowshipByName")) {
                    activate(LOTR_FELLOWSHIP_SHARE_ROW_ACTIVE_PROPERTY);
                    return basicClass;
                }
                if (!redirectPlayerDataCall(method, "getClientFellowshipByName",
                        "(Ljava/lang/String;)Llotr/common/fellowship/LOTRFellowshipClient;",
                        LOTR_FELLOWSHIP_SHARE_ROW_HOOK_OWNER, "fellowshipByName")) {
                    break;
                }
                activate(LOTR_FELLOWSHIP_SHARE_ROW_ACTIVE_PROPERTY);
                info("Patched LOTR's map to offer only the fellowships behind the character played");
                return write(owner);
            }
            warn("Could not locate LOTRGuiMap#getFellowshipByName's lookup; LOTR's map "
                    + "offers a fellowship held for the record as one to share into");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's map share rows: " + throwable);
            return basicClass;
        }
    }

    /**
     * Hands every call of {@code LOTRPlayerData.name(desc)} in the method to
     * the static {@code hookOwner.hookName}, which takes the player data
     * first and the call's arguments after it; should the hook fail, the
     * call is made as it was. Whether there was one.
     */
    private static boolean redirectPlayerDataCall(MethodNode method, String name,
                                                  String desc, String hookOwner,
                                                  String hookName) {
        boolean redirected = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "lotr/common/LOTRPlayerData".equals(call.owner)
                    && name.equals(call.name) && desc.equals(call.desc)) {
                MethodInsnNode hook = hookCall(hookOwner, hookName,
                        "(Llotr/common/LOTRPlayerData;" + desc.substring(1),
                        HookFallback.original(call));
                method.instructions.set(call, hook);
                instruction = hook;
                redirected = true;
            }
        }
        return redirected;
    }

    /**
     * Colours a travelling trader's notices by the trader's faction.
     *
     * <p>{@code LOTRTravellingTraderInfo} announces a trader arriving and
     * departing by handing each notice to
     * {@code LOTRSpeech.messageAllPlayersInWorld}, with the trader's name
     * in plain yellow. Just before every such call the notice and the
     * trader ({@code this.theEntity}) are handed to
     * {@code LostTalesLotrTraderNoticeHook.decorate}, which marks the name
     * with the faction colour and hands the notice back; the stack is
     * left as LOTR built it, so who is told and what is said do not
     * change. Without the patch the notices stay yellow.</p>
     */
    private static byte[] transformLotrTraderNotice(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            int patched = 0;
            boolean present = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if ((method.access & Opcodes.ACC_STATIC) != 0) {
                    continue;
                }
                if (containsHook(method, LOTR_TRADER_NOTICE_HOOK_OWNER, "decorate")) {
                    present = true;
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() != Opcodes.INVOKESTATIC
                            || !(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (!"messageAllPlayersInWorld".equals(call.name)
                            || !LOTR_MESSAGE_ALL_PLAYERS_DESC.equals(call.desc)) {
                        continue;
                    }
                    InsnList decorate = new InsnList();
                    decorate.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    decorate.add(new FieldInsnNode(Opcodes.GETFIELD,
                            owner.name, "theEntity",
                            "Llotr/common/entity/npc/LOTREntityNPC;"));
                    decorate.add(hookCall(LOTR_TRADER_NOTICE_HOOK_OWNER, "decorate",
                            "(Lnet/minecraft/util/IChatComponent;"
                                    + "Llotr/common/entity/npc/LOTREntityNPC;)"
                                    + "Lnet/minecraft/util/IChatComponent;",
                            HookFallback.argument(0)));
                    method.instructions.insertBefore(call, decorate);
                    patched++;
                }
            }
            if (patched == 0) {
                if (present) {
                    activate(LOTR_TRADER_NOTICE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                warn("Could not locate a travelling trader notice in "
                        + "LOTRTravellingTraderInfo; the notices stay yellow");
                return basicClass;
            }
            activate(LOTR_TRADER_NOTICE_ACTIVE_PROPERTY);
            info("Patched " + patched + " LOTR travelling trader notices to wear "
                    + "the trader's faction colour");
            return write(owner);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR travelling trader notices: " + throwable);
            return basicClass;
        }
    }

    /**
     * Draws LOTR's achievement hover card in the chat's palette.
     *
     * <p>LOTR draws the card for one of its achievements in the chat
     * itself, after every chat screen, through
     * {@code LOTRGuiAchievementHoverEvent}: a {@code GuiScreen} of its
     * own whose two hovering-text methods hand the lines to vanilla's
     * routine, in vanilla's colours. Each method opens with a call to
     * {@code LostTalesLotrAchievementHoverHook}, which draws the same
     * lines as the chat's own card and answers whether it did; when it
     * did not, LOTR's drawing runs unchanged. Without the patch LOTR's
     * card keeps its own colours.</p>
     */
    private static byte[] transformLotrAchievementHover(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            int patched = 0;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                boolean lines = "func_146283_a".equals(method.name)
                        && "(Ljava/util/List;II)V".equals(method.desc);
                boolean line = ("drawCreativeTabHoveringText".equals(method.name)
                        || "func_146279_a".equals(method.name))
                        && "(Ljava/lang/String;II)V".equals(method.desc);
                if (!lines && !line) {
                    continue;
                }
                String hookName = lines ? "drawLines" : "drawLine";
                if (containsHook(method, LOTR_ACHIEVEMENT_HOVER_HOOK_OWNER,
                        hookName)) {
                    patched++;
                    continue;
                }
                // if (Hook.draw(this, arg, x, y)) return;
                InsnList hook = new InsnList();
                LabelNode drawByLotr = new LabelNode();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 2));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
                hook.add(hookCall(LOTR_ACHIEVEMENT_HOVER_HOOK_OWNER, hookName,
                        lines ? "(Lnet/minecraft/client/gui/GuiScreen;"
                                + "Ljava/util/List;II)Z"
                                : "(Lnet/minecraft/client/gui/GuiScreen;"
                                + "Ljava/lang/String;II)Z", HookFallback.FALSE));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, drawByLotr));
                hook.add(new InsnNode(Opcodes.RETURN));
                hook.add(drawByLotr);
                method.instructions.insert(hook);
                patched++;
            }
            if (patched == 0) {
                warn("Could not locate LOTRGuiAchievementHoverEvent's hovering "
                        + "text; LOTR achievement hover cards keep LOTR's colours");
                return basicClass;
            }
            activate(LOTR_ACHIEVEMENT_HOVER_ACTIVE_PROPERTY);
            info("Patched LOTR achievement hover cards into the chat's palette");
            return write(owner);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR achievement hover cards: " + throwable);
            return basicClass;
        }
    }

    /**
     * Patches the head of {@code EntityPlayerMP.addChatMessage}, where
     * every line sent to one player passes, to call
     * {@code LostTalesServerBroadcastHook.onPlayerLine}, which records
     * a command's answer under the tab it was typed in and hands the
     * component back to be sent — or nothing, for a line this player is
     * not sent, and the method returns there. Idempotent, and never fails
     * the game.
     */
    private static byte[] transformEntityPlayerMpChatLine(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("addChatMessage".equals(method.name)
                        || "func_145747_a".equals(method.name))
                        || !"(Lnet/minecraft/util/IChatComponent;)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, SERVER_BROADCAST_HOOK_OWNER,
                        "onPlayerLine")) {
                    activate(PLAYER_LINE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(hookCall(SERVER_BROADCAST_HOOK_OWNER, "onPlayerLine",
                        "(Lnet/minecraft/entity/player/EntityPlayerMP;"
                                + "Lnet/minecraft/util/IChatComponent;)"
                                + "Lnet/minecraft/util/IChatComponent;",
                        HookFallback.argument(1)));
                hook.add(new VarInsnNode(Opcodes.ASTORE, 1));
                LabelNode send = new LabelNode();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new JumpInsnNode(Opcodes.IFNONNULL, send));
                hook.add(new InsnNode(Opcodes.RETURN));
                hook.add(send);
                method.instructions.insert(hook);
                activate(PLAYER_LINE_ACTIVE_PROPERTY);
                info("Patched player chat lines to keep a command's "
                        + "answers with its tab");
                return write(owner);
            }
            warn("Could not locate EntityPlayerMP#addChatMessage; "
                    + "command answers will not be kept in the history");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch player chat lines: " + throwable);
            return basicClass;
        }
    }

    /**
     * Opens the server's broadcast seam.
     *
     * <p>Every line the whole server sees — death messages, vanilla and
     * LOTR achievement announcements, joins and leaves, {@code /say} —
     * goes out through {@code ServerConfigurationManager.sendChatMsg}.
     * The component is handed to
     * {@code LostTalesServerBroadcastHook.onBroadcast} at the head of
     * the method, and what it hands back is sent: the line with its id
     * and the players it names, a join or a leave naming the account,
     * or nothing for a line held back for a moment, and the method
     * returns there. Without the patch the Discord bridge cannot hear of
     * deaths or achievements and says so when it starts.</p>
     */
    private static byte[] transformServerBroadcast(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("sendChatMsg".equals(method.name)
                        || "func_148539_a".equals(method.name))
                        || !"(Lnet/minecraft/util/IChatComponent;)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, SERVER_BROADCAST_HOOK_OWNER,
                        "onBroadcast")) {
                    activate(SERVER_BROADCAST_ACTIVE_PROPERTY);
                    return basicClass;
                }
                // The hook hands the component back, written over the
                // parameter itself, so the whole body sends the line
                // with the id run the hook appended; nothing handed back
                // sends nothing.
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(hookCall(SERVER_BROADCAST_HOOK_OWNER, "onBroadcast",
                        "(Lnet/minecraft/util/IChatComponent;)"
                                + "Lnet/minecraft/util/IChatComponent;",
                        HookFallback.argument(0)));
                hook.add(new VarInsnNode(Opcodes.ASTORE, 1));
                LabelNode send = new LabelNode();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new JumpInsnNode(Opcodes.IFNONNULL, send));
                hook.add(new InsnNode(Opcodes.RETURN));
                hook.add(send);
                method.instructions.insert(hook);
                activate(SERVER_BROADCAST_ACTIVE_PROPERTY);
                info("Patched server chat broadcasts to report every "
                        + "server-wide line");
                return write(owner);
            }
            warn("Could not locate ServerConfigurationManager#sendChatMsg; "
                    + "deaths and achievements will not reach Discord");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch server chat broadcasts: " + throwable);
            return basicClass;
        }
    }

    /** The single {@code getChatOpen()} call inside the wrap method. */
    private static MethodInsnNode findChatOpenCall(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/client/gui/GuiNewChat".equals(call.owner)
                    && "()Z".equals(call.desc)
                    && ("getChatOpen".equals(call.name)
                    || "func_146241_e".equals(call.name))) {
                return call;
            }
        }
        return null;
    }

    /**
     * The {@code ALOAD} of the list the nearest preceding
     * {@code ArrayList.add(Object)} appended to: the wrapped-line list.
     */
    private static VarInsnNode findWrappedLinesLocal(AbstractInsnNode before) {
        for (AbstractInsnNode cursor = before; cursor != null;
             cursor = cursor.getPrevious()) {
            if (!(cursor instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)cursor;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"java/util/ArrayList".equals(call.owner)
                    || !"add".equals(call.name)
                    || !"(Ljava/lang/Object;)Z".equals(call.desc)) {
                continue;
            }
            AbstractInsnNode element = previousCode(call);
            AbstractInsnNode list = previousCode(element);
            return element instanceof VarInsnNode
                    && list instanceof VarInsnNode
                    && list.getOpcode() == Opcodes.ALOAD
                    ? (VarInsnNode)list : null;
        }
        return null;
    }

    /**
     * Offers every tooltip to Lost Tales before vanilla draws it.
     *
     * <p>A tooltip carrying a key icon is laid out and drawn by the mod,
     * because an icon is taller than a line of text and the box has to be
     * measured around it; every other tooltip is declined and falls straight
     * through to vanilla's own drawing. Without this patch the hint lines keep
     * their plain "[SHIFT]" text, so a failure here costs the artwork and
     * nothing else.</p>
     */
    private static byte[] transformGuiScreenTooltip(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("drawHoveringText".equals(method.name)
                        || "func_146283_a".equals(method.name))
                        || !("(Ljava/util/List;IILnet/minecraft/client/gui/"
                        + "FontRenderer;)V").equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, TOOLTIP_HOOK_OWNER,
                        "drawHoveringText")) {
                    activate(TOOLTIP_ICON_ACTIVE_PROPERTY);
                    activate(TOOLTIP_SMOOTHING_ACTIVE_PROPERTY);
                    return basicClass;
                }
                LabelNode vanilla = new LabelNode();
                InsnList head = new InsnList();
                // First, and drawing nothing: every tooltip below is put
                // on the pointer's true position rather than on the whole
                // interface unit the screen was handed.
                head.add(new VarInsnNode(Opcodes.ILOAD, 2));
                head.add(new VarInsnNode(Opcodes.ILOAD, 3));
                head.add(hookCall(
                        TOOLTIP_SMOOTHING_OWNER,
                        "begin",
                        "(II)V", HookFallback.NOTHING));
                head.add(new VarInsnNode(Opcodes.ALOAD, 0));
                head.add(new VarInsnNode(Opcodes.ALOAD, 1));
                head.add(new VarInsnNode(Opcodes.ILOAD, 2));
                head.add(new VarInsnNode(Opcodes.ILOAD, 3));
                head.add(new VarInsnNode(Opcodes.ALOAD, 4));
                head.add(hookCall(
                        TOOLTIP_HOOK_OWNER,
                        "drawHoveringText",
                        "(Lnet/minecraft/client/gui/GuiScreen;"
                                + "Ljava/util/List;II"
                                + "Lnet/minecraft/client/gui/FontRenderer;)Z",
                        HookFallback.FALSE));
                head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
                head.add(new InsnNode(Opcodes.RETURN));
                head.add(vanilla);
                method.instructions.insert(head);
                // Every way out of the method takes the shift back off,
                // the icon renderer's own early return included.
                injectTooltipSmoothingEnd(method);
                activate(TOOLTIP_ICON_ACTIVE_PROPERTY);
                activate(TOOLTIP_SMOOTHING_ACTIVE_PROPERTY);
                info("Patched GuiScreen tooltips for inline key icons and "
                        + "pointer-accurate placement");
                return write(owner);
            }
            warn("Could not locate GuiScreen#drawHoveringText; tooltips will "
                    + "name their keys in text instead of icons, and will "
                    + "step with the interface grid rather than the pointer");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch tooltip key icons: " + throwable);
            return basicClass;
        }
    }

    /** Takes the pointer shift back off at every exit from the method. */
    private static void injectTooltipSmoothingEnd(MethodNode method) {
        AbstractInsnNode node = method.instructions.getFirst();
        while (node != null) {
            AbstractInsnNode next = node.getNext();
            if (node.getOpcode() == Opcodes.RETURN) {
                method.instructions.insertBefore(node, hookCall(
                        TOOLTIP_SMOOTHING_OWNER, "end", "()V", HookFallback.NOTHING));
            }
            node = next;
        }
    }

    /** Routes slot item rendering through the client-side movement animator. */
    private static byte[] transformGuiContainer(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean framePatched = false;
            boolean iconPatched = false;
            boolean overlayPatched = false;
            boolean intentPatched = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (("handleMouseClick".equals(method.name)
                        || "func_146984_a".equals(method.name))
                        && "(Lnet/minecraft/inventory/Slot;III)V"
                        .equals(method.desc)) {
                    if (!containsHook(method,
                            SMOOTH_INVENTORY_HOOK_OWNER,
                            "recordTransferIntent")) {
                        InsnList intent = new InsnList();
                        intent.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        intent.add(new VarInsnNode(Opcodes.ILOAD, 4));
                        intent.add(hookCall(
                                SMOOTH_INVENTORY_HOOK_OWNER,
                                "recordTransferIntent",
                                "(Lnet/minecraft/client/gui/inventory/"
                                        + "GuiContainer;I)V",
                                HookFallback.NOTHING));
                        method.instructions.insert(intent);
                    }
                    intentPatched = containsHook(method,
                            SMOOTH_INVENTORY_HOOK_OWNER,
                            "recordTransferIntent");
                }
                if (("drawScreen".equals(method.name)
                        || "func_73863_a".equals(method.name))
                        && "(IIF)V".equals(method.desc)) {
                    if (!containsHook(method,
                            SMOOTH_INVENTORY_HOOK_OWNER, "beginFrame")) {
                        InsnList begin = new InsnList();
                        begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        begin.add(hookCall(
                                SMOOTH_INVENTORY_HOOK_OWNER,
                                "beginFrame",
                                "(Lnet/minecraft/client/gui/inventory/"
                                        + "GuiContainer;)V",
                                HookFallback.NOTHING));
                        method.instructions.insert(begin);
                    }
                    framePatched = containsHook(method,
                            SMOOTH_INVENTORY_HOOK_OWNER, "beginFrame");
                }
                if (!("func_146977_a".equals(method.name)
                        || "drawSlot".equals(method.name))
                        || !"(Lnet/minecraft/inventory/Slot;)V"
                        .equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst(); instruction != null;) {
                    AbstractInsnNode next = instruction.getNext();
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)instruction;
                        if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                                && "net/minecraft/client/renderer/entity/"
                                .concat("RenderItem").equals(call.owner)
                                && "(Lnet/minecraft/client/gui/FontRenderer;"
                                .concat("Lnet/minecraft/client/renderer/"
                                        + "texture/TextureManager;"
                                        + "Lnet/minecraft/item/ItemStack;II)V")
                                .equals(call.desc)) {
                            method.instructions.insertBefore(call,
                                    containerAndSlotLoads());
                            redirect(call, SMOOTH_INVENTORY_HOOK_OWNER,
                                    "renderItemAndEffectIntoGUI",
                                    "(Lnet/minecraft/client/renderer/"
                                    + "entity/RenderItem;"
                                    + "Lnet/minecraft/client/gui/FontRenderer;"
                                    + "Lnet/minecraft/client/renderer/texture/"
                                    + "TextureManager;"
                                    + "Lnet/minecraft/item/ItemStack;II"
                                    + "Lnet/minecraft/client/gui/inventory/"
                                    + "GuiContainer;"
                                    + "Lnet/minecraft/inventory/Slot;)V");
                            iconPatched = true;
                        } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                                && "net/minecraft/client/renderer/entity/"
                                .concat("RenderItem").equals(call.owner)
                                && "(Lnet/minecraft/client/gui/FontRenderer;"
                                .concat("Lnet/minecraft/client/renderer/"
                                        + "texture/TextureManager;"
                                        + "Lnet/minecraft/item/ItemStack;II"
                                        + "Ljava/lang/String;)V")
                                .equals(call.desc)) {
                            method.instructions.insertBefore(call,
                                    containerAndSlotLoads());
                            redirect(call, SMOOTH_INVENTORY_HOOK_OWNER,
                                    "renderItemOverlayIntoGUI",
                                    "(Lnet/minecraft/client/renderer/"
                                    + "entity/RenderItem;"
                                    + "Lnet/minecraft/client/gui/FontRenderer;"
                                    + "Lnet/minecraft/client/renderer/texture/"
                                    + "TextureManager;"
                                    + "Lnet/minecraft/item/ItemStack;II"
                                    + "Ljava/lang/String;"
                                    + "Lnet/minecraft/client/gui/inventory/"
                                    + "GuiContainer;"
                                    + "Lnet/minecraft/inventory/Slot;)V");
                            overlayPatched = true;
                        }
                    }
                    instruction = next;
                }
            }
            if (framePatched && iconPatched && overlayPatched
                    && intentPatched) {
                activate(SMOOTH_INVENTORY_ACTIVE_PROPERTY);
                info("Installed smooth inventory item movement hooks");
                return write(owner);
            }
            warn("Could not install all smooth inventory item hooks");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch smooth inventory item rendering: "
                    + throwable);
            return basicClass;
        }
    }

    private static InsnList containerAndSlotLoads() {
        InsnList loads = new InsnList();
        loads.add(new VarInsnNode(Opcodes.ALOAD, 0));
        loads.add(new VarInsnNode(Opcodes.ALOAD, 1));
        return loads;
    }

    private static byte[] transformDebugBox(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            MethodNode method = findDebugBoxMethod(owner);
            if (method == null) {
                warn("Could not locate RenderManager#func_85094_b; F3+B may remain visually offset");
                return basicClass;
            }
            if (containsHook(method, DEBUG_HOOK_OWNER, "resolveRenderY")) {
                activate(DEBUG_BOX_ACTIVE_PROPERTY);
                return basicClass;
            }

            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
            hook.add(new VarInsnNode(Opcodes.DLOAD, 4));
            hook.add(hookCall(
                    DEBUG_HOOK_OWNER,
                    "resolveRenderY",
                    DEBUG_HOOK_DESC, HookFallback.argument(1)));
            hook.add(new VarInsnNode(Opcodes.DSTORE, 4));
            method.instructions.insert(hook);

            activate(DEBUG_BOX_ACTIVE_PROPERTY);
            info("Patched RenderManager F3+B origin for roleplay races");
            return write(owner);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch RenderManager F3+B origin: " + throwable);
            return basicClass;
        }
    }

    /**
     * Friend of the Wise (Nils, 2026-10-19): every gain LOTR's alignment
     * core adds passes {@code checkBonusForPledgeEnemyLimit}, the kill
     * path's and the direct path's alike, so the gain is made larger right
     * after it and LOTR's map, popup and conquest all read the larger
     * number. Penalties never pass there.
     */
    private static byte[] transformLotrAlignmentGain(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"addAlignment".equals(method.name)
                        || !LOTR_ADD_ALIGNMENT_DESC.equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, PERK_HOOK_OWNER, "alignmentGain")) {
                    activate(PERK_ALIGNMENT_ACTIVE_PROPERTY);
                    return basicClass;
                }
                List<AbstractInsnNode> limits = calls(method, Opcodes.INVOKESPECIAL,
                        "lotr/common/LOTRPlayerData", "checkBonusForPledgeEnemyLimit",
                        "(Llotr/common/fac/LOTRFaction;F)F");
                if (limits.size() < 2) {
                    warn("Could not locate both of LOTR's alignment gains; Friend"
                            + " of the Wise will not make them larger");
                    return basicClass;
                }
                for (AbstractInsnNode limit : limits) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(hookCall(PERK_HOOK_OWNER, "alignmentGain",
                            "(FLnet/minecraft/entity/player/EntityPlayer;)F",
                            HookFallback.argument(0)));
                    method.instructions.insert(limit, hook);
                }
                activate(PERK_ALIGNMENT_ACTIVE_PROPERTY);
                info("Patched LOTR alignment gains for Friend of the Wise");
                return write(owner);
            }
            warn("Could not locate LOTRPlayerData#addAlignment; Friend of the Wise"
                    + " will not make alignment gains larger");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR alignment gains: " + throwable);
            return basicClass;
        }
    }

    /**
     * Daggerwork (Nils, 2026-10-19): the ticks LOTR's server blocks a
     * player's next strike for, as {@code processUseEntity} reads them
     * from {@code LOTRWeaponStats.getAttackTimePlayer}.
     */
    private static byte[] transformLotrServerAttackTime(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("processUseEntity".equals(method.name)
                        || "func_147340_a".equals(method.name))
                        || !"(Lnet/minecraft/network/play/client/C02PacketUseEntity;)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, PERK_HOOK_OWNER, "attackTime")) {
                    activate(PERK_ATTACK_SERVER_ACTIVE_PROPERTY);
                    return basicClass;
                }
                List<AbstractInsnNode> times = calls(method, Opcodes.INVOKESTATIC,
                        "lotr/common/item/LOTRWeaponStats", "getAttackTimePlayer",
                        "(Lnet/minecraft/item/ItemStack;)I");
                if (times.isEmpty()) {
                    warn("Could not locate LOTR's attack time in processUseEntity;"
                            + " Daggerwork will not strike sooner");
                    return basicClass;
                }
                for (AbstractInsnNode time : times) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(hookCall(PERK_HOOK_OWNER, "attackTime",
                            "(ILnet/minecraft/network/NetHandlerPlayServer;)I",
                            HookFallback.argument(0)));
                    method.instructions.insert(time, hook);
                }
                activate(PERK_ATTACK_SERVER_ACTIVE_PROPERTY);
                info("Patched LOTR's server attack timer for Daggerwork");
                return write(owner);
            }
            warn("Could not locate LOTRNetHandlerPlayServer#processUseEntity;"
                    + " Daggerwork will not strike sooner");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's server attack timer: " + throwable);
            return basicClass;
        }
    }

    /** Daggerwork on LOTR's client: the wait its attack timer starts after a swing. */
    private static byte[] transformLotrClientAttackTime(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"doAttackTiming".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, CLIENT_PERK_HOOK_OWNER, "attackTime")) {
                    activate(PERK_ATTACK_CLIENT_ACTIVE_PROPERTY);
                    return basicClass;
                }
                List<AbstractInsnNode> times = calls(method, Opcodes.INVOKESTATIC,
                        "lotr/common/item/LOTRWeaponStats", "getAttackTimePlayer",
                        "(Lnet/minecraft/item/ItemStack;)I");
                if (times.isEmpty()) {
                    warn("Could not locate LOTR's attack time in its client timer;"
                            + " Daggerwork's sooner strikes will not show");
                    return basicClass;
                }
                for (AbstractInsnNode time : times) {
                    method.instructions.insert(time, hookCall(
                            CLIENT_PERK_HOOK_OWNER, "attackTime", "(I)I",
                            HookFallback.argument(0)));
                }
                activate(PERK_ATTACK_CLIENT_ACTIVE_PROPERTY);
                info("Patched LOTR's client attack timer for Daggerwork");
                return write(owner);
            }
            warn("Could not locate LOTRAttackTiming#doAttackTiming; Daggerwork's"
                    + " sooner strikes will not show");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's client attack timer: " + throwable);
            return basicClass;
        }
    }

    /**
     * Fair Dealer (Nils, 2026-10-19): a trade slot's price where LOTR
     * checks the player's coins and where it takes them.
     */
    private static byte[] transformLotrSlotTrade(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            int patched = 0;
            int found = 0;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                boolean check = ("canTakeStack".equals(method.name)
                        || "func_82869_a".equals(method.name))
                        && "(Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(method.desc);
                boolean take = ("onPickupFromSlot".equals(method.name)
                        || "func_82870_a".equals(method.name))
                        && "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)V"
                        .equals(method.desc);
                if (!check && !take) {
                    continue;
                }
                found++;
                if (containsHook(method, PERK_HOOK_OWNER, "tradeCost")) {
                    patched++;
                    continue;
                }
                List<AbstractInsnNode> costs = calls(method, Opcodes.INVOKEVIRTUAL,
                        "lotr/common/inventory/LOTRSlotTrade", "cost", "()I");
                for (AbstractInsnNode cost : costs) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(hookCall(PERK_HOOK_OWNER,
                            "tradeCost", "(ILnet/minecraft/entity/player/EntityPlayer;"
                                    + "Lnet/minecraft/inventory/Slot;)I",
                            HookFallback.argument(0)));
                    method.instructions.insert(cost, hook);
                }
                if (!costs.isEmpty()) {
                    patched++;
                }
            }
            if (found < 2 || patched < found) {
                rollBack();
                warn("Could not locate LOTR's trade price checks; Fair Dealer will"
                        + " not change prices");
                return basicClass;
            }
            activate(PERK_TRADE_ACTIVE_PROPERTY);
            info("Patched LOTR trade prices for Fair Dealer");
            return write(owner);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR trade prices: " + throwable);
            return basicClass;
        }
    }

    /**
     * Fair Dealer on LOTR's trade screen: the price it draws under each
     * trade and the total it adds up for what is offered for sale.
     */
    private static byte[] transformLotrGuiTrade(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean prices = false;
            boolean sales = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (containsHook(method, CLIENT_PERK_HOOK_OWNER, "shownTradeCost")) {
                    prices = true;
                }
                if (containsHook(method, CLIENT_PERK_HOOK_OWNER, "shownSellValue")) {
                    sales = true;
                }
            }
            if (prices && sales) {
                activate(PERK_TRADE_SCREEN_ACTIVE_PROPERTY);
                return basicClass;
            }
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if ("renderTradeSlot".equals(method.name)
                        && "(Llotr/common/inventory/LOTRSlotTrade;)V".equals(method.desc)) {
                    for (AbstractInsnNode cost : calls(method, Opcodes.INVOKEVIRTUAL,
                            "lotr/common/inventory/LOTRSlotTrade", "cost", "()I")) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        hook.add(hookCall(CLIENT_PERK_HOOK_OWNER,
                                "shownTradeCost", "(ILnet/minecraft/inventory/Slot;)I",
                                HookFallback.argument(0)));
                        method.instructions.insert(cost, hook);
                        prices = true;
                    }
                }
                for (AbstractInsnNode read : fieldReads(method,
                        "lotr/common/entity/npc/LOTRTradeSellResult", "totalSellValue")) {
                    method.instructions.insert(read, hookCall(
                            CLIENT_PERK_HOOK_OWNER, "shownSellValue", "(I)I",
                            HookFallback.argument(0)));
                    sales = true;
                }
            }
            if (!prices || !sales) {
                warn("Could not locate the prices LOTR's trade screen draws; Fair"
                        + " Dealer's prices will not show there");
                return basicClass;
            }
            activate(PERK_TRADE_SCREEN_ACTIVE_PROPERTY);
            info("Patched LOTR's trade screen for Fair Dealer");
            return write(owner);
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's trade screen: " + throwable);
            return basicClass;
        }
    }

    /** Fair Dealer when the player sells what they offered: each stack's value as LOTR pays it. */
    private static byte[] transformLotrPacketSell(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"onMessage".equals(method.name) || !("(Llotr/common/network/LOTRPacketSell;"
                        + "Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)"
                        + "Lcpw/mods/fml/common/network/simpleimpl/IMessage;").equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, PERK_HOOK_OWNER, "sellValue")) {
                    activate(PERK_SELL_ACTIVE_PROPERTY);
                    return basicClass;
                }
                int player = findLotrHandlerPlayerLocal(method);
                List<AbstractInsnNode> reads = fieldReads(method,
                        "lotr/common/entity/npc/LOTRTradeSellResult", "totalSellValue");
                if (player < 0 || reads.isEmpty()) {
                    warn("Could not locate what LOTR pays for a sale; Fair Dealer"
                            + " will not raise it");
                    return basicClass;
                }
                for (AbstractInsnNode read : reads) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, player));
                    hook.add(hookCall(PERK_HOOK_OWNER,
                            "sellValue", "(ILnet/minecraft/entity/player/EntityPlayer;)I",
                            HookFallback.argument(0)));
                    method.instructions.insert(read, hook);
                }
                activate(PERK_SELL_ACTIVE_PROPERTY);
                info("Patched LOTR sales for Fair Dealer");
                return write(owner);
            }
            warn("Could not locate LOTRPacketSell's handler; Fair Dealer will not"
                    + " raise what sales pay");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR sales: " + throwable);
            return basicClass;
        }
    }

    /**
     * Repair and Master Smith (Nils, 2026-10-19): each time LOTR's anvil
     * has worked out its output and price, on both sides.
     */
    private static byte[] transformLotrContainerAnvil(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"updateRepairOutput".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, PERK_HOOK_OWNER, "anvilOutput")) {
                    activate(PERK_ANVIL_ACTIVE_PROPERTY);
                    return basicClass;
                }
                List<AbstractInsnNode> returns = new ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() == Opcodes.RETURN) {
                        returns.add(instruction);
                    }
                }
                if (returns.isEmpty()) {
                    warn("Could not locate the ends of LOTR's anvil output; Repair"
                            + " and Master Smith will not change it");
                    return basicClass;
                }
                for (AbstractInsnNode end : returns) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(hookCall(PERK_HOOK_OWNER,
                            "anvilOutput", "(Llotr/common/inventory/LOTRContainerAnvil;)V",
                            HookFallback.NOTHING));
                    method.instructions.insertBefore(end, hook);
                }
                activate(PERK_ANVIL_ACTIVE_PROPERTY);
                info("Patched LOTR's anvil output for Repair and Master Smith");
                return write(owner);
            }
            warn("Could not locate LOTRContainerAnvil#updateRepairOutput; Repair and"
                    + " Master Smith will not change it");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's anvil output: " + throwable);
            return basicClass;
        }
    }

    /** A repair taken from LOTR's anvil counts toward Smithing. */
    private static byte[] transformLotrSlotAnvilOutput(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("onPickupFromSlot".equals(method.name)
                        || "func_82870_a".equals(method.name))
                        || !"(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, PERK_HOOK_OWNER, "anvilTaken")) {
                    activate(PERK_ANVIL_TAKE_ACTIVE_PROPERTY);
                    return basicClass;
                }
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                hook.add(hookCall(PERK_HOOK_OWNER,
                        "anvilTaken", "(Lnet/minecraft/entity/player/EntityPlayer;"
                                + "Lnet/minecraft/item/ItemStack;)V",
                        HookFallback.NOTHING));
                method.instructions.insert(hook);
                activate(PERK_ANVIL_TAKE_ACTIVE_PROPERTY);
                info("Patched LOTR's anvil output slot to count repairs");
                return write(owner);
            }
            warn("Could not locate LOTRSlotAnvilOutput#onPickupFromSlot; repairs at"
                    + " LOTR's anvil will not count toward Smithing");
            return basicClass;
        } catch (Throwable throwable) {
            rollBack();
            warn("Failed to patch LOTR's anvil output slot: " + throwable);
            return basicClass;
        }
    }

    /** Every call in the method to one owner's method by name and descriptor, in order. */
    private static List<AbstractInsnNode> calls(MethodNode method, int opcode,
                                                String owner, String name, String desc) {
        List<AbstractInsnNode> found = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode && instruction.getOpcode() == opcode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) {
                    found.add(call);
                }
            }
        }
        return found;
    }

    /** Every read of one owner's field in the method, in order. */
    private static List<AbstractInsnNode> fieldReads(MethodNode method, String owner, String name) {
        List<AbstractInsnNode> found = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode
                    && instruction.getOpcode() == Opcodes.GETFIELD
                    && owner.equals(((FieldInsnNode)instruction).owner)
                    && name.equals(((FieldInsnNode)instruction).name)) {
                found.add(instruction);
            }
        }
        return found;
    }

    /**
     * Reads a class to patch, which opens a new attempt: the flags the
     * attempt before set stand, and those this one sets are recorded
     * from here ({@link #activate}).
     */
    private static ClassNode read(byte[] bytes) {
        ACTIVATED.get().clear();
        DECLARED.get().clear();
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    /**
     * Sets a patch's active flag, noting what it held before, so the
     * attempt's {@link #rollBack} can put it back should anything after
     * it fail, the class's writing among them.
     */
    private static void activate(String property) {
        ACTIVATED.get().add(new String[] {property, System.getProperty(property)});
        System.setProperty(property, "true");
    }

    /**
     * Puts back every flag this attempt set, newest first: a patch that
     * failed returns the class as it was, and no flag may say otherwise.
     */
    static void rollBack() {
        List<String[]> activated = ACTIVATED.get();
        for (int index = activated.size() - 1; index >= 0; index--) {
            String[] flag = activated.get(index);
            if (flag[1] == null) {
                System.clearProperty(flag[0]);
            } else {
                System.setProperty(flag[0], flag[1]);
            }
        }
        activated.clear();
    }

    /**
     * Writes the patched class, every call into the mod first wrapped in
     * its guard ({@link HookGuard}); a call that declared no fallback fails
     * the patch, and the class stays as it was.
     */
    private static byte[] write(ClassNode node) {
        HookGuard.guard(node, DECLARED.get());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findCameraMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (!"(F)V".equals(method.desc)) {
                continue;
            }
            if (isNamed(owner, method, CAMERA_MCP, CAMERA_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findFovMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if ("(FZ)F".equals(method.desc)
                    && isNamed(owner, method, FOV_MCP, FOV_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findMouseOverMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if ("(F)V".equals(method.desc)
                    && isNamed(owner, method,
                    MOUSE_OVER_MCP, MOUSE_OVER_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findPlayerControllerAttackMethod(
            ClassNode owner) {
        String descriptor =
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)V";
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (descriptor.equals(method.desc)
                    && isNamed(owner, method,
                    ATTACK_ENTITY_MCP, ATTACK_ENTITY_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findPlayerControllerInteractionMethod(
            ClassNode owner) {
        String descriptor =
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)Z";
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (descriptor.equals(method.desc)
                    && isNamed(owner, method,
                    INTERACT_ENTITY_MCP, INTERACT_ENTITY_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findPlayerControllerBlockActionMethod(
            ClassNode owner) {
        String descriptor =
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/item/ItemStack;"
                        + "IIIILnet/minecraft/util/Vec3;)Z";
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (descriptor.equals(method.desc)
                    && isNamed(owner, method,
                    RIGHT_CLICK_BLOCK_MCP, RIGHT_CLICK_BLOCK_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findDebugBoxMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (!isNamed(owner, method, DEBUG_BOX_MCP, DEBUG_BOX_SRG)
                    || Type.getReturnType(method.desc).getSort() != Type.VOID) {
                continue;
            }
            Type[] arguments = Type.getArgumentTypes(method.desc);
            if (arguments.length == 7
                    && arguments[0].getSort() == Type.OBJECT
                    && arguments[1].getSort() == Type.DOUBLE
                    && arguments[2].getSort() == Type.DOUBLE
                    && arguments[3].getSort() == Type.DOUBLE
                    && arguments[4].getSort() == Type.FLOAT
                    && arguments[5].getSort() == Type.FLOAT) {
                return method;
            }
            // MCP's method has six arguments: entity, x, y, z, yaw, partial.
            if (arguments.length == 6
                    && arguments[0].getSort() == Type.OBJECT
                    && arguments[1].getSort() == Type.DOUBLE
                    && arguments[2].getSort() == Type.DOUBLE
                    && arguments[3].getSort() == Type.DOUBLE
                    && arguments[4].getSort() == Type.FLOAT
                    && arguments[5].getSort() == Type.FLOAT) {
                return method;
            }
        }
        return null;
    }

    private static boolean isNamed(
            ClassNode owner, MethodNode method, String mcpName, String srgName) {
        if (mcpName.equals(method.name) || srgName.equals(method.name)) {
            return true;
        }
        String mapped = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(
                owner.name, method.name, method.desc);
        return mcpName.equals(mapped) || srgName.equals(mapped);
    }

    private static VarInsnNode findVanillaCameraOffsetStore(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            String mapped = FMLDeobfuscatingRemapper.INSTANCE.mapFieldName(
                    field.owner, field.name, field.desc);
            if (!("yOffset".equals(field.name)
                    || "field_70129_M".equals(field.name)
                    || "yOffset".equals(mapped)
                    || "field_70129_M".equals(mapped))) {
                continue;
            }

            AbstractInsnNode cursor = nextCode(instruction);
            int searched = 0;
            boolean sawSubtract = false;
            while (cursor != null && searched++ < 8) {
                if (cursor.getOpcode() == Opcodes.FSUB) {
                    sawSubtract = true;
                } else if (sawSubtract && cursor instanceof VarInsnNode
                        && cursor.getOpcode() == Opcodes.FSTORE) {
                    return (VarInsnNode)cursor;
                }
                cursor = nextCode(cursor);
            }
        }
        return null;
    }

    private static int findViewEntityLocal(VarInsnNode offsetStore) {
        AbstractInsnNode cursor = previousCode(offsetStore);
        int searched = 0;
        while (cursor != null && searched++ < 10) {
            if (cursor instanceof FieldInsnNode
                    && cursor.getOpcode() == Opcodes.GETFIELD) {
                AbstractInsnNode ownerLoad = previousCode(cursor);
                if (ownerLoad instanceof VarInsnNode
                        && ownerLoad.getOpcode() == Opcodes.ALOAD) {
                    return ((VarInsnNode)ownerLoad).var;
                }
            }
            cursor = previousCode(cursor);
        }
        return -1;
    }

    private static VarInsnNode findThirdPersonDistanceStore(
            MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!isFieldNamed(
                    field, "thirdPersonDistanceTemp", "field_78491_C")) {
                continue;
            }
            AbstractInsnNode cursor = nextCode(instruction);
            int searched = 0;
            while (cursor != null && searched++ < 32) {
                if (cursor instanceof VarInsnNode
                        && cursor.getOpcode() == Opcodes.DSTORE) {
                    return (VarInsnNode)cursor;
                }
                cursor = nextCode(cursor);
            }
        }
        return null;
    }

    private static MethodInsnNode findThirdPersonDistanceTranslation(
            MethodNode method, int distanceLocal) {
        MethodInsnNode match = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !"org/lwjgl/opengl/GL11".equals(call.owner)
                    || !"glTranslatef".equals(call.name)
                    || !"(FFF)V".equals(call.desc)) {
                continue;
            }
            AbstractInsnNode convert = previousCode(call);
            AbstractInsnNode negate = previousCode(convert);
            AbstractInsnNode load = previousCode(negate);
            if (convert != null && convert.getOpcode() == Opcodes.D2F
                    && negate != null
                    && negate.getOpcode() == Opcodes.DNEG
                    && load instanceof VarInsnNode
                    && load.getOpcode() == Opcodes.DLOAD
                    && ((VarInsnNode)load).var == distanceLocal) {
                match = call;
            }
        }
        return match;
    }

    private static boolean injectFovHooks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.FRETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ILOAD, 2));
            hook.add(hookCall(
                    THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveFov", CAMERA_FOV_HOOK_DESC, HookFallback.argument(0)));
            method.instructions.insertBefore(instruction, hook);
            changed = true;
        }
        return changed;
    }

    private static boolean injectMouseOverHooks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.RETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.FLOAD, 1));
            hook.add(hookCall(
                    TARGETING_HOOK_OWNER,
                    "resolveMouseOver", TARGETING_HOOK_DESC, HookFallback.NOTHING));
            method.instructions.insertBefore(instruction, hook);
            changed = true;
        }
        return changed;
    }

    private static boolean injectEntityAttackHook(MethodNode method) {
        MethodInsnNode syncCall = findHeldItemSyncCall(method);
        if (syncCall == null) {
            warn("Could not locate PlayerControllerMP held-item sync in "
                    + method.name + "; vanilla entity packets remain active");
            return false;
        }

        LabelNode vanillaPath = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(hookCall(
                ENTITY_ACTION_HOOK_OWNER,
                "shouldHandleEntityAttack",
                ENTITY_ACTION_PREDICATE_DESC, HookFallback.FALSE));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, vanillaPath));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(hookCall(
                ENTITY_ACTION_HOOK_OWNER,
                "handleAttack",
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)V", HookFallback.NOTHING));
        hook.add(new org.objectweb.asm.tree.InsnNode(
                Opcodes.RETURN));
        hook.add(vanillaPath);
        method.instructions.insert(syncCall, hook);
        return true;
    }

    private static boolean injectEntityInteractionHook(MethodNode method) {
        MethodInsnNode syncCall = findHeldItemSyncCall(method);
        if (syncCall == null) {
            warn("Could not locate PlayerControllerMP held-item sync in "
                    + method.name + "; vanilla entity packets remain active");
            return false;
        }

        LabelNode vanillaPath = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(hookCall(
                ENTITY_ACTION_HOOK_OWNER,
                "shouldHandleEntityInteraction",
                ENTITY_ACTION_PREDICATE_DESC, HookFallback.FALSE));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, vanillaPath));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(hookCall(
                ENTITY_ACTION_HOOK_OWNER,
                "handleInteraction",
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)Z", HookFallback.FALSE));
        hook.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
        hook.add(vanillaPath);
        method.instructions.insert(syncCall, hook);
        return true;
    }

    private static boolean replaceBlockActionSend(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"net/minecraft/client/network/NetHandlerPlayClient"
                    .equals(call.owner)
                    || !"(Lnet/minecraft/network/Packet;)V"
                    .equals(call.desc)
                    || !("addToSendQueue".equals(call.name)
                    || "func_147297_a".equals(call.name))) {
                continue;
            }
            redirect(call, BLOCK_ACTION_HOOK_OWNER, "sendBlockActionOrVanilla",
                    BLOCK_ACTION_SEND_DESC);
            return true;
        }
        warn("Could not locate PlayerControllerMP block packet send; "
                + "vanilla C08 block packets remain active");
        return false;
    }

    private static MethodInsnNode findHeldItemSyncCall(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if ("net/minecraft/client/multiplayer/PlayerControllerMP"
                    .equals(call.owner)
                    && "()V".equals(call.desc)
                    && ("syncCurrentPlayItem".equals(call.name)
                    || "func_78750_j".equals(call.name))) {
                return call;
            }
        }
        return null;
    }

    private static boolean isFieldNamed(
            FieldInsnNode field, String mcpName, String srgName) {
        if (mcpName.equals(field.name) || srgName.equals(field.name)) {
            return true;
        }
        String mapped = FMLDeobfuscatingRemapper.INSTANCE.mapFieldName(
                field.owner, field.name, field.desc);
        return mcpName.equals(mapped) || srgName.equals(mapped);
    }

    /** Whether the method calls the hook, straight or through its guard. */
    private static boolean containsHook(
            MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && HookGuard.calls((MethodInsnNode)instruction, owner, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A call to the hook {@code owner.name}; as the class is written it is
     * wrapped in one catch (DATA-08), and should the hook fail, the call
     * answers {@code fallback}.
     */
    private static MethodInsnNode hookCall(String owner, String name, String desc,
                                           HookFallback fallback) {
        MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name,
                desc, false);
        DECLARED.get().put(call, fallback);
        return call;
    }

    /**
     * Turns {@code call} into a call to the hook {@code owner.name}, which
     * takes the call's receiver and arguments first; should the hook fail,
     * the call is made as it was.
     */
    private static void redirect(MethodInsnNode call, String owner, String name,
                                 String desc) {
        HookFallback original = HookFallback.original(call);
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = owner;
        call.name = name;
        call.desc = desc;
        call.itf = false;
        DECLARED.get().put(call, original);
    }

    private static MethodInsnNode findLotrFastTravelTargetCall(
            MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "lotr/common/LOTRPlayerData".equals(call.owner)
                    && "setTargetFTWaypoint".equals(call.name)
                    && "(Llotr/common/world/map/LOTRAbstractWaypoint;)V"
                    .equals(call.desc)) {
                return call;
            }
            if (HookGuard.calls(call, FAST_TRAVEL_HOOK_OWNER, "setTargetIfAllowed")) {
                return call;
            }
        }
        return null;
    }

    private static int findLotrHandlerPlayerLocal(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!"net/minecraft/network/NetHandlerPlayServer"
                    .equals(field.owner)
                    || !("playerEntity".equals(field.name)
                    || "field_147369_b".equals(field.name))) {
                continue;
            }
            AbstractInsnNode store = nextCode(instruction);
            if (store instanceof VarInsnNode
                    && store.getOpcode() == Opcodes.ASTORE) {
                return ((VarInsnNode)store).var;
            }
        }
        return -1;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction == null ? null : instruction.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction == null ? null : instruction.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static void activateGuiAnimationPart(String property) {
        activate(property);
        if (Boolean.getBoolean(GUI_ANIMATION_DRAW_ACTIVE_PROPERTY)
                && Boolean.getBoolean(
                GUI_ANIMATION_INPUT_ACTIVE_PROPERTY)
                && Boolean.getBoolean(
                GUI_ANIMATION_BACKGROUND_ACTIVE_PROPERTY)) {
            activate(GUI_ANIMATION_ACTIVE_PROPERTY);
        }
    }

    private static void warn(String message) {
        try {
            FMLLog.warning("[losttales] %s", message);
        } catch (Throwable ignored) {
            // Very early bootstrap may not have initialized FML's logger.
        }
    }

    private static void info(String message) {
        try {
            FMLLog.info("[losttales] %s", message);
        } catch (Throwable ignored) {
            // Very early bootstrap may not have initialized FML's logger.
        }
    }
}
