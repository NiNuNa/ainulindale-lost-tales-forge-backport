package com.ninuna.losttales.network.server;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Prevents MCP item-use calls from leaking into runtime bytecode. */
public final class LostTalesChargeServiceBytecodeTest {

    private static final String ENTITY_PLAYER =
            "net/minecraft/entity/player/EntityPlayer";
    private static final String ENTITY_PLAYER_MP =
            "net/minecraft/entity/player/EntityPlayerMP";
    private static final String ITEM_USE_ACCESS =
            "com/ninuna/losttales/compat/minecraft/PlayerItemUseAccess";

    @Test
    public void itemUseMethodsAreNeverCalledDirectly()
            throws IOException {
        ClassNode owner = readClass();
        boolean foundAccessor = false;
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode)instruction;
                boolean itemUseMethod = "getItemInUse".equals(call.name)
                        || "getItemInUseCount".equals(call.name)
                        || "func_71011_bu".equals(call.name)
                        || "func_71052_bv".equals(call.name);
                boolean vanillaOwner = ENTITY_PLAYER.equals(call.owner)
                        || ENTITY_PLAYER_MP.equals(call.owner);
                assertFalse("item-use calls must use the mapping-safe accessor",
                        itemUseMethod && vanillaOwner);
                foundAccessor |= itemUseMethod
                        && ITEM_USE_ACCESS.equals(call.owner);
            }
        }
        assertTrue("charge service must use mapping-safe item-use access",
                foundAccessor);
    }

    private static ClassNode readClass() throws IOException {
        String path = "/com/ninuna/losttales/network/server/"
                + "LostTalesChargeService.class";
        InputStream stream = LostTalesChargeServiceBytecodeTest.class
                .getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("missing " + path);
        }
        try {
            ClassNode owner = new ClassNode();
            new ClassReader(stream).accept(owner, 0);
            return owner;
        } finally {
            stream.close();
        }
    }
}
