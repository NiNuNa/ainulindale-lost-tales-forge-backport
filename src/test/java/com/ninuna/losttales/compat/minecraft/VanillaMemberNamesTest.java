package com.ninuna.losttales.compat.minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The vanilla members Lost Tales reads by reflection, under both of their
 * names. A development workspace runs on MCP names and a release jar on
 * SRG names, so every lookup of a named member has to try both. Each
 * entry is checked twice: its development name is declared on its owner
 * in the classpath's bytecode, and the workspace's mapping tables give
 * the release name for that development name on that owner. A member
 * with no MCP name keeps its SRG name in both environments.
 *
 * <p>The mapping tables live in the Gradle cache of a set-up Forge
 * workspace. Without them the release half is skipped.</p>
 */
public final class VanillaMemberNamesTest {

    private static final String CONF = "caches/minecraft/net/minecraftforge/"
            + "forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf";

    /** Owner, SRG name, MCP name (null when it has none), and the reader. */
    private static final String[][] FIELDS = {
            { "net/minecraft/client/gui/GuiChat", "field_146409_v",
                    "defaultInputFieldText", "LostTalesChatClientHandler" },
            { "net/minecraft/client/gui/GuiNewChat", "field_146252_h",
                    "chatLines", "ChatWindowLines" },
            { "net/minecraft/client/gui/GuiNewChat", "field_146253_i",
                    null, "LostTalesChatOverlayRenderer" },
            { "net/minecraft/client/gui/GuiTextField", "field_146225_q",
                    "lineScrollOffset", "ChatInputField" },
            { "net/minecraft/client/gui/GuiTextField", "field_146214_l",
                    "cursorCounter", "ChatInputField" },
            { "net/minecraft/client/gui/GuiScreen", "field_146292_n",
                    "buttonList", "LostTalesGuiPointerTargets" },
            { "net/minecraft/client/gui/inventory/GuiContainer",
                    "field_147006_u", "theSlot", "LostTalesGuiPointerTargets" },
            { "net/minecraft/client/shader/ShaderGroup", "field_148031_d",
                    "listShaders", "LostTalesGuiBlurRenderer" },
            { "net/minecraft/client/Minecraft", "field_110446_Y",
                    "fileAssets", "VanillaSkinCacheAccess" },
            { "net/minecraft/entity/player/EntityPlayer", "field_71074_e",
                    "itemInUse", "PlayerItemUseAccess" },
            { "net/minecraft/entity/player/EntityPlayer", "field_71072_f",
                    "itemInUseCount", "PlayerItemUseAccess" },
            { "net/minecraft/stats/StatFileWriter", "field_150875_a",
                    null, "VanillaStatisticsAccess" },
    };

    /** Owner, SRG name, MCP name (null when it has none), descriptor, reader. */
    private static final String[][] METHODS = {
            { "net/minecraft/entity/Entity", "func_70105_a", "setSize",
                    "(FF)V", "CharacterEntitySizeHelper" },
            { "net/minecraft/client/renderer/entity/Render", "func_110775_a",
                    "getEntityTexture",
                    "(Lnet/minecraft/entity/Entity;)"
                            + "Lnet/minecraft/util/ResourceLocation;",
                    "EntityRenderTextureAccess" },
            { "net/minecraft/block/BlockChest", "func_149951_m", null,
                    "(Lnet/minecraft/world/World;III)"
                            + "Lnet/minecraft/inventory/IInventory;",
                    "LostTalesQuickLootInventoryHelper" },
    };

