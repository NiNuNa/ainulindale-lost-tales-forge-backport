package com.ninuna.losttales.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Wraps every call a patch injects into vanilla or LOTR in one catch
 * (DATA-08). As a patched class is written, each call into the mod is
 * sent through a small method added to the class, its guard, which
 * makes the call inside a catch; a fault goes to {@link HookFaults} and
 * the guard answers what the patch declared the game would have had
 * without it ({@link HookFallback}). The call keeps its place and its
 * descriptor, so the code around it is as the patch wrote it.
 *
 * <p>A call into the mod that declares no fallback, or that is not a
 * static call, fails the patch, which then leaves the class as it was:
 * no hook reaches vanilla's or LOTR's code unguarded.</p>
 */
final class HookGuard {

    /** Where the mod's own classes live; every call there is a hook. */
    static final String MOD_PACKAGE = "com/ninuna/losttales/";
    /** What every guard's name begins with: the hook's class, its name, a number. */
    static final String GUARD_PREFIX = "losttales$hook$";
    private static final String FAULTS = "com/ninuna/losttales/core/HookFaults";
    private static final String FAULTS_DESC = "(Ljava/lang/String;Ljava/lang/Throwable;)V";
    private static final String THROWABLE = "java/lang/Throwable";

    private HookGuard() {}

