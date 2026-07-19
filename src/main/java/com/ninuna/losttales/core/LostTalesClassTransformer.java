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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Applies narrowly scoped compatibility hooks where public Forge 1.7.10 events
 * do not expose the required camera, debug-box, identity, and LOTR behavior.
 */
public final class LostTalesClassTransformer implements IClassTransformer {

    public static final String CAMERA_ACTIVE_PROPERTY =
            "losttales.cameraTransformer.active";
    public static final String THIRD_PERSON_CAMERA_ACTIVE_PROPERTY =
            "losttales.thirdPersonCameraTransformer.active";
    public static final String THIRD_PERSON_TARGETING_ACTIVE_PROPERTY =
            "losttales.thirdPersonTargetingTransformer.active";
    public static final String THIRD_PERSON_ENTITY_ACTION_ACTIVE_PROPERTY =
            "losttales.thirdPersonEntityActionTransformer.active";
    public static final String THIRD_PERSON_BLOCK_ACTION_ACTIVE_PROPERTY =
            "losttales.thirdPersonBlockActionTransformer.active";
    public static final String DEBUG_BOX_ACTIVE_PROPERTY =
            "losttales.debugHitboxTransformer.active";
    public static final String FAST_TRAVEL_ACTIVE_PROPERTY =
            "losttales.fastTravelTransformer.active";
    public static final String LOTR_BOUNTY_ACTIVE_PROPERTY =
            "losttales.lotrBountyTransformer.active";
    public static final String LOTR_SPEECH_ACTIVE_PROPERTY =
            "losttales.lotrSpeechTransformer.active";
    public static final String ACCESSORY_CONTAINER_ACTIVE_PROPERTY =
            "losttales.accessoryContainerTransformer.active";
    public static final String ACCESSORY_PICK_BLOCK_ACTIVE_PROPERTY =
            "losttales.accessoryPickBlockTransformer.active";
    public static final String ACCESSORY_CREATIVE_ACTIVE_PROPERTY =
            "losttales.accessoryCreativeTransformer.active";
    public static final String ACCESSORY_DEATH_ACTIVE_PROPERTY =
            "losttales.accessoryDeathTransformer.active";
    public static final String ACCESSORY_COLLISION_ACTIVE_PROPERTY =
            "losttales.accessoryCollisionTransformer.active";
    public static final String ACCESSORY_INVISIBILITY_ACTIVE_PROPERTY =
            "losttales.accessoryInvisibilityTransformer.active";
    public static final String ACCESSORY_AI_TARGET_ACTIVE_PROPERTY =
            "losttales.accessoryAiTargetTransformer.active";
    public static final String ACCESSORY_SIGHT_ACTIVE_PROPERTY =
            "losttales.accessorySightTransformer.active";
    public static final String ACCESSORY_LOTR_MAP_ACTIVE_PROPERTY =
            "losttales.accessoryLotrMapTransformer.active";

    private static final String ENTITY_RENDERER =
            "net.minecraft.client.renderer.EntityRenderer";
    private static final String MINECRAFT =
            "net.minecraft.client.Minecraft";
    private static final String PLAYER_CONTROLLER =
            "net.minecraft.client.multiplayer.PlayerControllerMP";
    private static final String ENTITY_PLAYER_MP =
            "net.minecraft.entity.player.EntityPlayerMP";
    private static final String ENTITY_PLAYER =
            "net.minecraft.entity.player.EntityPlayer";
    private static final String ENTITY =
            "net.minecraft.entity.Entity";
    private static final String ENTITY_LIVING_BASE =
            "net.minecraft.entity.EntityLivingBase";
    private static final String ENTITY_AI_TARGET =
            "net.minecraft.entity.ai.EntityAITarget";
    private static final String NET_HANDLER_PLAY_SERVER =
            "net.minecraft.network.NetHandlerPlayServer";
    private static final String RENDER_MANAGER =
            "net.minecraft.client.renderer.entity.RenderManager";
    private static final String LOTR_FAST_TRAVEL_HANDLER =
            "lotr.common.network.LOTRPacketFastTravel$Handler";
    private static final String LOTR_FACTION_BOUNTIES =
            "lotr.common.fac.LOTRFactionBounties";
    private static final String LOTR_FACTION_BOUNTY_PLAYER_DATA =
            "lotr.common.fac.LOTRFactionBounties$PlayerData";
    private static final String LOTR_SPEECH =
            "lotr.common.entity.npc.LOTRSpeech";
    private static final String LOTR_GUI_MAP =
            "lotr.client.gui.LOTRGuiMap";
    private static final String LOTR_LEVEL_DATA =
            "lotr.common.LOTRLevelData";

    private static final String CAMERA_MCP = "orientCamera";
    private static final String CAMERA_SRG = "func_78467_g";
    private static final String FOV_MCP = "getFOVModifier";
    private static final String FOV_SRG = "func_78481_a";
    private static final String MOUSE_OVER_MCP = "getMouseOver";
    private static final String MOUSE_OVER_SRG = "func_78473_a";
    private static final String ATTACK_ENTITY_MCP = "attackEntity";
    private static final String ATTACK_ENTITY_SRG = "func_78764_a";
    private static final String INTERACT_ENTITY_MCP =
            "interactWithEntitySendPacket";
    private static final String INTERACT_ENTITY_SRG = "func_78768_b";
    private static final String RIGHT_CLICK_BLOCK_MCP =
            "onPlayerRightClick";
    private static final String RIGHT_CLICK_BLOCK_SRG = "func_78760_a";
    private static final String DEBUG_BOX_MCP = "renderDebugBoundingBox";
    private static final String DEBUG_BOX_SRG = "func_85094_b";

