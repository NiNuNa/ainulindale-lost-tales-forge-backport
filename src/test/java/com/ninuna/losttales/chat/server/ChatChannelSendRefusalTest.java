package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Why a channel refuses a send. Membership is asked first — a party line
 * needs a party, a faction line a faction — and then the role gate the
 * config put on the channel. Every answer is the notice the sender is
 * told, so the reason is never guessed at the other end.
 */
public final class ChatChannelSendRefusalTest {

    private static final UUID ALDRIC =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BEREN =
            UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final String GONDOR = "lotr:gondor";

    private static final String PARTY_REFUSAL = "chat.losttales.channel.party_unavailable";
    private static final String FACTION_REFUSAL = "chat.losttales.channel.faction_unavailable";
    private static final String GATE_REFUSAL = "chat.losttales.channel.role_unavailable";

    @After
    public void tearDown() {
        ChatChannelGates.resetToDefaults();
        ChatRoleCatalog.resetToBuiltIn();
    }

    private static Party partyOf(UUID leader) {
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        members.add(new PartyMember(leader, UUID.randomUUID(), "Aldric", 1L,
                PartyColor.GREEN));
        return new Party(UUID.randomUUID(), leader, members, 1L, 0L,
                Party.CURRENT_DATA_VERSION);
    }

    /** An open channel refuses nobody, whatever they are or are not in. */
    @Test
    public void anOpenChannelRefusesNobody() {
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.ALL, null, ALDRIC, "", 0));
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.PROXIMITY, null, ALDRIC, "", 0));
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.OOC, null, null, "", 0));
    }

    /** A channel that is not a channel at all refuses, rather than passing. */
    @Test
    public void noChannelIsRefused() {
        assertEquals(GATE_REFUSAL,
                ChatChannelPolicy.sendRefusal(null, null, ALDRIC, GONDOR, 0));
    }

    /** A party line needs a party, and the sender's own place in it. */
    @Test
    public void aPartyLineNeedsThatPartysMembership() {
        assertEquals(PARTY_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.PARTY, null, ALDRIC, "", 0));
        Party party = partyOf(ALDRIC);
        assertNull(ChatChannelPolicy.sendRefusal(ChatChannel.PARTY, party, ALDRIC, "", 0));
        assertEquals("someone else's party is not the sender's",
                PARTY_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.PARTY, party, BEREN, "", 0));
        assertEquals("an identity with no gameplay id is in no party",
                PARTY_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.PARTY, party, null, "", 0));
    }

    /** A faction line needs a faction: the account, which has none, is refused. */
    @Test
    public void aFactionLineNeedsAFaction() {
        assertEquals(FACTION_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FACTION, null, ALDRIC, "", 0));
        assertEquals(FACTION_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FACTION, null, ALDRIC, null, 0));
        assertNull(ChatChannelPolicy.sendRefusal(
                ChatChannel.FACTION, null, ALDRIC, GONDOR, 0));
    }

    /** Membership is asked before the gate, so the notice names the nearer reason. */
    @Test
    public void membershipIsAskedBeforeTheGate() {
        installOperatorGateOn(ChatChannel.FACTION);
        assertEquals("no faction is the reason, not the gate",
                FACTION_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.FACTION, null, ALDRIC, "", 0));
    }

    /** The gate the config put on a channel refuses whoever does not hold its role. */
    @Test
    public void theGateRefusesWhoeverDoesNotHoldItsRole() {
        installOperatorGateOn(ChatChannel.ADMIN);
        assertEquals(GATE_REFUSAL,
                ChatChannelPolicy.sendRefusal(ChatChannel.ADMIN, null, ALDRIC, "", 0));
        int operator = ChatRoleCatalog.server().byId("operator").bit();
        assertNull(ChatChannelPolicy.sendRefusal(
                ChatChannel.ADMIN, null, ALDRIC, "", operator));
        assertTrue("the client is told to ask again for its tabs",
                ChatChannelPolicy.isGateRefusal(GATE_REFUSAL));
        assertTrue(!ChatChannelPolicy.isGateRefusal(FACTION_REFUSAL));
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

    /** A side of a gate naming a role nothing knows is closed, not opened. */
    @Test
    public void aMisspeltRoleClosesTheSideItNames() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null,
                ChatRoleConfig.SILENT);
        ChatRoleCatalog.installServer(catalog);
        ChatChannelGates.install(ChatRoleConfig.parseGates(
                new String[] {"admin=read:opreator;send:opreator"}, catalog,
                ChatRoleConfig.SILENT));
        assertEquals(GATE_REFUSAL, ChatChannelPolicy.sendRefusal(
                ChatChannel.ADMIN, null, ALDRIC,  "",
                catalog.byId("operator").bit()));
    }

    /** Every channel is decided the same way: none of them is named in the rule. */
    @Test
    public void everyChannelIsDecidedByItsOwnFacts() {
        for (ChatChannel channel : ChatChannel.values()) {
            String refusal = ChatChannelPolicy.sendRefusal(
                    channel, partyOf(ALDRIC), ALDRIC, GONDOR, 0);
            assertTrue(channel.getId() + " answers with a notice or with nothing",
                    refusal == null || Arrays.asList(PARTY_REFUSAL, FACTION_REFUSAL,
                            GATE_REFUSAL).contains(refusal));
        }
    }
}
