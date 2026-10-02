package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleSource;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.permission.LostTalesPermissions;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator",
                "", 0xA94B54, true, 15, null, null, null);
        ChatAccountRole herald = ChatAccountRole.custom("herald", "Herald", "",
                0x112233, true, 30, null, null, null);
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

    /**
     * An account whose player is not here holds what its assignments
     * give it, and a role an operator level grants only while it is an
     * operator of at least that level — never as a non-operator, even
     * for level zero, as vanilla answers a player who is here.
     */
    @Test
    public void anAbsentAccountHoldsItsAssignmentsAndItsOperatorLevel() {
        ChatAccountRole staff = ChatAccountRole.custom("staff", "Staff", "",
                0x112233, true, 10, Arrays.asList(ChatRoleSource.opLevel(2)), null, null);
        ChatAccountRole anyOperator = ChatAccountRole.custom("op0", "Op", "",
                0x223344, true, 20, Arrays.asList(ChatRoleSource.opLevel(0)), null, null);
        Map<String, Set<UUID>> characterMembers = new LinkedHashMap<String, Set<UUID>>();
        characterMembers.put("staff", setOf(ALDRIC));
        ChatRoleCatalog catalog = ChatRoleCatalog.of(Arrays.asList(staff, anyOperator),
                Collections.<String, Set<UUID>>emptyMap(), characterMembers);
        int staffBit = catalog.byId("staff").bit();
        int anyBit = catalog.byId("op0").bit();
        assertEquals(staffBit | anyBit,
                ChatAccountRoleResolver.absentMask(catalog, ACCOUNT, null, 2));
        assertEquals(anyBit,
                ChatAccountRoleResolver.absentMask(catalog, ACCOUNT, null, 1));
        assertEquals(0, ChatAccountRoleResolver.absentMask(catalog, ACCOUNT, null,
                ChatAccountRoleResolver.NOT_OPERATOR));
        // The character's own assignment, and only with the character.
        assertEquals(staffBit, ChatAccountRoleResolver.absentMask(catalog,
                ACCOUNT, ALDRIC, ChatAccountRoleResolver.NOT_OPERATOR));
        assertEquals(0, ChatAccountRoleResolver.absentMask(catalog, ACCOUNT,
                BEREN, ChatAccountRoleResolver.NOT_OPERATOR));
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

    /**
     * Capabilities come only from roles the account holds: assigned to
     * the account, or given by an operator level. A role earned by a LOTR
     * faction rank, or assigned to a character, keeps its bit for its look
     * and the gates it opens, and grants nothing, whatever it names.
     */
    @Test
    public void aRoleEarnedByRankOrHeldByACharacterGrantsNothing() {
        Set<String> moderate = Collections.singleton(
                LostTalesCapability.CHAT_MODERATE.getId());
        ChatAccountRole knight = ChatAccountRole.custom("knight", "Knight", "",
                0x112233, true, 30, Arrays.asList(
                        ChatRoleSource.factionRank("gondor", "gondor.knight")),
                moderate, null);
        ChatAccountRole herald = ChatAccountRole.custom("herald", "Herald", "",
                0x223344, true, 31, null, moderate, null);
        ChatAccountRole warden = ChatAccountRole.custom("warden", "Warden", "",
                0x334455, true, 32, null, moderate, null);
        ChatAccountRole staff = ChatAccountRole.custom("staff", "Staff", "",
                0x445566, true, 33, Arrays.asList(ChatRoleSource.opLevel(2)),
                moderate, null);
        Map<String, Set<UUID>> accounts = new LinkedHashMap<String, Set<UUID>>();
        accounts.put("warden", setOf(ACCOUNT));
        Map<String, Set<UUID>> characters = new LinkedHashMap<String, Set<UUID>>();
        characters.put("herald", setOf(ALDRIC));
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Arrays.asList(knight, herald, warden, staff), accounts, characters);
        int knightBit = catalog.byId("knight").bit();
        int heraldBit = catalog.byId("herald").bit();
        int wardenBit = catalog.byId("warden").bit();
        int staffBit = catalog.byId("staff").bit();

        ChatAccountRoleResolver.OperatorLevel operator =
                new ChatAccountRoleResolver.OperatorLevel() {
                    @Override
                    public boolean reaches(int level, String roleId) {
                        return level <= 2;
                    }
                };
        ChatAccountRoleResolver.OperatorLevel player =
                new ChatAccountRoleResolver.OperatorLevel() {
                    @Override
                    public boolean reaches(int level, String roleId) {
                        return false;
                    }
                };
        assertEquals(wardenBit | staffBit,
                ChatAccountRoleResolver.grantingMask(catalog, ACCOUNT, operator));
        assertEquals(wardenBit,
                ChatAccountRoleResolver.grantingMask(catalog, ACCOUNT, player));
        assertEquals(0, ChatAccountRoleResolver.grantingMask(catalog, OTHER_ACCOUNT,
                player));

        // Aldric still wears the herald's role for its look and its gates.
        assertEquals(wardenBit | heraldBit,
                ChatAccountRoleResolver.assignedMask(catalog, ACCOUNT, ALDRIC));
        // A rank or a character's role names a grant the catalogue keeps,
        // but the mask capabilities are asked through never holds it.
        assertTrue(LostTalesPermissions.isGranted(knightBit | heraldBit,
                LostTalesCapability.CHAT_MODERATE, catalog,
                LostTalesPermissionCatalog.empty()));
        int granting = ChatAccountRoleResolver.grantingMask(catalog, OTHER_ACCOUNT,
                player);
        assertEquals(0, granting & (knightBit | heraldBit));
        assertFalse(LostTalesPermissions.decide(false, granting,
                LostTalesCapability.CHAT_MODERATE, catalog,
                LostTalesPermissionCatalog.empty()));
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
