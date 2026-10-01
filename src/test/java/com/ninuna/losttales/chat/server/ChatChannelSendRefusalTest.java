package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipFixtures;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Why a channel refuses a send. Membership is asked first — a fellowship line
 * needs a fellowship, a faction line a faction — and then the role gate the
 * config put on the channel. Every answer is the notice the sender is
 * told, so the reason is never guessed at the other end.
 */
public final class ChatChannelSendRefusalTest {

    private static final UUID ALDRIC =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BEREN =
            UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final String GONDOR = "lotr:gondor";

    private static final String FELLOWSHIP_REFUSAL = "chat.losttales.channel.fellowship_unavailable";
    private static final String FACTION_REFUSAL = "chat.losttales.channel.faction_unavailable";
    private static final String GATE_REFUSAL = "chat.losttales.channel.role_unavailable";

    @After
    public void tearDown() {
        ChatChannelGates.install(ChatChannelGates.defaults());
        ChatRoleCatalog.resetToBuiltIn();
    }

    private static Fellowship fellowshipOf(UUID leader) {
        ArrayList<FellowshipMember> members = new ArrayList<FellowshipMember>();
        members.add(new FellowshipMember(leader, UUID.randomUUID(), "Aldric", 1L,
                FellowshipColor.GREEN));
        return FellowshipFixtures.of(UUID.randomUUID(), leader, members);
    }

