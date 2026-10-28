package com.ninuna.losttales.command;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * An edit names options one at a time and keeps the rest of the entry;
 * who stands where among the roles (DATA-14).
 */
public final class LostTalesCommandRoleTest {

    /**
     * An operator holding {@code roles.manage} by level stands above every
     * role, as a Discord server's owner; a sender that holds it neither by
     * level nor through a role a player's account holds stands below them all.
     */
    @Test
    public void anAdministratorStandsAboveEveryRole() {
        LostTalesCommandRole.Standing administrator = LostTalesCommandRole.standingOf(
                FakeCommandSender.administrator("Admin"));
        assertTrue(administrator.manages(1));
        assertTrue(administrator.manages(1000));
        assertNull(administrator.highest);
        LostTalesCommandRole.Standing operator = LostTalesCommandRole.standingOf(
                FakeCommandSender.operator("Ops"));
        assertFalse(operator.manages(1000));
        assertNull(operator.highest);
        assertFalse(LostTalesCommandRole.standingOf(null).manages(1000));
    }

    @Test
    public void anOptionIsReplacedInPlaceOrAppended() {
        String entry = "moderator=name:Moderator;colour:A94B54";
        assertEquals("moderator=name:Staff;colour:A94B54",
                LostTalesCommandRole.replaceOption(entry, "NAME", "Staff"));
        assertEquals("moderator=name:Moderator;colour:A94B54;rank:5",
                LostTalesCommandRole.replaceOption(entry, "rank", "5"));
        assertEquals("moderator=name:Moderator;colour:A94B54;op:1",
                LostTalesCommandRole.replaceOption(entry, "op", "1"));
    }

    @Test
    public void aRepeatableOptionAddsAndAnEmptyOneClearsItsKind() {
        String entry = "moderator=name:Moderator;op:1;faction:GONDOR@gondor.knight";
        assertEquals("moderator=name:Moderator;op:1;faction:GONDOR@gondor.knight;op:2",
                LostTalesCommandRole.replaceOption(entry, "op", "2"));
        assertEquals("moderator=name:Moderator;faction:GONDOR@gondor.knight",
                LostTalesCommandRole.replaceOption(entry, "op", ""));
        assertEquals("moderator=name:Moderator;op:1",
                LostTalesCommandRole.replaceOption(entry, "faction", ""));
        assertEquals("builder=name:Builder",
                LostTalesCommandRole.replaceOption("builder=", "name", "Builder"));
    }
}
