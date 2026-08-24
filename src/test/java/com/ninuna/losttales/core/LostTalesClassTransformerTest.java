package com.ninuna.losttales.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the supported LOTR v36.15 integration points against the local jar. */
public final class LostTalesClassTransformerTest {

    private static final String HOOK_OWNER =
            "com/ninuna/losttales/character/identity/"
                    + "RoleplayCharacterIdentityHook";
    private static final String CAMERA_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterCameraHook";
    private static final String THIRD_PERSON_CAMERA_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonCameraHooks";
    private static final String THIRD_PERSON_TARGETING_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonTargetingHooks";
    private static final String THIRD_PERSON_ENTITY_ACTION_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonEntityActionHooks";
    private static final String THIRD_PERSON_BLOCK_ACTION_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonBlockActionHooks";
    private static final String TOOLTIP_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/tooltip/LostTalesTooltipHooks";
    private static final String CHAT_HIT_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesChatHitHooks";
    private static final String CHAT_HISTORY_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesChatHistoryHooks";
    private static final String CHAT_WRAP_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesChatWrapHooks";
    private static final String FAST_TRAVEL_ARRIVAL_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/LostTalesLotrFastTravelArrivalHook";
    private static final String DEBUG_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterDebugHitboxHook";
    private static final String FAST_TRAVEL_HOOK_OWNER =
            "com/ninuna/losttales/world/map/waypoint/"
                    + "LostTalesWaypointFastTravelPolicy";
    private static final String NPC_CHAT_HOOK_OWNER =
            "com/ninuna/losttales/client/chat/LostTalesNpcChatHook";
    private static final String CLIENT_IDENTITY_HOOK_OWNER =
            "com/ninuna/losttales/client/character/"
                    + "ClientRoleplayCharacterIdentityHook";
    private static final String ACCESSORY_CONTAINER_CLASS =
            "com/ninuna/losttales/accessory/inventory/"
                    + "LostTalesContainerPlayer";
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
    private static final String LOTR_MAP_LABEL_STYLE_HOOK_OWNER =
            "com/ninuna/losttales/client/mapmarker/"
                    + "LostTalesLotrMapLabelStyle";
    private static final String GUI_ANIMATION_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/animation/"
                    + "LostTalesGuiAnimationHooks";
    private static final String SMOOTH_INVENTORY_HOOK_OWNER =
            "com/ninuna/losttales/client/gui/inventory/"
                    + "LostTalesSmoothInventoryHooks";

    @Test
    public void factionBountiesUseRoleplayCharacterUuid() throws Exception {
        ClassNode transformed = transform(
                "lotr.common.fac.LOTRFactionBounties");
        assertTrue(containsStaticHook(
                transformed, "forPlayer", HOOK_OWNER,
                "resolveGameplayId"));
    }

    @Test
    public void bountyLabelsResolveRoleplayCharacterName() throws Exception {
        ClassNode transformed = transform(
                "lotr.common.fac.LOTRFactionBounties$PlayerData");
        assertTrue(containsStaticHook(
                transformed, "findUsername", HOOK_OWNER,
                "resolveGameplayName"));
    }

    @Test
    public void lotrSpeechUsesRoleplayCharacterNameOnlyInFormatter()
            throws Exception {
        ClassNode transformed = transform(
                "lotr.common.entity.npc.LOTRSpeech");
        assertTrue(containsStaticHook(
                transformed, "formatSpeech", HOOK_OWNER,
                "resolveRoleplayName"));
    }

