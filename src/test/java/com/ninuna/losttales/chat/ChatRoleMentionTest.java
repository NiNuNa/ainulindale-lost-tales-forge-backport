package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A role can be addressed like a player: {@code @Operator} finds the role
 * in the completion list, and a client holding that role sees the message
 * as a mention of itself. Roles are listed before players, so addressing a
 * whole group is never buried under a list of names.
 */
public final class ChatRoleMentionTest {

    @Before
    public void setUp() {
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void onlyRolesWorthAnsweringCanBeAddressed() {
        List<ChatAccountRole> roles = ChatAccountRole.mentionable();
        assertFalse(roles.isEmpty());
        for (ChatAccountRole role : roles) {
            assertFalse(role.isNone());
            assertTrue(role.isMentionable());
            assertTrue(role.getDisplayName().length() > 0);
            // The plain name is its own, never the bracketed tag.
            assertFalse(role.getDisplayName().equals(role.getDisplayTag()));
        }
        assertTrue(roles.contains(ChatRoleFixtures.OPERATOR));
        // A vanity mark is worn and never called.
        assertFalse(ChatAccountRole.TEAM.isMentionable());
        assertFalse(roles.contains(ChatAccountRole.TEAM));
        assertFalse(ChatAccountRole.NONE.isMentionable());
        assertEquals("", ChatAccountRole.NONE.getNameKey());
        // Wearing it is untouched: it still tags and colours a name.
        assertTrue(ChatAccountRole.TEAM.getTagKey().length() > 0);
        assertEquals(ChatAccountRole.TEAM, ChatAccountRole.primary(
                ChatAccountRole.maskOf(ChatAccountRole.TEAM)));
    }

    @Test
    public void aRoleCandidateIsNamedInItsColourAndFoundByPrefix() {
        ChatMentionCandidate operator = ChatMentionCandidate.role(
                "role:operator", "Operator",
                ChatRoleFixtures.OPERATOR.getColor());
        assertTrue(operator.isRole());
        assertEquals(ChatRoleFixtures.OPERATOR.getColor(),
                operator.getRoleColor());
        assertEquals("", operator.getAccountId());
        assertTrue(operator.matches("op"));
        assertFalse(operator.matches("dev"));
    }


    /** The list keeps candidate order, so roles stay above the players. */
    @Test
    public void rolesComeBeforePlayersInTheCompletionList() {
        ChatMentionCandidate operator = ChatMentionCandidate.role(
                "role:operator", "Operator", 0xA94B54);
        ChatMentionCandidate player = ChatMentionCandidate.player(
                "key", "Opal", "Opal", "", "", Collections.<String>emptyList());
        List<ChatMentionCandidate> matches = ChatNameSuggester.matches(
                "op", Arrays.asList(operator, player), 8);
        assertEquals(2, matches.size());
        assertSame(operator, matches.get(0));
        assertSame(player, matches.get(1));
    }

}
