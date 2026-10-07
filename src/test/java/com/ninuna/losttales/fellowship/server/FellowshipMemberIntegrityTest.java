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

import static org.junit.Assert.assertTrue;

/**
 * A fellowship is a company of characters: the referential-integrity pass
 * removes a member filed under an id that names no character, its owner's
 * own id included.
 */
public final class FellowshipMemberIntegrityTest {

    @Test
    public void aMemberNamingNoCharacterIsRemoved() {
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

        FellowshipService.getInstance().ensureFellowshipIntegrity(
                null, fellowships, characters);

        Fellowship fellowship = fellowships.getFellowship(fellowshipId);
        assertTrue("an emptied fellowship ends", fellowship == null
                || fellowship.getMember(accountId) == null
                && fellowship.getMember(orphanId) == null);
    }
}