    /**
     * Sends every call into the mod that the class's methods make through
     * a guard, reusing one where the same hook falls back the same way.
     * {@code declared} holds each injected call's fallback.
     */
    static void guard(ClassNode owner, Map<AbstractInsnNode, HookFallback> declared) {
        List<MethodNode> methods = new ArrayList<MethodNode>();
        for (Object value : owner.methods) {
            methods.add((MethodNode)value);
        }
        Map<String, MethodNode> guards = new HashMap<String, MethodNode>();
        for (MethodNode method : methods) {
            if (isGuard(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                refuseOtherReferences(owner, method, instruction);
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (!call.owner.startsWith(MOD_PACKAGE)) {
                    continue;
                }
                if (call.getOpcode() != Opcodes.INVOKESTATIC) {
                    throw new IllegalStateException(where(owner, method, call)
                            + " is not a static hook call");
                }
                HookFallback fallback = declared == null ? null : declared.get(call);
                if (fallback == null) {
                    throw new IllegalStateException(where(owner, method, call)
                            + " declares no fallback");
                }
                if ((owner.access & Opcodes.ACC_INTERFACE) != 0) {
                    throw new IllegalStateException(where(owner, method, call)
                            + " stands in an interface, which takes no guard");
                }
                String key = call.owner + '.' + call.name + call.desc + '|' + fallback.key();
                MethodNode guard = guards.get(key);
                if (guard == null) {
                    guard = newGuard(owner, call, fallback);
                    guards.put(key, guard);
                }
                call.owner = owner.name;
                call.name = guard.name;
                call.itf = false;
            }
        }
    }

    /**
     * Whether a call is to the hook {@code hookOwner.hookName}, made
     * straight or through the guard of a class already written.
     */
    static boolean calls(MethodInsnNode call, String hookOwner, String hookName) {
        if (call == null || call.getOpcode() != Opcodes.INVOKESTATIC) {
            return false;
        }
        return hookOwner.equals(call.owner) && hookName.equals(call.name)
                || call.name.startsWith(guardPrefix(hookOwner, hookName));
    }

    /** Whether a method is a guard this class added. */
    static boolean isGuard(String methodName) {
        return methodName != null && methodName.startsWith(GUARD_PREFIX);
    }

    /** The name every guard of that hook begins with. */
    static String guardPrefix(String hookOwner, String hookName) {
        return GUARD_PREFIX + simpleName(hookOwner) + '$' + hookName + '$';
    }

    /** What the fault log calls the hook a guard wraps in a class. */
    static String hookName(String className, String hookOwner, String hookName) {
        return className.replace('/', '.') + " -> " + simpleName(hookOwner) + '.' + hookName;
    }

    /**
     * Only calls reach into the mod: a field the patch reads there, or a
     * type it builds or casts to, could fail outside any guard.
     */
    private static void refuseOtherReferences(ClassNode owner, MethodNode method,
                                              AbstractInsnNode instruction) {
        String reference = null;
        if (instruction instanceof FieldInsnNode) {
            reference = ((FieldInsnNode)instruction).owner;
        } else if (instruction instanceof TypeInsnNode) {
            reference = ((TypeInsnNode)instruction).desc;
        } else if (instruction instanceof LdcInsnNode
                && ((LdcInsnNode)instruction).cst instanceof Type) {
            reference = ((Type)((LdcInsnNode)instruction).cst).getInternalName();
        }
        if (reference != null && reference.replace("[", "").replaceFirst("^L", "")
                .startsWith(MOD_PACKAGE)) {
            throw new IllegalStateException(owner.name + "." + method.name
                    + " names " + reference + " outside a hook call");
        }
    }

    private static MethodNode newGuard(ClassNode owner, MethodInsnNode call,
                                       HookFallback fallback) {
        Type[] arguments = Type.getArgumentTypes(call.desc);
        Type result = Type.getReturnType(call.desc);
        MethodNode guard = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                freeName(owner, guardPrefix(call.owner, call.name)), call.desc, null, null);
        InsnList code = guard.instructions;
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode reportStart = new LabelNode();
        LabelNode reportEnd = new LabelNode();
        LabelNode answer = new LabelNode();
        LabelNode reportFailed = new LabelNode();
        Object[] locals = frameTypes(arguments, null);
        Object[] localsWithFault = frameTypes(arguments, THROWABLE);
        int faultSlot = slotOf(arguments, arguments.length);

        code.add(start);
        load(code, arguments, arguments.length, null);
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, call.owner, call.name,
                call.desc, false));
        code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
        code.add(end);

        code.add(handler);
        code.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 1,
                new Object[] {THROWABLE}));
        code.add(new VarInsnNode(Opcodes.ASTORE, faultSlot));
        code.add(reportStart);
        code.add(new LdcInsnNode(hookName(owner.name, call.owner, call.name)));
        code.add(new VarInsnNode(Opcodes.ALOAD, faultSlot));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FAULTS, "caught",
                FAULTS_DESC, false));
        code.add(reportEnd);
        code.add(answer);
        code.add(new FrameNode(Opcodes.F_FULL, localsWithFault.length, localsWithFault,
                0, new Object[0]));
        answer(code, fallback, arguments, result, call);

        // A report that cannot be made still answers.
        code.add(reportFailed);
        code.add(new FrameNode(Opcodes.F_FULL, localsWithFault.length, localsWithFault,
                1, new Object[] {THROWABLE}));
        code.add(new InsnNode(Opcodes.POP));
        code.add(new JumpInsnNode(Opcodes.GOTO, answer));

        guard.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, THROWABLE));
        guard.tryCatchBlocks.add(new TryCatchBlockNode(reportStart, reportEnd,
                reportFailed, THROWABLE));
        owner.methods.add(guard);
        return guard;
    }

    /** Writes what the guard answers after a fault, and its return. */
    private static void answer(InsnList code, HookFallback fallback, Type[] arguments,
                               Type result, MethodInsnNode call) {
        String hook = call.owner + "." + call.name + call.desc;
        switch (fallback.kind()) {
            case NOTHING:
                require(result.getSort() == Type.VOID, hook, "returns a value");
                code.add(new InsnNode(Opcodes.RETURN));
                return;
            case FALSE:
            case TRUE:
                require(result.getSort() == Type.BOOLEAN, hook, "answers no yes or no");
                code.add(new InsnNode(fallback.kind() == HookFallback.Kind.TRUE
                        ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                code.add(new InsnNode(Opcodes.IRETURN));
                return;
            case NULL:
                require(isReference(result), hook, "answers no object");
                code.add(new InsnNode(Opcodes.ACONST_NULL));
                code.add(new InsnNode(Opcodes.ARETURN));
                return;
            case ARGUMENT:
                int index = fallback.argumentIndex();
                require(index < arguments.length
                        && arguments[index].getDescriptor().equals(result.getDescriptor()),
                        hook, "takes no argument " + index + " of the type it answers");
                code.add(new VarInsnNode(arguments[index].getOpcode(Opcodes.ILOAD),
                        slotOf(arguments, index)));
                code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
                return;
            case CONSTANT:
                Object value = fallback.constantValue();
                require(value instanceof Integer ? result.getSort() == Type.INT
                        : "Ljava/lang/String;".equals(result.getDescriptor()),
                        hook, "answers no " + value.getClass().getSimpleName());
                code.add(new LdcInsnNode(value));
                code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
                return;
            case ORIGINAL:
                original(code, fallback.instruction(), arguments, result, hook);
                return;
            case CONSTRUCT:
                construct(code, (MethodInsnNode)fallback.instruction(), arguments,
                        result, hook);
                return;
            default:
                throw new IllegalStateException(hook + ": no fallback " + fallback);
        }
    }

    /** Runs the replaced call or field read on the guard's first arguments. */
    private static void original(InsnList code, AbstractInsnNode instruction,
                                 Type[] arguments, Type result, String hook) {
        List<Type> consumed = new ArrayList<Type>();
        Type answers;
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC) {
                consumed.add(Type.getObjectType(call.owner));
            }
            for (Type argument : Type.getArgumentTypes(call.desc)) {
                consumed.add(argument);
            }
            answers = Type.getReturnType(call.desc);
        } else {
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (field.getOpcode() == Opcodes.GETFIELD) {
                consumed.add(Type.getObjectType(field.owner));
            }
            answers = Type.getType(field.desc);
        }
        require(consumed.size() <= arguments.length, hook,
                "takes fewer arguments than the call it replaced");
        load(code, arguments, consumed.size(), consumed);
        code.add(instruction);
        if (result.getSort() == Type.VOID) {
            if (answers.getSort() != Type.VOID) {
                code.add(new InsnNode(answers.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
            }
            code.add(new InsnNode(Opcodes.RETURN));
            return;
        }
        require(answers.getSort() != Type.VOID
                && isReference(answers) == isReference(result)
                && (isReference(result) || answers.getSort() == result.getSort()),
                hook, "answers what the call it replaced does not");
        if (isReference(result) && !answers.getDescriptor().equals(result.getDescriptor())) {
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, result.getInternalName()));
        }
        code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
    }

    /** Builds the game's own object from the guard's first arguments. */
    private static void construct(InsnList code, MethodInsnNode constructor,
                                  Type[] arguments, Type result, String hook) {
        Type[] wanted = Type.getArgumentTypes(constructor.desc);
        require(isReference(result) && wanted.length <= arguments.length, hook,
                "cannot hand back a " + constructor.owner);
        List<Type> consumed = new ArrayList<Type>();
        for (Type type : wanted) {
            consumed.add(type);
        }
        code.add(new TypeInsnNode(Opcodes.NEW, constructor.owner));
        code.add(new InsnNode(Opcodes.DUP));
        load(code, arguments, wanted.length, consumed);
        code.add(constructor);
        code.add(new InsnNode(Opcodes.ARETURN));
    }

    /**
     * Loads the first {@code count} arguments, cast where {@code wanted}
     * names a narrower type than the guard's own descriptor does.
     */
    private static void load(InsnList code, Type[] arguments, int count, List<Type> wanted) {
        for (int index = 0; index < count; index++) {
            Type argument = arguments[index];
            code.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slotOf(arguments, index)));
            if (wanted == null) {
                continue;
            }
            Type target = wanted.get(index);
            if (isReference(target) != isReference(argument)
                    || !isReference(target) && target.getSort() != argument.getSort()) {
                throw new IllegalStateException("argument " + index + " is a "
                        + argument + ", not the " + target + " the game's call takes");
            }
            if (isReference(target) && !target.getDescriptor().equals(argument.getDescriptor())
                    && !"java/lang/Object".equals(target.getInternalName())) {
                code.add(new TypeInsnNode(Opcodes.CHECKCAST, target.getInternalName()));
            }
        }
    }

    /** The local slot of argument {@code index} in a static method. */
    private static int slotOf(Type[] arguments, int index) {
        int slot = 0;
        for (int at = 0; at < index; at++) {
            slot += arguments[at].getSize();
        }
        return slot;
    }

    /** The arguments as a full frame's locals, with one more after them if given. */
    private static Object[] frameTypes(Type[] arguments, String extra) {
        List<Object> types = new ArrayList<Object>();
        for (Type argument : arguments) {
            switch (argument.getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    types.add(Opcodes.INTEGER);
                    break;
                case Type.FLOAT:
                    types.add(Opcodes.FLOAT);
                    break;
                case Type.LONG:
                    types.add(Opcodes.LONG);
                    break;
                case Type.DOUBLE:
                    types.add(Opcodes.DOUBLE);
                    break;
                default:
                    types.add(argument.getSort() == Type.ARRAY
                            ? argument.getDescriptor() : argument.getInternalName());
            }
        }
        if (extra != null) {
            types.add(extra);
        }
        return types.toArray();
    }

    private static boolean isReference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private static void require(boolean condition, String hook, String problem) {
        if (!condition) {
            throw new IllegalStateException(hook + " " + problem);
        }
    }

    /** The first name with that prefix no method of the class has. */
    private static String freeName(ClassNode owner, String prefix) {
        for (int number = 0; ; number++) {
            String name = prefix + number;
            boolean taken = false;
            for (Object value : owner.methods) {
                if (name.equals(((MethodNode)value).name)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return name;
            }
        }
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1).replace('$', '_');
    }

    private static String where(ClassNode owner, MethodNode method, MethodInsnNode call) {
        return "the call in " + owner.name + "." + method.name + " to " + call.owner
                + "." + call.name + call.desc;
    }
}