    /** An open channel refuses nobody, whatever they are or are not in. */
    @Test
    public void anOpenChannelRefusesNobody() {
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.GLOBAL, null, ALDRIC, "", 0, false, false));
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.PROXIMITY, null, ALDRIC, "", 0, false, false));
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.OOC, null, null, "", 0, false, false));
    }

    /** A channel that is not a channel at all refuses, rather than passing. */
    @Test
    public void noChannelIsRefused() {
        assertEquals(GATE_REFUSAL,
                ChatChannelPolicy.sendRefusal(null, null, ALDRIC, GONDOR, 0, false, false));
    }

    /** A fellowship line needs a fellowship, and the sender's own place in it. */
    @Test
    public void aFellowshipLineNeedsThatFellowshipsMembership() {
        assertEquals(FELLOWSHIP_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FELLOWSHIP, null, ALDRIC, "", 0, false, false));
        Fellowship fellowship = fellowshipOf(ALDRIC);
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.FELLOWSHIP, fellowship, ALDRIC, "", 0, false, false));
        assertEquals("someone else's fellowship is not the sender's",
                FELLOWSHIP_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FELLOWSHIP, fellowship, BEREN, "", 0, false, false));
        assertEquals("an identity with no gameplay id is in no fellowship",
                FELLOWSHIP_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FELLOWSHIP, fellowship, null, "", 0, false, false));
    }

    /** A faction line needs a faction: the account, which has none, is refused. */
    @Test
    public void aFactionLineNeedsAFaction() {
        assertEquals(FACTION_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FACTION, null, ALDRIC, "", 0, false, false));
        assertEquals(FACTION_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FACTION, null, ALDRIC, null, 0, false, false));
        assertNull(ChatChannelPolicy.sendRefusal(
                ChatChannel.FACTION, null, ALDRIC, GONDOR, 0, false, false));
    }

    /** Membership is asked before the gate, so the notice names the nearer reason. */
    @Test
    public void membershipIsAskedBeforeTheGate() {
        installOperatorGateOn(ChatChannel.FACTION);
        assertEquals("no faction is the reason, not the gate",
                FACTION_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FACTION, null, ALDRIC, "", 0, false, false));
    }

    /** The gate the config put on a channel refuses whoever does not hold its role. */
    @Test
    public void theGateRefusesWhoeverDoesNotHoldItsRole() {
        installOperatorGateOn(ChatChannel.OPERATOR);
        assertEquals(GATE_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.OPERATOR, null, ALDRIC, "", 0, false, false));
        int operator = ChatRoleCatalog.server().byId("operator").bit();
        assertNull(ChatChannelPolicy.sendRefusal(
                ChatChannel.OPERATOR, null, ALDRIC, "", operator, false, false));
        assertTrue("the client is told to ask again for its tabs",
                ChatChannelPolicy.isGateRefusal(GATE_REFUSAL));
        assertTrue(!ChatChannelPolicy.isGateRefusal(FACTION_REFUSAL));
    }

    /**
     * A staff channel with no gate on it is the server's operators and
     * nobody else. The config chooses which roles reach such a channel;
     * a line missing from a file does not choose that everyone does.
     */
    @Test
    public void anUngatedStaffChannelIsOperatorsOnly() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null,
                ChatRoleConfig.SILENT);
        ChatRoleCatalog.installServer(catalog);
        // A channels file that names no gate at all.
        ChatChannelGates.install(ChatChannelGates.defaults());

        assertTrue("the staff channel is restricted by its own rule",
                ChatChannelPolicy.staffOnly(ChatChannel.OPERATOR,
                        ChatChannelGates.current()));
        assertEquals("a player who is not an operator is refused",
                GATE_REFUSAL, ChatChannelPolicy.sendRefusal(
                        ChatChannel.OPERATOR, null, ALDRIC, "", 0, false, false));
        assertNull("an operator still reaches it",
                ChatChannelPolicy.sendRefusal(
                        ChatChannel.OPERATOR, null, ALDRIC, "", 0, true, false));
        assertTrue("no other channel is restricted by its rule",
                !ChatChannelPolicy.staffOnly(ChatChannel.GLOBAL,
                        ChatChannelGates.current())
                        && !ChatChannelPolicy.staffOnly(ChatChannel.OOC,
                                ChatChannelGates.current()));
    }

    /**
     * A file that names the staff channel and leaves both sides open has
     * decided; the floor is for a file that says nothing, not for one
     * that says "any".
     */
    @Test
    public void aStaffChannelTheConfigDeliberatelyOpensStaysOpen() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null,
                ChatRoleConfig.SILENT);
        ChatRoleCatalog.installServer(catalog);
        ChatChannelGates.install(ChatRoleConfig.parseGates(
                new String[] {"operator=read:any;send:any"}, catalog,
                ChatRoleConfig.SILENT));

        assertTrue("the file named it, so the floor stands down",
                !ChatChannelPolicy.staffOnly(ChatChannel.OPERATOR,
                        ChatChannelGates.current()));
        assertNull("anyone may send into it",
                ChatChannelPolicy.sendRefusal(
                        ChatChannel.OPERATOR, null, ALDRIC, "", 0, false, false));
    }

    /** With a gate in place the config decides again, operator or not. */
    @Test
    public void aGatedStaffChannelFollowsTheConfigNotTheOperatorFloor() {
        installOperatorGateOn(ChatChannel.OPERATOR);
        assertTrue("a gate is what the config put there",
                !ChatChannelPolicy.staffOnly(ChatChannel.OPERATOR,
                        ChatChannelGates.current()));
        assertEquals("the gate refuses whoever does not hold its role",
                GATE_REFUSAL, ChatChannelPolicy.sendRefusal(
                        ChatChannel.OPERATOR, null, ALDRIC, "", 0, true, false));
    }

    /** Installs the seeded operator role and puts its gate on one channel. */
    private static void installOperatorGateOn(ChatChannel channel) {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null,
                ChatRoleConfig.SILENT);
        ChatRoleCatalog.installServer(catalog);
        ChatAccountRole operator = catalog.byId("operator");
        assertTrue("the fixture role exists", operator != null);
        ChatChannelGates.install(ChatRoleConfig.parseGates(
                new String[] {channel.getId() + "=read:operator;send:operator"},
                catalog, ChatRoleConfig.SILENT));
    }

    /**
     * The Server Console is opened by the {@code chat.server_console.read}
     * capability and by nothing else: no role, no gate and no operator
     * level reaches it on their own, and holding the capability reaches
     * it whatever the config says.
     */
    @Test
    public void theServerConsoleAsksForItsCapabilityAlone() {
        assertTrue("the rule is what marks the channel",
                ChatChannelPolicy.isServerConsole(ChatChannel.SERVER_CONSOLE));
        assertTrue("no other channel takes it",
                !ChatChannelPolicy.isServerConsole(ChatChannel.CLIENT_CONSOLE)
                        && !ChatChannelPolicy.isServerConsole(ChatChannel.OPERATOR));
        assertEquals("a player without the capability is refused",
                GATE_REFUSAL, ChatChannelPolicy.sendRefusal(
                        ChatChannel.SERVER_CONSOLE, null, ALDRIC, "", 0,
                        false, false));
        assertEquals("being an operator is not the question asked",
                GATE_REFUSAL, ChatChannelPolicy.sendRefusal(
                        ChatChannel.SERVER_CONSOLE, null, ALDRIC, "", 0,
                        true, false));
        assertNull("a reader reaches it",
                ChatChannelPolicy.sendRefusal(ChatChannel.SERVER_CONSOLE,
                        null, ALDRIC, "", 0, false, true));
    }

    /**
     * The player's own console refuses nobody: it is theirs, it echoes
     * back to them alone, and no capability stands between them and it.
     */
    @Test
    public void theClientConsoleIsEveryPlayersOwn() {
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.CLIENT_CONSOLE, null,
                ALDRIC, "", 0, false, false));
    }

    /** A side of a gate naming a role nothing knows is closed, not opened. */
    @Test
    public void aMisspeltRoleClosesTheSideItNames() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null,
                ChatRoleConfig.SILENT);
        ChatRoleCatalog.installServer(catalog);
        ChatChannelGates.install(ChatRoleConfig.parseGates(
                new String[] {"operator=read:opreator;send:opreator"}, catalog,
                ChatRoleConfig.SILENT));
        assertEquals(GATE_REFUSAL, ChatChannelPolicy.sendRefusal(
                ChatChannel.OPERATOR, null, ALDRIC,  "",
                catalog.byId("operator").bit(), false, false));
    }

}
