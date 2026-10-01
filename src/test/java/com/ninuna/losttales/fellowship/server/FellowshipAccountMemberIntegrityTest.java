package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * An account playing as itself is a fellowship member filed under its own id.
 * The referential-integrity pass must keep such a member while the account
 * has a roster, and still remove a member that belongs to nobody.
 */
public final class FellowshipAccountMemberIntegrityTest {

    @Test
    public void anAccountMemberSurvivesTheIntegrityPass() {
        UUID accountId = UUID.randomUUID();
        UUID orphanId = UUID.randomUUID();
        CharacterWorldData characters = new CharacterWorldData(
                CharacterWorldData.DATA_NAME);
        characters.getOrCreateRoster(accountId);

        FellowshipWorldData fellowships = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        ArrayList<FellowshipMember> members = new ArrayList<FellowshipMember>();
        members.add(new FellowshipMember(accountId, accountId, "Alice", 1L, FellowshipColor.GREEN));
        members.add(new FellowshipMember(orphanId, UUID.randomUUID(), "Nobody", 2L,
                FellowshipColor.PURPLE));
        UUID fellowshipId = UUID.randomUUID();
        fellowships.saveFellowship(new Fellowship(fellowshipId, accountId, members,
                Collections.<UUID>emptyList(), "Grey Company", null,
                EnumSet.allOf(FellowshipSwitch.class), 1L, 0L,
                Fellowship.CURRENT_DATA_VERSION));

        assertTrue(FellowshipService.getInstance().ensureFellowshipIntegrity(
                null, fellowships, characters));

        Fellowship fellowship = fellowships.getFellowship(fellowshipId);
        assertNotNull(fellowship);
        assertNotNull("the account keeps its place", fellowship.getMember(accountId));
        assertEquals("the stored name stands when none can be resolved",
                "Alice", fellowship.getMember(accountId).getCharacterName());
        assertNull("an id belonging to nobody is removed", fellowship.getMember(orphanId));
        assertEquals(1, fellowship.getMemberCount());
        assertTrue(fellowships.areCharacterReferencesValidated());
    }

    @Test
    public void anIdThatOnlyLooksLikeAnAccountIsRemoved() {
        UUID accountId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        CharacterWorldData characters = new CharacterWorldData(
                CharacterWorldData.DATA_NAME);
        characters.getOrCreateRoster(accountId);

        FellowshipWorldData fellowships = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        ArrayList<FellowshipMember> members = new ArrayList<FellowshipMember>();
        members.add(new FellowshipMember(accountId, accountId, "Alice", 1L, FellowshipColor.GREEN));
        // Filed under its own id like an account member, but no roster
        // belongs to it: nothing on this server plays as that id.
        members.add(new FellowshipMember(strangerId, strangerId, "Stranger", 2L,
                FellowshipColor.PURPLE));
        UUID fellowshipId = UUID.randomUUID();
        fellowships.saveFellowship(new Fellowship(fellowshipId, accountId, members,
                Collections.<UUID>emptyList(), "Grey Company", null,
                EnumSet.allOf(FellowshipSwitch.class), 1L, 0L,
                Fellowship.CURRENT_DATA_VERSION));

        FellowshipService.getInstance().ensureFellowshipIntegrity(null, fellowships, characters);

        Fellowship fellowship = fellowships.getFellowship(fellowshipId);
        assertNotNull(fellowship.getMember(accountId));
        assertNull(fellowship.getMember(strangerId));
    }
}
