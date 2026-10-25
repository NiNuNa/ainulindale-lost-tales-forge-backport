package com.ninuna.losttales.core;

import java.awt.Point;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The one catch every injected call runs in (DATA-08), on a class made
 * here and run: a hook that works answers itself, one that fails answers
 * what its patch declared the game would have had, the fault is reported
 * once, and a call that declares nothing, or nothing that fits, fails
 * the patch. The class is written at Java 7's version, so the JVM checks
 * each guard's frames itself.
 */
public final class HookGuardTest {

    private static final String HOOKS = "com/ninuna/losttales/core/GuardTestHooks";
    private static final String PATCHED = "guardtest/Patched";

    private final Map<AbstractInsnNode, HookFallback> declared =
            new IdentityHashMap<AbstractInsnNode, HookFallback>();
    private final ClassNode patched = new ClassNode();

    @Before
    public void setUp() {
        GuardTestHooks.failure = null;
        HookFaults.forget();
        this.patched.version = Opcodes.V1_7;
        this.patched.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        this.patched.name = PATCHED;
        this.patched.superName = "java/lang/Object";
    }

    @After
    public void tearDown() {
        GuardTestHooks.failure = null;
        HookFaults.forget();
    }

    @Test
    public void aHookThatWorksAnswersItself() throws Exception {
        call("answer", "answer", "()Z", HookFallback.FALSE);
        call("scale", "scale", "(F)F", HookFallback.argument(0));
        call("limit", "limit", "()I", HookFallback.constant(Integer.valueOf(100)));
        call("length", "length", "(Ljava/lang/CharSequence;)I",
                HookFallback.original(virtual("java/lang/String", "length", "()I")));
        Class<?> type = load();
        assertEquals(Boolean.TRUE, invoke(type, "answer"));
        assertEquals(Float.valueOf(6F), invoke(type, "scale", Float.valueOf(3F)));
        assertEquals(Integer.valueOf(7), invoke(type, "limit"));
        assertEquals(Integer.valueOf(-1), invoke(type, "length", "abcd"));
        assertFalse(HookFaults.hasFailed(PATCHED.replace('/', '.') + " -> GuardTestHooks.answer"));
    }

    @Test
    public void aHookThatFailsAnswersWhatTheGameWouldHave() throws Exception {
        call("nothing", "nothing", "()V", HookFallback.NOTHING);
        call("no", "answer", "()Z", HookFallback.FALSE);
        call("yes", "answer", "()Z", HookFallback.TRUE);
        call("object", "object", "()Ljava/lang/Object;", HookFallback.NULL);
        call("scale", "scale", "(F)F", HookFallback.argument(0));
        call("wide", "wide", "(IDJ)D", HookFallback.argument(1));
        call("limit", "limit", "()I", HookFallback.constant(Integer.valueOf(100)));
        call("name", "name", "()Ljava/lang/String;", HookFallback.constant(""));
        Class<?> type = load();
        GuardTestHooks.failure = new IllegalStateException("a fault of the hook's");
        assertNull(invoke(type, "nothing"));
        assertEquals(Boolean.FALSE, invoke(type, "no"));
        assertEquals(Boolean.TRUE, invoke(type, "yes"));
        assertNull(invoke(type, "object"));
        assertEquals(Float.valueOf(3F), invoke(type, "scale", Float.valueOf(3F)));
        assertEquals(Double.valueOf(2.5D), invoke(type, "wide",
                Integer.valueOf(1), Double.valueOf(2.5D), Long.valueOf(9L)));
        assertEquals(Integer.valueOf(100), invoke(type, "limit"));
        assertEquals("", invoke(type, "name"));
    }

