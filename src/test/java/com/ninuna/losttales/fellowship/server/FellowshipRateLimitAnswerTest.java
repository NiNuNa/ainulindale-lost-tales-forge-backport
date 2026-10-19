package com.ninuna.losttales.fellowship.server;

import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The fellowship door answers a request refused for its rate only as the
 * character door does: when the limiter allows an answer, so a flood of
 * requests past the limit is never a flood of answers.
 */
public final class FellowshipRateLimitAnswerTest {
    private static final String LIMITER =
            "com/ninuna/losttales/network/server/LostTalesRequestRateLimiter";
    private static final String SYNC =
            "com/ninuna/losttales/fellowship/server/FellowshipSyncManager";

    @Test
    public void theRateLimitedAnswerWaitsOnTheLimitersLeave() throws IOException {
        MethodNode submit = method(readClass(), "submit");
        assertNotNull("the dispatcher submits", submit);
        boolean leaveAsked = false;
        boolean answeredAfterLeave = false;
        for (AbstractInsnNode instruction = submit.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (LIMITER.equals(call.owner) && "allowReply".equals(call.name)) {
                    leaveAsked = true;
                    // The answer is behind the leave's branch.
                    assertTrue(call.getNext() instanceof JumpInsnNode);
                }
            }
            if (instruction instanceof FieldInsnNode
                    && "RATE_LIMITED".equals(((FieldInsnNode)instruction).name)) {
                assertTrue("a rate-limited answer is sent only once the limiter allows it",
                        leaveAsked);
                for (AbstractInsnNode next = instruction; next != null; next = next.getNext()) {
                    if (next instanceof MethodInsnNode
                            && SYNC.equals(((MethodInsnNode)next).owner)
                            && "sendFailure".equals(((MethodInsnNode)next).name)) {
                        answeredAfterLeave = true;
                        break;
                    }
                }
            }
        }
        assertTrue(leaveAsked);
        assertTrue(answeredAfterLeave);
    }

    private static MethodNode method(ClassNode owner, String name) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (name.equals(method.name)) {
                return method;
            }
        }
        return null;
    }

    private static ClassNode readClass() throws IOException {
        String path = "/com/ninuna/losttales/fellowship/server/"
                + "FellowshipServerPacketDispatcher.class";
        InputStream stream = FellowshipRateLimitAnswerTest.class.getResourceAsStream(path);
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
