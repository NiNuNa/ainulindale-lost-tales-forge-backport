package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipNbtCodec;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    /**
     * The pass names each fellowship it changed or ended, whose LOTR
     * fellowship is then set again, and no other.
     */
    @Test
    public void thePassNamesTheFellowshipsItChanged() {
        UUID account = new UUID(1L, 1L);
        UUID aldric = new UUID(10L, 10L);
        CharacterWorldData characters = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        CharacterRoster roster = characters.getOrCreateRoster(account);
        roster.addCharacter(RoleplayCharacter.builder(aldric, account)
                .slot(0)
                .name("Aldric the Bold")
                .race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE)
                .skin("losttales:human_bree_masculine_0")
                .build());
        characters.saveRoster(roster);

        FellowshipWorldData fellowships = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        UUID renamed = new UUID(20L, 20L);
        UUID current = new UUID(21L, 21L);
        UUID emptied = new UUID(22L, 22L);
        fellowships.saveFellowship(company(renamed, new FellowshipMember(aldric, account,
                "Aldric", 1L, FellowshipColor.GREEN)));
        fellowships.saveFellowship(company(current, new FellowshipMember(aldric, account,
                "Aldric the Bold", 1L, FellowshipColor.GREEN)));
        UUID nobody = new UUID(30L, 30L);
        fellowships.saveFellowship(company(emptied, new FellowshipMember(nobody,
                new UUID(3L, 3L), "Nobody", 1L, FellowshipColor.RED)));

        List<UUID> changed = new ArrayList<UUID>();
        assertEquals(1, FellowshipService.repairMemberReferences(fellowships, characters,
                Collections.<UUID>emptySet(), changed));
        assertEquals(Arrays.asList(renamed, emptied), changed);
        assertEquals("Aldric the Bold",
                fellowships.getFellowship(renamed).getMember(aldric).getCharacterName());
        assertNull(fellowships.getFellowship(emptied));
    }

    /**
     * A pass the quarantine has no room for changes nothing and leaves the
     * store closed, read-only, rather than let a member go unkept.
     */
    @Test
    public void aPassTheQuarantineHasNoRoomForChangesNothing() {
        List<NBTTagCompound> full = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < FellowshipNbtCodec.MAX_QUARANTINED; index++) {
            full.add(FellowshipNbtCodec.createQuarantineEntry("missing_character",
                    null, UUID.randomUUID()));
        }
        NBTTagCompound saved = new NBTTagCompound();
        FellowshipNbtCodec.write(saved, Collections.<Fellowship>emptyList(),
                Collections.<UUID, UUID>emptyMap(), Collections.<UUID, UUID>emptyMap(), full);
        FellowshipWorldData fellowships = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        fellowships.readFromNBT(saved);
        UUID fellowshipId = new UUID(20L, 20L);
        UUID nobody = new UUID(30L, 30L);
        fellowships.saveFellowship(company(fellowshipId, new FellowshipMember(nobody,
                new UUID(3L, 3L), "Nobody", 1L, FellowshipColor.RED)));

        assertFalse(FellowshipService.getInstance().ensureFellowshipIntegrity(
                null, fellowships, new CharacterWorldData(CharacterWorldData.DATA_NAME)));
        assertTrue(fellowships.isReadOnlyForNewerVersion());
        assertFalse(fellowships.areCharacterReferencesValidated());
        assertEquals(1, fellowships.getFellowship(fellowshipId).getMemberCount());
        assertEquals(FellowshipNbtCodec.MAX_QUARANTINED,
                fellowships.getQuarantinedEntryCount());
    }

    private static Fellowship company(UUID fellowshipId, FellowshipMember leader) {
        return new Fellowship(fellowshipId, leader.getIdentityId(),
                Collections.singletonList(leader), Collections.<UUID>emptyList(),
                "Grey Company", null, EnumSet.allOf(FellowshipSwitch.class), 1L, 0L,
                Fellowship.CURRENT_DATA_VERSION);
    }
}
