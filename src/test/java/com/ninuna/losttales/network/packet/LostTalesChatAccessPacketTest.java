package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleSource;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
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
 * The chat access packet carries the player's own roles beside the
 * Operator channel's gate, so the client can notice a mention addressed
 * to one of them, then the catalogue in force, the channel gates, the
 * capability flags and everything else the class comment lists. The
 * layout is complete or the payload is malformed.
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

    private static LostTalesChatAccessPacket roundTrip(
            LostTalesChatAccessPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    @Test
    public void accessAndRolesRoundTrip() {
        int roles = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        LostTalesChatAccessPacket decoded = roundTrip(
                new LostTalesChatAccessPacket(true, roles));
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.hasAdminAccess());
        assertEquals(roles, decoded.getRoleMask());
        assertEquals(2, decoded.getCatalog().size());
        assertEquals(LostTalesChatAccessPacket.allChannelIds(),
                decoded.getReadableChannels());
        assertTrue(decoded.getProfanityWords().isEmpty());
    }

    /** A payload that stops short of the whole layout is refused whole. */
    @Test
    public void aPayloadThatStopsShortIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBoolean(false);
        buffer.writeInt(0);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.hasAdminAccess());
        assertEquals(0, decoded.getRoleMask());
        assertTrue(decoded.getCatalog().isEmpty());
        assertEquals(LostTalesChatAccessPacket.allChannelIds(),
                decoded.getSendableChannels());
    }

    /** A mask naming roles this build does not know is taken as none. */
    @Test
    public void anUnknownMaskIsDiscardedRatherThanShown() {
        LostTalesChatAccessPacket decoded = roundTrip(
                new LostTalesChatAccessPacket(false, 0x40000000));
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

    /**
     * The capabilities the player holds travel by id, so the client's
     * menus follow what the server can actually do rather than a flag
     * per capability.
     */
    @Test
    public void theHeldCapabilitiesRoundTrip() {
        List<String> held = Arrays.asList(
                com.ninuna.losttales.permission.LostTalesCapability.CHAT_MODERATE.getId(),
                com.ninuna.losttales.permission.LostTalesCapability.WAYSTONE_MANAGE.getId());
        LostTalesChatAccessPacket decoded = roundTrip(new LostTalesChatAccessPacket(false, 0,
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList(),
                Collections.<UUID>emptyList(),
                Collections.<ChatAccountRole>emptyList(),
                LostTalesChatAccessPacket.allChannelIds(),
                LostTalesChatAccessPacket.allChannelIds(),
                true, false, held));
        assertFalse(decoded.isMalformed());
        assertEquals(held, decoded.getCapabilities());
        assertTrue(decoded.canModerate());
        assertFalse(decoded.canEditServerConfig());
    }

    /** More capability ids than a player could hold is a malformed payload. */
    @Test
    public void tooManyCapabilitiesAreRefused() {
        ByteBuf buffer = header();
        buffer.writeByte(0);
        buffer.writeByte(0);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeShort(LostTalesChatAccessPacket.MAX_CAPABILITIES + 1);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getCapabilities().isEmpty());
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
        List<String> readable = Arrays.asList(
                ChatChannel.ALL.getId(), ChatChannel.ADMIN.getId());
        List<String> sendable = Arrays.asList(ChatChannel.ALL.getId());
        LostTalesChatAccessPacket decoded = roundTrip(new LostTalesChatAccessPacket(false, held,
                Collections.singletonList(new LostTalesChatAccessPacket.RoleHolder("Steve", held)),
                Collections.<UUID>emptyList(), catalog.roles(), readable, sendable));
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

    @Test
    public void theCapabilityFlagsRoundTrip() {
        LostTalesChatAccessPacket decoded = roundTrip(new LostTalesChatAccessPacket(false, 0,
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList(),
                Collections.<UUID>emptyList(), ChatRoleCatalog.builtIn().roles(),
                LostTalesChatAccessPacket.allChannelIds(),
                LostTalesChatAccessPacket.allChannelIds(), true, false));
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.canModerate());
        assertFalse(decoded.canEditServerConfig());
    }

    /**
     * The icons the server puts on its channels travel by id, and one
     * naming an icon that reads as nothing is refused.
     */
    @Test
    public void theChannelIconsRoundTrip() {
        java.util.Map<String, com.ninuna.losttales.chat.ChatChannelIconSpec> icons =
                new java.util.LinkedHashMap<String,
                        com.ninuna.losttales.chat.ChatChannelIconSpec>();
        icons.put(ChatChannel.ADMIN.getId(),
                com.ninuna.losttales.chat.ChatChannelIconSpec.parse(
                        "item:minecraft:iron_sword"));
        icons.put(ChatChannel.PARTY.getId(),
                com.ninuna.losttales.chat.ChatChannelIconSpec.parse("emoji:joy"));
        LostTalesChatAccessPacket packet = new LostTalesChatAccessPacket(false, 0,
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList(),
                Collections.<UUID>emptyList(), ChatRoleCatalog.builtIn().roles(),
                LostTalesChatAccessPacket.allChannelIds(),
                LostTalesChatAccessPacket.allChannelIds(), false, false,
                Collections.<String>emptyList(), 0,
                Collections.<UUID, Integer>emptyMap(), 64, icons,
                ChatProfanityWords.NONE);
        LostTalesChatAccessPacket decoded = roundTrip(packet);
        assertFalse(decoded.isMalformed());
        assertEquals(64, decoded.getProximityRadius());
        assertEquals(icons, decoded.getChannelIcons());
        assertEquals(new java.util.ArrayList<String>(icons.keySet()),
                new java.util.ArrayList<String>(decoded.getChannelIcons().keySet()));

        // An icon that reads as nothing is a broken payload, not a guess.
        LostTalesChatAccessPacket bare = new LostTalesChatAccessPacket(false, 0,
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList(),
                Collections.<UUID>emptyList(), ChatRoleCatalog.builtIn().roles(),
                LostTalesChatAccessPacket.allChannelIds(),
                LostTalesChatAccessPacket.allChannelIds(), false, false,
                Collections.<String>emptyList(), 0,
                Collections.<UUID, Integer>emptyMap(), 64);
        ByteBuf broken = Unpooled.buffer();
        bare.toBytes(broken);
        // The icon count and the word count are the last three bytes.
        broken.writerIndex(broken.writerIndex() - 3);
        broken.writeByte(1);
        writeString(broken, ChatChannel.ADMIN.getId());
        writeString(broken, "item:");
        broken.writeShort(0);
        LostTalesChatAccessPacket refused = new LostTalesChatAccessPacket();
        refused.fromBytes(broken);
        assertTrue(refused.isMalformed());
    }

    /**
     * The words the server adds to the profanity list travel last, as
     * the entries a list reads; one the list would skip is refused.
     */
    @Test
    public void theServersProfanityWordsRoundTrip() {
        ChatProfanityWords words = ChatProfanityWords.parse(
                new String[] {"grumbold=grumpy", "fuck=fudge"},
                ChatProfanityWords.MAX_WORDS, null);
        LostTalesChatAccessPacket decoded = roundTrip(new LostTalesChatAccessPacket(false, 0,
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList(),
                Collections.<UUID>emptyList(), ChatRoleCatalog.builtIn().roles(),
                LostTalesChatAccessPacket.allChannelIds(),
                LostTalesChatAccessPacket.allChannelIds(), false, false,
                Collections.<String>emptyList(), 0,
                Collections.<UUID, Integer>emptyMap(), 0,
                Collections.<String, com.ninuna.losttales.chat.ChatChannelIconSpec>emptyMap(),
                words));
        assertFalse(decoded.isMalformed());
        assertEquals(words.entries(), decoded.getProfanityWords().entries());

        ByteBuf broken = Unpooled.buffer();
        new LostTalesChatAccessPacket(false, 0).toBytes(broken);
        broken.writerIndex(broken.writerIndex() - 2);
        broken.writeShort(1);
        writeString(broken, "not a word=x");
        LostTalesChatAccessPacket refused = new LostTalesChatAccessPacket();
        refused.fromBytes(broken);
        assertTrue(refused.isMalformed());
        assertTrue(refused.getProfanityWords().isEmpty());

        ByteBuf tooMany = Unpooled.buffer();
        new LostTalesChatAccessPacket(false, 0).toBytes(tooMany);
        tooMany.writerIndex(tooMany.writerIndex() - 2);
        tooMany.writeShort(ChatProfanityWords.MAX_WORDS + 1);
        LostTalesChatAccessPacket overfull = new LostTalesChatAccessPacket();
        overfull.fromBytes(tooMany);
        assertTrue(overfull.isMalformed());
    }

    private static void writeString(ByteBuf buffer, String text) {
        byte[] bytes = text.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(buffer, bytes.length, 2);
        buffer.writeBytes(bytes);
    }

    /**
     * The channel answer travels as ids, so a channel is named by the
     * string that is its permanent wire surface rather than by where its
     * constant happens to sit.
     */
    @Test
    public void theChannelAnswerTravelsAsIdsAndNotAsPositions() {
        List<String> readable = Arrays.asList(
                ChatChannel.CONSOLE.getId(), ChatChannel.OOC.getId());
        LostTalesChatAccessPacket packet = new LostTalesChatAccessPacket(false, 0,
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList(),
                Collections.<UUID>emptyList(), ChatRoleCatalog.builtIn().roles(),
                readable, Collections.<String>emptyList());
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        // The ids, not the ordinals: read off the written bytes before the
        // decode below consumes them.
        String written = buffer.toString(0, buffer.writerIndex(),
                java.nio.charset.Charset.forName("UTF-8"));
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(readable, decoded.getReadableChannels());
        assertTrue(decoded.getSendableChannels().isEmpty());
        assertTrue(written.contains(ChatChannel.CONSOLE.getId()));
        assertTrue(written.contains(ChatChannel.OOC.getId()));
    }

    /** A channel named twice, or named with nothing, is a broken payload. */
    @Test
    public void aRepeatedOrEmptyChannelIdIsRefused() {
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(channelPayload(
                ChatChannel.ALL.getId(), ChatChannel.ALL.getId()));
        assertTrue(decoded.isMalformed());

        LostTalesChatAccessPacket blank = new LostTalesChatAccessPacket();
        blank.fromBytes(channelPayload(""));
        assertTrue(blank.isMalformed());
    }

    /** A payload naming absurdly many channels is refused rather than read. */
    @Test
    public void tooManyChannelIdsAreRefused() {
        ByteBuf buffer = header();
        buffer.writeByte(200);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
    }

    /** Everything up to the channel answer, with no roles and no holders. */
    private static ByteBuf header() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBoolean(true);
        buffer.writeInt(0);
        buffer.writeShort(0);
        buffer.writeShort(0);
        buffer.writeByte(0);
        return buffer;
    }

    /** A payload whose readable side names exactly these ids, and stops there. */
    private static ByteBuf channelPayload(String... ids) {
        ByteBuf buffer = header();
        buffer.writeByte(ids.length);
        for (String id : ids) {
            byte[] bytes = id.getBytes(java.nio.charset.Charset.forName("UTF-8"));
            // The same length prefix the codec writes.
            cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(
                    buffer, bytes.length, 2);
            buffer.writeBytes(bytes);
        }
        buffer.writeByte(0);
        return buffer;
    }

    /**
     * A channel a server has of its own reaches the client whole: it
     * cannot show one it was never told about, and it must not have to
     * guess how one routes.
     */
    @Test
    public void aServerDefinedChannelTravelsWhole() {
        com.ninuna.losttales.chat.ChatChannel defined =
                com.ninuna.losttales.chat.ChatChannel.register(
                        new com.ninuna.losttales.chat.ChatChannelDescriptor(
                                "trade", "Trade",
                                com.ninuna.losttales.chat.ChatPresentationMode.OUT_OF_CHARACTER,
                                com.ninuna.losttales.chat.ChatRecipientRule.PROXIMITY,
                                com.ninuna.losttales.chat.ChatChannelAccess.NONE,
                                0xC9A227, true,
                                com.ninuna.losttales.chat.ChatChannelScope.NONE));
        try {
            LostTalesChatAccessPacket decoded = roundTrip(
                    new LostTalesChatAccessPacket(false, 0));
            assertFalse(decoded.isMalformed());
            assertEquals(1, decoded.getDefinedChannels().size());
            com.ninuna.losttales.chat.ChatChannelDescriptor read =
                    decoded.getDefinedChannels().get(0);
            assertEquals("trade", read.getId());
            assertEquals("Trade", read.getDisplayName());
            assertEquals(com.ninuna.losttales.chat.ChatRecipientRule.PROXIMITY,
                    read.getRecipientRule());
            assertEquals(0xC9A227, read.getDisplayColor());
            assertTrue(read.isBridgeable());
        } finally {
            com.ninuna.losttales.chat.ChatChannel.resetToBuiltIn();
        }
        assertEquals("the built-ins are never sent", 0,
                new LostTalesChatAccessPacket(false, 0)
                        .getDefinedChannels().size());
    }

    private static List<String> ids(List<ChatAccountRole> roles) {
        List<String> ids = new java.util.ArrayList<String>();
        for (ChatAccountRole role : roles) {
            ids.add(role.getId());
        }
        return ids;
    }
}
