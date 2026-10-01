package com.ninuna.losttales.fellowship.model;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A fellowship knows which accounts its characters belong to: one account has one character in a fellowship. */
public final class FellowshipAccountTest {

    @Test
    public void aFellowshipKnowsTheAccountsOfItsCharacters() {
        UUID account = UUID.randomUUID();
        Fellowship fellowship = Fellowship.createNew(UUID.randomUUID(), "Grey Company",
                new FellowshipMember(UUID.randomUUID(), account, "Aldric", 1L,
                        FellowshipColor.GREEN), 1L);

        assertTrue(fellowship.hasMemberOwnedBy(account));
        assertFalse(fellowship.hasMemberOwnedBy(UUID.randomUUID()));
        assertFalse(fellowship.hasMemberOwnedBy(null));
    }
}
