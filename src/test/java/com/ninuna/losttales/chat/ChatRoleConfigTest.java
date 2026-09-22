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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The config form reads back into the catalogue it describes, refuses
 * the team mark and the malformed with a warning apiece, treats the
 * operator role as a role like any other, closes a gate that names a
 * role nobody has, and writes itself out again.
 */
public final class ChatRoleConfigTest {

    private static final UUID STEVE = UUID.fromString("c6000000-0000-0000-0000-00000000006c");
    private static final UUID ALEX = UUID.fromString("d6000000-0000-0000-0000-00000000006d");
    private static final UUID ALDRIC = UUID.fromString("e6000000-0000-0000-0000-00000000006e");

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
        ChatRoleCatalog.installServer(null);
    }

    /** A role's icon is written as a channel's is, read back and written out again. */
    @Test
    public void aRoleWearsTheIconItsEntryNames() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "herald=name:Herald;icon:emoji:bee",
                "smith=name:Smith;icon:item:minecraft:iron_sword@3",
                "scribe=name:Scribe;icon:not an icon",
                "reeve=name:Reeve",
        }, null, collect);
        ChatAccountRole herald = catalog.byId("herald");
        assertEquals(ChatChannelIconSpec.Kind.EMOJI, herald.getIcon().getKind());
        assertEquals("bee", herald.getIcon().getName());
        ChatAccountRole smith = catalog.byId("smith");
        assertEquals(ChatChannelIconSpec.Kind.ITEM, smith.getIcon().getKind());
        assertEquals(3, smith.getIcon().getMeta());
        // Text that names no icon wears the plain face, and says so.
        assertNull(catalog.byId("scribe").getIcon());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("scribe"));
        assertNull(catalog.byId("reeve").getIcon());
        assertTrue(ChatRoleConfig.formatRole(herald).contains(";icon:emoji:bee"));
        assertTrue(ChatRoleConfig.formatRole(smith)
                .contains(";icon:item:minecraft:iron_sword@3"));
        assertFalse(ChatRoleConfig.formatRole(catalog.byId("reeve"))
                .contains("icon:"));
        // The seeded operator wears the Operator channel's face.
        ChatRoleCatalog seeded = ChatRoleConfig.parse(new String[] {
                ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null, collect);
        assertEquals("expressionless",
                seeded.byId("operator").getIcon().getName());
        // The team mark wears its own, in the code.
        assertEquals("purple_heart", ChatAccountRole.TEAM.getIcon().getName());
    }

    @Test
    public void rolesMembersAndSourcesAreRead() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "# comment",
                "moderator=name:Moderator;color:#A94B54;mention:true;rank:15;op:1;"
                        + "faction:GONDOR@gondor.knight;desc:Keeps the peace.",
                "builder=color:112233;mention:false",
        }, new String[] {
                "moderator=" + STEVE + "," + ALEX,
                "builder=" + STEVE + ", not-a-uuid",
        }, collect);
        ChatAccountRole moderator = catalog.byId("moderator");
        assertNotNull(moderator);
        assertEquals("Moderator", moderator.getName());
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
        assertFalse(builder.isMentionable());
        assertEquals(0x112233, builder.getColor());
        assertEquals(new HashSet<UUID>(Arrays.asList(STEVE, ALEX)),
                catalog.membersOf("moderator"));
        assertEquals(Collections.singleton(STEVE), catalog.membersOf("builder"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("not-a-uuid"));
        // Bits after the team mark, in file order; precedence by rank. A
        // file that lists no operator role has none.
        assertEquals(2, moderator.bit());
        assertEquals(4, builder.bit());
        assertNull(catalog.byId(ChatRoleFixtures.OPERATOR_ID));
        assertEquals(Arrays.asList(ChatAccountRole.TEAM, moderator, builder), catalog.roles());
    }

    /** The seeded operator entry reads as the default the code describes. */
    @Test
    public void theSeededOperatorIsARoleLikeAnyOther() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null, collect);
        ChatAccountRole operator = catalog.byId(ChatRoleFixtures.OPERATOR_ID);
        assertNotNull(operator);
        assertEquals("Operator", operator.getDisplayName());
        assertEquals(0xA94B54, operator.getColor());
        assertEquals(10, operator.getRank());
        assertEquals(2, operator.bit());
        assertEquals("Runs the server day to day.", operator.getDisplayDescription());
        assertTrue(operator.isMentionable());
        assertFalse(operator.isLocked());
        assertEquals(1, operator.getSources().size());
        assertEquals(2, operator.getSources().get(0).getLevel());
        assertTrue(operator.getGrants().isEmpty());
        assertTrue(warnings.isEmpty());
        // Restyled, regranted, given another source: the file's word holds.
        ChatRoleCatalog restyled = ChatRoleConfig.parse(new String[] {
                "operator=name:Staff;color:00FF00;op:4;grant:chat.moderate",
        }, null, collect);
        ChatAccountRole staff = restyled.byId(ChatRoleFixtures.OPERATOR_ID);
        assertEquals("Staff", staff.getDisplayName());
        assertEquals(0x00FF00, staff.getColor());
        assertEquals(4, staff.getSources().get(0).getLevel());
        assertEquals(java.util.Collections.singleton(
                LostTalesCapability.CHAT_MODERATE.getId()), staff.getGrants());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void theTeamMarkAndTheMalformedAreRefusedWithAWarning() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "team=name:Owner",
                "Bad Id=name:x",
                "dup=name:One",
                "dup=name:Two",
        }, new String[] {
                "team=" + STEVE,
                "ghost=" + STEVE,
        }, collect);
        assertNull(catalog.byId("bad id"));
        assertNull(catalog.byId("team=name:Owner"));
        assertEquals("One", catalog.byId("dup").getName());
        assertTrue(catalog.membersOf("team").isEmpty());
        assertTrue(catalog.membersOf("ghost").isEmpty());
        assertEquals(5, warnings.size());
    }

    /**
     * A grant names a permission, or a capability directly; one naming
     * neither is kept, warned about, and allows nothing until a
     * permission of that id is defined.
     */
    @Test
    public void grantsAreReadAndWarned() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "moderator=name:Moderator;grant:chat.moderate;grant:Server.Config",
                "builder=grant:build.everything",
        }, null, collect);
        assertEquals(new java.util.LinkedHashSet<String>(java.util.Arrays.asList(
                        LostTalesCapability.CHAT_MODERATE.getId(),
                        LostTalesCapability.SERVER_CONFIG.getId())),
                catalog.byId("moderator").getGrants());
        // A grant naming nothing known is kept as written, so a
        // permission defined later starts working without the role
        // being rewritten; the operator is told it allows nothing yet.
        assertEquals(java.util.Collections.singleton("build.everything"),
                catalog.byId("builder").getGrants());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("build.everything"));
        assertTrue(ChatRoleConfig.formatRole(catalog.byId("moderator"))
                .endsWith(";grant:chat.moderate;grant:server.config"));
        assertTrue(ChatRoleConfig.formatRole(catalog.byId("builder"))
                .endsWith(";grant:build.everything"));
    }

    /**
     * An account member holds the role as every identity; a character
     * member holds it as that character alone. Both read and write.
     */
    @Test
    public void characterMembersAreKeptApartFromAccounts() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {"knight=name:Knight"},
                new String[] {
                        "knight=" + STEVE + ",character:" + ALDRIC + ",Character:not-a-uuid",
                }, collect);
        assertEquals(Collections.singleton(STEVE), catalog.membersOf("knight"));
        assertEquals(Collections.singleton(ALDRIC), catalog.characterMembersOf("knight"));
        assertTrue(catalog.characterMembersOf("nobody").isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("character UUID"));
        List<String> written = ChatRoleConfig.withMembers(new String[] {"builder=" + STEVE},
                "knight", Collections.singleton(STEVE), Collections.singleton(ALDRIC));
        assertEquals(Arrays.asList("builder=" + STEVE,
                "knight=" + STEVE + ",character:" + ALDRIC), written);
        // Characters alone keep the entry; nobody at all drops it.
        assertEquals(Collections.singletonList("knight=character:" + ALDRIC),
                ChatRoleConfig.withMembers(new String[0], "knight",
                        Collections.<UUID>emptySet(), Collections.singleton(ALDRIC)));
        assertTrue(ChatRoleConfig.withMembers(new String[] {"knight=" + STEVE}, "knight",
                Collections.<UUID>emptySet(), Collections.<UUID>emptySet()).isEmpty());
    }

    @Test
    public void gatesNameTheRolesAChannelAsksFor() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                ChatRoleConfig.DEFAULT_OPERATOR_ENTRY, "moderator=name:Mod"}, null, collect);
        ChatRoleCatalog.installServer(catalog);
        ChatChannelGates gates = ChatRoleConfig.parseGates(new String[] {
                "all=send:moderator,operator",
                "admin=read:moderator,operator;send:operator",
                "nowhere=read:operator",
                "ooc=read:ghost",
                "party=send:none",
        }, catalog, collect);
        int moderator = catalog.byId("moderator").bit();
        int operator = catalog.byId("operator").bit();
        assertTrue(gates.canRead(0, ChatChannel.ALL));
        assertFalse(gates.canSend(0, ChatChannel.ALL));
        assertTrue(gates.canSend(moderator, ChatChannel.ALL));
        assertTrue(gates.canRead(moderator, ChatChannel.ADMIN));
        assertFalse(gates.canSend(moderator, ChatChannel.ADMIN));
        assertTrue(gates.canSend(operator, ChatChannel.ADMIN));
        assertTrue(gates.isGated(ChatChannel.ALL));
        assertFalse(gates.isGated(ChatChannel.PROXIMITY));
        // A side naming a role nobody has is closed to everyone, an
        // operator included, until the entry is fixed; so is "none".
        assertFalse(gates.canRead(moderator | operator, ChatChannel.OOC));
        assertTrue(gates.canSend(0, ChatChannel.OOC));
        assertTrue(gates.isGated(ChatChannel.OOC));
        assertFalse(gates.canSend(operator, ChatChannel.PARTY));
        assertTrue(gates.canRead(0, ChatChannel.PARTY));
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(1).contains("closed"));
        // Nothing is gated before the file says so; the seeded entry is
        // what makes the Operator channel the operators' alone.
        ChatChannelGates defaults = ChatChannelGates.defaults();
        assertTrue(defaults.canRead(moderator, ChatChannel.ADMIN));
        ChatChannelGates seeded = ChatRoleConfig.parseGates(
                new String[] {ChatRoleConfig.DEFAULT_ADMIN_GATE}, catalog, collect);
        assertFalse(seeded.canRead(moderator, ChatChannel.ADMIN));
        assertTrue(seeded.canRead(operator, ChatChannel.ADMIN));
        assertFalse(seeded.canSend(0, ChatChannel.ADMIN));
        assertEquals("admin=read:moderator,operator;send:operator",
                ChatChannelGates.format(ChatChannel.ADMIN, gates.gateOf(ChatChannel.ADMIN)));
        assertEquals("party=read:any;send:none",
                ChatChannelGates.format(ChatChannel.PARTY, gates.gateOf(ChatChannel.PARTY)));
        assertNull(ChatChannelGates.format(ChatChannel.PROXIMITY,
                gates.gateOf(ChatChannel.PROXIMITY)));
    }

    /**
     * A missing Operator gate line is put back with a warning; a line
     * that names the channel, however open, is a decision and stands.
     */
    @Test
    public void theOperatorGateIsPutBackWhenItsLineIsMissing() {
        String[] reseeded = ChatRoleConfig.withRequiredGates(new String[] {
                "# gates", "all=send:moderator"}, collect);
        assertEquals(3, reseeded.length);
        assertEquals(ChatRoleConfig.DEFAULT_ADMIN_GATE, reseeded[2]);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Operator channel"));
        assertEquals(1, ChatRoleConfig.withRequiredGates(null, collect).length);
        assertEquals(1, ChatRoleConfig.withRequiredGates(new String[0], collect).length);
        warnings.clear();
        String[] opened = new String[] {"admin=read:any;send:any"};
        assertSame(opened, ChatRoleConfig.withRequiredGates(opened, collect));
        String[] spaced = new String[] {" Admin = read:operator;send:none "};
        assertSame(spaced, ChatRoleConfig.withRequiredGates(spaced, collect));
        assertTrue(warnings.isEmpty());
        // The put-back line is the seeded gate: the operators' alone.
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                ChatRoleConfig.DEFAULT_OPERATOR_ENTRY, "moderator=name:Mod"}, null, collect);
        ChatRoleCatalog.installServer(catalog);
        ChatChannelGates gates = ChatRoleConfig.parseGates(
                ChatRoleConfig.withRequiredGates(new String[0], collect), catalog, collect);
        assertTrue(gates.hasEntry(ChatChannel.ADMIN));
        assertFalse(gates.canRead(catalog.byId("moderator").bit(), ChatChannel.ADMIN));
        assertTrue(gates.canSend(catalog.byId("operator").bit(), ChatChannel.ADMIN));
    }

    @Test
    public void rolesAndMembersWriteThemselvesBackAndRoundTrip() {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator",
                "Keeps the peace.", 0xA94B54, true, 15,
                Arrays.asList(ChatRoleSource.opLevel(1),
                        ChatRoleSource.factionRank("gondor", "Gondor.Knight")));
        String entry = ChatRoleConfig.formatRole(moderator);
        assertEquals("moderator=name:Moderator;color:A94B54;mention:true;rank:15;"
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
