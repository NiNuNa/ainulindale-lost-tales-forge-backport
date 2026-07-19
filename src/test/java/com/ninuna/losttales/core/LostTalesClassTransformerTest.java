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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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
    private static final String DEBUG_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterDebugHitboxHook";
    private static final String FAST_TRAVEL_HOOK_OWNER =
            "com/ninuna/losttales/world/map/waypoint/"
                    + "LostTalesWaypointFastTravelPolicy";
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
