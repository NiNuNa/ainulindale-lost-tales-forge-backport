package com.ninuna.losttales.core;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * What a call to a hook answers when the hook fails (DATA-08): what the
 * game would have had there without the patch. Every call a patch
 * injects names one, and {@link HookGuard} writes it into the catch that
 * wraps the call.
 */
final class HookFallback {

    /** The kinds of answer, each checked against the hook's descriptor. */
    enum Kind {
        /** A hook that returns nothing: nothing happens. */
        NOTHING,
        /** A hook that answers yes or no: no, the game's own path. */
        FALSE,
        /** A hook that answers yes or no: yes, the game's own path. */
        TRUE,
        /** A hook that answers an object the game does without. */
        NULL,
        /** The value the game handed the hook, unchanged. */
        ARGUMENT,
        /** The literal the patch replaced. */
        CONSTANT,
        /** The instruction the patch replaced, run on the hook's first arguments. */
        ORIGINAL,
        /** The game's own object, built from the hook's first arguments. */
        CONSTRUCT
    }

    static final HookFallback NOTHING = new HookFallback(Kind.NOTHING, -1, null, null);
    static final HookFallback FALSE = new HookFallback(Kind.FALSE, -1, null, null);
    static final HookFallback TRUE = new HookFallback(Kind.TRUE, -1, null, null);
    static final HookFallback NULL = new HookFallback(Kind.NULL, -1, null, null);

    private final Kind kind;
    private final int argument;
    private final Object constant;
    private final AbstractInsnNode original;

    private HookFallback(Kind kind, int argument, Object constant,
                         AbstractInsnNode original) {
        this.kind = kind;
        this.argument = argument;
        this.constant = constant;
        this.original = original;
    }

    /** The argument at {@code index}: the value the game computed and handed in. */
    static HookFallback argument(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("no argument " + index);
        }
        return new HookFallback(Kind.ARGUMENT, index, null, null);
    }

    /** A literal the patch replaced, an {@code Integer} or a {@code String}. */
    static HookFallback constant(Object value) {
        if (!(value instanceof Integer) && !(value instanceof String)) {
            throw new IllegalArgumentException("a constant fallback is an int or a string");
        }
        return new HookFallback(Kind.CONSTANT, -1, value, null);
    }

    /**
     * The call or field read the patch replaced with the hook, copied as it
     * stands, before it is changed. The hook takes its receiver and its
     * arguments first, in order, and may take more after them.
     */
    static HookFallback original(AbstractInsnNode instruction) {
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
                throw new IllegalArgumentException(
                        "a private or super call cannot be made again from a hook's guard");
            }
            return new HookFallback(Kind.ORIGINAL, -1, null, new MethodInsnNode(
                    call.getOpcode(), call.owner, call.name, call.desc, call.itf));
        }
        if (instruction instanceof FieldInsnNode
                && (instruction.getOpcode() == Opcodes.GETFIELD
                        || instruction.getOpcode() == Opcodes.GETSTATIC)) {
            FieldInsnNode field = (FieldInsnNode)instruction;
            return new HookFallback(Kind.ORIGINAL, -1, null, new FieldInsnNode(
                    field.getOpcode(), field.owner, field.name, field.desc));
        }
        throw new IllegalArgumentException("only a call or a field read is made again");
    }

    /**
     * A new {@code owner}, built by its constructor {@code descriptor} from
     * the hook's first arguments: the object the game built before the
     * patch built the mod's own.
     */
    static HookFallback construct(String owner, String descriptor) {
        return new HookFallback(Kind.CONSTRUCT, -1, null, new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, "<init>", descriptor, false));
    }

    Kind kind() {
        return this.kind;
    }

    int argumentIndex() {
        return this.argument;
    }

    Object constantValue() {
        return this.constant;
    }

    /** The instruction an {@code ORIGINAL} or {@code CONSTRUCT} answer runs; a fresh copy. */
    AbstractInsnNode instruction() {
        if (this.original instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)this.original;
            return new MethodInsnNode(call.getOpcode(), call.owner, call.name,
                    call.desc, call.itf);
        }
        if (this.original instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode)this.original;
            return new FieldInsnNode(field.getOpcode(), field.owner, field.name,
                    field.desc);
        }
        return null;
    }

    /** Equal answers share one guard per hook in a class. */
    String key() {
        StringBuilder key = new StringBuilder(this.kind.name());
        if (this.kind == Kind.ARGUMENT) {
            key.append(':').append(this.argument);
        } else if (this.kind == Kind.CONSTANT) {
            key.append(':').append(this.constant.getClass().getSimpleName())
                    .append(':').append(this.constant);
        } else if (this.original instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)this.original;
            key.append(':').append(call.getOpcode()).append(':').append(call.owner)
                    .append('.').append(call.name).append(call.desc);
        } else if (this.original instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode)this.original;
            key.append(':').append(field.getOpcode()).append(':').append(field.owner)
                    .append('.').append(field.name).append(' ').append(field.desc);
        }
        return key.toString();
    }

    @Override
    public String toString() {
        return key();
    }
}
