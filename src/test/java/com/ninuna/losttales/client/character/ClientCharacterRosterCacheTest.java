package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The roster answers each request on its own, as the fellowship does: a second
 * quick request no longer takes the first one's answer, and an older
 * roster arriving late completes its request without being shown.
 */
public final class ClientCharacterRosterCacheTest {

    private static final UUID OWNER =
            UUID.fromString("da000000-0000-0000-0000-0000000000ad");

    @After
    public void clear() {
        ClientCharacterRosterCache.clear();
    }

    @Test
    public void eachRequestKeepsItsOwnAnswer() {
        ClientCharacterRosterCache.beginRequest(1, CharacterOperationType.CAPE_UPDATE);
        ClientCharacterRosterCache.beginRequest(2, CharacterOperationType.PROFILE_UPDATE);
        ClientCharacterRosterCache.acceptOperation(feedback(1, true));
        ClientCharacterRosterCache.acceptOperation(feedback(2, false));

        CharacterOperationFeedback first = ClientCharacterRosterCache.getOperation(1);
        CharacterOperationFeedback second = ClientCharacterRosterCache.getOperation(2);
        assertNotNull("the first answer is still there", first);
        assertEquals(CharacterOperationType.CAPE_UPDATE, first.getOperationType());
        assertNotNull(second);
        assertFalse(second.isSuccessful());

        ClientCharacterRosterCache.clearOperation(1);
        assertNull(ClientCharacterRosterCache.getOperation(1));
        assertNotNull(ClientCharacterRosterCache.getOperation(2));
    }

    @Test
    public void anOlderRosterArrivingLateIsNotShown() {
        ClientCharacterRosterCache.beginRequest(7, CharacterOperationType.REQUEST_ROSTER);
        ClientCharacterRosterCache.acceptRoster(0, roster(5L));
        ClientCharacterRosterCache.acceptRoster(7, roster(4L));

        assertEquals(5L, ClientCharacterRosterCache.getSnapshot().getRevision());
        assertFalse("the late answer still completes its request",
                ClientCharacterRosterCache.isRequestPending(7));

        ClientCharacterRosterCache.acceptRoster(0, roster(5L));
        ClientCharacterRosterCache.acceptRoster(0, roster(6L));
        assertEquals(6L, ClientCharacterRosterCache.getSnapshot().getRevision());
    }

    private static CharacterOperationFeedback feedback(int requestId,
                                                       boolean successful) {
        return new CharacterOperationFeedback(requestId,
                requestId == 1 ? CharacterOperationType.CAPE_UPDATE
                        : CharacterOperationType.PROFILE_UPDATE,
                successful,
                successful ? CharacterErrorId.NONE : CharacterErrorId.STALE_ROSTER,
                -1L, false);
    }

    private static CharacterRosterSnapshot roster(long revision) {
        return new CharacterRosterSnapshot(OWNER, 1, null, revision,
                Collections.<CharacterSummary>emptyList(),
                Collections.<DeletedCharacterSummary>emptyList());
    }
}