    @Test
    public void entityRendererUsesRaceCameraOffsetHook() throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.client.renderer.EntityRenderer");
        assertTrue(containsStaticHook(
                transformed, "orientCamera", CAMERA_HOOK_OWNER,
                "resolveCameraOffset"));
    }

    @Test
    public void entityRendererUsesAllThirdPersonCameraHooks()
            throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.client.renderer.EntityRenderer");

        assertTrue(containsStaticHook(
                transformed, "orientCamera",
                THIRD_PERSON_CAMERA_HOOK_OWNER, "resolveDistance"));
        assertTrue(containsStaticHook(
                transformed, "orientCamera",
                THIRD_PERSON_CAMERA_HOOK_OWNER, "applyCameraOffset"));
        assertTrue(containsStaticHook(
                transformed, "getFOVModifier",
                THIRD_PERSON_CAMERA_HOOK_OWNER, "resolveFov"));
        assertTrue(containsStaticHook(
                transformed, "getMouseOver",
                THIRD_PERSON_TARGETING_HOOK_OWNER,
                "resolveMouseOver"));
    }

    @Test
    public void guiAnimationTransformsDrawAndInputCoordinates()
            throws Exception {
        ClassNode renderer = transform(
                "net.minecraft.client.renderer.EntityRenderer");
        assertTrue(containsStaticHook(
                renderer, "updateCameraAndRender",
                GUI_ANIMATION_HOOK_OWNER, "drawScreen"));

        ClassNode screen = transform("net.minecraft.client.gui.GuiScreen");
        assertTrue(containsStaticHook(
                screen, "handleMouseInput",
                GUI_ANIMATION_HOOK_OWNER, "inverseMouseX"));
        assertTrue(containsStaticHook(
                screen, "handleMouseInput",
                GUI_ANIMATION_HOOK_OWNER, "inverseMouseY"));
        assertTrue(containsStaticHook(
                screen, "drawWorldBackground",
                GUI_ANIMATION_HOOK_OWNER,
                "beginVanillaBackground"));
        assertTrue(containsStaticHook(
                screen, "drawWorldBackground",
                GUI_ANIMATION_HOOK_OWNER,
                "endVanillaBackground"));
    }

    @Test
    public void chatHitTestRoutesThroughTheLostTalesLayout()
            throws Exception {
        ClassNode chat = transform("net.minecraft.client.gui.GuiNewChat");
        assertTrue(containsStaticHook(
                chat, "func_146236_a", CHAT_HIT_HOOK_OWNER, "isActive"));
        assertTrue(containsStaticHook(
                chat, "func_146236_a", CHAT_HIT_HOOK_OWNER, "componentAt"));
        // The check has to come first so vanilla's geometry is never
        // consulted while the Lost Tales chat screen is open.
        MethodNode method = findMethod(chat, "func_146236_a");
        assertTrue(firstCallIsHook(
                method, CHAT_HIT_HOOK_OWNER, "isActive"));
    }

    @Test
    public void chatLinesAreLaidOutByTheLostTalesWrapper() throws Exception {
        ClassNode chat = transform("net.minecraft.client.gui.GuiNewChat");
        MethodNode method = findMethod(chat, "func_146237_a");
        assertTrue(containsStaticHook(
                chat, "func_146237_a", CHAT_WRAP_HOOK_OWNER, "wrap"));
        // The replacement happens after vanilla has wrapped (so the
        // vanilla list is complete) and before getChatOpen() starts filing
        // lines into the history, and it stores back into the same local
        // the list was loaded from.
        boolean hookSeen = false;
        boolean storesBack = false;
        boolean openAfterHook = false;
        int loadedVar = -1;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (CHAT_WRAP_HOOK_OWNER.equals(call.owner)
                        && "wrap".equals(call.name)) {
                    hookSeen = true;
                    loadedVar = ((org.objectweb.asm.tree.VarInsnNode)
                            previousCode(call)).var;
                    org.objectweb.asm.tree.VarInsnNode store =
                            (org.objectweb.asm.tree.VarInsnNode)
                                    nextCode(call);
                    storesBack = store.getOpcode() == Opcodes.ASTORE
                            && store.var == loadedVar;
                } else if (hookSeen && "net/minecraft/client/gui/GuiNewChat"
                        .equals(call.owner)
                        && ("getChatOpen".equals(call.name)
                        || "func_146241_e".equals(call.name))) {
                    openAfterHook = true;
                }
            }
        }
        assertTrue(hookSeen);
        assertTrue(storesBack);
        assertTrue(openAfterHook);
    }

    @Test
    public void chatLineReplacementSkipsRefreshes() throws Exception {
        ClassNode chat = transform("net.minecraft.client.gui.GuiNewChat");
        assertTrue(containsStaticHook(chat, "func_146237_a",
                CHAT_HISTORY_HOOK_OWNER, "deleteUnlessRefreshing"));
        MethodNode method = findMethod(chat, "func_146237_a");
        // Vanilla's own call is gone, so nothing can delete a line the
        // refresh is about to file again.
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                assertFalse("deleteChatLine".equals(call.name)
                        && Opcodes.INVOKEVIRTUAL == call.getOpcode());
                assertFalse("func_146242_c".equals(call.name)
                        && Opcodes.INVOKEVIRTUAL == call.getOpcode());
            }
        }
    }

    @Test
    public void chatHistoryCapacityComesFromTheLostTalesHook()
            throws Exception {
        ClassNode chat = transform("net.minecraft.client.gui.GuiNewChat");
        MethodNode method = findMethod(chat, "func_146237_a");
        int hooks = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && CHAT_HISTORY_HOOK_OWNER.equals(
                            ((MethodInsnNode)instruction).owner)
                    && "capacity".equals(((MethodInsnNode)instruction).name)) {
                hooks++;
            }
            // Vanilla's literal hundred is gone from both trimming loops.
            assertFalse(instruction.getOpcode() == Opcodes.BIPUSH
                    && ((org.objectweb.asm.tree.IntInsnNode)instruction)
                            .operand == 100);
        }
        assertEquals(2, hooks);
    }

    @Test
    public void lotrFastTravelCompletionReportsArrivals() throws Exception {
        ClassNode data = transform("lotr.common.LOTRPlayerData");
        MethodNode method = findMethod(data, "receiveFTBouncePacket");
        assertTrue(containsStaticHook(data, "receiveFTBouncePacket",
                FAST_TRAVEL_ARRIVAL_HOOK_OWNER, "onArrived"));
        // The hook runs directly after the teleport, fed by a copy of the
        // receiver and waypoint taken just before it.
        boolean ordered = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if ("fastTravelTo".equals(call.name)) {
                AbstractInsnNode before = previousCode(call);
                AbstractInsnNode after = nextCode(call);
                ordered = before != null
                        && before.getOpcode() == Opcodes.DUP2
                        && after instanceof MethodInsnNode
                        && FAST_TRAVEL_ARRIVAL_HOOK_OWNER.equals(
                                ((MethodInsnNode)after).owner)
                        && "onArrived".equals(((MethodInsnNode)after).name);
            }
        }
        assertTrue(ordered);
    }

    @Test
    public void tooltipsOfferThemselvesToTheKeyIconRenderer()
            throws Exception {
        ClassNode screen = transform("net.minecraft.client.gui.GuiScreen");
        assertTrue(containsStaticHook(
                screen, "drawHoveringText",
                TOOLTIP_HOOK_OWNER, "drawHoveringText"));
        // The offer has to come before vanilla draws anything, or the tooltip
        // would be drawn twice, once in each layout.
        MethodNode method = findMethod(screen, "drawHoveringText");
        assertTrue(firstCallIsHook(
                method, TOOLTIP_HOOK_OWNER, "drawHoveringText"));
    }

    @Test
    public void guiContainerUsesSmoothInventoryRenderingHooks()
            throws Exception {
        ClassNode container = transform(
                "net.minecraft.client.gui.inventory.GuiContainer");
        assertTrue(containsStaticHook(
                container, "drawScreen",
                SMOOTH_INVENTORY_HOOK_OWNER, "beginFrame"));
        assertTrue(containsStaticHook(
                container, "handleMouseClick",
                SMOOTH_INVENTORY_HOOK_OWNER,
                "recordTransferIntent"));
        assertTrue(containsStaticHook(
                container, "func_146977_a",
                SMOOTH_INVENTORY_HOOK_OWNER,
                "renderItemAndEffectIntoGUI"));
        assertTrue(containsStaticHook(
                container, "func_146977_a",
                SMOOTH_INVENTORY_HOOK_OWNER,
                "renderItemOverlayIntoGUI"));
    }

    @Test
    public void playerControllerUsesAuthoritativeEntityActionHooks()
            throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.client.multiplayer.PlayerControllerMP");
        assertTrue(containsStaticHook(
                transformed, "attackEntity",
                THIRD_PERSON_ENTITY_ACTION_HOOK_OWNER,
                "shouldHandleEntityAttack"));
        assertTrue(containsStaticHook(
                transformed, "attackEntity",
                THIRD_PERSON_ENTITY_ACTION_HOOK_OWNER,
                "handleAttack"));
        assertTrue(containsStaticHook(
                transformed, "interactWithEntitySendPacket",
                THIRD_PERSON_ENTITY_ACTION_HOOK_OWNER,
                "shouldHandleEntityInteraction"));
        assertTrue(containsStaticHook(
                transformed, "interactWithEntitySendPacket",
                THIRD_PERSON_ENTITY_ACTION_HOOK_OWNER,
                "handleInteraction"));
    }

    @Test
    public void playerControllerUsesAuthoritativeBlockActionHook()
            throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.client.multiplayer.PlayerControllerMP");
        assertTrue(containsStaticHook(
                transformed, "onPlayerRightClick",
                THIRD_PERSON_BLOCK_ACTION_HOOK_OWNER,
                "sendBlockActionOrVanilla"));
    }

    @Test
    public void renderManagerUsesRaceDebugBoxHook() throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.client.renderer.entity.RenderManager");
        assertTrue(containsStaticHookAnywhere(
                transformed, DEBUG_HOOK_OWNER, "resolveRenderY"));
    }

    @Test
    public void fastTravelUsesCharacterDiscoveryPolicy() throws Exception {
        ClassNode transformed = transform(
                "lotr.common.network.LOTRPacketFastTravel$Handler");
        assertTrue(containsStaticHook(
                transformed, "onMessage", FAST_TRAVEL_HOOK_OWNER,
                "setTargetIfAllowed"));
    }

    @Test
    public void npcSpeechChatLinesAreRestyledOnTheClient()
            throws Exception {
        ClassNode transformed = transform(
                "lotr.common.network.LOTRPacketNPCSpeech$Handler");
        assertTrue(containsStaticHook(
                transformed, "onMessage", NPC_CHAT_HOOK_OWNER,
                "addNpcChatMessage"));
    }

    @Test
    public void playerDeathMessagesUseRoleplayCharacterNames()
            throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.entity.player.EntityPlayerMP");
        assertTrue(containsStaticHook(
                transformed, "onDeath", HOOK_OWNER,
                "resolveDeathMessage"));
    }

    @Test
    public void lotrMapTooltipsUseSynchronizedRoleplayName()
            throws Exception {
        ClassNode transformed = transform("lotr.client.gui.LOTRGuiMap");
        assertTrue(containsStaticHook(
                transformed, "renderPlayers",
                CLIENT_IDENTITY_HOOK_OWNER,
                "resolveMapPlayerName"));
        assertTrue(containsStaticHook(
                transformed, "renderPlayers",
                "com/ninuna/losttales/client/mapmarker/"
                        + "LostTalesLotrMapMarkerIconOverlay",
                "shouldSuppressNativePlayerRendering"));
    }

    /**
     * Everything LOTR draws on the map is positioned through this one method,
     * so it is the single place the map's rotation is applied. If it moves or
     * changes shape the map would silently stop turning, taking marker
     * placement and hit testing out of step with what is drawn.
     */
    @Test
    public void lostTalesMapRotationPatchesLotrsOwnMapSpace()
            throws Exception {
        ClassNode transformed = transform("lotr.client.gui.LOTRGuiMap");
        assertTrue(containsStaticHook(
                transformed, "transformMapCoords",
                "com/ninuna/losttales/client/mapmarker/"
                        + "LostTalesLotrMapRotation",
                "rotate"));
        // The region names are painted on the sheet, so they turn and lean
        // with it; that needs this one pass bracketed.
        assertTrue(containsStaticHook(
                transformed, "renderLabels",
                "com/ninuna/losttales/client/mapmarker/"
                        + "LostTalesLotrMapRotation",
                "beginSheetPass"));
        assertTrue(containsStaticHook(
                transformed, "renderLabels",
                "com/ninuna/losttales/client/mapmarker/"
                        + "LostTalesLotrMapRotation",
                "endSheetPass"));
        // The compass rose is moved off the control strip and turned with
        // the map, by redirecting the one call that draws it.
        assertTrue(containsStaticHook(
                transformed, "drawScreen",
                "com/ninuna/losttales/client/mapmarker/"
                        + "LostTalesLotrMapCompass",
                "drawMapCompass"));
    }

    @Test
    public void clippedLotrMapPreviewsUseOceanPadding() throws Exception {
        ClassNode transformed = transform("lotr.client.gui.LOTRGuiMap");
        assertTrue(containsStaticHook(
                transformed, "renderMapAndOverlay",
                LOTR_MAP_EDGE_FILL_HOOK_OWNER,
                "fillClippedMapBackground"));
    }

    @Test
    public void lostTalesMapUsesResponsiveFullscreenLayoutHooks()
            throws Exception {
        ClassNode transformed = transform("lotr.client.gui.LOTRGuiMap");
        assertTrue(containsStaticHook(
                transformed, "setupMapDimensions",
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "applyFullscreenBounds"));
        assertTrue(containsStaticHook(
                transformed, "renderGraduatedRects",
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "shouldSuppressMapFrame"));
        assertTrue(containsStaticHook(
                transformed, "renderFullscreenSubtitles",
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "beginFullscreenSubtitles"));
        assertTrue(containsStaticHook(
                transformed, "renderFullscreenSubtitles",
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "endFullscreenSubtitles"));
        assertTrue(containsStaticHook(
                transformed, "renderFullscreenSubtitles",
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "filterFullscreenSubtitles"));
        assertTrue(containsStaticHook(
                transformed, "renderFullscreenSubtitles",
                LOTR_MAP_LABEL_STYLE_HOOK_OWNER,
                "restyleMapSubtitle"));
        assertTrue(containsStaticHook(
                transformed, "renderWaypointTooltip",
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "beginMapTooltip"));
        assertTrue(containsStaticHook(
                transformed, "renderWaypointTooltip",
                LOTR_MAP_LAYOUT_HOOK_OWNER,
                "endMapTooltip"));
        assertTrue(containsStaticHook(
                transformed, "renderMiniQuests",
                LOTR_MAP_LEGEND_HOOK_OWNER,
                "shouldRenderLotrMiniQuests"));
    }

    @Test
    public void entityPlayerUsesAccessoryAwareContainer() throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.entity.player.EntityPlayer");
        assertTrue(containsConstructedType(
                transformed, ACCESSORY_CONTAINER_CLASS));
    }

    @Test
    public void entityPlayerCapturesAccessoryBeforePlayerDropsEvent()
            throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.entity.player.EntityPlayer");
        assertTrue(containsStaticHook(
                transformed, "onDeath", ACCESSORY_DEATH_HOOK_OWNER,
                "captureAccessoryDrop"));
        MethodNode death = findMethod(transformed, "onDeath");
        int hookIndex = -1;
        int captureCloseIndex = -1;
        int dropsEventIndex = -1;
        int index = 0;
        for (AbstractInsnNode instruction = death.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext(), index++) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (ACCESSORY_DEATH_HOOK_OWNER.equals(call.owner)
                        && "captureAccessoryDrop".equals(call.name)) {
                    hookIndex = index;
                }
            } else if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                if (field.getOpcode() == Opcodes.PUTFIELD
                        && "captureDrops".equals(field.name)) {
                    AbstractInsnNode previous = previousCode(field);
                    if (previous != null
                            && previous.getOpcode() == Opcodes.ICONST_0) {
                        captureCloseIndex = index;
                    }
                }
            } else if (instruction instanceof TypeInsnNode
                    && instruction.getOpcode() == Opcodes.NEW
                    && "net/minecraftforge/event/entity/player/PlayerDropsEvent"
                    .equals(((TypeInsnNode)instruction).desc)) {
                dropsEventIndex = index;
            }
        }
        assertTrue(hookIndex >= 0 && hookIndex < captureCloseIndex);
        assertTrue(captureCloseIndex < dropsEventIndex);
    }

    @Test
    public void concealedPlayerIsNotRayCollidable() throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.entity.EntityLivingBase");
        assertTrue(containsStaticHook(
                transformed, "canBeCollidedWith",
                ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed"));
    }

    @Test
    public void concealedPlayerUsesVanillaInvisibilityPath()
            throws Exception {
        ClassNode transformed = transform("net.minecraft.entity.Entity");
        assertTrue(containsStaticHook(
                transformed, "isInvisible",
                ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed"));
    }

    @Test
    public void vanillaAndLotrAiRejectConcealedTargets() throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.entity.ai.EntityAITarget");
        assertTrue(containsStaticHookAnywhere(
                transformed, ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed"));
    }

    @Test
    public void livingSightRejectsConcealedPlayers() throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.entity.EntityLivingBase");
        assertTrue(containsStaticHook(
                transformed, "canEntityBeSeen",
                ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed"));
    }

    @Test
    public void lotrMapOmitsConcealedPlayerLocations() throws Exception {
        ClassNode transformed = transform("lotr.common.LOTRLevelData");
        assertTrue(containsStaticHook(
                transformed, "sendPlayerLocationsToPlayer",
                ACCESSORY_LOTR_MAP_HOOK_OWNER,
                "addPlayerLocationIfVisible"));
    }

    @Test
    public void minecraftPickBlockPreservesVanillaHotbarIds()
            throws Exception {
        ClassNode transformed = transform("net.minecraft.client.Minecraft");
        assertTrue(containsStaticHookAnywhere(
                transformed,
                ACCESSORY_CONTAINER_HOOK_OWNER,
                "resolveVanillaInventorySlotCount"));
        MethodNode method = findMethod(transformed, "func_147112_ai");
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (ACCESSORY_CONTAINER_HOOK_OWNER.equals(call.owner)
                        && "resolveVanillaInventorySlotCount".equals(
                        call.name)) {
                    assertFalse(call.itf);
                    return;
                }
            }
        }
        throw new AssertionError("Missing accessory pick-block hook");
    }

    @Test
    public void creativeAccessoryWritesUseServerValidator()
            throws Exception {
        ClassNode transformed = transform(
                "net.minecraft.network.NetHandlerPlayServer");
        assertTrue(containsStaticHook(
                transformed, "processCreativeInventoryAction",
                ACCESSORY_CREATIVE_HOOK_OWNER, "handle"));
    }

    private static ClassNode transform(String binaryName) throws IOException {
        byte[] original = readResource(binaryName.replace('.', '/') + ".class");
        byte[] bytes = new LostTalesClassTransformer().transform(
                binaryName, binaryName, original);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static boolean containsStaticHook(
            ClassNode owner, String methodName,
            String hookOwner, String hookName) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (!methodName.equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                        && hookOwner.equals(call.owner)
                        && hookName.equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsStaticHookAnywhere(
            ClassNode owner, String hookOwner, String hookName) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (containsStaticHook(
                    owner, method.name, hookOwner, hookName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsConstructedType(
            ClassNode owner, String internalName) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof TypeInsnNode
                        && instruction.getOpcode() == Opcodes.NEW
                        && internalName.equals(
                        ((TypeInsnNode)instruction).desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when the first call the method makes is that hook. */
    private static boolean firstCallIsHook(
            MethodNode method, String hookOwner, String hookName) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            return call.getOpcode() == Opcodes.INVOKESTATIC
                    && hookOwner.equals(call.owner)
                    && hookName.equals(call.name);
        }
        return false;
    }

    private static MethodNode findMethod(ClassNode owner, String name) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (name.equals(method.name)) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + name);
    }

    private static AbstractInsnNode previousCode(
            AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction == null
                ? null : instruction.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction == null
                ? null : instruction.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static byte[] readResource(String path) throws IOException {
        InputStream input = LostTalesClassTransformerTest.class
                .getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing test class resource " + path);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
