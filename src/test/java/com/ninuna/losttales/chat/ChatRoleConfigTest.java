package com.ninuna.losttales.chat;

import com.ninuna.losttales.permission.LostTalesCapability;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The config form reads back into the catalogue it describes, refuses
 * the team mark and the malformed with a warning apiece, and writes
 * itself out again.
 */
public final class ChatRoleConfigTest {

    private static final UUID STEVE = UUID.fromString("c6000000-0000-0000-0000-00000000006c");
    private static final UUID ALEX = UUID.fromString("d6000000-0000-0000-0000-00000000006d");

    private final List<String> warnings = new ArrayList<String>();
    private final ChatRoleConfig.Warnings collect = new ChatRoleConfig.Warnings() {
        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    };

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void rolesMembersAndSourcesAreRead() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "# comment",
                "moderator=name:Moderator;tag:[Mod];color:#A94B54;mention:true;rank:15;op:1;"
                        + "faction:GONDOR@gondor.knight;desc:Keeps the peace.",
                "builder=color:112233;mention:false",
        }, new String[] {
                "moderator=" + STEVE + "," + ALEX,
                "builder=" + STEVE + ", not-a-uuid",
        }, collect);
        ChatAccountRole moderator = catalog.byId("moderator");
        assertNotNull(moderator);
        assertEquals("Moderator", moderator.getName());
        assertEquals("[Mod]", moderator.getTag());
        assertEquals(0xA94B54, moderator.getColor());
        assertTrue(moderator.isMentionable());
        assertEquals(15, moderator.getRank());
        assertEquals("Keeps the peace.", moderator.getDescription());
        assertEquals(2, moderator.getSources().size());
        assertEquals(ChatRoleSource.Kind.OP_LEVEL, moderator.getSources().get(0).getKind());
        assertEquals(1, moderator.getSources().get(0).getLevel());
        assertEquals("GONDOR", moderator.getSources().get(1).getFaction());
        assertEquals("gondor.knight", moderator.getSources().get(1).getRank());
        ChatAccountRole builder = catalog.byId("builder");
        assertEquals("builder", builder.getDisplayName());
        assertEquals("[builder]", builder.getDisplayTag());
        assertFalse(builder.isMentionable());
        assertEquals(0x112233, builder.getColor());
        assertEquals(new HashSet<UUID>(Arrays.asList(STEVE, ALEX)),
                catalog.membersOf("moderator"));
        assertEquals(Collections.singleton(STEVE), catalog.membersOf("builder"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("not-a-uuid"));
        // Bits after the built-ins, in file order; precedence by rank.
        assertEquals(4, moderator.bit());
        assertEquals(8, builder.bit());
        assertEquals(Arrays.asList(ChatAccountRole.TEAM, ChatAccountRole.OPERATOR,
                moderator, builder), catalog.roles());
    }

    @Test
    public void theTeamMarkAndTheMalformedAreRefusedWithAWarning() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "team=name:Owner",
                "Bad Id=name:x",
                "dup=name:One",
                "dup=name:Two",
                "operator=name:Staff;tag:[Staff];color:00FF00;op:4",
        }, new String[] {
                "team=" + STEVE,
                "ghost=" + STEVE,
        }, collect);
        assertNull(catalog.byId("bad id"));
        assertEquals("One", catalog.byId("dup").getName());
        ChatAccountRole operator = catalog.operator();
        assertEquals("Staff", operator.getDisplayName());
        assertEquals("[Staff]", operator.getDisplayTag());
        assertEquals(0x00FF00, operator.getColor());
        assertEquals(ChatAccountRole.OPERATOR.bit(), operator.bit());
        assertEquals(1, operator.getSources().size());
        assertEquals(2, operator.getSources().get(0).getLevel());
        assertTrue(catalog.membersOf("team").isEmpty());
        assertTrue(catalog.membersOf("ghost").isEmpty());
        assertEquals(6, warnings.size());
    }

    /**
     * A grant names a capability; one naming nothing known is skipped
     * with a warning, and the operator's are ignored because an operator
     * holds every capability already.
     */
    @Test
    public void grantsAreReadWarnedAndKeptOffTheOperator() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "moderator=name:Moderator;grant:chat.moderate;grant:Server.Config",
                "builder=grant:build.everything",
                "operator=grant:chat.moderate",
        }, null, collect);
        assertEquals(java.util.EnumSet.of(LostTalesCapability.CHAT_MODERATE,
                LostTalesCapability.SERVER_CONFIG), catalog.byId("moderator").getGrants());
        assertTrue(catalog.byId("builder").getGrants().isEmpty());
        assertTrue(catalog.operator().getGrants().isEmpty());
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("build.everything"));
        assertTrue(warnings.get(1).contains("operator"));
        assertTrue(ChatRoleConfig.formatRole(catalog.byId("moderator"))
                .endsWith(";grant:chat.moderate;grant:server.config"));
        assertFalse(ChatRoleConfig.formatRole(catalog.operator()).contains("grant:"));
    }

    @Test
    public void gatesNameTheRolesAChannelAsksFor() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {"moderator=name:Mod"},
                null, collect);
        ChatRoleCatalog.installServer(catalog);
        ChatChannelGates gates = ChatRoleConfig.parseGates(new String[] {
                "all=send:moderator,operator",
                "admin=read:moderator,operator;send:operator",
                "nowhere=read:operator",
                "ooc=read:ghost",
        }, catalog, collect);
        int moderator = catalog.byId("moderator").bit();
        int operator = ChatAccountRole.OPERATOR.bit();
        assertTrue(gates.canRead(0, ChatChannel.ALL));
        assertFalse(gates.canSend(0, ChatChannel.ALL));
        assertTrue(gates.canSend(moderator, ChatChannel.ALL));
        assertTrue(gates.canRead(moderator, ChatChannel.ADMIN));
        assertFalse(gates.canSend(moderator, ChatChannel.ADMIN));
        assertTrue(gates.canSend(operator, ChatChannel.ADMIN));
        assertTrue(gates.canRead(0, ChatChannel.OOC));
        assertTrue(gates.isGated(ChatChannel.ALL));
        assertFalse(gates.isGated(ChatChannel.PARTY));
        assertEquals(2, warnings.size());
        // The defaults: the Operator channel is the operators' alone.
        ChatChannelGates defaults = ChatChannelGates.defaults();
        assertFalse(defaults.canRead(moderator, ChatChannel.ADMIN));
        assertTrue(defaults.canRead(operator, ChatChannel.ADMIN));
        assertEquals("admin=read:moderator,operator;send:operator",
                ChatChannelGates.format(ChatChannel.ADMIN, gates.gateOf(ChatChannel.ADMIN)));
        assertNull(ChatChannelGates.format(ChatChannel.PARTY, gates.gateOf(ChatChannel.PARTY)));
    }

    @Test
    public void rolesAndMembersWriteThemselvesBackAndRoundTrip() {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator", "[Mod]",
                "Keeps the peace.", 0xA94B54, true, 15,
                Arrays.asList(ChatRoleSource.opLevel(1),
                        ChatRoleSource.factionRank("gondor", "Gondor.Knight")));
        String entry = ChatRoleConfig.formatRole(moderator);
        assertEquals("moderator=name:Moderator;tag:[Mod];color:A94B54;mention:true;rank:15;"
                + "op:1;faction:GONDOR@gondor.knight;desc:Keeps the peace.", entry);
        ChatAccountRole again = ChatRoleConfig.parse(new String[] {entry}, null, collect)
                .byId("moderator");
        assertEquals(entry, ChatRoleConfig.formatRole(again));

        List<String> upserted = ChatRoleConfig.upsertRole(
                new String[] {"# keep", "moderator=name:Old", "builder=name:B"}, moderator);
        assertEquals(Arrays.asList("# keep", entry, "builder=name:B"), upserted);
        assertEquals(Arrays.asList("# keep", "builder=name:B"),
                ChatRoleConfig.removeKey(new String[] {"# keep", "moderator=x", "builder=name:B"},
                        "MODERATOR"));
        Set<UUID> members = new HashSet<UUID>(Arrays.asList(ALEX, STEVE));
        List<String> written = ChatRoleConfig.withMembers(
                new String[] {"builder=" + STEVE, "moderator=old"}, "moderator", members);
        assertEquals(Arrays.asList("builder=" + STEVE, "moderator=" + STEVE + "," + ALEX),
                written);
        assertEquals(Collections.singletonList("builder=" + STEVE),
                ChatRoleConfig.withMembers(new String[] {"builder=" + STEVE, "moderator=old"},
                        "moderator", Collections.<UUID>emptySet()));
    }
}
