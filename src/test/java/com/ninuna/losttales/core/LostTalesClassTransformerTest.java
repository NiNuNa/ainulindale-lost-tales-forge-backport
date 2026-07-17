package com.ninuna.losttales.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
    private static final String DEBUG_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterDebugHitboxHook";
    private static final String FAST_TRAVEL_HOOK_OWNER =
            "com/ninuna/losttales/world/map/waypoint/"
                    + "LostTalesWaypointFastTravelPolicy";
    private static final String CLIENT_IDENTITY_HOOK_OWNER =
            "com/ninuna/losttales/client/character/"
                    + "ClientRoleplayCharacterIdentityHook";

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
