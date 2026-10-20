package com.ninuna.losttales.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the coremod writes into vanilla and LOTR exists in the mod: every
 * class a patched method names in the mod's own packages loads, and every
 * method, constructor and field it calls or reads is there, with the
 * descriptor, the static-ness and the public access the call needs. The
 * targets are read off {@link LostTalesClassTransformer#transform} itself,
 * so a new patch is covered the moment it is dispatched.
 */
public final class LostTalesClassTransformerHooksTest {
    private static final String MOD_PACKAGE = "com/ninuna/losttales/";

    @Test
    public void everyHookThePatchesCallExists() throws Exception {
        List<String> problems = new ArrayList<String>();
        Set<String> owners = new TreeSet<String>();
        LostTalesClassTransformer transformer = new LostTalesClassTransformer();
        for (String target : dispatchedTargets()) {
            byte[] original = readResource(target.replace('.', '/') + ".class");
            if (original == null) {
                problems.add("no class " + target + " on the classpath");
                continue;
            }
            ClassNode patched = new ClassNode();
            new ClassReader(transformer.transform(target, target, original))
                    .accept(patched, 0);
            for (Object value : patched.methods) {
                MethodNode method = (MethodNode)value;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    String problem = check(instruction, owners);
                    if (problem != null) {
                        problems.add(target + "." + method.name + ": " + problem);
                    }
                }
            }
        }
        assertEquals(new ArrayList<String>(), problems);
        assertTrue("the patches call the mod's hooks: " + owners, owners.size() > 20);
    }

    /** What is wrong with one reference into the mod, or null. */
    private static String check(AbstractInsnNode instruction, Set<String> owners) {
        try {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (!call.owner.startsWith(MOD_PACKAGE)) {
                    return null;
                }
                owners.add(call.owner);
                return checkCall(call);
            }
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode read = (FieldInsnNode)instruction;
                if (!read.owner.startsWith(MOD_PACKAGE)) {
                    return null;
                }
                owners.add(read.owner);
                return checkField(read);
            }
            if (instruction instanceof TypeInsnNode) {
                String type = ((TypeInsnNode)instruction).desc;
                if (type.startsWith(MOD_PACKAGE)) {
                    owners.add(type);
                    return publicClass(load(type)) ? null : type + " is not public";
                }
            }
            if (instruction instanceof LdcInsnNode
                    && ((LdcInsnNode)instruction).cst instanceof Type) {
                String type = ((Type)((LdcInsnNode)instruction).cst).getInternalName();
                if (type.startsWith(MOD_PACKAGE)) {
                    load(type);
                }
            }
            return null;
        } catch (ClassNotFoundException missing) {
            return "missing class " + missing.getMessage();
        } catch (LinkageError broken) {
            return "class does not link: " + broken;
        }
    }

    private static String checkCall(MethodInsnNode call) throws ClassNotFoundException {
        Class<?> owner = load(call.owner);
        String name = call.owner + "." + call.name + call.desc;
        if ("<init>".equals(call.name)) {
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                if (Type.getConstructorDescriptor(constructor).equals(call.desc)) {
                    return Modifier.isPublic(constructor.getModifiers())
                            && publicClass(owner) ? null : name + " is not public";
                }
            }
            return "no constructor " + name;
        }
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(call.name)
                        || !Type.getMethodDescriptor(method).equals(call.desc)) {
                    continue;
                }
                boolean wantedStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
                if (Modifier.isStatic(method.getModifiers()) != wantedStatic) {
                    return name + (wantedStatic ? " is not static" : " is static");
                }
                return Modifier.isPublic(method.getModifiers()) && publicClass(owner)
                        ? null : name + " is not public";
            }
        }
        return "no method " + name;
    }

    private static String checkField(FieldInsnNode read) throws ClassNotFoundException {
        Class<?> owner = load(read.owner);
        String name = read.owner + "." + read.name + " " + read.desc;
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getName().equals(read.name)
                        || !Type.getDescriptor(field.getType()).equals(read.desc)) {
                    continue;
                }
                boolean wantedStatic = read.getOpcode() == Opcodes.GETSTATIC
                        || read.getOpcode() == Opcodes.PUTSTATIC;
                if (Modifier.isStatic(field.getModifiers()) != wantedStatic) {
                    return name + (wantedStatic ? " is not static" : " is static");
                }
                return Modifier.isPublic(field.getModifiers()) && publicClass(owner)
                        ? null : name + " is not public";
            }
        }
        return "no field " + name;
    }

    private static boolean publicClass(Class<?> type) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getDeclaringClass()) {
            if (!Modifier.isPublic(cursor.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> load(String internalName) throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'), false,
                LostTalesClassTransformerHooksTest.class.getClassLoader());
    }

    /** The class names {@code transform} dispatches on, as its bytecode holds them. */
    private static Set<String> dispatchedTargets() throws IOException {
        byte[] bytes = readResource(
                LostTalesClassTransformer.class.getName().replace('.', '/') + ".class");
        ClassNode transformer = new ClassNode();
        new ClassReader(bytes).accept(transformer, 0);
        Set<String> targets = new TreeSet<String>();
        for (Object value : transformer.methods) {
            MethodNode method = (MethodNode)value;
            if (!"transform".equals(method.name)
                    || !"(Ljava/lang/String;Ljava/lang/String;[B)[B".equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode
                        && ((LdcInsnNode)instruction).cst instanceof String
                        && namesAClass((String)((LdcInsnNode)instruction).cst)) {
                    targets.add((String)((LdcInsnNode)instruction).cst);
                }
            }
        }
        assertTrue("transform dispatches on the patched classes: " + targets,
                targets.size() > 20);
        return targets;
    }

    /**
     * Whether a string is a class's binary name, {@code lotr.client.gui.LOTRGuiMap},
     * and not one of the flags the dispatch passes on, {@code losttales.….active}.
     */
    private static boolean namesAClass(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 && dot + 1 < value.length()
                && Character.isUpperCase(value.charAt(dot + 1));
    }

    private static byte[] readResource(String path) throws IOException {
        InputStream input = LostTalesClassTransformerHooksTest.class
                .getClassLoader().getResourceAsStream(path);
        if (input == null) {
            return null;
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
