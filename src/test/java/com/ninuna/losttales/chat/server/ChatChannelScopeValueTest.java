package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipFixtures;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;

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
        assertTrue(ChatChannel.FELLOWSHIP.isScoped());
        assertEquals(ChatChannelScope.FACTION, ChatChannel.FACTION.getScope());
        assertEquals(ChatChannelScope.FELLOWSHIP, ChatChannel.FELLOWSHIP.getScope());

        assertFalse(ChatChannel.GLOBAL.isScoped());
        assertFalse(ChatChannel.PROXIMITY.isScoped());
        assertFalse(ChatChannel.OOC.isScoped());
        assertFalse(ChatChannel.OPERATOR.isScoped());
        assertFalse(ChatChannel.CLIENT_CONSOLE.isScoped());
        assertFalse(ChatChannel.WHISPER.isScoped());
    }

    /** A faction line is stamped with the faction it was spoken to. */
    @Test
    public void aFactionLineNamesItsFaction() {
        assertEquals(GONDOR, ChatChannelPolicy.scopeValueOf(
                ChatChannel.FACTION, null, GONDOR));
    }

    /**
     * A fellowship line is stamped with the fellowship it was said in, so two
     * fellowships an account is in through two characters are two
     * conversations rather than one tab holding both.
     */
    @Test
    public void aFellowshipLineNamesItsFellowship() {
        Fellowship first = fellowship();
        Fellowship second = fellowship();

        String firstScope = ChatChannelPolicy.scopeValueOf(
                ChatChannel.FELLOWSHIP, first, GONDOR);
        String secondScope = ChatChannelPolicy.scopeValueOf(
                ChatChannel.FELLOWSHIP, second, GONDOR);

        assertEquals(first.getFellowshipId().toString(), firstScope);
        assertFalse("two fellowships are two conversations",
                firstScope.equals(secondScope));
    }


    /** A channel that is one conversation is stamped with nothing. */
    @Test
    public void anUnscopedChannelNamesNoConversation() {
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.GLOBAL, fellowship(), GONDOR));
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.OOC, fellowship(), GONDOR));
        assertEquals("", ChatChannelPolicy.scopeValueOf(null, fellowship(), GONDOR));
    }

    /** With nothing to name the conversation, nothing is named. */
    @Test
    public void aScopedChannelWithNothingToNameNamesNothing() {
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.FELLOWSHIP, null, GONDOR));
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.FACTION, fellowship(), ""));
        assertEquals("", ChatChannelPolicy.scopeValueOf(
                ChatChannel.FACTION, fellowship(), null));
    }

    private static Fellowship fellowship() {
        ArrayList<FellowshipMember> members = new ArrayList<FellowshipMember>();
        members.add(new FellowshipMember(ALDRIC, UUID.randomUUID(), "Aldric", 1L,
                FellowshipColor.GREEN));
        return FellowshipFixtures.of(UUID.randomUUID(), ALDRIC, members);
    }
}