    @Test
    public void developmentNamesAreDeclaredOnTheirOwners() throws IOException {
        for (String[] entry : FIELDS) {
            String name = entry[2] == null ? entry[1] : entry[2];
            FieldNode field = findField(readClass(entry[0]), name);
            assertNotNull(entry[3] + " reads " + entry[0] + "." + name
                    + ", which the development classpath does not declare",
                    field);
            assertTrue(entry[0] + "." + name + " is read as an instance field",
                    (field.access & Opcodes.ACC_STATIC) == 0);
        }
        for (String[] entry : METHODS) {
            String name = entry[2] == null ? entry[1] : entry[2];
            assertNotNull(entry[4] + " calls " + entry[0] + "." + name
                    + entry[3] + ", which the development classpath does "
                    + "not declare",
                    findMethod(readClass(entry[0]), name, entry[3]));
        }
    }

    @Test
    public void releaseNamesMapToTheDevelopmentNames() throws IOException {
        File conf = findConf();
        Assume.assumeTrue("The Forge workspace's mapping tables are not in "
                + "the Gradle cache", conf != null);
        Map<String, String> fieldNames =
                readNames(new File(conf, "fields.csv"));
        Map<String, String> methodNames =
                readNames(new File(conf, "methods.csv"));
        Set<String> srgMembers = readSrgMembers(new File(conf, "packaged.srg"));

        for (String[] entry : FIELDS) {
            assertTrue(entry[1] + " is not a field of " + entry[0],
                    srgMembers.contains(entry[0] + "/" + entry[1]));
            assertMapping(entry[0], entry[1], entry[2], fieldNames.get(entry[1]));
        }
        for (String[] entry : METHODS) {
            assertTrue(entry[1] + entry[3] + " is not a method of " + entry[0],
                    srgMembers.contains(entry[0] + "/" + entry[1] + " "
                            + entry[3]));
            assertMapping(entry[0], entry[1], entry[2],
                    methodNames.get(entry[1]));
        }
    }

    private static void assertMapping(String owner, String srgName,
                                      String expected, String mapped) {
        if (expected == null) {
            assertFalse(owner + "." + srgName + " has the MCP name " + mapped
                    + ", so its lookup needs both names", mapped != null);
        } else {
            assertEquals(owner + "." + srgName + " is looked up in development as "
                    + expected, expected, mapped);
        }
    }

    private static File findConf() {
        String[] homes = {
                System.getenv("GRADLE_USER_HOME"),
                ".gradle-user-home",
                new File(System.getProperty("user.home"), ".gradle").getPath(),
        };
        for (String home : homes) {
            if (home == null) {
                continue;
            }
            File conf = new File(home, CONF);
            if (new File(conf, "fields.csv").isFile()
                    && new File(conf, "methods.csv").isFile()
                    && new File(conf, "packaged.srg").isFile()) {
                return conf;
            }
        }
        return null;
    }

    /** An MCP table's SRG name to MCP name, from its first two columns. */
    private static Map<String, String> readNames(File csv) throws IOException {
        Map<String, String> names = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(csv), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",", 3);
                if (columns.length >= 2) {
                    names.put(columns[0], columns[1]);
                }
            }
        } finally {
            reader.close();
        }
        return names;
    }

    /**
     * Every field as {@code owner/srg} and every method as
     * {@code owner/srg descriptor}, on the named side of the SRG file.
     */
    private static Set<String> readSrgMembers(File srg) throws IOException {
        Set<String> members = new HashSet<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(srg), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ");
                if (line.startsWith("FD: ") && tokens.length >= 3) {
                    members.add(tokens[2]);
                } else if (line.startsWith("MD: ") && tokens.length >= 5) {
                    members.add(tokens[3] + " " + tokens[4]);
                }
            }
        } finally {
            reader.close();
        }
        return members;
    }

    private static FieldNode findField(ClassNode owner, String name) {
        for (Object value : owner.fields) {
            FieldNode field = (FieldNode)value;
            if (field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode owner, String name,
                                         String desc) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    private static ClassNode readClass(String internalName) throws IOException {
        InputStream input = VanillaMemberNamesTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class");
        if (input == null) {
            throw new IOException("Missing test class resource "
                    + internalName + ".class");
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
            return node;
        } finally {
            input.close();
        }
    }
}
