package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.storage.CharacterIndex;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A released lore figure keeps its places in fellowships, held by nobody,
 * and its next holder takes them up (CHAR-36): it never leads while
 * nobody holds it, and a fellowship nobody plays a member of ends.
 */
public final class FellowshipReleasedFigureTest {

    private static final FellowshipService.LeadLimits ANY =
            new FellowshipService.LeadLimits() {
                @Override
                public int of(FellowshipMember member) {
                    return 8;
                }
            };

    private final FellowshipMember aldric = member("Aldric", UUID.randomUUID(), 1L,
            FellowshipColor.GREEN);
    private final FellowshipMember beren = member("Beren", FellowshipMember.UNHELD, 2L,
            FellowshipColor.BLUE);
    private final FellowshipMember celeb = member("Celeb", UUID.randomUUID(), 3L,
            FellowshipColor.RED);

    @Test
    public void aFigureNobodyHoldsNeverTakesTheLead() {
        FellowshipWorldData data = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        Fellowship grey = company(data);
        assertTrue(beren.isUnheld());
        assertEquals("Beren joined before Celeb, but nobody holds him",
                celeb.getIdentityId(), FellowshipService.successorOf(data, grey,
                        aldric.getIdentityId(), ANY));
        grey.removeMember(celeb.getIdentityId());
        assertNull("only Beren is left, and he cannot lead",
                FellowshipService.successorOf(data, grey, aldric.getIdentityId(), ANY));
        assertFalse(grey.hasHeldMemberBesides(aldric.getIdentityId()));
        assertTrue(grey.hasHeldMemberBesides(beren.getIdentityId()));
    }

    @Test
    public void theLeadPassesToSomebodysFigure() {
        FellowshipWorldData data = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        Fellowship grey = company(data);
        // Beren joined before Celeb, but nobody holds him.
        grey.removeMember(aldric.getIdentityId());
        assertEquals(celeb.getIdentityId(), grey.getLeader().getIdentityId());
    }

    @Test
    public void theNextHolderTakesUpThePlaceOrLeavesWhereTheirAccountIs() {
        FellowshipWorldData data = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        Fellowship grey = company(data);
        UUID holder = UUID.randomUUID();
        assertTrue(FellowshipService.takeUp(grey, beren.getIdentityId(), holder, "Beren"));
        assertEquals(holder, grey.getMember(beren.getIdentityId()).getOwnerId());
        assertFalse("taken up once", FellowshipService.takeUp(grey, beren.getIdentityId(),
                holder, "Beren"));

        Fellowship rangers = company(data);
        assertTrue("Celeb's account claims Beren: one character of an account in a fellowship",
                FellowshipService.takeUp(rangers, beren.getIdentityId(),
                        celeb.getOwnerId(), "Beren"));
        assertFalse(rangers.containsMember(beren.getIdentityId()));
    }

    @Test
    public void aFigureNobodyHoldsStandsWhileItIsALoreFigure() {
        CharacterIndex index = new CharacterWorldData(CharacterWorldData.DATA_NAME)
                .characterIndex();
        UUID berenId = beren.getIdentityId();
        assertNull(FellowshipService.memberRemovalReason(beren, index,
                new HashSet<UUID>(Collections.singleton(berenId))));
        assertNull("a lore store that cannot say keeps it",
                FellowshipService.memberRemovalReason(beren, index, null));
        assertEquals("missing_character", FellowshipService.memberRemovalReason(beren, index,
                Collections.<UUID>emptySet()));
        assertEquals("a held member still needs its character", "missing_character",
                FellowshipService.memberRemovalReason(aldric, index,
                        new HashSet<UUID>(Collections.singleton(aldric.getIdentityId()))));
    }

    @Test
    public void aFigureClaimedSinceStandsForItsHolderToTakeUp() {
        CharacterWorldData characters = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        UUID holder = UUID.randomUUID();
        assertTrue(characters.getOrCreateRoster(holder).addCharacter(
                RoleplayCharacter.builder(beren.getIdentityId(), holder)
                        .slot(0).name("Beren").race(CharacterRaceRegistry.HUMAN)
                        .gender(CharacterGenderRegistry.MALE)
                        .skin("losttales:human_bree_male_0").age(30)
                        .startingFaction("lotr:bree").createdAt(1L).build()));
        assertNull(FellowshipService.memberRemovalReason(beren, characters.characterIndex(),
                Collections.<UUID>emptySet()));
    }

    private Fellowship company(FellowshipWorldData data) {
        Fellowship grey = Fellowship.createNew(UUID.randomUUID(), "The Grey Company",
                aldric, 1L);
        grey.addMember(beren);
        grey.addMember(celeb);
        data.saveFellowship(grey);
        return grey;
    }

    private static FellowshipMember member(String name, UUID owner, long joinedAt,
                                           FellowshipColor color) {
        return new FellowshipMember(UUID.randomUUID(), owner, name, joinedAt, color);
    }
}
