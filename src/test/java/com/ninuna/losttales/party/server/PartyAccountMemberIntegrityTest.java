package com.ninuna.losttales.party.server;

import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyWorldData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * An account playing as itself is a party member filed under its own id.
 * The referential-integrity pass must keep such a member while the account
 * has a roster, and still remove a member that belongs to nobody.
 */
public final class PartyAccountMemberIntegrityTest {

    @Test
    public void anAccountMemberSurvivesTheIntegrityPass() {
        UUID accountId = UUID.randomUUID();
        UUID orphanId = UUID.randomUUID();
        CharacterWorldData characters = new CharacterWorldData(
                CharacterWorldData.DATA_NAME);
        characters.getOrCreateRoster(accountId);

        PartyWorldData parties = new PartyWorldData(PartyWorldData.DATA_NAME);
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        members.add(new PartyMember(accountId, accountId, "Alice", 1L, PartyColor.GREEN));
        members.add(new PartyMember(orphanId, UUID.randomUUID(), "Nobody", 2L,
                PartyColor.PURPLE));
        UUID partyId = UUID.randomUUID();
        parties.saveParty(new Party(partyId, accountId, members, 1L, 0L,
                Party.CURRENT_DATA_VERSION));

        assertTrue(PartyService.getInstance().ensurePartyIntegrity(
                null, parties, characters));

        Party party = parties.getParty(partyId);
        assertNotNull(party);
        assertNotNull("the account keeps its place", party.getMember(accountId));
        assertEquals("the stored name stands when none can be resolved",
                "Alice", party.getMember(accountId).getCharacterName());
        assertNull("an id belonging to nobody is removed", party.getMember(orphanId));
        assertEquals(1, party.getMemberCount());
        assertTrue(parties.areCharacterReferencesValidated());
    }

    @Test
    public void anIdThatOnlyLooksLikeAnAccountIsRemoved() {
        UUID accountId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        CharacterWorldData characters = new CharacterWorldData(
                CharacterWorldData.DATA_NAME);
        characters.getOrCreateRoster(accountId);

        PartyWorldData parties = new PartyWorldData(PartyWorldData.DATA_NAME);
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        members.add(new PartyMember(accountId, accountId, "Alice", 1L, PartyColor.GREEN));
        // Filed under its own id like an account member, but no roster
        // belongs to it: nothing on this server plays as that id.
        members.add(new PartyMember(strangerId, strangerId, "Stranger", 2L,
                PartyColor.PURPLE));
        UUID partyId = UUID.randomUUID();
        parties.saveParty(new Party(partyId, accountId, members, 1L, 0L,
                Party.CURRENT_DATA_VERSION));

        PartyService.getInstance().ensurePartyIntegrity(null, parties, characters);

        Party party = parties.getParty(partyId);
        assertNotNull(party.getMember(accountId));
        assertNull(party.getMember(strangerId));
    }
}
