package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.character.deletion.CharacterDeletionTombstone;
import com.ninuna.losttales.character.deletion.CharacterDeletionWorldData;
import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A waystone names characters: who it is called after, by the name that
 * character has now (WAY-01), and who it is shared with, a deleted
 * character standing until its purge (WAY-03).
 */
public final class LostTalesWaystoneCharactersTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ARAGORN = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID TOBIN = UUID.fromString("b0000000-0000-0000-0000-00000000000b");

    @Test
    public void aWaystoneIsCalledAfterTheCharacterThatPlacedIt() {
        LostTalesMapMarkerRecord record = placed(ARAGORN);
        assertTrue(record.isCalledAfter(ARAGORN));
        assertFalse(record.isCalledAfter(TOBIN));
        assertFalse(record.isCalledAfter(null));
        assertEquals(ARAGORN, record.getOwnerCharacterId());
        assertTrue("handed over, it stays called after its placer",
                record.withOwner(TOBIN).isCalledAfter(ARAGORN));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aWaystoneCalledAfterNobodyIsNotMade() {
        LostTalesMapMarkerRecord.createPlayerMarker("losttales:player/abc",
                null, 0, 1.0D, 64.0D, 1.0D, UUID.randomUUID());
    }

    /** The owner's own characters stay on the list as any share would. */
    @Test
    public void aShareKeepsTheCharacterWhoeverOwnsIt() {
        UUID ownersCharacter = UUID.randomUUID();
        LostTalesMapMarkerRecord record = placed(ARAGORN).withSharedCharacters(
                Collections.singleton(ownersCharacter));
        assertTrue(record.getSharedCharacterIds().contains(ownersCharacter));
    }

    /** Each lookup names the placer as it stands: renamed, deleted, purged. */
    @Test
    public void thePlacerIsNamedAsItStandsNow() {
        CharacterWorldData rosters = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        CharacterRoster roster = new CharacterRoster(OWNER, 3, null, 0L);
        RoleplayCharacter aragorn = character(ARAGORN, "Aragorn", 0);
        assertTrue(roster.addCharacter(aragorn));
        rosters.saveRoster(roster);
        CharacterDeletionWorldData deletions = deletions();
        LostTalesMapMarkerRecord record = placed(ARAGORN);

        assertEquals("player:Aragorn", record.toDefinition(
                new LostTalesWaystonePlacers(rosters, deletions, null))
                .getNamedAfter());

        assertTrue(roster.replaceCharacter(RoleplayCharacter.builder(aragorn)
                .name("Elessar").build()));
        rosters.saveRoster(roster);
        assertEquals("an admin's rename reaches it", "player:Elessar",
                record.toDefinition(new LostTalesWaystonePlacers(
                        rosters, deletions, null)).getNamedAfter());

        roster.removeCharacter(ARAGORN);
        rosters.saveRoster(roster);
        deletions.saveTombstone(committed(character(ARAGORN, "Elessar", 0)));
        assertEquals("deleted, it keeps the name it went with", "Elessar",
                new LostTalesWaystonePlacers(rosters, deletions, null)
                        .nameOf(ARAGORN));

        deletions.removeTombstone(ARAGORN);
        LostTalesWaystonePlacers purged =
                new LostTalesWaystonePlacers(rosters, deletions, null);
        assertNull(purged.nameOf(ARAGORN));
        assertEquals("purged, it is sent as kept, and reads Waystone",
                record.getNamedAfter(),
                record.toDefinition(purged).getNamedAfter());
        assertNull("no store names nobody",
                new LostTalesWaystonePlacers(null, null, null).nameOf(ARAGORN));
    }

    /** A snapshot sends every map the placer by its name, never the id it is kept by. */
    @Test
    public void aSnapshotSendsThePlacerByName() {
        CharacterWorldData rosters = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        CharacterRoster roster = new CharacterRoster(OWNER, 3, null, 0L);
        assertTrue(roster.addCharacter(character(ARAGORN, "Aragorn", 0)));
        rosters.saveRoster(roster);
        LostTalesMapMarkerRecord record = placed(ARAGORN).toBuilder()
                .discovery(false, true, false).build();
        assertEquals("player:Aragorn", LostTalesMapMarkerSyncManager.select(
                Collections.singletonList(record),
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(ARAGORN, false, null),
                null, new LostTalesWaystonePlacers(rosters, deletions(), null))
                .get(0).getNamedAfter());
    }

    /** A lore figure nobody holds keeps the name its story gives it. */
    @Test
    public void aLoreFigureNobodyHoldsIsNamedByItsStory() {
        LoreCharacterRegistry.load(null);
        LoreCharacterDefinition figure = null;
        for (LoreCharacterDefinition definition : LoreCharacterRegistry.getAll()) {
            if (definition.hasAppearance()) {
                figure = definition;
                break;
            }
        }
        assertNotNull("a bundled lore figure", figure);
        LoreCharacterOwnershipWorldData lore = new LoreCharacterOwnershipWorldData(
                LoreCharacterOwnershipWorldData.DATA_NAME);
        UUID figureId = UUID.randomUUID();
        lore.tryClaim(figure.getId(), OWNER, figureId, 0L, 1L);
        lore.tryRelease(figure.getId(), OWNER,
                lore.getRecordByCharacterId(figureId).getRevision(), 2L);
        assertFalse(lore.getRecordByCharacterId(figureId).isClaimed());
        assertEquals(figure.getName(), new LostTalesWaystonePlacers(
                new CharacterWorldData(CharacterWorldData.DATA_NAME),
                deletions(), lore).nameOf(figureId));
    }

    /** A deleted character's share stands until its purge (WAY-03). */
    @Test
    public void aDeletedCharactersShareStandsUntilItsPurge() {
        CharacterWorldData rosters = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        CharacterDeletionWorldData deletions = deletions();
        deletions.saveTombstone(committed(character(TOBIN, "Tobin", 1)));
        LostTalesMapMarkerRecord shared = placed(ARAGORN)
                .withSharedCharacters(Collections.singleton(TOBIN));

        assertSame("deleted, the share stays", shared,
                LostTalesWaystoneSettingsService.withoutLostShares(shared,
                        subjects(rosters, deletions)));

        deletions.removeTombstone(TOBIN);
        LostTalesMapMarkerRecord after =
                LostTalesWaystoneSettingsService.withoutLostShares(shared,
                        subjects(rosters, deletions));
        assertTrue("purged, the share counts for nothing",
                after.getSharedCharacterIds().isEmpty());
        assertEquals("and the record's revision stays",
                shared.getRevision(), after.getRevision());
    }

    /** A deletion journal that reads only lets no share go on its word. */
    @Test
    public void aJournalThatReadsOnlyKeepsEveryShare() {
        CharacterDeletionWorldData unreadable = deletions();
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion", CharacterDeletionWorldData.CURRENT_DATA_VERSION + 1);
        unreadable.readFromNBT(newer);
        assertTrue(unreadable.isReadOnlyForNewerVersion());
        LostTalesMapMarkerRecord shared = placed(ARAGORN)
                .withSharedCharacters(Collections.singleton(TOBIN));
        assertSame(shared, LostTalesWaystoneSettingsService.withoutLostShares(
                shared, subjects(new CharacterWorldData(CharacterWorldData.DATA_NAME),
                        unreadable)));
    }

    /** Unshare finds a deleted character by the name it went with. */
    @Test
    public void unshareFindsADeletedCharacterByItsName() {
        CharacterDeletionWorldData deletions = deletions();
        deletions.saveTombstone(committed(character(TOBIN, "Tobin", 1)));
        LostTalesMapMarkerRecord shared = placed(ARAGORN)
                .withSharedCharacters(Collections.singleton(TOBIN));
        LostTalesWaystonePlacers names = new LostTalesWaystonePlacers(
                new CharacterWorldData(CharacterWorldData.DATA_NAME), deletions, null);

        assertEquals(TOBIN, LostTalesWaystoneSettingsService.sharedNamed(
                names, shared, "tobin"));
        assertNull(LostTalesWaystoneSettingsService.sharedNamed(
                names, shared, "Beren"));
        LostTalesWaystoneSettingsService.Outcome unshared =
                LostTalesWaystoneSettingsService.unshareCharacter(shared,
                        LostTalesWaystoneSettingsService.sharedNamed(
                                names, shared, "Tobin"));
        assertNull(unshared.refusal());
        assertTrue(unshared.updated().getSharedCharacterIds().isEmpty());
    }

    /** The purge takes the character's share off every waystone, and only its own. */
    @Test
    public void thePurgeTakesTheShareOffEveryWaystone() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData(LostTalesMapMarkerWorldData.DATA_NAME);
        UUID beren = UUID.randomUUID();
        LostTalesMapMarkerRecord both = placed(ARAGORN, "losttales:player/both", 1)
                .withSharedCharacters(new LinkedHashSet<UUID>(Arrays.asList(TOBIN, beren)));
        LostTalesMapMarkerRecord tobins = placed(ARAGORN, "losttales:player/tobin", 2)
                .withSharedCharacters(Collections.singleton(TOBIN));
        LostTalesMapMarkerRecord none = placed(ARAGORN, "losttales:player/none", 3);
        data.saveRecord(both);
        data.saveRecord(tobins);
        data.saveRecord(none);

        assertEquals(2, data.forgetSharedCharacter(TOBIN));

        LostTalesMapMarkerRecord bothAfter = data.getRecord(both.getId());
        assertEquals(Collections.singleton(beren), bothAfter.getSharedCharacterIds());
        assertTrue(bothAfter.getRevision() > both.getRevision());
        assertEquals("with no share left, it reads private",
                LostTalesMapMarkerVisibility.PRIVATE,
                data.getRecord(tobins.getId()).getVisibility());
        assertSame("a waystone it had no share of is untouched", none,
                data.getRecord(none.getId()));
        assertEquals("nothing left to take", 0, data.forgetSharedCharacter(TOBIN));
        assertSame(none, none.withoutSharedCharacter(TOBIN));
    }

    private static LostTalesWaystoneSettingsService.Subjects subjects(
            CharacterWorldData rosters, CharacterDeletionWorldData deletions) {
        return LostTalesWaystoneSettingsService.subjects(rosters,
                new LoreCharacterOwnershipWorldData(
                        LoreCharacterOwnershipWorldData.DATA_NAME),
                deletions,
                new FellowshipWorldData(FellowshipWorldData.DATA_NAME));
    }

    private static CharacterDeletionWorldData deletions() {
        return new CharacterDeletionWorldData(CharacterDeletionWorldData.DATA_NAME);
    }

    private static CharacterDeletionTombstone committed(RoleplayCharacter character) {
        CharacterDeletionTombstone tombstone =
                CharacterDeletionTombstone.prepared(character, 1L, 1000L);
        tombstone.commit(1000L, 1000L + 30L * 24L * 60L * 60L * 1000L);
        return tombstone;
    }

    private static LostTalesMapMarkerRecord placed(UUID placer) {
        return placed(placer, "losttales:player/abc", 1);
    }

    private static LostTalesMapMarkerRecord placed(UUID placer, String id, int x) {
        return LostTalesMapMarkerRecord.createPlayerMarker(id, placer, 0,
                x, 64.0D, 1.0D, UUID.randomUUID());
    }

    private static RoleplayCharacter character(UUID id, String name, int slot) {
        return RoleplayCharacter.builder(id, OWNER)
                .slot(slot)
                .name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .startingFaction("lotr:gondor")
                .build();
    }
}
