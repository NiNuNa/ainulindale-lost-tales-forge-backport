package com.ninuna.losttales.network;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Packet discriminators are protocol surface: a client and a server that
 * disagree about which id carries which packet decode each other's traffic
 * into the wrong handler. The ids are handed out by hand in
 * {@code registerCommonPackets}, so this reads that method's compiled
 * bytecode and holds the allocation to the rules the registration follows —
 * every id used once, contiguous from zero, and each id still on the packet
 * it was checked in with.
 *
 * <p>The method cannot be called here: it needs a live channel from FML.
 * The class is therefore read as a resource, which also keeps its static
 * channel field from being initialised.</p>
 */
public final class LostTalesNetworkHandlerDiscriminatorTest {

    private static final String HANDLER_CLASS =
            "/com/ninuna/losttales/network/LostTalesNetworkHandler.class";
    private static final String REGISTER_METHOD = "registerCommonPackets";
    private static final String CHANNEL_OWNER =
            "cpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper";
    private static final String REGISTER_DESC =
            "(Ljava/lang/Class;Ljava/lang/Class;ILcpw/mods/fml/relauncher/Side;)V";
    private static final String SIDE_OWNER = "cpw/mods/fml/relauncher/Side";

    /**
     * The allocation as it stands, indexed by discriminator. Ids are never
     * renumbered or reused, so a change here has to be a deliberate append
     * at the end rather than a silent shuffle.
     */
    private static final String[] EXPECTED = {
            "LostTalesQuickLootRequestPacket SERVER",
            "LostTalesQuickLootDropItemPacket SERVER",
            "LostTalesQuickLootContainerSyncPacket CLIENT",
            "LostTalesQuestSyncPacket CLIENT",
            "LostTalesQuestActionPacket SERVER",
            "LostTalesMobAggroSyncPacket CLIENT",
            "LostTalesMapMarkerDiscoveryPacket CLIENT",
            "LostTalesMissiveAcceptPacket SERVER",
            "CharacterRosterRequestPacket SERVER",
            "CharacterCreateRequestPacket SERVER",
            "CharacterSelectRequestPacket SERVER",
            "CharacterDeleteRequestPacket SERVER",
            "CharacterRosterSyncPacket CLIENT",
            "CharacterOperationResultPacket CLIENT",
            "CharacterAppearanceSyncPacket CLIENT",
            "CharacterCreationCatalogSyncPacket CLIENT",
            "CharacterCapeUpdateRequestPacket SERVER",
            "PartyActionRequestPacket SERVER",
            "PartyStateSyncPacket CLIENT",
            "PartyOperationResultPacket CLIENT",
            "PartyMemberStatusSyncPacket CLIENT",
            "PartyTrackingSyncPacket CLIENT",
            "LoreCharacterClaimRequestPacket SERVER",
            "LoreCharacterReleaseRequestPacket SERVER",
            "LoreCharacterSyncPacket CLIENT",
            "LostTalesThirdPersonEntityActionPacket SERVER",
            "LostTalesThirdPersonBlockActionPacket SERVER",
            "LostTalesThirdPersonAimPacket SERVER",
            "LostTalesChargeTierSyncPacket CLIENT",
            "AccessoryInventorySyncPacket CLIENT",
            "AccessoryEffectSyncPacket CLIENT",
            "LostTalesMapMarkerSnapshotPacket CLIENT",
            "LostTalesWaystoneSettingsRequestPacket SERVER",
            "LostTalesWaystoneStatePacket CLIENT",
            "LostTalesWaystoneTravelRequestPacket SERVER",
            "LostTalesChatSendPacket SERVER",
            "LostTalesChatMessagePacket CLIENT",
            "LostTalesChatAccessPacket CLIENT",
            "LostTalesFastTravelArrivalPacket CLIENT",
            "LostTalesChatTypingPacket SERVER",
            "LostTalesChatTypingSyncPacket CLIENT",
            "LostTalesChatEditPacket SERVER",
            "LostTalesChatDeletePacket SERVER",
            "LostTalesChatUpdatePacket CLIENT",
            "LostTalesServerConfigRequestPacket SERVER",
            "LostTalesServerConfigSyncPacket CLIENT",
            "LostTalesServerConfigApplyPacket SERVER",
            "LostTalesServerConfigResultPacket CLIENT",
            "LostTalesChatHistorySyncPacket CLIENT",
            "LostTalesChatConsoleSyncPacket CLIENT",
            "LostTalesChatContextHistoryPacket SERVER",
            "CharacterTemplateAdoptRequestPacket SERVER"
    };

    /** One registerMessage call, read back off the stack that fed it. */
    private static final class Registration {
        private final String handler;
        private final String packet;
        private final int discriminator;
        private final String side;

        private Registration(String handler, String packet,
                             int discriminator, String side) {
            this.handler = handler;
            this.packet = packet;
            this.discriminator = discriminator;
            this.side = side;
        }

        private String simpleName() {
            return packet.substring(packet.lastIndexOf('/') + 1);
        }
    }

    /** Two packets on one id would make the same bytes mean two things. */
    @Test
    public void everyDiscriminatorIsUsedOnce() throws IOException {
        Map<Integer, Registration> byId = readRegistrations();
        assertEquals("the registration count changed; update EXPECTED",
                EXPECTED.length, byId.size());
        Map<String, Integer> byPacket = new TreeMap<String, Integer>();
        for (Registration registration : byId.values()) {
            Integer previous = byPacket.put(
                    registration.packet, Integer.valueOf(registration.discriminator));
            if (previous != null) {
                throw new AssertionError(registration.packet
                        + " is registered on ids " + previous + " and "
                        + registration.discriminator
                        + "; a packet belongs to exactly one discriminator");
            }
        }
    }

