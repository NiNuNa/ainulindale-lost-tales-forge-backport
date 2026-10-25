package com.ninuna.losttales.armour;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * LOTR v36.15's and Minecraft's armour as the bytecode on the test
 * classpath holds it, read without starting the game: the armour material
 * names the game registers ({@code LOTR_GONDOR}: LOTR prefixes each of its
 * materials' names; {@code IRON}), and each LOTR armour item's registry
 * name with the material it is made of.
 */
final class LotrArmourBytecode {
    private static final String LOTR_MOD = "lotr/common/LOTRMod";
    private static final String LOTR_MATERIAL = "lotr/common/item/LOTRMaterial";
    private static final String LOTR_ARMOUR = "lotr/common/item/LOTRItemArmor";

    private LotrArmourBytecode() {}

    /** Every armour material name the game can register: LOTR's and vanilla's. */
    static Set<String> materials() throws IOException {
        Set<String> names = new LinkedHashSet<String>();
        for (FieldNode field : read(LOTR_MATERIAL + ".class").fields) {
            if (("L" + LOTR_MATERIAL + ";").equals(field.desc)) {
                names.add("LOTR_" + field.name);
            }
        }
        String vanilla = "net/minecraft/item/ItemArmor$ArmorMaterial";
        for (FieldNode field : read(vanilla + ".class").fields) {
            if (("L" + vanilla + ";").equals(field.desc)
                    && (field.access & Opcodes.ACC_ENUM) != 0) {
                names.add(field.name);
            }
        }
        return names;
    }

    /**
     * Each LOTR armour item, {@code lotr:item.helmetGondor}, with the
     * material it is built with, {@code LOTR_GONDOR}.
     */
    static Map<String, String> armourItems() throws IOException {
        Map<String, String> items = new LinkedHashMap<String, String>();
        for (MethodNode method : read(LOTR_MOD + ".class").methods) {
            boolean building = false;
            String material = null;
            String name = null;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.NEW) {
                    building = LOTR_ARMOUR.equals(((TypeInsnNode) insn).desc);
                    material = null;
                    name = null;
                } else if (building && insn.getOpcode() == Opcodes.GETSTATIC
                        && LOTR_MATERIAL.equals(((FieldInsnNode) insn).owner) && material == null) {
                    material = "LOTR_" + ((FieldInsnNode) insn).name;
                } else if (building && insn instanceof LdcInsnNode
                        && ((LdcInsnNode) insn).cst instanceof String) {
                    AbstractInsnNode next = insn.getNext();
                    while (next != null && next.getOpcode() < 0) {
                        next = next.getNext();
                    }
                    String text = (String) ((LdcInsnNode) insn).cst;
                    if (next instanceof MethodInsnNode
                            && "setUnlocalizedName".equals(((MethodInsnNode) next).name)
                            && text.startsWith("lotr:")) {
                        name = "lotr:item." + text.substring("lotr:".length());
                    }
                } else if (building && insn.getOpcode() == Opcodes.PUTSTATIC
                        && LOTR_MOD.equals(((FieldInsnNode) insn).owner)) {
                    if (material != null && name != null) {
                        items.put(name, material);
                    }
                    building = false;
                }
            }
        }
        return items;
    }

    private static ClassNode read(String resource) throws IOException {
        InputStream in = LotrArmourBytecode.class.getClassLoader().getResourceAsStream(resource);
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