    /** A hook that took a call's place makes the call as it was. */
    @Test
    public void theReplacedCallIsMadeAgain() throws Exception {
        call("length", "length", "(Ljava/lang/CharSequence;)I",
                HookFallback.original(virtual("java/lang/String", "length", "()I")));
        call("size", "size", "(Ljava/util/List;)I", HookFallback.original(
                new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "size",
                        "()I", true)));
        call("larger", "larger", "(IIZ)I", HookFallback.original(new MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)));
        call("add", "add", "(Ljava/util/List;Ljava/lang/Object;)V", HookFallback.original(
                new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "add",
                        "(Ljava/lang/Object;)Z", true)));
        call("across", "across", "(Ljava/awt/Point;)I", HookFallback.original(
                new FieldInsnNode(Opcodes.GETFIELD, "java/awt/Point", "x", "I")));
        call("build", "build", "(Ljava/lang/String;)Ljava/lang/Object;",
                HookFallback.construct("java/lang/StringBuilder", "(Ljava/lang/String;)V"));
        Class<?> type = load();
        GuardTestHooks.failure = new IllegalArgumentException("a fault of the hook's");
        assertEquals(Integer.valueOf(4), invoke(type, "length", "abcd"));
        assertEquals(Integer.valueOf(3), invoke(type, "size", Arrays.asList("a", "b", "c")));
        assertEquals(Integer.valueOf(9), invoke(type, "larger",
                Integer.valueOf(9), Integer.valueOf(4), Boolean.TRUE));
        List<Object> list = new ArrayList<Object>();
        assertNull(invoke(type, "add", list, "kept"));
        assertEquals(Arrays.<Object>asList("kept"), list);
        assertEquals(Integer.valueOf(5), invoke(type, "across", new Point(5, 6)));
        assertEquals("built", invoke(type, "build", "built").toString());
    }

    /** An error is a fault like any other: a class that cannot load, a missing method. */
    @Test
    public void anErrorIsCaughtToo() throws Exception {
        call("nothing", "nothing", "()V", HookFallback.NOTHING);
        call("answer", "answer", "()Z", HookFallback.FALSE);
        Class<?> type = load();
        GuardTestHooks.failure = new NoClassDefFoundError("a class the hook needs");
        assertNull(invoke(type, "nothing"));
        assertEquals(Boolean.FALSE, invoke(type, "answer"));
    }

    @Test
    public void aFaultIsReportedOncePerHook() throws Exception {
        call("answer", "answer", "()Z", HookFallback.FALSE);
        call("nothing", "nothing", "()V", HookFallback.NOTHING);
        Class<?> type = load();
        GuardTestHooks.failure = new IllegalStateException("a fault of the hook's");
        invoke(type, "answer");
        invoke(type, "answer");
        assertTrue(HookFaults.hasFailed("guardtest.Patched -> GuardTestHooks.answer"));
        assertFalse(HookFaults.hasFailed("guardtest.Patched -> GuardTestHooks.nothing"));
    }

    /** Calls to one hook that fall back the same way share one guard. */
    @Test
    public void aGuardIsSharedByCallsThatFallBackAlike() {
        call("first", "answer", "()Z", HookFallback.FALSE);
        call("second", "answer", "()Z", HookFallback.FALSE);
        call("third", "answer", "()Z", HookFallback.TRUE);
        HookGuard.guard(this.patched, this.declared);
        assertEquals(2, GuardedCalls.guards(this.patched).size());
        for (MethodNode guard : GuardedCalls.guards(this.patched)) {
            assertTrue(guard.name.startsWith("losttales$hook$GuardTestHooks$answer$"));
            assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    guard.access);
        }
    }

    /** A class guarded already is left as it is, and its calls still read as the hook's. */
    @Test
    public void aClassGuardedTwiceIsGuardedOnce() {
        MethodInsnNode call = call("answer", "answer", "()Z", HookFallback.FALSE);
        HookGuard.guard(this.patched, this.declared);
        int methods = this.patched.methods.size();
        HookGuard.guard(this.patched, new IdentityHashMap<AbstractInsnNode, HookFallback>());
        assertEquals(methods, this.patched.methods.size());
        assertEquals(PATCHED, call.owner);
        assertTrue(HookGuard.calls(call, HOOKS, "answer"));
        assertFalse(HookGuard.calls(call, HOOKS, "nothing"));
    }

    @Test
    public void aCallThatDeclaresNoFallbackFailsThePatch() {
        addCaller("answer", "answer", "()Z");
        refused();
    }

    @Test
    public void aFallbackThatDoesNotFitFailsThePatch() {
        call("answer", "answer", "()Z", HookFallback.NOTHING);
        refused();
        reset();
        call("limit", "limit", "()I", HookFallback.argument(0));
        refused();
        reset();
        call("wide", "wide", "(IDJ)D", HookFallback.argument(0));
        refused();
        reset();
        call("limit", "limit", "()I", HookFallback.constant(""));
        refused();
        reset();
        call("limit", "limit", "()I",
                HookFallback.original(virtual("java/lang/String", "length", "()I")));
        refused();
        reset();
        call("nothing", "nothing", "()V", HookFallback.TRUE);
        refused();
    }

    /** Only a static call reaches the mod: anything else there could fail outside a guard. */
    @Test
    public void onlyAStaticCallReachesTheMod() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "build", "()V", null, null);
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, HOOKS));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        this.patched.methods.add(method);
        refused();
        reset();
        MethodNode virtual = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "virtual", "(Ljava/lang/Object;)V", null, null);
        virtual.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HOOKS, "toString",
                "()Ljava/lang/String;", false);
        virtual.instructions.add(call);
        virtual.instructions.add(new InsnNode(Opcodes.POP));
        virtual.instructions.add(new InsnNode(Opcodes.RETURN));
        this.patched.methods.add(virtual);
        this.declared.put(call, HookFallback.NULL);
        refused();
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPrivateOrSuperCallCannotBeMadeAgain() {
        HookFallback.original(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object",
                "toString", "()Ljava/lang/String;", false));
    }

    private MethodInsnNode call(String name, String hook, String desc, HookFallback fallback) {
        MethodInsnNode call = addCaller(name, hook, desc);
        this.declared.put(call, fallback);
        return call;
    }

    /** A static method of the made class that hands its arguments to the hook and answers it. */
    private MethodInsnNode addCaller(String name, String hook, String desc) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, desc, null, null);
        int slot = 0;
        for (Type argument : Type.getArgumentTypes(desc)) {
            method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
            slot += argument.getSize();
        }
        MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, desc, false);
        method.instructions.add(call);
        method.instructions.add(new InsnNode(
                Type.getReturnType(desc).getOpcode(Opcodes.IRETURN)));
        this.patched.methods.add(method);
        return call;
    }

    private static MethodInsnNode virtual(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, false);
    }

    private void refused() {
        try {
            HookGuard.guard(this.patched, this.declared);
            fail("the patch should fail");
        } catch (IllegalStateException expected) {
            // The transformer then keeps the class as it was.
        }
    }

    private void reset() {
        this.patched.methods.clear();
        this.declared.clear();
    }

    private Class<?> load() {
        HookGuard.guard(this.patched, this.declared);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.patched.accept(writer);
        return new Loader().define(PATCHED.replace('/', '.'), writer.toByteArray());
    }

    private static Object invoke(Class<?> type, String name, Object... arguments)
            throws Exception {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                try {
                    return method.invoke(null, arguments);
                } catch (InvocationTargetException fault) {
                    throw new AssertionError("the guard let a fault through: "
                            + fault.getCause());
                }
            }
        }
        throw new AssertionError("no method " + name);
    }

    /** Defines the made class beside the test's own, so it sees the hooks. */
    private static final class Loader extends ClassLoader {
        Loader() {
            super(HookGuardTest.class.getClassLoader());
        }

        Class<?> define(String name, byte[] bytes) {
            Class<?> type = defineClass(name, bytes, 0, bytes.length);
            resolveClass(type);
            return type;
        }
    }
}
