package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Who may make a fellowship: with LOTR's creation off only staff, who
 * hold {@code fellowship.admin} (FEL-30), anyone else told why; a
 * character in as many as it can be, or leading as many as LOTR lets it,
 * never. And where a member whose account is online stands: here,
 * elsewhere, or a ghost making a character (FEL-32).
 */
public final class FellowshipCreateRefusalTest {

    @Test
    public void withCreationOffOnlyStaffMayMakeOne() {
        assertEquals("anyone, creation off", FellowshipErrorId.CREATION_DISABLED,
                FellowshipService.createRefusal(false, 0, 0, 1));
        assertEquals("staff, creation off, or anyone with it on", FellowshipErrorId.NONE,
                FellowshipService.createRefusal(true, 0, 0, 1));
    }

    @Test
    public void staffKeepTheOtherLimits() {
        assertEquals(FellowshipErrorId.TOO_MANY_FELLOWSHIPS, FellowshipService.createRefusal(
                true, Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY, 0, 8));
        assertEquals(FellowshipErrorId.LEAD_LIMIT_REACHED,
                FellowshipService.createRefusal(true, 2, 2, 2));
        assertEquals(FellowshipErrorId.NONE, FellowshipService.createRefusal(true, 2, 1, 2));
    }

    @Test
    public void anAccountPlayingNoCharacterIsAGhost() {
        UUID member = new UUID(1L, 1L);
        assertEquals(FellowshipMemberPresence.HERE,
                FellowshipOnlineView.presenceOf(member, member));
        assertEquals(FellowshipMemberPresence.ELSEWHERE,
                FellowshipOnlineView.presenceOf(member, new UUID(2L, 2L)));
        assertEquals(FellowshipMemberPresence.GHOST,
                FellowshipOnlineView.presenceOf(member, null));
        assertEquals(FellowshipMemberPresence.GHOST, FellowshipMemberPresence.fromNetworkId(
                FellowshipMemberPresence.GHOST.getNetworkId()));
    }
}
