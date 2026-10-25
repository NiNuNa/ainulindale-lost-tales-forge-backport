package com.ninuna.losttales.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every call the coremod injects into the real vanilla and LOTR classes
 * runs in one catch (DATA-08): no method of a patched class calls into
 * the mod but through a guard, every guard makes its hook's call inside a
 * catch that reports the fault, and every guard checks out as the JVM's
 * verifier would read it, its fallback's casts and types included.
 */
public final class LostTalesClassTransformerGuardTest {

    @Test
    public void everyCallIntoTheModGoesThroughAGuard() throws Exception {
        List<String> problems = new ArrayList<String>();
        int guards = 0;
        LostTalesClassTransformer transformer = new LostTalesClassTransformer();
        for (String target : LostTalesClassTransformerHooksTest.dispatchedTargets()) {
            byte[] original = LostTalesClassTransformerHooksTest.readResource(
                    target.replace('.', '/') + ".class");
            if (original == null) {
                problems.add("no class " + target + " on the classpath");
                continue;
            }
            ClassNode patched = read(transformer.transform(target, target, original));
            for (Object value : patched.methods) {
                MethodNode method = (MethodNode)value;
                if (HookGuard.isGuard(method.name)) {
                    guards++;
                    String problem = checkGuard(patched, method);
                    if (problem != null) {
                        problems.add(target + "." + method.name + ": " + problem);
                    }
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction instanceof MethodInsnNode && ((MethodInsnNode)instruction)
                            .owner.startsWith(HookGuard.MOD_PACKAGE)) {
                        problems.add(target + "." + method.name + " calls "
                                + ((MethodInsnNode)instruction).name + " unguarded");
                    }
                }
            }
        }
        assertEquals(new ArrayList<String>(), problems);
        assertTrue("the patches inject their hooks: " + guards, guards > 90);
    }

    /** A class written twice keeps the guards it had and gains none. */
    @Test
    public void aGuardedClassIsGuardedOnceWhateverRunsItTwice() throws Exception {
        LostTalesClassTransformer transformer = new LostTalesClassTransformer();
        for (String target : LostTalesClassTransformerHooksTest.dispatchedTargets()) {
            byte[] original = LostTalesClassTransformerHooksTest.readResource(
                    target.replace('.', '/') + ".class");
            byte[] once = transformer.transform(target, target, original);
            byte[] twice = transformer.transform(target, target, once);
            assertEquals(target, GuardedCalls.guards(read(once)).size(),
                    GuardedCalls.guards(read(twice)).size());
        }
    }

    /**
     * What is wrong with a guard: its hook's call must stand inside the
     * first catch, the catch must hand the fault to {@link HookFaults},
     * and the whole must verify.
     */
    private static String checkGuard(ClassNode owner, MethodNode guard) {
        if ((guard.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC))
                != (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) {
            return "not a private static synthetic method";
        }
        if (guard.tryCatchBlocks.size() != 2) {
            return guard.tryCatchBlocks.size() + " catches";
        }
        TryCatchBlockNode first = (TryCatchBlockNode)guard.tryCatchBlocks.get(0);
        MethodInsnNode hook = GuardedCalls.hookOf(guard);
        if (!within(guard, hook, first)) {
            return "the hook's call stands outside the catch";
        }
        if (!"java/lang/Throwable".equals(first.type)) {
            return "the catch takes " + first.type;
        }
        boolean reports = false;
        for (AbstractInsnNode instruction = first.handler; instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && "com/ninuna/losttales/core/HookFaults".equals(
                            ((MethodInsnNode)instruction).owner)) {
                reports = true;
                break;
            }
        }
        if (!reports) {
            return "the catch reports nothing";
        }
        try {
            List<Type> interfaces = new ArrayList<Type>();
            for (Object name : owner.interfaces) {
                interfaces.add(Type.getObjectType((String)name));
            }
            SimpleVerifier verifier = new SimpleVerifier(Type.getObjectType(owner.name),
                    owner.superName == null ? null : Type.getObjectType(owner.superName),
                    interfaces, (owner.access & Opcodes.ACC_INTERFACE) != 0);
            verifier.setClassLoader(LostTalesClassTransformerGuardTest.class.getClassLoader());
            new Analyzer<BasicValue>(verifier).analyze(owner.name, guard);
        } catch (AnalyzerException broken) {
            return "does not verify: " + broken.getMessage();
        }
        return null;
    }

    private static boolean within(MethodNode method, AbstractInsnNode instruction,
                                  TryCatchBlockNode block) {
        int at = method.instructions.indexOf(instruction);
        return at > method.instructions.indexOf(block.start)
                && at < method.instructions.indexOf(block.end);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
