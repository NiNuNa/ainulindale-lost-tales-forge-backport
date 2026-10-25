package com.ninuna.losttales.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A patched class as its patches wrote it: every call through a guard
 * ({@link HookGuard}) read as the call to the hook it guards, and the
 * guards left out. What a patch puts where is checked on this view; the
 * guards themselves are checked by {@code LostTalesClassTransformerGuardTest}.
 */
final class GuardedCalls {

    private GuardedCalls() {}

    /** Reads every guarded call in the class as its hook's, in place, and drops the guards. */
    static ClassNode unguard(ClassNode owner) {
        Map<String, MethodInsnNode> hooks = new HashMap<String, MethodInsnNode>();
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (HookGuard.isGuard(method.name)) {
                hooks.put(method.name + method.desc, hookOf(method));
            }
        }
        for (Iterator<?> methods = owner.methods.iterator(); methods.hasNext();) {
            MethodNode method = (MethodNode)methods.next();
            if (HookGuard.isGuard(method.name)) {
                methods.remove();
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode)instruction;
                MethodInsnNode hook = owner.name.equals(call.owner)
                        ? hooks.get(call.name + call.desc) : null;
                if (hook != null) {
                    call.owner = hook.owner;
                    call.name = hook.name;
                }
            }
        }
        return owner;
    }

    /** The guards a class holds, in order. */
    static List<MethodNode> guards(ClassNode owner) {
        List<MethodNode> guards = new ArrayList<MethodNode>();
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (HookGuard.isGuard(method.name)) {
                guards.add(method);
            }
        }
        return guards;
    }

    /** The hook call a guard makes inside its catch. */
    static MethodInsnNode hookOf(MethodNode guard) {
        for (AbstractInsnNode instruction = guard.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && instruction.getOpcode() == Opcodes.INVOKESTATIC
                    && ((MethodInsnNode)instruction).owner.startsWith(HookGuard.MOD_PACKAGE)) {
                return (MethodInsnNode)instruction;
            }
        }
        throw new AssertionError(guard.name + " calls no hook");
    }
}
