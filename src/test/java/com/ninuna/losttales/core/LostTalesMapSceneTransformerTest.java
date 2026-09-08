package com.ninuna.losttales.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Pins the common map-scene seams to the installed LOTR v36.15 bytecode. */
public final class LostTalesMapSceneTransformerTest {
    private static final String MAP = "lotr/client/gui/LOTRGuiMap";
    private static final String BACKGROUND =
            "lotr/client/gui/LOTRGuiRendererMap";
    private static final String SCENE =
            "com/ninuna/losttales/client/mapmarker/LostTalesMapScene";
    private static final String FULL_BACKGROUND =
            "(Lnet/minecraft/client/gui/GuiScreen;L" + MAP + ";FIIII)V";
    private static final String SCREEN_BACKGROUND =
            "(Lnet/minecraft/client/gui/GuiScreen;L" + MAP + ";F)V";
    private static final String BACKGROUND_HOOK =
            "(L" + BACKGROUND + ";Lnet/minecraft/client/gui/GuiScreen;L"
                    + MAP + ";FIIIIZ)Z";
    private static final String MAP_HOOK = "(L" + MAP + ";ZFZ)Z";

    private String previousSceneProperty;
    private String previousBackgroundProperty;

    @Before
    public void clearActiveProperties() {
        previousSceneProperty = System.getProperty(
                LostTalesClassTransformer.LOTR_MAP_SCENE_ACTIVE_PROPERTY);
        previousBackgroundProperty = System.getProperty(
                LostTalesClassTransformer.LOTR_MAP_BACKGROUND_ACTIVE_PROPERTY);
        System.clearProperty(
                LostTalesClassTransformer.LOTR_MAP_SCENE_ACTIVE_PROPERTY);
        System.clearProperty(
                LostTalesClassTransformer.LOTR_MAP_BACKGROUND_ACTIVE_PROPERTY);
    }

    @After
    public void restoreActiveProperties() {
        restoreProperty(LostTalesClassTransformer.LOTR_MAP_SCENE_ACTIVE_PROPERTY,
                previousSceneProperty);
        restoreProperty(
                LostTalesClassTransformer.LOTR_MAP_BACKGROUND_ACTIVE_PROPERTY,
                previousBackgroundProperty);
    }

    @Test
    public void baseMapUsesSharedSceneAndRetainsNativeFallback() throws Exception {
        byte[] original = readResource(MAP);
        byte[] transformed = transform(MAP, original);
        MethodNode method = findMethod(read(transformed),
                "renderMapAndOverlay", "(ZFZ)V");

        MethodInsnNode hook = onlyCall(method, SCENE, "renderMap", MAP_HOOK);
        assertHandledGuard(hook);
        assertTrue("Native texture rendering remains behind the fallback guard",
                countCalls(method, "lotr/client/LOTRTextures", "drawMap", null) > 0);
        assertTrue(Boolean.getBoolean(
                LostTalesClassTransformer.LOTR_MAP_SCENE_ACTIVE_PROPERTY));
        assertArrayEquals(transformed, transform(MAP, transformed));
    }

    @Test
    public void backgroundSceneReceivesViewportAndNativePalette() throws Exception {
        byte[] original = readResource(BACKGROUND);
        byte[] transformed = transform(BACKGROUND, original);
        MethodNode method = findMethod(read(transformed),
                "renderMap", FULL_BACKGROUND);

        MethodInsnNode hook = onlyCall(method, SCENE,
                "renderBackground", BACKGROUND_HOOK);
        assertHandledGuard(hook);
        AbstractInsnNode palette = previousCode(hook);
        assertTrue("The caller's sepia setting is passed to the shared scene",
                palette instanceof FieldInsnNode);
        FieldInsnNode field = (FieldInsnNode)palette;
        assertEquals(Opcodes.GETFIELD, field.getOpcode());
        assertEquals(BACKGROUND, field.owner);
        assertEquals("sepia", field.name);
        assertEquals("Z", field.desc);
        assertTrue("Native map remains available if the scene cannot render",
                countCalls(method, MAP, "renderMapAndOverlay", "(ZFZ)V") > 0);
        assertTrue(Boolean.getBoolean(
                LostTalesClassTransformer.LOTR_MAP_BACKGROUND_ACTIVE_PROPERTY));
        assertArrayEquals(transformed, transform(BACKGROUND, transformed));
    }

