package com.ninuna.losttales.core;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Corrects the two client code paths that bypass the logical player profile in
 * Minecraft 1.7.10: EntityRenderer's hard-coded camera offset and
 * RenderManager's debug-box Y origin.
 */
public final class LostTalesClassTransformer implements IClassTransformer {

    public static final String CAMERA_ACTIVE_PROPERTY =
            "losttales.cameraTransformer.active";
    public static final String DEBUG_BOX_ACTIVE_PROPERTY =
            "losttales.debugHitboxTransformer.active";
    public static final String FAST_TRAVEL_ACTIVE_PROPERTY =
            "losttales.fastTravelTransformer.active";

    private static final String ENTITY_RENDERER =
            "net.minecraft.client.renderer.EntityRenderer";
    private static final String RENDER_MANAGER =
            "net.minecraft.client.renderer.entity.RenderManager";
    private static final String LOTR_FAST_TRAVEL_HANDLER =
            "lotr.common.network.LOTRPacketFastTravel$Handler";

    private static final String CAMERA_MCP = "orientCamera";
    private static final String CAMERA_SRG = "func_78467_g";
    private static final String DEBUG_BOX_MCP = "renderDebugBoundingBox";
    private static final String DEBUG_BOX_SRG = "func_85094_b";

    private static final String CAMERA_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterCameraHook";
    private static final String CAMERA_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;F)F";
    private static final String DEBUG_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterDebugHitboxHook";
    private static final String DEBUG_HOOK_DESC =
            "(Lnet/minecraft/entity/Entity;D)D";
    private static final String FAST_TRAVEL_HOOK_OWNER =
            "com/ninuna/losttales/world/map/waypoint/"
                    + "LostTalesWaypointFastTravelPolicy";
    private static final String FAST_TRAVEL_HOOK_DESC =
            "(Llotr/common/LOTRPlayerData;"
                    + "Llotr/common/world/map/LOTRAbstractWaypoint;"
                    + "Lnet/minecraft/entity/player/EntityPlayerMP;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (ENTITY_RENDERER.equals(transformedName)) {
            return transformCamera(basicClass);
        }
        if (RENDER_MANAGER.equals(transformedName)) {
            return transformDebugBox(basicClass);
        }
        if (LOTR_FAST_TRAVEL_HANDLER.equals(transformedName)) {
            return transformLotrFastTravelHandler(basicClass);
        }
        return basicClass;
    }

    private static byte[] transformLotrFastTravelHandler(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"onMessage".equals(method.name)
                        || !method.desc.startsWith(
                        "(Llotr/common/network/LOTRPacketFastTravel;")) {
                    continue;
                }
                MethodInsnNode targetCall = findLotrFastTravelTargetCall(method);
                if (targetCall == null) {
                    continue;
                }
                if (targetCall.getOpcode() == Opcodes.INVOKESTATIC
                        && FAST_TRAVEL_HOOK_OWNER.equals(targetCall.owner)) {
                    System.setProperty(FAST_TRAVEL_ACTIVE_PROPERTY, "true");
                    return basicClass;
                }
                int playerLocal = findLotrHandlerPlayerLocal(method);
                if (playerLocal < 0) {
                    warn("Could not identify LOTR fast-travel handler player local; "
                            + "the per-tick fallback will remain active");
                    return basicClass;
                }
                method.instructions.insertBefore(
                        targetCall,
                        new VarInsnNode(Opcodes.ALOAD, playerLocal));
                targetCall.setOpcode(Opcodes.INVOKESTATIC);
                targetCall.owner = FAST_TRAVEL_HOOK_OWNER;
                targetCall.name = "setTargetIfAllowed";
                targetCall.desc = FAST_TRAVEL_HOOK_DESC;
                System.setProperty(FAST_TRAVEL_ACTIVE_PROPERTY, "true");
                FMLLog.info("[losttales] Patched LOTR fast travel for "
                        + "character-specific marker discovery");
                return write(owner);
            }
            warn("Could not locate LOTRPacketFastTravel$Handler#onMessage; "
                    + "the per-tick fallback will remain active");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch LOTR fast travel: " + throwable);
            return basicClass;
        }
    }

    private static byte[] transformCamera(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            MethodNode method = findCameraMethod(owner);
            if (method == null) {
                warn("Could not locate EntityRenderer#orientCamera; race camera height will remain vanilla");
                return basicClass;
            }
            if (containsHook(method, CAMERA_HOOK_OWNER, "resolveCameraOffset")) {
                System.setProperty(CAMERA_ACTIVE_PROPERTY, "true");
                return basicClass;
            }

            VarInsnNode offsetStore = findVanillaCameraOffsetStore(method);
            if (offsetStore == null) {
                warn("Could not locate EntityRenderer's vanilla yOffset subtraction; race camera height will remain vanilla");
                return basicClass;
            }
            int viewEntityLocal = findViewEntityLocal(offsetStore);
            if (viewEntityLocal < 0) {
                warn("Could not identify EntityRenderer's view-entity local; race camera height will remain vanilla");
                return basicClass;
            }

            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, viewEntityLocal));
            hook.add(new VarInsnNode(Opcodes.FLOAD, offsetStore.var));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    CAMERA_HOOK_OWNER,
                    "resolveCameraOffset",
                    CAMERA_HOOK_DESC));
            hook.add(new VarInsnNode(Opcodes.FSTORE, offsetStore.var));
            method.instructions.insert(offsetStore, hook);

            System.setProperty(CAMERA_ACTIVE_PROPERTY, "true");
            FMLLog.info("[losttales] Patched EntityRenderer camera origin for roleplay races");
            return write(owner);
        } catch (Throwable throwable) {
            warn("Failed to patch EntityRenderer camera origin: " + throwable);
            return basicClass;
        }
    }

    private static byte[] transformDebugBox(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            MethodNode method = findDebugBoxMethod(owner);
            if (method == null) {
                warn("Could not locate RenderManager#func_85094_b; F3+B may remain visually offset");
                return basicClass;
            }
            if (containsHook(method, DEBUG_HOOK_OWNER, "resolveRenderY")) {
                System.setProperty(DEBUG_BOX_ACTIVE_PROPERTY, "true");
                return basicClass;
            }

            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
            hook.add(new VarInsnNode(Opcodes.DLOAD, 4));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    DEBUG_HOOK_OWNER,
                    "resolveRenderY",
                    DEBUG_HOOK_DESC));
            hook.add(new VarInsnNode(Opcodes.DSTORE, 4));
            method.instructions.insert(hook);

            System.setProperty(DEBUG_BOX_ACTIVE_PROPERTY, "true");
            FMLLog.info("[losttales] Patched RenderManager F3+B origin for roleplay races");
            return write(owner);
        } catch (Throwable throwable) {
            warn("Failed to patch RenderManager F3+B origin: " + throwable);
            return basicClass;
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findCameraMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (!"(F)V".equals(method.desc)) {
                continue;
            }
            if (isNamed(owner, method, CAMERA_MCP, CAMERA_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findDebugBoxMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (!isNamed(owner, method, DEBUG_BOX_MCP, DEBUG_BOX_SRG)
                    || Type.getReturnType(method.desc).getSort() != Type.VOID) {
                continue;
            }
            Type[] arguments = Type.getArgumentTypes(method.desc);
            if (arguments.length == 7
                    && arguments[0].getSort() == Type.OBJECT
                    && arguments[1].getSort() == Type.DOUBLE
                    && arguments[2].getSort() == Type.DOUBLE
                    && arguments[3].getSort() == Type.DOUBLE
                    && arguments[4].getSort() == Type.FLOAT
                    && arguments[5].getSort() == Type.FLOAT) {
                return method;
            }
            // MCP's method has six arguments: entity, x, y, z, yaw, partial.
            if (arguments.length == 6
                    && arguments[0].getSort() == Type.OBJECT
                    && arguments[1].getSort() == Type.DOUBLE
                    && arguments[2].getSort() == Type.DOUBLE
                    && arguments[3].getSort() == Type.DOUBLE
                    && arguments[4].getSort() == Type.FLOAT
                    && arguments[5].getSort() == Type.FLOAT) {
                return method;
            }
        }
        return null;
    }

    private static boolean isNamed(
            ClassNode owner, MethodNode method, String mcpName, String srgName) {
        if (mcpName.equals(method.name) || srgName.equals(method.name)) {
            return true;
        }
        String mapped = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(
                owner.name, method.name, method.desc);
        return mcpName.equals(mapped) || srgName.equals(mapped);
    }

    private static VarInsnNode findVanillaCameraOffsetStore(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            String mapped = FMLDeobfuscatingRemapper.INSTANCE.mapFieldName(
                    field.owner, field.name, field.desc);
            if (!("yOffset".equals(field.name)
                    || "field_70129_M".equals(field.name)
                    || "yOffset".equals(mapped)
                    || "field_70129_M".equals(mapped))) {
                continue;
            }

            AbstractInsnNode cursor = nextCode(instruction);
            int searched = 0;
            boolean sawSubtract = false;
            while (cursor != null && searched++ < 8) {
                if (cursor.getOpcode() == Opcodes.FSUB) {
                    sawSubtract = true;
                } else if (sawSubtract && cursor instanceof VarInsnNode
                        && cursor.getOpcode() == Opcodes.FSTORE) {
                    return (VarInsnNode)cursor;
                }
                cursor = nextCode(cursor);
            }
        }
        return null;
    }

    private static int findViewEntityLocal(VarInsnNode offsetStore) {
        AbstractInsnNode cursor = previousCode(offsetStore);
        int searched = 0;
        while (cursor != null && searched++ < 10) {
            if (cursor instanceof FieldInsnNode
                    && cursor.getOpcode() == Opcodes.GETFIELD) {
                AbstractInsnNode ownerLoad = previousCode(cursor);
                if (ownerLoad instanceof VarInsnNode
                        && ownerLoad.getOpcode() == Opcodes.ALOAD) {
                    return ((VarInsnNode)ownerLoad).var;
                }
            }
            cursor = previousCode(cursor);
        }
        return -1;
    }

    private static boolean containsHook(
            MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && owner.equals(call.owner) && name.equals(call.name)) {
                return true;
            }
        }
        return false;
    }

    private static MethodInsnNode findLotrFastTravelTargetCall(
            MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "lotr/common/LOTRPlayerData".equals(call.owner)
                    && "setTargetFTWaypoint".equals(call.name)
                    && "(Llotr/common/world/map/LOTRAbstractWaypoint;)V"
                    .equals(call.desc)) {
                return call;
            }
            if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && FAST_TRAVEL_HOOK_OWNER.equals(call.owner)
                    && "setTargetIfAllowed".equals(call.name)) {
                return call;
            }
        }
        return null;
    }

    private static int findLotrHandlerPlayerLocal(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!"net/minecraft/network/NetHandlerPlayServer"
                    .equals(field.owner)
                    || !("playerEntity".equals(field.name)
                    || "field_147369_b".equals(field.name))) {
                continue;
            }
            AbstractInsnNode store = nextCode(instruction);
            if (store instanceof VarInsnNode
                    && store.getOpcode() == Opcodes.ASTORE) {
                return ((VarInsnNode)store).var;
            }
        }
        return -1;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction == null ? null : instruction.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction == null ? null : instruction.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static void warn(String message) {
        FMLLog.warning("[losttales] %s", message);
    }
}
