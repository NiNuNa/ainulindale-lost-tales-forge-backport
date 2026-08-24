package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    @Test
    public void onlyRolesWorthAnsweringCanBeAddressed() {
        List<ChatAccountRole> roles = ChatAccountRole.mentionable();
        assertFalse(roles.isEmpty());
        for (ChatAccountRole role : roles) {
            assertFalse(role == ChatAccountRole.NONE);
            assertTrue(role.isMentionable());
            assertTrue(role.getNameKey().length() > 0);
            // The plain name is its own key, never the bracketed tag.
            assertFalse(role.getNameKey().equals(role.getTagKey()));
        }
        assertTrue(roles.contains(ChatAccountRole.OPERATOR));
        // A vanity mark is worn and never called.
        assertFalse(ChatAccountRole.DEVELOPER.isMentionable());
        assertFalse(roles.contains(ChatAccountRole.DEVELOPER));
        assertFalse(ChatAccountRole.NONE.isMentionable());
        assertEquals("", ChatAccountRole.NONE.getNameKey());
        // Wearing it is untouched: it still tags and colours a name.
        assertTrue(ChatAccountRole.DEVELOPER.getTagKey().length() > 0);
        assertEquals(ChatAccountRole.DEVELOPER, ChatAccountRole.primary(
                ChatAccountRole.maskOf(ChatAccountRole.DEVELOPER)));
    }

    @Test
    public void aRoleCandidateIsNamedInItsColourAndFoundByPrefix() {
        ChatMentionCandidate operator = ChatMentionCandidate.role(
                "role:operator", "Operator",
                ChatAccountRole.OPERATOR.getColor());
        assertTrue(operator.isRole());
        assertEquals(ChatAccountRole.OPERATOR.getColor(),
                operator.getRoleColor());
        assertEquals("", operator.getAccountId());
        assertTrue(operator.matches("op"));
        assertFalse(operator.matches("dev"));
    }

    @Test
    public void aPlayerCandidateCarriesTheFaceToDrawAndIsNotARole() {
        ChatMentionCandidate player = ChatMentionCandidate.player(
                "key", "Aragorn", "Steve", "Aragorn",
                "0-0-0-0-1", Arrays.asList("Steve", "Aragorn"));
        assertFalse(player.isRole());
        assertEquals(-1, player.getRoleColor());
        assertEquals("0-0-0-0-1", player.getAccountId());
        assertTrue(player.matches("ste"));
        assertTrue(player.matches("ara"));
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

    /**
     * Mention detection is the same word-boundary match a name gets, so a
     * client holding the role recognises it and one that does not never
     * sees the message as addressed to it.
     */
    @Test
    public void holdingTheRoleIsWhatMakesARoleMentionLand() {
        String message = "@Operator please look at spawn";
        assertTrue(ChatMentions.mentionsAny(message,
                Arrays.asList("Steve", "Operator")));
        assertFalse(ChatMentions.mentionsAny(message,
                Arrays.asList("Steve", "Developer")));
        // The mask the server sends is what names those roles.
        int mask = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR);
        assertEquals(Collections.singletonList(ChatAccountRole.OPERATOR),
                ChatAccountRole.fromMask(mask));
        assertTrue(ChatAccountRole.isValidMask(mask));
    }
}
