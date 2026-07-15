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

    @Test
    public void factionBountiesUseRoleplayCharacterUuid() throws Exception {
        ClassNode transformed = transform(
                "lotr.common.fac.LOTRFactionBounties");
        assertTrue(containsStaticHook(
                transformed, "forPlayer", "resolveGameplayId"));
    }

    @Test
    public void bountyLabelsResolveRoleplayCharacterName() throws Exception {
        ClassNode transformed = transform(
                "lotr.common.fac.LOTRFactionBounties$PlayerData");
        assertTrue(containsStaticHook(
                transformed, "findUsername", "resolveGameplayName"));
    }

    @Test
    public void lotrSpeechUsesRoleplayCharacterNameOnlyInFormatter()
            throws Exception {
        ClassNode transformed = transform(
                "lotr.common.entity.npc.LOTRSpeech");
        assertTrue(containsStaticHook(
                transformed, "formatSpeech", "resolveRoleplayName"));
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
            ClassNode owner, String methodName, String hookName) {
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
                        && HOOK_OWNER.equals(call.owner)
                        && hookName.equals(call.name)) {
                    return true;
                }
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
