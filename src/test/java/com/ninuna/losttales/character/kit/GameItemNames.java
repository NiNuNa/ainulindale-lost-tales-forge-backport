package com.ninuna.losttales.character.kit;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The item ids the game registers, read from the bytecode on the test
 * classpath without starting the game: LOTR v36.15's from
 * {@code LOTRMod} in the deobf jar, and Minecraft's own from
 * {@code Item.registerItems} and {@code Block.registerBlocks}.
 *
 * <p>LOTR names an item {@code setUnlocalizedName("lotr:x")}, keeps it in a
 * static field and registers that field as {@code lotr:item.x}; a block
 * named {@code setBlockName("lotr:x")} and registered becomes the item
 * {@code lotr:tile.x}. Minecraft registers each under the name pushed
 * right after its numeric id.</p>
 */
final class GameItemNames {
    private static final String LOTR_MOD = "lotr/common/LOTRMod";

    private GameItemNames() {}

    /** Every item id LOTR registers. */
    static Set<String> lotr() throws IOException {
        ClassNode mod = read(LOTR_MOD + ".class");
        Map<String, String> idByField = new HashMap<String, String>();
        Set<String> registered = new LinkedHashSet<String>();
        for (MethodNode method : mod.methods) {
            String pending = null;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                    String text = (String) ((LdcInsnNode) insn).cst;
                    AbstractInsnNode next = nextReal(insn);
                    if (text.startsWith("lotr:") && next instanceof MethodInsnNode) {
                        String called = ((MethodInsnNode) next).name;
                        if ("setUnlocalizedName".equals(called)) {
                            pending = "lotr:item." + text.substring("lotr:".length());
                        } else if ("setBlockName".equals(called)) {
                            pending = "lotr:tile." + text.substring("lotr:".length());
                        }
                    }
                } else if (insn.getOpcode() == Opcodes.PUTSTATIC && pending != null
                        && LOTR_MOD.equals(((FieldInsnNode) insn).owner)) {
                    idByField.put(((FieldInsnNode) insn).name, pending);
                    pending = null;
                } else if (insn.getOpcode() == Opcodes.GETSTATIC
                        && LOTR_MOD.equals(((FieldInsnNode) insn).owner)) {
                    AbstractInsnNode next = nextReal(insn);
                    if (next instanceof LdcInsnNode) {
                        next = nextReal(next);
                    }
                    if (next instanceof MethodInsnNode
                            && LOTR_MOD.equals(((MethodInsnNode) next).owner)
                            && ("registerItem".equals(((MethodInsnNode) next).name)
                            || "registerBlock".equals(((MethodInsnNode) next).name))) {
                        registered.add(((FieldInsnNode) insn).name);
                    }
                }
            }
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (String field : registered) {
            String id = idByField.get(field);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Every item id Minecraft registers, its blocks' items among them. */
    static Set<String> vanilla() throws IOException {
        Set<String> ids = new LinkedHashSet<String>();
        addRegistered(ids, read("net/minecraft/item/Item.class"), "registerItems");
        addRegistered(ids, read("net/minecraft/block/Block.class"), "registerBlocks");
        return ids;
    }

    private static void addRegistered(Set<String> ids, ClassNode owner, String methodName) {
        for (MethodNode method : owner.methods) {
            if (!methodName.equals(method.name)) {
                continue;
            }
            AbstractInsnNode previous = null;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (insn.getOpcode() < 0) {
                    continue;
                }
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String
                        && isIntPush(previous)) {
                    ids.add("minecraft:" + ((LdcInsnNode) insn).cst);
                }
                previous = insn;
            }
        }
    }

    private static boolean isIntPush(AbstractInsnNode insn) {
        if (insn == null) {
            return false;
        }
        int opcode = insn.getOpcode();
        return insn instanceof IntInsnNode || opcode >= Opcodes.ICONST_M1
                && opcode <= Opcodes.ICONST_5;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }

    private static ClassNode read(String resource) throws IOException {
        InputStream in = GameItemNames.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException(resource + " is not on the test classpath");
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } finally {
            in.close();
        }
    }
}
