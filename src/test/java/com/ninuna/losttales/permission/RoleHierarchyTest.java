package com.ninuna.losttales.permission;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The role hierarchy, as Discord's (DATA-14): rank 1 is the highest, a
 * manager stands at their highest role and manages only the roles ranked
 * strictly below it, and an operator holding {@code roles.manage} by
 * level stands above every role.
 */
public final class RoleHierarchyTest {

    private static ChatAccountRole role(String id, int rank) {
        return ChatAccountRole.custom(id, id, "", 0x112233, true, rank, null, null, null);
    }

    /** Admin 5, moderator 15, helper 15, member 30, given in another order. */
    private static ChatRoleCatalog catalog() {
        return ChatRoleCatalog.of(Arrays.asList(role("member", 30), role("moderator", 15),
                role("admin", 5), role("helper", 15)), null, null);
    }

    private static int mask(ChatRoleCatalog catalog, String... ids) {
        int mask = 0;
        for (String id : ids) {
            mask |= catalog.byId(id).bit();
        }
        return mask;
    }

    @Test
    public void theHighestRoleIsTheOneOfLowestRank() {
        ChatRoleCatalog catalog = catalog();
        assertEquals("admin", RoleHierarchy.highestOf(
                mask(catalog, "member", "admin", "moderator"), catalog).getId());
        assertEquals(15, RoleHierarchy.placeOf(mask(catalog, "member", "moderator"), catalog));
        assertNull(RoleHierarchy.highestOf(0, catalog));
        assertEquals(RoleHierarchy.BELOW_EVERY_ROLE, RoleHierarchy.placeOf(0, catalog));
    }

    /** The Lost Tales Team mark is a vanity mark: it places nobody. */
    @Test
    public void theTeamMarkStandsNowhere() {
        ChatRoleCatalog catalog = catalog();
        int team = ChatAccountRole.TEAM.bit();
        assertNull(RoleHierarchy.highestOf(team, catalog));
        assertEquals(30, RoleHierarchy.placeOf(team | mask(catalog, "member"), catalog));
    }

    @Test
    public void aManagerManagesOnlyTheRolesBelowTheirHighest() {
        ChatRoleCatalog catalog = catalog();
        int moderator = RoleHierarchy.placeOf(mask(catalog, "moderator", "member"), catalog);
        assertTrue(RoleHierarchy.manages(moderator, catalog.byId("member").getRank()));
        assertFalse("their own role", RoleHierarchy.manages(moderator,
                catalog.byId("moderator").getRank()));
        assertFalse("a role of the same rank stands beside them", RoleHierarchy.manages(
                moderator, catalog.byId("helper").getRank()));
        assertFalse("a role above them", RoleHierarchy.manages(moderator,
                catalog.byId("admin").getRank()));
        assertTrue("a new role ranked lower", RoleHierarchy.manages(moderator, 16));
        assertFalse("a new role ranked as high", RoleHierarchy.manages(moderator, 15));
    }

    @Test
    public void aboveEveryRoleManagesThemAllAndBelowNone() {
        for (int rank : new int[] {1, 5, 1000}) {
            assertTrue(RoleHierarchy.manages(RoleHierarchy.ABOVE_EVERY_ROLE, rank));
            assertFalse(RoleHierarchy.manages(RoleHierarchy.BELOW_EVERY_ROLE, rank));
        }
    }
}
