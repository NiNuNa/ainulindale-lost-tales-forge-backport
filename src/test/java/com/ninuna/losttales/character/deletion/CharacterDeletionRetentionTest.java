package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.server.SeenAccountNames;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Deleted characters: the owner may restore one within the retention,
 * into a free open slot, while no other character or other account has
 * taken its name; past the retention the server purges it, soonest due
 * first and a bounded number at a time.
 */
public final class CharacterDeletionRetentionTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final UUID OWNER =
            UUID.fromString("b3000000-0000-0000-0000-00000000003b");
    private static final UUID OTHER =
            UUID.fromString("b4000000-0000-0000-0000-00000000004b");

    @Before
    public void loadLore() {
        LoreCharacterRegistry.load(null);
    }

    @Test
    public void aDeletedCharacterIsRestorableByItsOwnerWithinTheRetention() {
        CharacterDeletionTombstone tombstone = committed(
                character(OWNER, "Aldric", 1), 1000L, 1000L + 30L * DAY);
        assertTrue(CharacterDeletionService.isRestorable(tombstone, OWNER,
                1000L + 29L * DAY));
        assertFalse("past its retention",
                CharacterDeletionService.isRestorable(tombstone, OWNER,
                        1000L + 30L * DAY));
        assertFalse("not another player's to restore",
                CharacterDeletionService.isRestorable(tombstone, OTHER,
                        2000L));
        CharacterDeletionTombstone prepared = CharacterDeletionTombstone
                .prepared(character(OWNER, "Beren", 2), 1L, 1000L);
        assertFalse("a deletion that never committed",
                CharacterDeletionService.isRestorable(prepared, OWNER, 2000L));
    }

    @Test
    public void itGoesIntoTheFirstFreeOpenSlot() {
        CharacterRoster roster = new CharacterRoster(OWNER, 3, null, 0L);
        assertTrue(roster.addCharacter(character(OWNER, "Aldric", 0)));
        assertEquals(1, CharacterDeletionService.firstFreeOpenSlot(roster));
        assertTrue(roster.addCharacter(character(OWNER, "Beren", 1)));
        assertTrue(roster.addCharacter(character(OWNER, "Cirion", 2)));
        assertEquals("every open slot is filled", -1,
                CharacterDeletionService.firstFreeOpenSlot(roster));
    }

    @Test
    public void itIsRefusedWhileItsNameIsAnothersNow() {
        CharacterWorldData data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        RoleplayCharacter deleted = character(OWNER, "Aldric", 0);
        assertEquals(CharacterErrorId.NONE,
                CharacterDeletionService.nameRefusal(data, deleted, "Owner",
                        accounts()));
        assertTrue(data.getOrCreateRoster(OTHER).addCharacter(
                character(OTHER, "Al-dric", 0)));
        assertEquals(CharacterErrorId.DUPLICATE_NAME,
                CharacterDeletionService.nameRefusal(data, deleted, "Owner",
                        accounts()));
    }

    @Test
    public void aLoreNameOrAChatVoiceIsReserved() {
        CharacterWorldData data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        assertEquals(CharacterErrorId.NAME_RESERVED,
                CharacterDeletionService.nameRefusal(data,
                        character(OWNER, "Gandalf", 0), "Owner", accounts()));
        assertEquals(CharacterErrorId.NAME_RESERVED,
                CharacterDeletionService.nameRefusal(data,
                        character(OWNER, "Narrator", 0), "Owner", accounts()));
    }

    /** An account the server has seen takes its name back from a deleted character; the owner's own does not. */
    @Test
    public void itIsRefusedWhileItsNameIsAnAccountsTheServerHasSeen() {
        CharacterWorldData data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        assertEquals(CharacterErrorId.ACCOUNT_NAME,
                CharacterDeletionService.nameRefusal(data,
                        character(OWNER, "Notch", 0), "Owner",
                        accounts("Owner", "Notch")));
        assertEquals(CharacterErrorId.NONE,
                CharacterDeletionService.nameRefusal(data,
                        character(OWNER, "Owner", 0), "Owner",
                        accounts("Owner", "Notch")));
    }

    /** The accounts a server has seen, as a test names them. */
    private static SeenAccountNames.Source accounts(final String... names) {
        return new SeenAccountNames.Source() {
            @Override
            public void addNames(List<String> into, int limit) {
                for (String name : names) {
                    if (into.size() < limit) {
                        into.add(name);
                    }
                }
            }
        };
    }

    @Test
    public void theExpiredArePurgedSoonestDueFirstAndBounded() {
        CharacterDeletionWorldData data = new CharacterDeletionWorldData(
                CharacterDeletionWorldData.DATA_NAME);
        long now = 100L * DAY;
        data.saveTombstone(committed(character(OWNER, "Late", 1),
                1000L, now - DAY));
        data.saveTombstone(committed(character(OWNER, "Early", 2),
                1000L, now - 5L * DAY));
        data.saveTombstone(committed(character(OTHER, "Theirs", 1),
                1000L, now - 3L * DAY));
        data.saveTombstone(committed(character(OWNER, "Kept", 3),
                1000L, now + DAY));
        data.savePrepared(CharacterDeletionTombstone.prepared(
                character(OWNER, "Unfinished", 4), 1L, 1000L));

        List<CharacterDeletionTombstone> all = data.getExpired(null, now, 10);
        assertEquals(3, all.size());
        assertEquals("Early", all.get(0).getCharacterCopy().getName());
        assertEquals("Theirs", all.get(1).getCharacterCopy().getName());
        assertEquals("Late", all.get(2).getCharacterCopy().getName());

        List<CharacterDeletionTombstone> owners = data.getExpired(OWNER, now, 10);
        assertEquals(2, owners.size());
        assertEquals(1, data.getExpired(null, now, 1).size());
        assertTrue(data.getExpired(OWNER, now, 0).isEmpty());
    }

    @Test
    public void daysLeftCountUp() {
        assertEquals(30, DeletedCharacterSummary.daysLeft(0L, 30L * DAY));
        assertEquals(1, DeletedCharacterSummary.daysLeft(0L, 60L * 60L * 1000L));
        assertEquals(0, DeletedCharacterSummary.daysLeft(5L, 5L));
        assertEquals(0, DeletedCharacterSummary.daysLeft(10L, 5L));
    }

    private static CharacterDeletionTombstone committed(
            RoleplayCharacter character, long deletedAt, long purgeAfter) {
        CharacterDeletionTombstone tombstone = CharacterDeletionTombstone
                .prepared(character, 1L, deletedAt);
        tombstone.commit(deletedAt, purgeAfter);
        return tombstone;
    }

    private static RoleplayCharacter character(UUID owner, String name,
                                               int slot) {
        return RoleplayCharacter.builder(UUID.randomUUID(), owner)
                .slot(slot).name(name).race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_bree_male_0").age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .build();
    }
}
