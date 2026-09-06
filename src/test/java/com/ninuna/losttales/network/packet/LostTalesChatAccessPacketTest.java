package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleSource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The chat access packet carries the player's own roles beside the two
 * channel flags, so the client can notice a mention addressed to one of
 * them. The roles sit at the end of the layout: a payload written before
 * they existed still reads, and names none. The catalogue in force, the
 * channel gates and the two capability flags ride after them the same
 * way.
 */
public final class LostTalesChatAccessPacketTest {

    @Before
    public void setUp() {
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void accessAndRolesRoundTrip() {
        int roles = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        LostTalesChatAccessPacket packet =
                new LostTalesChatAccessPacket(true, false, roles);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.hasAdminAccess());
        assertFalse(decoded.hasDiscordAccess());
        assertEquals(roles, decoded.getRoleMask());
        assertEquals(2, decoded.getCatalog().size());
        assertEquals(LostTalesChatAccessPacket.ALL_CHANNELS, decoded.getReadableChannels());
    }

    @Test
    public void aPayloadWithoutRolesNamesNone() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBoolean(false);
        buffer.writeBoolean(true);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.hasAdminAccess());
        assertTrue(decoded.hasDiscordAccess());
        assertEquals(0, decoded.getRoleMask());
        assertEquals(ChatRoleCatalog.builtIn().roles(), decoded.getCatalog());
        assertEquals(LostTalesChatAccessPacket.ALL_CHANNELS, decoded.getSendableChannels());
    }

    /** A mask naming roles this build does not know is taken as none. */
    @Test
    public void anUnknownMaskIsDiscardedRatherThanShown() {
        LostTalesChatAccessPacket packet =
                new LostTalesChatAccessPacket(false, false, 0x40000000);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(0, decoded.getRoleMask());
    }

    @Test
    public void anOversizedPayloadIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        for (int index = 0; index < 64; index++) {
            buffer.writeByte(1);
        }
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.hasAdminAccess());
        assertEquals(0, decoded.getRoleMask());
    }

    /** The catalogue the server sends is what the masks are read against. */
    @Test
    public void theCatalogueAndTheGatesRoundTrip() {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator", "[Mod]",
                "Keeps the peace.", 0xA94B54, true, 15,
                Collections.singletonList(ChatRoleSource.opLevel(1)));
        ChatRoleCatalog catalog = ChatRoleCatalog.of(Arrays.asList(
                ChatRoleFixtures.OPERATOR.withLook("Staff", "[Staff]", "", 0x00FF00, true, 10),
                moderator), null, null);
        int held = catalog.byId("moderator").bit() | ChatRoleFixtures.OPERATOR.bit();
        int readable = 1 << ChatChannel.ALL.ordinal() | 1 << ChatChannel.ADMIN.ordinal();
        int sendable = 1 << ChatChannel.ALL.ordinal();
        LostTalesChatAccessPacket packet = new LostTalesChatAccessPacket(false, true, held,
                Collections.singletonList(new LostTalesChatAccessPacket.RoleHolder("Steve", held)),
                Collections.<UUID>emptyList(), catalog.roles(), readable, sendable);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        // The built-in catalogue is in force here, yet the mask is read
        // against the one the packet carries.
        assertEquals(held, decoded.getRoleMask());
        assertEquals(held, decoded.getRoleHolders().get(0).getMask());
        List<ChatAccountRole> roles = decoded.getCatalog();
        assertEquals(3, roles.size());
        ChatRoleCatalog read = ChatRoleCatalog.fromWire(roles);
        assertEquals("Moderator", read.byId("moderator").getDisplayName());
        assertEquals("[Mod]", read.byId("moderator").getDisplayTag());
        assertEquals("Keeps the peace.", read.byId("moderator").getDisplayDescription());
        assertEquals(4, read.byId("moderator").bit());
        assertEquals("Staff", read.byId("operator").getDisplayName());
        assertEquals(0x00FF00, read.byId("operator").getColor());
        assertTrue(read.byId("team").isLocked());
        assertEquals("Lost Tales Team", read.byId("team").getName().length() == 0
                ? "Lost Tales Team" : read.byId("team").getName());
        assertEquals(Arrays.asList("team", "operator", "moderator"), ids(roles));
        assertEquals(readable, decoded.getReadableChannels());
        assertEquals(sendable, decoded.getSendableChannels());
        assertFalse(decoded.canModerate());
        assertFalse(decoded.canEditServerConfig());
    }

    /** The capability flags ride last; a payload without them says no to both. */
    @Test
    public void theCapabilityFlagsRoundTripAndDefaultToNo() {
        LostTalesChatAccessPacket packet = new LostTalesChatAccessPacket(false, true, 0,
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList(),
                Collections.<UUID>emptyList(), ChatRoleCatalog.builtIn().roles(),
                LostTalesChatAccessPacket.ALL_CHANNELS,
                LostTalesChatAccessPacket.ALL_CHANNELS, true, false);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.canModerate());
        assertFalse(decoded.canEditServerConfig());

        // Written by a build before the flags: everything up to the gates.
        ByteBuf older = Unpooled.buffer();
        older.writeBoolean(true);
        older.writeBoolean(true);
        older.writeInt(0);
        older.writeShort(0);
        older.writeShort(0);
        older.writeByte(0);
        older.writeInt(LostTalesChatAccessPacket.ALL_CHANNELS);
        older.writeInt(LostTalesChatAccessPacket.ALL_CHANNELS);
        LostTalesChatAccessPacket fromOlder = new LostTalesChatAccessPacket();
        fromOlder.fromBytes(older);
        assertFalse(fromOlder.isMalformed());
        assertTrue(fromOlder.hasAdminAccess());
        assertFalse(fromOlder.canModerate());
        assertFalse(fromOlder.canEditServerConfig());
    }

    private static List<String> ids(List<ChatAccountRole> roles) {
        List<String> ids = new java.util.ArrayList<String>();
        for (ChatAccountRole role : roles) {
            ids.add(role.getId());
        }
        return ids;
    }
}