    private static final String CAMERA_HOOK_OWNER =
            "com/ninuna/losttales/character/physics/CharacterCameraHook";
    private static final String CAMERA_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;F)F";
    private static final String THIRD_PERSON_CAMERA_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonCameraHooks";
    private static final String CAMERA_DISTANCE_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;DF)D";
    private static final String CAMERA_OFFSET_HOOK_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;FD)V";
    private static final String CAMERA_FOV_HOOK_DESC = "(FZ)F";
    private static final String TARGETING_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonTargetingHooks";
    private static final String TARGETING_HOOK_DESC = "(F)V";
    private static final String ENTITY_ACTION_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonEntityActionHooks";
    private static final String ENTITY_ACTION_PREDICATE_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/entity/Entity;)Z";
    private static final String BLOCK_ACTION_HOOK_OWNER =
            "com/ninuna/losttales/client/camera/ThirdPersonBlockActionHooks";
    private static final String BLOCK_ACTION_SEND_DESC =
            "(Lnet/minecraft/client/network/NetHandlerPlayClient;"
                    + "Lnet/minecraft/network/Packet;)V";
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
    private static final String ROLEPLAY_IDENTITY_HOOK_OWNER =
            "com/ninuna/losttales/character/identity/"
                    + "RoleplayCharacterIdentityHook";
    private static final String GAMEPLAY_ID_HOOK_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;)Ljava/util/UUID;";
    private static final String ROLEPLAY_NAME_HOOK_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;)Ljava/lang/String;";
    private static final String GAMEPLAY_NAME_HOOK_DESC =
            "(Ljava/util/UUID;)Ljava/lang/String;";
    private static final String DEATH_MESSAGE_HOOK_DESC =
            "(Lnet/minecraft/util/IChatComponent;"
                    + "Lnet/minecraft/entity/player/EntityPlayerMP;)"
                    + "Lnet/minecraft/util/IChatComponent;";
    private static final String CLIENT_IDENTITY_HOOK_OWNER =
            "com/ninuna/losttales/client/character/"
                    + "ClientRoleplayCharacterIdentityHook";
    private static final String MAP_PLAYER_NAME_HOOK_DESC =
            "(Lcom/mojang/authlib/GameProfile;)Ljava/lang/String;";
    private static final String ACCESSORY_CONTAINER_CLASS =
            "com/ninuna/losttales/accessory/inventory/"
                    + "LostTalesContainerPlayer";
    private static final String ACCESSORY_CONTAINER_HOOK_OWNER =
            "com/ninuna/losttales/accessory/inventory/"
                    + "AccessoryContainerHooks";
    private static final String ACCESSORY_CREATIVE_HOOK_OWNER =
            "com/ninuna/losttales/accessory/inventory/"
                    + "AccessoryCreativeInventoryHook";
    private static final String ACCESSORY_DEATH_HOOK_OWNER =
            "com/ninuna/losttales/accessory/player/AccessoryDeathHooks";
    private static final String ACCESSORY_CONCEALMENT_HOOK_OWNER =
            "com/ninuna/losttales/accessory/effect/"
                    + "AccessoryConcealmentHooks";
    private static final String ACCESSORY_LOTR_MAP_HOOK_OWNER =
            "com/ninuna/losttales/compat/lotr/LotrAccessoryMapHooks";
    private static final String LOTR_MAP_EDGE_FILL_HOOK_OWNER =
            "com/ninuna/losttales/client/map/"
                    + "LostTalesLotrMapEdgeRenderer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (ENTITY_RENDERER.equals(transformedName)) {
            return transformCamera(basicClass);
        }
        if (MINECRAFT.equals(transformedName)) {
            return transformMinecraftPickBlock(basicClass);
        }
        if (PLAYER_CONTROLLER.equals(transformedName)) {
            return transformPlayerController(basicClass);
        }
        if (ENTITY_PLAYER_MP.equals(transformedName)) {
            return transformEntityPlayerMpDeathMessage(basicClass);
        }
        if (ENTITY_PLAYER.equals(transformedName)) {
            return transformEntityPlayerContainer(basicClass);
        }
        if (ENTITY.equals(transformedName)) {
            return transformEntityInvisibility(basicClass);
        }
        if (ENTITY_LIVING_BASE.equals(transformedName)) {
            return transformEntityLivingBaseSight(basicClass);
        }
        if (ENTITY_AI_TARGET.equals(transformedName)) {
            return transformEntityAiTarget(basicClass);
        }
        if (NET_HANDLER_PLAY_SERVER.equals(transformedName)) {
            return transformCreativeInventoryAction(basicClass);
        }
        if (RENDER_MANAGER.equals(transformedName)) {
            return transformDebugBox(basicClass);
        }
        if (LOTR_FAST_TRAVEL_HANDLER.equals(transformedName)) {
            return transformLotrFastTravelHandler(basicClass);
        }
        if (LOTR_FACTION_BOUNTIES.equals(transformedName)) {
            return transformLotrFactionBounties(basicClass);
        }
        if (LOTR_FACTION_BOUNTY_PLAYER_DATA.equals(transformedName)) {
            return transformLotrFactionBountyPlayerData(basicClass);
        }
        if (LOTR_SPEECH.equals(transformedName)) {
            return transformLotrSpeech(basicClass);
        }
        if (LOTR_GUI_MAP.equals(transformedName)) {
            return transformLotrGuiMap(basicClass);
        }
        if (LOTR_LEVEL_DATA.equals(transformedName)) {
            return transformLotrPlayerLocations(basicClass);
        }
        return basicClass;
    }

    /** Installs the player container and the Forge drop-capture transaction hook. */
    private static byte[] transformEntityPlayerContainer(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean containerPatched = false;
            boolean deathPatched = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if ("<init>".equals(method.name)
                        && "(Lnet/minecraft/world/World;"
                        .concat("Lcom/mojang/authlib/GameProfile;)V")
                        .equals(method.desc)) {
                    boolean allocationPatched = false;
                    boolean constructorPatched = false;
                    for (AbstractInsnNode instruction =
                         method.instructions.getFirst();
                         instruction != null;
                         instruction = instruction.getNext()) {
                        if (instruction instanceof TypeInsnNode
                                && instruction.getOpcode() == Opcodes.NEW) {
                            TypeInsnNode allocation = (TypeInsnNode)instruction;
                            if ("net/minecraft/inventory/ContainerPlayer"
                                    .equals(allocation.desc)) {
                                allocation.desc = ACCESSORY_CONTAINER_CLASS;
                                allocationPatched = true;
                            } else if (ACCESSORY_CONTAINER_CLASS.equals(
                                    allocation.desc)) {
                                allocationPatched = true;
                            }
                        } else if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode)instruction;
                            if (call.getOpcode() == Opcodes.INVOKESPECIAL
                                    && "<init>".equals(call.name)
                                    && "(Lnet/minecraft/entity/player/InventoryPlayer;"
                                    .concat("ZLnet/minecraft/entity/player/EntityPlayer;)V")
                                    .equals(call.desc)) {
                                if ("net/minecraft/inventory/ContainerPlayer"
                                        .equals(call.owner)) {
                                    call.owner = ACCESSORY_CONTAINER_CLASS;
                                    constructorPatched = true;
                                } else if (ACCESSORY_CONTAINER_CLASS.equals(
                                        call.owner)) {
                                    constructorPatched = true;
                                }
                            }
                        }
                    }
                    containerPatched = allocationPatched && constructorPatched;
                }

                if (("onDeath".equals(method.name)
                        || "func_70645_a".equals(method.name))
                        && "(Lnet/minecraft/util/DamageSource;)V".equals(
                        method.desc)) {
                    if (containsHook(method, ACCESSORY_DEATH_HOOK_OWNER,
                            "captureAccessoryDrop")) {
                        deathPatched = true;
                        continue;
                    }
                    for (AbstractInsnNode instruction =
                         method.instructions.getFirst();
                         instruction != null;
                         instruction = instruction.getNext()) {
                        if (!(instruction instanceof FieldInsnNode)
                                || instruction.getOpcode() != Opcodes.PUTFIELD) {
                            continue;
                        }
                        FieldInsnNode field = (FieldInsnNode)instruction;
                        AbstractInsnNode zero = previousCode(field);
                        AbstractInsnNode playerLoad = previousCode(zero);
                        if (!"captureDrops".equals(field.name)
                                || !"Z".equals(field.desc)
                                || zero == null
                                || zero.getOpcode() != Opcodes.ICONST_0
                                || !(playerLoad instanceof VarInsnNode)
                                || playerLoad.getOpcode() != Opcodes.ALOAD
                                || ((VarInsnNode)playerLoad).var != 0) {
                            continue;
                        }
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        hook.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                ACCESSORY_DEATH_HOOK_OWNER,
                                "captureAccessoryDrop",
                                "(Lnet/minecraft/entity/player/EntityPlayer;)V",
                                false));
                        method.instructions.insertBefore(playerLoad, hook);
                        deathPatched = true;
                        break;
                    }
                }

            }
            if (containerPatched) {
                System.setProperty(
                        ACCESSORY_CONTAINER_ACTIVE_PROPERTY, "true");
                info("Installed accessory-aware player container");
            } else {
                warn("Could not patch EntityPlayer player-container construction; "
                        + "accessory slots will remain unavailable");
            }
            if (deathPatched) {
                System.setProperty(ACCESSORY_DEATH_ACTIVE_PROPERTY, "true");
                info("Installed accessory death-drop capture hook");
            } else {
                warn("Could not patch EntityPlayer death-drop capture; "
                        + "accessory insertion must remain disabled");
            }
            return containerPatched || deathPatched ? write(owner) : basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch EntityPlayer accessory integration: "
                    + throwable);
            return basicClass;
        }
    }

    /** Rejects concealed players before vanilla and LOTR target suitability. */
    private static byte[] transformEntityAiTarget(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("isSuitableTarget".equals(method.name)
                        || "func_75296_a".equals(method.name))
                        || !"(Lnet/minecraft/entity/EntityLivingBase;Z)Z"
                        .equals(method.desc)) {
                    continue;
                }
                if (!containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    injectConcealmentGuard(method, 1);
                }
                if (containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    System.setProperty(
                            ACCESSORY_AI_TARGET_ACTIVE_PROPERTY, "true");
                    info("Installed accessory AI target-suitability hook");
                    return write(owner);
                }
            }
            warn("Could not patch EntityAITarget target suitability");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch EntityAITarget target suitability: "
                    + throwable);
            return basicClass;
        }
    }

    /** Makes vanilla rendering and visibility consumers treat the wearer as vanished. */
    private static byte[] transformEntityInvisibility(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("isInvisible".equals(method.name)
                        || "func_82150_aj".equals(method.name))
                        || !"()Z".equals(method.desc)) {
                    continue;
                }
                if (!containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    injectBooleanTrueGuard(method, 0);
                }
                if (containsHook(method,
                        ACCESSORY_CONCEALMENT_HOOK_OWNER,
                        "isConcealed")) {
                    System.setProperty(
                            ACCESSORY_INVISIBILITY_ACTIVE_PROPERTY, "true");
                    info("Installed accessory vanilla-invisibility hook");
                    return write(owner);
                }
            }
            warn("Could not patch Entity invisibility predicate");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch Entity invisibility predicate: "
                    + throwable);
            return basicClass;
        }
    }

    /** Makes living sight and collision checks fail for concealed players. */
    private static byte[] transformEntityLivingBaseSight(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean sightPatched = false;
            boolean collisionPatched = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (("canEntityBeSeen".equals(method.name)
                        || "func_70685_l".equals(method.name))
                        && "(Lnet/minecraft/entity/Entity;)Z".equals(
                        method.desc)) {
                    if (!containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed")) {
                        injectConcealmentGuard(method, 1);
                    }
                    sightPatched = containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed");
                }
                if (("canBeCollidedWith".equals(method.name)
                        || "func_70067_L".equals(method.name))
                        && "()Z".equals(method.desc)) {
                    if (!containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed")) {
                        injectConcealmentGuard(method, 0);
                    }
                    collisionPatched = containsHook(method,
                            ACCESSORY_CONCEALMENT_HOOK_OWNER,
                            "isConcealed");
                }
            }
            if (sightPatched) {
                System.setProperty(
                        ACCESSORY_SIGHT_ACTIVE_PROPERTY, "true");
            } else {
                warn("Could not patch EntityLivingBase sight checks");
            }
            if (collisionPatched) {
                System.setProperty(
                        ACCESSORY_COLLISION_ACTIVE_PROPERTY, "true");
            } else {
                warn("Could not patch EntityLivingBase collision targeting");
            }
            if (sightPatched || collisionPatched) {
                info("Installed accessory living sight/collision hooks");
                return write(owner);
            }
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch EntityLivingBase sight checks: "
                    + throwable);
            return basicClass;
        }
    }

    private static void injectConcealmentGuard(
            MethodNode method, int entityLocal) {
        LabelNode vanilla = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed",
                "(Lnet/minecraft/entity/Entity;)Z",
                false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
        guard.add(vanilla);
        method.instructions.insert(guard);
    }

    private static void injectBooleanTrueGuard(
            MethodNode method, int entityLocal) {
        LabelNode vanilla = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ACCESSORY_CONCEALMENT_HOOK_OWNER,
                "isConcealed",
                "(Lnet/minecraft/entity/Entity;)Z",
                false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1));
        guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
        guard.add(vanilla);
        method.instructions.insert(guard);
    }

    /** Preserves vanilla hotbar slot IDs after appending the accessory slot. */
    private static byte[] transformMinecraftPickBlock(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("middleClickMouse".equals(method.name)
                        || "func_147112_ai".equals(method.name))
                        || !"()V".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method,
                        ACCESSORY_CONTAINER_HOOK_OWNER,
                        "resolveVanillaInventorySlotCount")) {
                    System.setProperty(
                            ACCESSORY_PICK_BLOCK_ACTIVE_PROPERTY, "true");
                    return basicClass;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                            && "java/util/List".equals(call.owner)
                            && "size".equals(call.name)
                            && "()I".equals(call.desc)) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = ACCESSORY_CONTAINER_HOOK_OWNER;
                        call.name = "resolveVanillaInventorySlotCount";
                        call.desc = "(Ljava/util/List;)I";
                        // MethodInsnNode retains the original interface-owner
                        // flag when only its opcode is changed. Leaving it set
                        // writes an InterfaceMethodref for this class method,
                        // which Java 8 rejects during Minecraft verification.
                        call.itf = false;
                        System.setProperty(
                                ACCESSORY_PICK_BLOCK_ACTIVE_PROPERTY,
                                "true");
                        info("Patched creative pick-block hotbar indexing");
                        return write(owner);
                    }
                }
            }
            warn("Could not patch Minecraft creative pick-block indexing");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch creative pick-block indexing: "
                    + throwable);
            return basicClass;
        }
    }

    /** Routes creative slot 45 through the accessory validator. */
    private static byte[] transformCreativeInventoryAction(
            byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("processCreativeInventoryAction".equals(method.name)
                        || "func_147344_a".equals(method.name))
                        || !"(Lnet/minecraft/network/play/client/"
                        .concat("C10PacketCreativeInventoryAction;)V")
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, ACCESSORY_CREATIVE_HOOK_OWNER,
                        "handle")) {
                    System.setProperty(
                            ACCESSORY_CREATIVE_ACTIVE_PROPERTY, "true");
                    return basicClass;
                }
                String playerField = findPlayerEntityField(method);
                if (playerField == null) {
                    warn("Could not locate NetHandlerPlayServer player field "
                            + "for accessory creative validation");
                    return basicClass;
                }
                LabelNode vanilla = new LabelNode();
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new FieldInsnNode(
                        Opcodes.GETFIELD,
                        "net/minecraft/network/NetHandlerPlayServer",
                        playerField,
                        "Lnet/minecraft/entity/player/EntityPlayerMP;"));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        ACCESSORY_CREATIVE_HOOK_OWNER,
                        "handle",
                        "(Lnet/minecraft/entity/player/EntityPlayerMP;"
                                + "Lnet/minecraft/network/play/client/"
                                + "C10PacketCreativeInventoryAction;)Z"));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
                hook.add(new org.objectweb.asm.tree.InsnNode(
                        Opcodes.RETURN));
                hook.add(vanilla);
                method.instructions.insertBefore(
                        method.instructions.getFirst(), hook);
                System.setProperty(
                        ACCESSORY_CREATIVE_ACTIVE_PROPERTY, "true");
                info("Installed server accessory creative-slot validation");
                return write(owner);
            }
            warn("Could not patch creative inventory action handling");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch creative inventory action handling: "
                    + throwable);
            return basicClass;
        }
    }

    private static String findPlayerEntityField(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            if ("net/minecraft/network/NetHandlerPlayServer"
                    .equals(field.owner)
                    && "Lnet/minecraft/entity/player/EntityPlayerMP;"
                    .equals(field.desc)) {
                return field.name;
            }
        }
        return null;
    }

    /** Rewrites only the already-generated player death chat component. */
    private static byte[] transformEntityPlayerMpDeathMessage(
            byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!("onDeath".equals(method.name)
                        || "func_70645_a".equals(method.name))
                        || !"(Lnet/minecraft/util/DamageSource;)V"
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(
                        method, ROLEPLAY_IDENTITY_HOOK_OWNER,
                        "resolveDeathMessage")) {
                    return basicClass;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode invocation =
                            (MethodInsnNode)instruction;
                    if (!"net/minecraft/util/CombatTracker"
                            .equals(invocation.owner)
                            || !("func_151521_b".equals(invocation.name)
                            || "getDeathMessage".equals(invocation.name))
                            || !"()Lnet/minecraft/util/IChatComponent;"
                            .equals(invocation.desc)) {
                        continue;
                    }
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ROLEPLAY_IDENTITY_HOOK_OWNER,
                            "resolveDeathMessage",
                            DEATH_MESSAGE_HOOK_DESC));
                    method.instructions.insert(invocation, hook);
                    info("Patched player death messages with roleplay names");
                    return write(owner);
                }
            }
            warn("Could not patch player death-message broadcast");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch player death messages: " + throwable);
            return basicClass;
        }
    }

    /** Applies client map rendering hooks without changing LOTR map data. */
    private static byte[] transformLotrGuiMap(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            boolean changed = false;
            boolean playerNameHookPresent = false;
            boolean edgeFillHookPresent = false;
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if ("renderPlayers".equals(method.name)
                        && "(II)V".equals(method.desc)) {
                    playerNameHookPresent = containsHook(
                            method, CLIENT_IDENTITY_HOOK_OWNER,
                            "resolveMapPlayerName");
                    if (!playerNameHookPresent) {
                        for (AbstractInsnNode instruction =
                             method.instructions.getFirst();
                             instruction != null;
                             instruction = instruction.getNext()) {
                            if (!(instruction instanceof MethodInsnNode)) {
                                continue;
                            }
                            MethodInsnNode invocation =
                                    (MethodInsnNode)instruction;
                            if (invocation.getOpcode()
                                    != Opcodes.INVOKEVIRTUAL
                                    || !"com/mojang/authlib/GameProfile"
                                    .equals(invocation.owner)
                                    || !"getName".equals(invocation.name)
                                    || !"()Ljava/lang/String;"
                                    .equals(invocation.desc)) {
                                continue;
                            }
                            invocation.setOpcode(Opcodes.INVOKESTATIC);
                            invocation.owner = CLIENT_IDENTITY_HOOK_OWNER;
                            invocation.name = "resolveMapPlayerName";
                            invocation.desc = MAP_PLAYER_NAME_HOOK_DESC;
                            playerNameHookPresent = true;
                            changed = true;
                            info("Patched LOTR map player tooltips with "
                                    + "roleplay names");
                            break;
                        }
                    }
                } else if ("renderMapAndOverlay".equals(method.name)
                        && "(ZFZ)V".equals(method.desc)) {
                    edgeFillHookPresent = containsHook(
                            method, LOTR_MAP_EDGE_FILL_HOOK_OWNER,
                            "fillClippedMapBackground");
                    if (!edgeFillHookPresent) {
                        edgeFillHookPresent = injectLotrMapEdgeFill(method);
                        changed |= edgeFillHookPresent;
                    }
                }
            }
            if (!playerNameHookPresent) {
                warn("Could not patch LOTR map player tooltip names");
            }
            if (!edgeFillHookPresent) {
                warn("Could not patch LOTR clipped map background");
            }
            return changed ? write(owner) : basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch LOTR map rendering: "
                    + throwable);
            return basicClass;
        }
    }

    private static boolean injectLotrMapEdgeFill(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !"lotr/client/LOTRTextures".equals(call.owner)
                    || !"drawMap".equals(call.name)) {
                continue;
            }

            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMin");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMax");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMin");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMax");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMin_W");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapXMax_W");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMin_W");
            addStaticIntField(hook, LOTR_GUI_MAP, "mapYMax_W");
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    LOTR_MAP_EDGE_FILL_HOOK_OWNER,
                    "fillClippedMapBackground",
                    "(ZIIIIIIII)V"));
            method.instructions.insertBefore(call, hook);
            info("Patched clipped LOTR map previews with ocean padding");
            return true;
        }
        return false;
    }

    private static void addStaticIntField(
            InsnList instructions, String owner, String name) {
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, owner.replace('.', '/'), name, "I"));
    }

    /** Filters concealed players before LOTR serializes map coordinates. */
    private static byte[] transformLotrPlayerLocations(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"sendPlayerLocationsToPlayer".equals(method.name)
                        || !"(Lnet/minecraft/entity/player/EntityPlayer;"
                        .concat("Lnet/minecraft/world/World;)V")
                        .equals(method.desc)) {
                    continue;
                }
                if (containsHook(method,
                        ACCESSORY_LOTR_MAP_HOOK_OWNER,
                        "addPlayerLocationIfVisible")) {
                    System.setProperty(
                            ACCESSORY_LOTR_MAP_ACTIVE_PROPERTY, "true");
                    return basicClass;
                }
                for (AbstractInsnNode instruction =
                     method.instructions.getFirst(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                            || !"lotr/common/network/"
                            .concat("LOTRPacketUpdatePlayerLocations")
                            .equals(call.owner)
                            || !"addPlayerLocation".equals(call.name)
                            || !"(Lcom/mojang/authlib/GameProfile;DD)V"
                            .equals(call.desc)) {
                        continue;
                    }
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = ACCESSORY_LOTR_MAP_HOOK_OWNER;
                    call.name = "addPlayerLocationIfVisible";
                    call.desc = "(Llotr/common/network/"
                            + "LOTRPacketUpdatePlayerLocations;"
                            + "Lcom/mojang/authlib/GameProfile;DD)V";
                    call.itf = false;
                    System.setProperty(
                            ACCESSORY_LOTR_MAP_ACTIVE_PROPERTY, "true");
                    info("Patched LOTR map locations for accessory concealment");
                    return write(owner);
                }
            }
            warn("Could not patch LOTR player-location packet construction");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch LOTR player-location packets: "
                    + throwable);
            return basicClass;
        }
    }

    /**
     * Replaces only active offset-camera action packets. Forge 1.7.10 has no
     * cancelable hook between PlayerControllerMP's held-item synchronization
     * and its C02/C08 packets, so the original paths remain the fallbacks.
     */
    private static byte[] transformPlayerController(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            MethodNode attack = findPlayerControllerAttackMethod(owner);
            MethodNode interact =
                    findPlayerControllerInteractionMethod(owner);
            MethodNode block = findPlayerControllerBlockActionMethod(owner);
            if (attack == null || interact == null) {
                warn("Could not locate PlayerControllerMP entity actions; "
                        + "vanilla C02 entity packets will remain active");
            }
            if (block == null) {
                warn("Could not locate PlayerControllerMP block action; "
                        + "vanilla C08 block packets will remain active");
            }

            boolean changed = false;
            if (attack != null && !containsHook(
                    attack, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityAttack")) {
                changed |= injectEntityAttackHook(attack);
            }
            if (interact != null && !containsHook(
                    interact, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityInteraction")) {
                changed |= injectEntityInteractionHook(interact);
            }
            if (block != null && !containsHook(
                    block, BLOCK_ACTION_HOOK_OWNER,
                    "sendBlockActionOrVanilla")) {
                changed |= replaceBlockActionSend(block);
            }

            boolean entityActive = attack != null && interact != null
                    && containsHook(
                    attack, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityAttack")
                    && containsHook(
                    attack, ENTITY_ACTION_HOOK_OWNER, "handleAttack")
                    && containsHook(
                    interact, ENTITY_ACTION_HOOK_OWNER,
                    "shouldHandleEntityInteraction")
                    && containsHook(
                    interact, ENTITY_ACTION_HOOK_OWNER,
                    "handleInteraction");
            if (entityActive) {
                System.setProperty(
                        THIRD_PERSON_ENTITY_ACTION_ACTIVE_PROPERTY,
                        "true");
            }
            boolean blockActive = block != null && containsHook(
                    block, BLOCK_ACTION_HOOK_OWNER,
                    "sendBlockActionOrVanilla");
            if (blockActive) {
                System.setProperty(
                        THIRD_PERSON_BLOCK_ACTION_ACTIVE_PROPERTY,
                        "true");
            }
            if (changed) {
                info("Patched PlayerControllerMP third-person actions "
                        + "with vanilla fallbacks");
                return write(owner);
            }
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch PlayerControllerMP entity actions: "
                    + throwable);
            return basicClass;
        }
    }

    /** Makes LOTR's faction-bounty map use the active character UUID. */
    private static byte[] transformLotrFactionBounties(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"forPlayer".equals(method.name)
                        || !"(Lnet/minecraft/entity/player/EntityPlayer;)"
                        .concat("Llotr/common/fac/LOTRFactionBounties$PlayerData;")
                        .equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC
                            && ROLEPLAY_IDENTITY_HOOK_OWNER.equals(call.owner)
                            && "resolveGameplayId".equals(call.name)) {
                        activateLotrBountyTransformer();
                        return basicClass;
                    }
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && "net/minecraft/entity/player/EntityPlayer"
                            .equals(call.owner)
                            && "()Ljava/util/UUID;".equals(call.desc)) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = ROLEPLAY_IDENTITY_HOOK_OWNER;
                        call.name = "resolveGameplayId";
                        call.desc = GAMEPLAY_ID_HOOK_DESC;
                        activateLotrBountyTransformer();
                        info("Bound LOTR faction bounties "
                                + "to active roleplay-character UUIDs");
                        return write(owner);
                    }
                }
            }
            warn("Could not patch LOTRFactionBounties#forPlayer; faction "
                    + "bounties will remain account-bound");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to bind LOTR faction bounties to characters: "
                    + throwable);
            return basicClass;
        }
    }

    /** Resolves bounty target labels from roleplay-character UUIDs. */
    private static byte[] transformLotrFactionBountyPlayerData(
            byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"findUsername".equals(method.name)
                        || !"()Ljava/lang/String;".equals(method.desc)) {
                    continue;
                }
                if (containsHook(method, ROLEPLAY_IDENTITY_HOOK_OWNER,
                        "resolveGameplayName")) {
                    activateLotrBountyTransformer();
                    return basicClass;
                }

                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) {
                    method.localVariables.clear();
                }
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new FieldInsnNode(
                        Opcodes.GETFIELD,
                        "lotr/common/fac/LOTRFactionBounties$PlayerData",
                        "playerID",
                        "Ljava/util/UUID;"));
                method.instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        ROLEPLAY_IDENTITY_HOOK_OWNER,
                        "resolveGameplayName",
                        GAMEPLAY_NAME_HOOK_DESC));
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(
                        Opcodes.ARETURN));
                activateLotrBountyTransformer();
                info("Patched LOTR bounty target names "
                        + "for roleplay-character UUIDs");
                return write(owner);
            }
            warn("Could not patch LOTR faction bounty target names");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch LOTR faction bounty target names: "
                    + throwable);
            return basicClass;
        }
    }

    /** Changes only LOTR speech's # placeholder, not the player's account name. */
    private static byte[] transformLotrSpeech(byte[] basicClass) {
        try {
            ClassNode owner = read(basicClass);
            for (Object value : owner.methods) {
                MethodNode method = (MethodNode)value;
                if (!"formatSpeech".equals(method.name)
                        || !"(Ljava/lang/String;"
                        .concat("Lnet/minecraft/entity/player/EntityPlayer;")
                        .concat("Ljava/lang/String;Ljava/lang/String;)")
                        .concat("Ljava/lang/String;")
                        .equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC
                            && ROLEPLAY_IDENTITY_HOOK_OWNER.equals(call.owner)
                            && "resolveRoleplayName".equals(call.name)) {
                        System.setProperty(LOTR_SPEECH_ACTIVE_PROPERTY, "true");
                        return basicClass;
                    }
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && "net/minecraft/entity/player/EntityPlayer"
                            .equals(call.owner)
                            && "()Ljava/lang/String;".equals(call.desc)
                            && ("getCommandSenderName".equals(call.name)
                            || "func_70005_c_".equals(call.name))) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = ROLEPLAY_IDENTITY_HOOK_OWNER;
                        call.name = "resolveRoleplayName";
                        call.desc = ROLEPLAY_NAME_HOOK_DESC;
                        System.setProperty(LOTR_SPEECH_ACTIVE_PROPERTY, "true");
                        info("Patched LOTR NPC speech to "
                                + "use active roleplay-character names");
                        return write(owner);
                    }
                }
            }
            warn("Could not patch LOTRSpeech#formatSpeech; NPC speech will "
                    + "use account names");
            return basicClass;
        } catch (Throwable throwable) {
            warn("Failed to patch LOTR NPC speech names: " + throwable);
            return basicClass;
        }
    }

    private static void activateLotrBountyTransformer() {
        System.setProperty(LOTR_BOUNTY_ACTIVE_PROPERTY, "true");
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
                info("Patched LOTR fast travel for "
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
            MethodNode cameraMethod = findCameraMethod(owner);
            if (cameraMethod == null) {
                warn("Could not locate EntityRenderer#orientCamera; race camera height will remain vanilla");
                return basicClass;
            }

            boolean changed = false;
            VarInsnNode offsetStore = findVanillaCameraOffsetStore(
                    cameraMethod);
            if (offsetStore == null) {
                warn("Could not locate EntityRenderer's vanilla yOffset subtraction; race camera height will remain vanilla");
                return basicClass;
            }
            int viewEntityLocal = findViewEntityLocal(offsetStore);
            if (viewEntityLocal < 0) {
                warn("Could not identify EntityRenderer's view-entity local; race camera height will remain vanilla");
                return basicClass;
            }

            if (!containsHook(
                    cameraMethod, CAMERA_HOOK_OWNER,
                    "resolveCameraOffset")) {
                InsnList raceOffsetHook = new InsnList();
                raceOffsetHook.add(new VarInsnNode(
                        Opcodes.ALOAD, viewEntityLocal));
                raceOffsetHook.add(new VarInsnNode(
                        Opcodes.FLOAD, offsetStore.var));
                raceOffsetHook.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        CAMERA_HOOK_OWNER,
                        "resolveCameraOffset",
                        CAMERA_HOOK_DESC));
                raceOffsetHook.add(new VarInsnNode(
                        Opcodes.FSTORE, offsetStore.var));
                cameraMethod.instructions.insert(
                        offsetStore, raceOffsetHook);
                changed = true;
            }

            System.setProperty(CAMERA_ACTIVE_PROPERTY, "true");

            VarInsnNode distanceStore = findThirdPersonDistanceStore(
                    cameraMethod);
            if (distanceStore == null) {
                warn("Could not locate EntityRenderer's third-person distance local; the optional camera overhaul will remain inactive");
            } else {
                if (!containsHook(
                        cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                        "resolveDistance")) {
                    InsnList distanceHook = new InsnList();
                    distanceHook.add(new VarInsnNode(
                            Opcodes.ALOAD, viewEntityLocal));
                    distanceHook.add(new VarInsnNode(
                            Opcodes.DLOAD, distanceStore.var));
                    distanceHook.add(new VarInsnNode(Opcodes.FLOAD, 1));
                    distanceHook.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            THIRD_PERSON_CAMERA_HOOK_OWNER,
                            "resolveDistance",
                            CAMERA_DISTANCE_HOOK_DESC));
                    distanceHook.add(new VarInsnNode(
                            Opcodes.DSTORE, distanceStore.var));
                    cameraMethod.instructions.insert(
                            distanceStore, distanceHook);
                    changed = true;
                }

                if (!containsHook(
                        cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                        "applyCameraOffset")) {
                    MethodInsnNode distanceTranslation =
                            findThirdPersonDistanceTranslation(
                            cameraMethod, distanceStore.var);
                    if (distanceTranslation == null) {
                        warn("Could not locate EntityRenderer's normal third-person translation; shoulder offsets will remain inactive");
                    } else {
                        InsnList offsetHook = new InsnList();
                        offsetHook.add(new VarInsnNode(
                                Opcodes.ALOAD, viewEntityLocal));
                        offsetHook.add(new VarInsnNode(
                                Opcodes.FLOAD, 1));
                        offsetHook.add(new VarInsnNode(
                                Opcodes.DLOAD, distanceStore.var));
                        offsetHook.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                THIRD_PERSON_CAMERA_HOOK_OWNER,
                                "applyCameraOffset",
                                CAMERA_OFFSET_HOOK_DESC));
                        cameraMethod.instructions.insertBefore(
                                distanceTranslation, offsetHook);
                        changed = true;
                    }
                }
            }

            MethodNode fovMethod = findFovMethod(owner);
            if (fovMethod == null) {
                warn("Could not locate EntityRenderer#getFOVModifier; camera FOV profiles will remain inactive");
            } else if (!containsHook(
                    fovMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveFov")) {
                changed |= injectFovHooks(fovMethod);
            }

            boolean cameraOverhaulActive = distanceStore != null
                    && containsHook(
                    cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveDistance")
                    && containsHook(
                    cameraMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "applyCameraOffset")
                    && fovMethod != null
                    && containsHook(
                    fovMethod, THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveFov");
            if (cameraOverhaulActive) {
                System.setProperty(
                        THIRD_PERSON_CAMERA_ACTIVE_PROPERTY, "true");
            }
            MethodNode mouseOverMethod = findMouseOverMethod(owner);
            if (mouseOverMethod == null) {
                warn("Could not locate EntityRenderer#getMouseOver; "
                        + "camera-intent targeting will remain inactive");
            } else {
                if (!containsHook(
                        mouseOverMethod, TARGETING_HOOK_OWNER,
                        "resolveMouseOver")) {
                    changed |= injectMouseOverHooks(mouseOverMethod);
                }
                if (containsHook(
                        mouseOverMethod, TARGETING_HOOK_OWNER,
                        "resolveMouseOver")) {
                    System.setProperty(
                            THIRD_PERSON_TARGETING_ACTIVE_PROPERTY,
                            "true");
                }
            }
            if (changed) {
                info("Patched EntityRenderer race origin, third-person "
                        + "camera, and targeting hooks");
                return write(owner);
            }
            return basicClass;
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
            info("Patched RenderManager F3+B origin for roleplay races");
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

    private static MethodNode findFovMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if ("(FZ)F".equals(method.desc)
                    && isNamed(owner, method, FOV_MCP, FOV_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findMouseOverMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if ("(F)V".equals(method.desc)
                    && isNamed(owner, method,
                    MOUSE_OVER_MCP, MOUSE_OVER_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findPlayerControllerAttackMethod(
            ClassNode owner) {
        String descriptor =
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)V";
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (descriptor.equals(method.desc)
                    && isNamed(owner, method,
                    ATTACK_ENTITY_MCP, ATTACK_ENTITY_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findPlayerControllerInteractionMethod(
            ClassNode owner) {
        String descriptor =
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)Z";
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (descriptor.equals(method.desc)
                    && isNamed(owner, method,
                    INTERACT_ENTITY_MCP, INTERACT_ENTITY_SRG)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode findPlayerControllerBlockActionMethod(
            ClassNode owner) {
        String descriptor =
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/item/ItemStack;"
                        + "IIIILnet/minecraft/util/Vec3;)Z";
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (descriptor.equals(method.desc)
                    && isNamed(owner, method,
                    RIGHT_CLICK_BLOCK_MCP, RIGHT_CLICK_BLOCK_SRG)) {
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

    private static VarInsnNode findThirdPersonDistanceStore(
            MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode)instruction;
            if (!isFieldNamed(
                    field, "thirdPersonDistanceTemp", "field_78491_C")) {
                continue;
            }
            AbstractInsnNode cursor = nextCode(instruction);
            int searched = 0;
            while (cursor != null && searched++ < 32) {
                if (cursor instanceof VarInsnNode
                        && cursor.getOpcode() == Opcodes.DSTORE) {
                    return (VarInsnNode)cursor;
                }
                cursor = nextCode(cursor);
            }
        }
        return null;
    }

    private static MethodInsnNode findThirdPersonDistanceTranslation(
            MethodNode method, int distanceLocal) {
        MethodInsnNode match = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !"org/lwjgl/opengl/GL11".equals(call.owner)
                    || !"glTranslatef".equals(call.name)
                    || !"(FFF)V".equals(call.desc)) {
                continue;
            }
            AbstractInsnNode convert = previousCode(call);
            AbstractInsnNode negate = previousCode(convert);
            AbstractInsnNode load = previousCode(negate);
            if (convert != null && convert.getOpcode() == Opcodes.D2F
                    && negate != null
                    && negate.getOpcode() == Opcodes.DNEG
                    && load instanceof VarInsnNode
                    && load.getOpcode() == Opcodes.DLOAD
                    && ((VarInsnNode)load).var == distanceLocal) {
                match = call;
            }
        }
        return match;
    }

    private static boolean injectFovHooks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.FRETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ILOAD, 2));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    THIRD_PERSON_CAMERA_HOOK_OWNER,
                    "resolveFov", CAMERA_FOV_HOOK_DESC));
            method.instructions.insertBefore(instruction, hook);
            changed = true;
        }
        return changed;
    }

    private static boolean injectMouseOverHooks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.RETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.FLOAD, 1));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    TARGETING_HOOK_OWNER,
                    "resolveMouseOver", TARGETING_HOOK_DESC));
            method.instructions.insertBefore(instruction, hook);
            changed = true;
        }
        return changed;
    }

    private static boolean injectEntityAttackHook(MethodNode method) {
        MethodInsnNode syncCall = findHeldItemSyncCall(method);
        if (syncCall == null) {
            warn("Could not locate PlayerControllerMP held-item sync in "
                    + method.name + "; vanilla entity packets remain active");
            return false;
        }

        LabelNode vanillaPath = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ENTITY_ACTION_HOOK_OWNER,
                "shouldHandleEntityAttack",
                ENTITY_ACTION_PREDICATE_DESC));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, vanillaPath));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ENTITY_ACTION_HOOK_OWNER,
                "handleAttack",
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)V"));
        hook.add(new org.objectweb.asm.tree.InsnNode(
                Opcodes.RETURN));
        hook.add(vanillaPath);
        method.instructions.insert(syncCall, hook);
        return true;
    }

    private static boolean injectEntityInteractionHook(MethodNode method) {
        MethodInsnNode syncCall = findHeldItemSyncCall(method);
        if (syncCall == null) {
            warn("Could not locate PlayerControllerMP held-item sync in "
                    + method.name + "; vanilla entity packets remain active");
            return false;
        }

        LabelNode vanillaPath = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ENTITY_ACTION_HOOK_OWNER,
                "shouldHandleEntityInteraction",
                ENTITY_ACTION_PREDICATE_DESC));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, vanillaPath));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ENTITY_ACTION_HOOK_OWNER,
                "handleInteraction",
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/entity/Entity;)Z"));
        hook.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
        hook.add(vanillaPath);
        method.instructions.insert(syncCall, hook);
        return true;
    }

    private static boolean replaceBlockActionSend(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"net/minecraft/client/network/NetHandlerPlayClient"
                    .equals(call.owner)
                    || !"(Lnet/minecraft/network/Packet;)V"
                    .equals(call.desc)
                    || !("addToSendQueue".equals(call.name)
                    || "func_147297_a".equals(call.name))) {
                continue;
            }
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = BLOCK_ACTION_HOOK_OWNER;
            call.name = "sendBlockActionOrVanilla";
            call.desc = BLOCK_ACTION_SEND_DESC;
            return true;
        }
        warn("Could not locate PlayerControllerMP block packet send; "
                + "vanilla C08 block packets remain active");
        return false;
    }

    private static MethodInsnNode findHeldItemSyncCall(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if ("net/minecraft/client/multiplayer/PlayerControllerMP"
                    .equals(call.owner)
                    && "()V".equals(call.desc)
                    && ("syncCurrentPlayItem".equals(call.name)
                    || "func_78750_j".equals(call.name))) {
                return call;
            }
        }
        return null;
    }

    private static boolean isFieldNamed(
            FieldInsnNode field, String mcpName, String srgName) {
        if (mcpName.equals(field.name) || srgName.equals(field.name)) {
            return true;
        }
        String mapped = FMLDeobfuscatingRemapper.INSTANCE.mapFieldName(
                field.owner, field.name, field.desc);
        return mcpName.equals(mapped) || srgName.equals(mapped);
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
        try {
            FMLLog.warning("[losttales] %s", message);
        } catch (Throwable ignored) {
            // Unit tests and very early bootstrap may not have initialized FML's logger.
        }
    }

    private static void info(String message) {
        try {
            FMLLog.info("[losttales] %s", message);
        } catch (Throwable ignored) {
            // Unit tests and very early bootstrap may not have initialized FML's logger.
        }
    }
}