    /**
     * Ids are handed out in order of addition, so the next free one is the
     * count. A gap means an id was dropped instead of kept reserved, and the
     * next contributor would reuse it.
     */
    @Test
    public void discriminatorsAreContiguousFromZero() throws IOException {
        Map<Integer, Registration> byId = readRegistrations();
        for (int id = 0; id < byId.size(); id++) {
            assertTrue("discriminator " + id + " is missing; ids run from 0 "
                            + "with no gaps",
                    byId.containsKey(Integer.valueOf(id)));
        }
    }

    /**
     * The checked-in allocation. Renumbering an existing packet breaks every
     * client already on the old ids, so it has to show up as a diff here.
     */
    @Test
    public void discriminatorsMatchTheCheckedInAllocation() throws IOException {
        Map<Integer, Registration> byId = readRegistrations();
        assertEquals("the registration count changed; update EXPECTED",
                EXPECTED.length, byId.size());
        for (int id = 0; id < EXPECTED.length; id++) {
            Registration registration = byId.get(Integer.valueOf(id));
            assertTrue("discriminator " + id + " is no longer registered",
                    registration != null);
            assertEquals("discriminator " + id + " moved",
                    EXPECTED[id],
                    registration.simpleName() + " " + registration.side);
        }
    }

    /**
     * Each packet is handled by its own nested Handler. Pairing a packet with
     * another packet's handler compiles cleanly and only fails on the wire.
     */
    @Test
    public void everyPacketIsHandledByItsOwnNestedHandler() throws IOException {
        Map<Integer, Registration> byId = readRegistrations();
        for (Registration registration : byId.values()) {
            assertEquals("discriminator " + registration.discriminator
                            + " is handled by the wrong class",
                    registration.packet + "$Handler", registration.handler);
        }
    }

    /** Reads every registerMessage call in registerCommonPackets, by id. */
    private static Map<Integer, Registration> readRegistrations()
            throws IOException {
        MethodNode method = findRegisterMethod(readClass());
        Map<Integer, Registration> byId = new TreeMap<Integer, Registration>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !CHANNEL_OWNER.equals(call.owner)
                    || !"registerMessage".equals(call.name)
                    || !REGISTER_DESC.equals(call.desc)) {
                continue;
            }
            // The four arguments are pushed as constants right before the
            // call: handler class, packet class, discriminator, side.
            AbstractInsnNode sideNode = previousCode(call);
            AbstractInsnNode idNode = previousCode(sideNode);
            AbstractInsnNode packetNode = previousCode(idNode);
            AbstractInsnNode handlerNode = previousCode(packetNode);
            Registration registration = new Registration(
                    classConstant(handlerNode, "handler class"),
                    classConstant(packetNode, "packet class"),
                    intConstant(idNode),
                    sideConstant(sideNode));
            Registration previous = byId.put(
                    Integer.valueOf(registration.discriminator), registration);
            if (previous != null) {
                throw new AssertionError("discriminator "
                        + registration.discriminator + " is used by both "
                        + previous.simpleName() + " and "
                        + registration.simpleName());
            }
        }
        if (byId.isEmpty()) {
            throw new AssertionError("No registerMessage calls were found in "
                    + REGISTER_METHOD + "; this test read the wrong method and "
                    + "proved nothing");
        }
        return byId;
    }

    private static MethodNode findRegisterMethod(ClassNode owner) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (REGISTER_METHOD.equals(method.name)) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + REGISTER_METHOD);
    }

    private static String classConstant(AbstractInsnNode node, String what) {
        if (node instanceof LdcInsnNode
                && ((LdcInsnNode)node).cst instanceof Type) {
            return ((Type)((LdcInsnNode)node).cst).getInternalName();
        }
        throw new AssertionError("Expected a " + what + " constant before "
                + "registerMessage; the registration is no longer written as "
                + "one call with constant arguments, so this test can no "
                + "longer read the ids");
    }

    private static int intConstant(AbstractInsnNode node) {
        if (node instanceof IntInsnNode
                && (node.getOpcode() == Opcodes.BIPUSH
                || node.getOpcode() == Opcodes.SIPUSH)) {
            return ((IntInsnNode)node).operand;
        }
        if (node instanceof LdcInsnNode
                && ((LdcInsnNode)node).cst instanceof Integer) {
            return ((Integer)((LdcInsnNode)node).cst).intValue();
        }
        if (node != null && node.getOpcode() >= Opcodes.ICONST_0
                && node.getOpcode() <= Opcodes.ICONST_5) {
            return node.getOpcode() - Opcodes.ICONST_0;
        }
        throw new AssertionError("The discriminator is not a literal; ids must "
                + "stay written out in registerCommonPackets so they can be "
                + "read and checked");
    }

    private static String sideConstant(AbstractInsnNode node) {
        if (node instanceof FieldInsnNode
                && node.getOpcode() == Opcodes.GETSTATIC
                && SIDE_OWNER.equals(((FieldInsnNode)node).owner)) {
            return ((FieldInsnNode)node).name;
        }
        throw new AssertionError("Expected a Side constant before "
                + "registerMessage");
    }

    /** The previous real instruction, skipping labels and line numbers. */
    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction == null
                ? null : instruction.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static ClassNode readClass() throws IOException {
        InputStream stream = LostTalesNetworkHandlerDiscriminatorTest.class
                .getResourceAsStream(HANDLER_CLASS);
        if (stream == null) {
            throw new IOException("missing " + HANDLER_CLASS);
        }
        try {
            ClassNode owner = new ClassNode();
            new ClassReader(stream).accept(owner, 0);
            return owner;
        } finally {
            stream.close();
        }
    }
}
