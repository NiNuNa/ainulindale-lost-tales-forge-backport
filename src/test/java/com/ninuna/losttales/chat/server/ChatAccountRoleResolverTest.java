package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Two questions, two answers. The account's roles are what a capability
 * is granted through; the roles of one of its characters are what a line
 * is signed with and a gate is passed with. A character's assignment is
 * that character's alone: it never reaches the account, and never
 * reaches the account's other characters.
 */
public final class ChatAccountRoleResolverTest {

    private static final UUID ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID ALDRIC =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BEREN =
            UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    private static ChatRoleCatalog catalogue(Set<UUID> accounts, Set<UUID> characters) {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator", "",
                "", 0xA94B54, true, 15, null, null);
        ChatAccountRole herald = ChatAccountRole.custom("herald", "Herald", "", "",
                0x112233, true, 30, null, null);
        Map<String, Set<UUID>> accountMembers = new LinkedHashMap<String, Set<UUID>>();
        if (accounts != null) {
            accountMembers.put("moderator", accounts);
        }
        Map<String, Set<UUID>> characterMembers = new LinkedHashMap<String, Set<UUID>>();
        if (characters != null) {
            characterMembers.put("herald", characters);
        }
        return ChatRoleCatalog.of(Arrays.asList(moderator, herald),
                accountMembers, characterMembers);
    }

    private static Set<UUID> setOf(UUID... ids) {
        return new HashSet<UUID>(Arrays.asList(ids));
    }

    /** An account's role is worn by every identity it plays. */
    @Test
    public void anAccountRoleIsHeldWhicheverCharacterIsPlayed() {
        ChatRoleCatalog catalog = catalogue(setOf(ACCOUNT), null);
        int moderator = catalog.byId("moderator").bit();
        assertEquals(moderator,
                ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, null));
        assertEquals(moderator,
                ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, ALDRIC));
        assertEquals(moderator,
                ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, BEREN));
    }

    /**
     * A character's role is that character's alone: the account asking
     * without a character — which is how a capability is checked — never
     * sees it, and the account's other character never wears it.
     */
    @Test
    public void aCharacterRoleReachesNeitherTheAccountNorItsOtherCharacters() {
        ChatRoleCatalog catalog = catalogue(null, setOf(ALDRIC));
        int herald = catalog.byId("herald").bit();
        assertEquals("the account alone holds nothing the character was given",
                0, ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, null));
        assertEquals(herald,
                ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, ALDRIC));
        assertEquals("the account's other character wears none of it",
                0, ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, BEREN));
    }

    /** The two kinds add up when both are assigned. */
    @Test
    public void anAccountAndItsCharacterWearBothRoles() {
        ChatRoleCatalog catalog = catalogue(setOf(ACCOUNT), setOf(ALDRIC));
        int moderator = catalog.byId("moderator").bit();
        int herald = catalog.byId("herald").bit();
        assertEquals(moderator | herald,
                ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, ALDRIC));
        assertEquals(moderator,
                ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, BEREN));
    }

    /** Another account's assignment is nothing of this account's. */
    @Test
    public void anotherAccountsRoleIsNotHeld() {
        ChatRoleCatalog catalog = catalogue(setOf(OTHER_ACCOUNT), null);
        assertEquals(0, ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, ALDRIC));
    }

    /**
     * The team mark is the code's: no assignment reaches it, so it never
     * appears in the bits the catalogue's own lists give.
     */
    @Test
    public void theTeamMarkIsNeverAssigned() {
        Map<String, Set<UUID>> members = new LinkedHashMap<String, Set<UUID>>();
        members.put(ChatAccountRole.TEAM_ID, setOf(ACCOUNT));
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Collections.<ChatAccountRole>emptyList(), members, null);
        assertEquals(0, ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, ALDRIC));
    }

    /** Nothing at all is asked of a missing catalogue or a nameless account. */
    @Test
    public void nothingIsHeldWithoutACatalogueOrAnAccount() {
        assertEquals(0, ChatAccountRoleResolver.assignedMask(null, ACCOUNT, ALDRIC));
        ChatRoleCatalog catalog = catalogue(setOf(ACCOUNT), setOf(ALDRIC));
        assertEquals(catalog.byId("herald").bit(),
                ChatAccountRoleResolver.assignedMask(catalog, null, ALDRIC));
        assertEquals(0, ChatAccountRoleResolver.assignedMask(catalog, null, null));
    }
}