    @Test
    public void fullscreenBackgroundOverloadDelegatesToSharedViewportSeam()
            throws Exception {
        ClassNode background = read(transform(
                BACKGROUND, readResource(BACKGROUND)));
        MethodNode fullscreen = findMethod(background,
                "renderMap", SCREEN_BACKGROUND);

        onlyCall(fullscreen, BACKGROUND, "renderMap", FULL_BACKGROUND);
        assertEquals("Only the common viewport overload owns the scene hook",
                0, countCalls(fullscreen, SCENE, "renderBackground", null));
        onlyCall(findMethod(background, "renderMap", FULL_BACKGROUND),
                SCENE, "renderBackground", BACKGROUND_HOOK);
    }

    @Test
    public void allBackgroundScreensReachTheCommonRenderer() throws Exception {
        assertBackgroundCaller("lotr/client/gui/LOTRGuiMainMenu",
                SCREEN_BACKGROUND);
        assertBackgroundCaller("lotr/client/gui/LOTRGuiFastTravel",
                SCREEN_BACKGROUND);
        assertBackgroundCaller("lotr/client/gui/LOTRGuiDownloadTerrain",
                FULL_BACKGROUND);
    }

    @Test
    public void factionInsetReachesTheBaseMapSceneWithoutBackgroundCamera()
            throws Exception {
        MethodNode drawing = findMethod(read(readResource(
                "lotr/client/gui/LOTRGuiFactions")), "drawScreen", "(IIF)V");
        onlyCall(drawing, MAP, "renderMapAndOverlay", "(ZFZ)V");
        onlyCall(drawing, MAP, "setFakeMapProperties", "(FFFFF)V");
        assertEquals("The inset saves and restores its own viewport", 2,
                countCalls(drawing, MAP, "setFakeStaticProperties", "(IIIIII)[I"));
        assertEquals(0, countCalls(drawing, BACKGROUND, "renderMap", null));
        onlyCall(findMethod(read(transform(MAP, readResource(MAP))),
                "renderMapAndOverlay", "(ZFZ)V"), SCENE, "renderMap", MAP_HOOK);
    }

    @Test
    public void unknownBackgroundMethodShapeFailsOpen() throws Exception {
        ClassNode unsupported = read(readResource(BACKGROUND));
        findMethod(unsupported, "renderMap", FULL_BACKGROUND).name =
                "renderMapUnsupportedVersion";
        ClassWriter writer = new ClassWriter(0);
        unsupported.accept(writer);
        byte[] original = writer.toByteArray();

        assertArrayEquals(original, transform(BACKGROUND, original));
        assertFalse(Boolean.getBoolean(
                LostTalesClassTransformer.LOTR_MAP_BACKGROUND_ACTIVE_PROPERTY));
    }

    private static void assertBackgroundCaller(String owner, String descriptor)
            throws Exception {
        MethodNode drawing = findMethod(read(readResource(owner)),
                "drawScreen", "(IIF)V");
        onlyCall(drawing, BACKGROUND, "renderMap", descriptor);
    }

    /** A false result continues LOTR; a true result returns before native drawing. */
    private static void assertHandledGuard(MethodInsnNode hook) {
        AbstractInsnNode branch = nextCode(hook);
        assertTrue(branch instanceof JumpInsnNode);
        assertEquals(Opcodes.IFEQ, branch.getOpcode());
        assertEquals(Opcodes.RETURN, nextCode(branch).getOpcode());
    }

    private static MethodInsnNode onlyCall(MethodNode method, String owner,
                                         String name, String descriptor) {
        assertEquals(owner + "." + name + descriptor, 1,
                countCalls(method, owner, name, descriptor));
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (owner.equals(call.owner) && name.equals(call.name)
                        && descriptor.equals(call.desc)) {
                    if (SCENE.equals(owner)) {
                        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                    }
                    return call;
                }
            }
        }
        throw new AssertionError("Missing call " + name);
    }

    private static int countCalls(MethodNode method, String owner,
                                  String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (owner.equals(call.owner) && name.equals(call.name)
                        && (descriptor == null || descriptor.equals(call.desc))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        assertNotNull(next);
        return next;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        assertNotNull(previous);
        return previous;
    }

    private static MethodNode findMethod(ClassNode owner, String name,
                                         String descriptor) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        throw new AssertionError(owner.name + "." + name + descriptor);
    }

    private static byte[] transform(String name, byte[] bytes) {
        String binaryName = name.replace('/', '.');
        return new LostTalesClassTransformer().transform(
                binaryName, binaryName, bytes);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static byte[] readResource(String name) throws IOException {
        InputStream input = LostTalesMapSceneTransformerTest.class
                .getClassLoader().getResourceAsStream(name + ".class");
        if (input == null) throw new IOException("Missing class " + name);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
