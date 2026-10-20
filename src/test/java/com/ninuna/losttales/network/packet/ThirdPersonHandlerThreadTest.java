package com.ninuna.losttales.network.packet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A third-person request's handler runs on the network thread: it reads
 * the packet and queues a task, and never reads the player's world or
 * hand there. The task reads them on the server thread.
 */
public final class ThirdPersonHandlerThreadTest {
    /** The player's state the network thread must not read. */
    private static final Set<String> SERVER_THREAD_FIELDS = new HashSet<String>(
            Arrays.asList("worldObj", "inventory", "currentItem", "mainInventory"));
    private static final Set<String> SERVER_THREAD_CALLS = new HashSet<String>(
            Arrays.asList("getCurrentItem", "copyItemStack", "getHeldItem"));

    @Test
    public void theHandlersReadNothingOfThePlayer() throws IOException {
        for (Class<?> packet : new Class<?>[] {
                LostTalesThirdPersonAimPacket.class,
                LostTalesThirdPersonBlockActionPacket.class,
                LostTalesThirdPersonEntityActionPacket.class}) {
            MethodNode onMessage = onMessage(handlerOf(packet));
            boolean queued = false;
            for (AbstractInsnNode instruction = onMessage.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof FieldInsnNode) {
                    String field = ((FieldInsnNode)instruction).name;
                    assertFalse(packet.getSimpleName() + " reads " + field
                                    + " on the network thread",
                            SERVER_THREAD_FIELDS.contains(field));
                }
                if (instruction instanceof MethodInsnNode) {
                    String call = ((MethodInsnNode)instruction).name;
                    assertFalse(packet.getSimpleName() + " calls " + call
                                    + " on the network thread",
                            SERVER_THREAD_CALLS.contains(call));
                    queued |= "submit".equals(call);
                }
            }
            assertTrue(packet.getSimpleName() + " queues its task", queued);
        }
    }

    private static MethodNode onMessage(ClassNode handler) {
        for (Object value : handler.methods) {
            MethodNode method = (MethodNode)value;
            if ("onMessage".equals(method.name)
                    && (method.access & Opcodes.ACC_BRIDGE) == 0) {
                return method;
            }
        }
        throw new AssertionError("no onMessage in " + handler.name);
    }

    private static ClassNode handlerOf(Class<?> packet) throws IOException {
        String path = "/" + packet.getName().replace('.', '/') + "$Handler.class";
        InputStream stream = ThirdPersonHandlerThreadTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("missing " + path);
        }
        try {
            ClassNode handler = new ClassNode();
            new ClassReader(stream).accept(handler, 0);
            return handler;
        } finally {
            stream.close();
        }
    }
}
