package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;

import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which conversation of a channel a line belongs to. A channel that is
 * more than one conversation stamps the one it was said in on every copy,
 * so the clients file it under that conversation's tab and under no
 * other; a channel that is only ever one stamps nothing.
 */
public final class ChatChannelScopeValueTest {

    private static final UUID ALDRIC =
            UUID.fromString("e0000000-0000-0000-0000-00000000000e");
    private static final String GONDOR = "lotr:gondor";

    /** Both scoped channels say so, and the rest say nothing. */
    @Test
    public void onlyTheScopedChannelsAreMoreThanOneConversation() {
        assertTrue(ChatChannel.FACTION.isScoped());
        assertTrue(ChatChannel.PARTY.isScoped());
        assertEquals(ChatChannelScope.FACTION, ChatChannel.FACTION.getScope());
        assertEquals(ChatChannelScope.PARTY, ChatChannel.PARTY.getScope());

        assertFalse(ChatChannel.ALL.isScoped());
        assertFalse(ChatChannel.PROXIMITY.isScoped());
        assertFalse(ChatChannel.OOC.isScoped());
        assertFalse(ChatChannel.ADMIN.isScoped());
        assertFalse(ChatChannel.CONSOLE.isScoped());
        assertFalse(ChatChannel.WHISPER.isScoped());
    }

    /** A faction line is stamped with the faction it was spoken to. */
    @Test
    public void aFactionLineNamesItsFaction() {
        assertEquals(GONDOR, ChatChannelPolicy.scopeValueOf(
                ChatChannel.FACTION, null, GONDOR));
    }

    /**
     * A party line is stamped with the party it was said in, so two
     * parties an account is in through two characters are two
     * conversations rather than one tab holding both.
     */
    @Test
    public void aPartyLineNamesItsParty() {
        Party first = party();
        Party second = party();

        String firstScope = ChatChannelPolicy.scopeValueOf(
                ChatChannel.PARTY, first, GONDOR);
        String secondScope = ChatChannelPolicy.scopeValueOf(
                ChatChannel.PARTY, second, GONDOR);

        assertEquals(first.getPartyId().toString(), firstScope);
        assertFalse("two parties are two conversations",
                firstScope.equals(secondScope));
    }

    /**
     * A party line is named by the party and never by the faction of
     * whoever happened to say it: two members of one party in different
     * factions are still in the one conversation.
     */
    @Test
    public void aPartyLineIsNotNamedByTheSpeakersFaction() {
        Party party = party();

        assertEquals(ChatChannelPolicy.scopeValueOf(
                        ChatChannel.PARTY, party, GONDOR),
                ChatChannelPolicy.scopeValueOf(
                        ChatChannel.PARTY, party, "lotr:rohan"));
    }

    /** A channel that is one conversation is stamped with nothing. */
    @Test
    public void anUnscopedChannelNamesNoConversation() {
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.ALL, party(), GONDOR));
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.OOC, party(), GONDOR));
        assertEquals("", ChatChannelPolicy.scopeValueOf(null, party(), GONDOR));
    }

    /** With nothing to name the conversation, nothing is named. */
    @Test
    public void aScopedChannelWithNothingToNameNamesNothing() {
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.PARTY, null, GONDOR));
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.FACTION, party(), ""));
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.FACTION, party(), null));
    }

    private static Party party() {
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        members.add(new PartyMember(ALDRIC, UUID.randomUUID(), "Aldric", 1L,
                PartyColor.GREEN));
        return new Party(UUID.randomUUID(), ALDRIC, members, 1L, 0L,
                Party.CURRENT_DATA_VERSION);
    }
}
