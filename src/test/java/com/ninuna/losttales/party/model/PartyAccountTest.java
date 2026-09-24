package com.ninuna.losttales.party.model;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A party knows which accounts its characters belong to: one account has one character in a party. */
public final class PartyAccountTest {

    @Test
    public void aPartyKnowsTheAccountsOfItsCharacters() {
        UUID account = UUID.randomUUID();
        Party party = Party.createNew(UUID.randomUUID(), new PartyMember(
                UUID.randomUUID(), account, "Aldric", 1L, PartyColor.GREEN), 1L);

        assertTrue(party.hasMemberOwnedBy(account));
        assertFalse(party.hasMemberOwnedBy(UUID.randomUUID()));
        assertFalse(party.hasMemberOwnedBy(null));
    }
}
