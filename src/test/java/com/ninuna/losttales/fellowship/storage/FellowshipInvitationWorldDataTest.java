package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * The store keeps no more invitations open for a fellowship, nor waiting
 * for one account, than it may; saving one past either cap is refused.
 */
public final class FellowshipInvitationWorldDataTest {

    private static final UUID FELLOWSHIP = new UUID(2L, 2L);
    private static final UUID ACCOUNT = new UUID(6L, 6L);

    @Test
    public void anInvitationPastAFellowshipsCapIsRefused() {
        FellowshipInvitationWorldData data = store();
        for (int index = 0; index < FellowshipInvitation.MAX_PER_FELLOWSHIP; index++) {
            data.saveInvitation(invitation(index, FELLOWSHIP, new UUID(500L, index)));
        }
        try {
            data.saveInvitation(invitation(99, FELLOWSHIP, new UUID(500L, 99L)));
            fail("a fellowship past its cap");
        } catch (IllegalStateException expected) {
            // The store said no; nothing was kept.
        }
        assertEquals(FellowshipInvitation.MAX_PER_FELLOWSHIP, data.getInvitationCount());
    }

    @Test
    public void anInvitationPastAnAccountsCapIsRefused() {
        FellowshipInvitationWorldData data = store();
        for (int index = 0; index < FellowshipInvitation.MAX_PER_TARGET_ACCOUNT; index++) {
            data.saveInvitation(invitation(index, new UUID(300L, index), ACCOUNT));
        }
        assertEquals(FellowshipInvitation.MAX_PER_TARGET_ACCOUNT,
                data.getInvitationCountForTargetAccount(ACCOUNT));
        try {
            data.saveInvitation(invitation(99, new UUID(300L, 99L), ACCOUNT));
            fail("an account past its cap");
        } catch (IllegalStateException expected) {
            // The store said no; nothing was kept.
        }
        assertEquals(0, data.getInvitationCountForTargetAccount(new UUID(7L, 7L)));
    }

    /** Taking one back makes room again. */
    @Test
    public void aRemovedInvitationMakesRoom() {
        FellowshipInvitationWorldData data = store();
        for (int index = 0; index < FellowshipInvitation.MAX_PER_FELLOWSHIP; index++) {
            data.saveInvitation(invitation(index, FELLOWSHIP, new UUID(500L, index)));
        }
        data.removeInvitation(new UUID(900L, 0L));
        data.saveInvitation(invitation(99, FELLOWSHIP, new UUID(500L, 99L)));
        assertEquals(FellowshipInvitation.MAX_PER_FELLOWSHIP,
                data.getInvitationsForFellowship(FELLOWSHIP).size());
    }

    private static FellowshipInvitation invitation(int index, UUID fellowship, UUID targetAccount) {
        return new FellowshipInvitation(new UUID(900L, index), fellowship,
                new UUID(100L, 1L), new UUID(100L, 2L), "Aldric",
                new UUID(700L, index), targetAccount, "Beren", 1000L + index, 90000L);
    }

    private static FellowshipInvitationWorldData store() {
        return new FellowshipInvitationWorldData(FellowshipInvitationWorldData.DATA_NAME);
    }
}
