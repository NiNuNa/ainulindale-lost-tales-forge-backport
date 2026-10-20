package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerWorldDataTest {

    @Test
    public void deletedPresetIsNotReseeded() {
        LostTalesMapMarkerDefinition definition =
                new LostTalesMapMarkerDefinition(
                        "losttales:preset", "Preset", "fort", "white",
                        "Town", "", true, 0,
                        32, 64, 32, 128, 8,
                        true, true, false,
                        LostTalesMapMarkerSource.CUSTOM_PRESET,
                        true,
                        "losttales:glowstone_house", 0);
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(definition));
        LostTalesMapMarkerRecord seeded =
                data.getRecord(definition.getId());
        assertNotNull(seeded);
        int travelId = data.getOrCreateLotrTravelId(definition.getId());
        assertTrue(travelId > 0);

        assertTrue(data.removeRecord(definition.getId()));
        assertNull(data.findByLotrTravelId(travelId));
        data.seedDefinitions(Collections.singleton(definition));

        assertEquals(null, data.getRecord(definition.getId()));

        NBTTagCompound serialized = new NBTTagCompound();
        data.writeToNBT(serialized);
        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);
        restored.seedDefinitions(Collections.singleton(definition));
        assertEquals(null, restored.getRecord(definition.getId()));
    }

    @Test
    public void lotrTravelIdsAreUniqueAndPersisted() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(java.util.Arrays.asList(
                definition("losttales:first"),
                definition("losttales:second")));

        int first = data.getOrCreateLotrTravelId(
                "losttales:first");
        int second = data.getOrCreateLotrTravelId(
                "losttales:second");
        assertNotEquals(first, second);
        assertEquals(first, data.getOrCreateLotrTravelId(
                "losttales:first"));

        NBTTagCompound serialized = new NBTTagCompound();
        data.writeToNBT(serialized);
        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);

        assertEquals("losttales:first",
                restored.findByLotrTravelId(first).getId());
        assertEquals("losttales:second",
                restored.findByLotrTravelId(second).getId());
    }

    @Test
    public void nativeLotrIdentityIsCanonicalForLookupAndRemoval() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(
                definition("lotr:waypoint:HOBBITON")));

        assertNotNull(data.getRecord(
                "LOTR:WAYPOINT:hobbiton"));
        int travelId = data.getOrCreateLotrTravelId(
                "lotr:waypoint:hobbiton");
        assertEquals(travelId, data.getOrCreateLotrTravelId(
                "LOTR:WAYPOINT:HOBBITON"));
        assertTrue(data.removeRecord(
                "LOTR:WAYPOINT:hobbiton"));
        assertEquals(null, data.getRecord(
                "lotr:waypoint:HOBBITON"));
        assertNull(data.findByLotrTravelId(travelId));
    }

    @Test
    public void seedingSkipsLogicalLotrDuplicates() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");

        data.seedDefinitions(java.util.Arrays.asList(
                definition("lotr:waypoint:HOBBITON"),
                definition("LOTR:WAYPOINT:hobbiton")));

        assertEquals(1, data.getRecords().size());
        assertEquals("lotr:waypoint:HOBBITON",
                data.getRecord("lotr:waypoint:hobbiton").getId());
    }

    @Test
    public void customMarkerIdsRetainCaseSensitiveIdentity() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");

        data.seedDefinitions(java.util.Arrays.asList(
                definition("losttales:Town"),
                definition("losttales:town")));

        assertEquals(2, data.getRecords().size());
    }

    @Test
    public void loadKeepsHighestRevisionLogicalLotrDuplicate() {
        LostTalesMapMarkerRecord oldRecord =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("lotr:waypoint:HOBBITON"));
        LostTalesMapMarkerRecord newRecord =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("LOTR:WAYPOINT:hobbiton"))
                        .toBuilder()
                        .name("New Hobbiton")
                        .revision(5L)
                        .build();
        NBTTagCompound serialized = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(
                serialized,
                java.util.Arrays.asList(oldRecord, newRecord),
                Collections.<NBTTagCompound>emptyList());
        serialized.setTag("SeededMarkers", new NBTTagList());

        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);

        assertEquals(1, restored.getRecords().size());
        assertEquals("New Hobbiton", restored.getRecord(
                "lotr:waypoint:HOBBITON").getName());
        NBTTagCompound repaired = new NBTTagCompound();
        restored.writeToNBT(repaired);
        NBTTagList quarantine = repaired.getTagList(
                "Quarantine", Constants.NBT.TAG_COMPOUND);
        assertEquals(1, quarantine.tagCount());
        assertTrue(quarantine.getCompoundTagAt(0).hasKey(
                "OriginalData", Constants.NBT.TAG_COMPOUND));
        assertEquals("lotr:waypoint:HOBBITON",
                quarantine.getCompoundTagAt(0)
                        .getCompoundTag("OriginalData")
                        .getString("Id"));
    }

    @Test(expected = IllegalStateException.class)
    public void repositoryRejectsConflictingEqualRevision() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(
                definition("losttales:revision_guard")));
        LostTalesMapMarkerRecord current = data.getRecord(
                "losttales:revision_guard");

        data.saveRecord(current.toBuilder()
                .name("Conflicting write")
                .revision(current.getRevision())
                .build());
    }

    @Test(expected = IllegalStateException.class)
    public void repositoryRejectsLogicalLotrDuplicateWrite() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(
                definition("lotr:waypoint:HOBBITON")));
        LostTalesMapMarkerRecord duplicate =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("LOTR:WAYPOINT:hobbiton"))
                        .toBuilder()
                        .revision(2L)
                        .build();

        data.saveRecord(duplicate);
    }

    @Test
    public void updatedJsonDoesNotOverwriteSavedMarkerState() {
        LostTalesMapMarkerDefinition original =
                definition("losttales:reconciled", 64.0D);
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(original));
        data.saveRecord(data.getRecord(original.getId())
                .withGenerationState(
                        LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED));

        NBTTagCompound serialized = new NBTTagCompound();
        data.writeToNBT(serialized);
        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);
        LostTalesMapMarkerDefinition updated =
                definition("losttales:reconciled",
                        LostTalesMapMarkerDefinition.AUTOMATIC_Y);

        restored.seedDefinitions(Collections.singleton(updated));
        LostTalesMapMarkerRecord saved =
                restored.getRecord(updated.getId());
        assertEquals(64.0D, saved.getY(), 0.0D);
        assertEquals(LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED,
                saved.getGenerationState());
    }

    @Test
    public void newerDataSkipsPresetSeedingAndRemainsPreserved() {
        NBTTagCompound futureData = new NBTTagCompound();
        futureData.setInteger("DataVersion",
                LostTalesMapMarkerNbtCodec.CURRENT_DATA_VERSION + 1);
        NBTTagCompound futurePayload = new NBTTagCompound();
        futurePayload.setString("Sentinel", "preserve-me");
        futureData.setTag("FuturePayload", futurePayload);

        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.readFromNBT(futureData);
        futurePayload.setString("Sentinel", "mutated-after-read");

        assertTrue(data.isReadOnlyForNewerVersion());
        assertFalse(data.seedDefinitions(Collections.singleton(
                definition("losttales:must-not-be-seeded"))));
        assertTrue(data.getRecords().isEmpty());

        NBTTagCompound preserved = new NBTTagCompound();
        data.writeToNBT(preserved);
        assertEquals(LostTalesMapMarkerNbtCodec.CURRENT_DATA_VERSION + 1,
                preserved.getInteger("DataVersion"));
        assertEquals("preserve-me", preserved
                .getCompoundTag("FuturePayload").getString("Sentinel"));
        assertFalse(preserved.hasKey("Markers"));
    }

    @Test
    public void linkedMarkerPositionIsRepairedToPhysicalWaystone() {
        LostTalesMapMarkerDefinition definition =
                definition("losttales:misaligned");
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(definition));
        LostTalesMapMarkerRecord misaligned =
                data.getRecord(definition.getId())
                        .withLink(0, 200, 70, -300,
                                UUID.randomUUID())
                        .toBuilder()
                        .position(0, 192.0D, 70.0D, -288.0D)
                        .revision(3L)
                        .build();
        NBTTagCompound serialized = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(
                serialized, Collections.singleton(misaligned),
                Collections.<NBTTagCompound>emptyList());
        serialized.setTag("SeededMarkers", new NBTTagList());
        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);
        LostTalesMapMarkerRecord repaired =
                restored.getRecord(definition.getId());

        assertEquals(200.0D, repaired.getX(), 0.0D);
        assertEquals(70.0D, repaired.getY(), 0.0D);
        assertEquals(-300.0D, repaired.getZ(), 0.0D);
        assertEquals(4L, repaired.getRevision());
    }

    @Test(expected = IllegalArgumentException.class)
    public void repositoryRejectsNewLinkedPositionMismatch() {
        LostTalesMapMarkerDefinition definition =
                definition("losttales:new_misalignment");
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(definition));
        data.saveRecord(data.getRecord(definition.getId())
                .withLink(0, 200, 70, -300, UUID.randomUUID())
                .toBuilder()
                .position(0, 201.0D, 70.0D, -300.0D)
                .revision(3L)
                .build());
    }

    /**
     * Two records linked to one block, which no write makes, are settled
     * once as the store loads: the higher revision keeps the block, the
     * other is quarantined whole, and saving again adds nothing.
     */
    @Test
    public void aSharedLinkIsQuarantinedOnceAsTheStoreLoads() {
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord first = LostTalesMapMarkerRecord
                .fromDefinition(definition("losttales:first_link"))
                .withLink(0, 200, 70, -300, token);
        LostTalesMapMarkerRecord second = LostTalesMapMarkerRecord
                .fromDefinition(definition("losttales:second_link"))
                .withLink(0, 200, 70, -300, token).toBuilder()
                .revision(9L).build();
        NBTTagCompound serialized = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(serialized,
                java.util.Arrays.asList(first, second),
                Collections.<NBTTagCompound>emptyList());
        serialized.setTag("SeededMarkers", new NBTTagList());

        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);

        assertEquals("losttales:second_link", restored.findByLinkedPosition(
                0, 200, 70, -300).getId());
        assertNull(restored.getRecord("losttales:first_link"));
        restored.saveRecord(restored.getRecord("losttales:second_link")
                .withGenerationState(LostTalesWaystoneGenerationState.PLACED));
        restored.saveRecord(restored.getRecord("losttales:second_link")
                .withGenerationState(LostTalesWaystoneGenerationState.PLACED));
        NBTTagCompound saved = new NBTTagCompound();
        restored.writeToNBT(saved);
        NBTTagList quarantine = saved.getTagList(
                "Quarantine", Constants.NBT.TAG_COMPOUND);
        assertEquals("every save keeps the one entry", 1,
                quarantine.tagCount());
        assertEquals("duplicate_waystone_link",
                quarantine.getCompoundTagAt(0).getString("Reason"));
        assertEquals("losttales:first_link", quarantine.getCompoundTagAt(0)
                .getCompoundTag("OriginalData").getString("Id"));
    }

    /**
     * A marker new to the files is seeded on a later seed, as the server
     * starts after an update or with a new server file; one seeded and
     * removed since never comes back, after a save too (MOD-17).
     */
    @Test
    public void aNewMarkerIsSeededLaterAndARemovedOneIsNot() {
        LostTalesMapMarkerWorldData data = new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(definition("losttales:first")));
        assertTrue(data.removeRecord("losttales:first"));

        assertTrue(data.seedDefinitions(java.util.Arrays.asList(
                definition("losttales:first"), definition("losttales:second"))));
        assertNull(data.getRecord("losttales:first"));
        assertNotNull(data.getRecord("losttales:second"));

        NBTTagCompound serialized = new NBTTagCompound();
        data.writeToNBT(serialized);
        assertEquals(2, serialized.getTagList("SeededMarkers",
                Constants.NBT.TAG_STRING).tagCount());
        LostTalesMapMarkerWorldData restored = new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);
        assertFalse(restored.seedDefinitions(java.util.Arrays.asList(
                definition("losttales:first"), definition("losttales:second"))));
        assertNull(restored.getRecord("losttales:first"));

        restored.noteSeeded("losttales:third");
        assertFalse(restored.seedDefinitions(Collections.singleton(
                definition("losttales:third"))));
    }

    /** A store that does not say which markers the files seeded is kept as it is and reads only. */
    @Test
    public void aStoreWithoutItsSeedingFlagReadsOnly() {
        NBTTagCompound serialized = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(serialized,
                Collections.singleton(LostTalesMapMarkerRecord.fromDefinition(
                        definition("losttales:flagless"))),
                Collections.<NBTTagCompound>emptyList());
        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);
        assertTrue(restored.isReadOnlyForNewerVersion());
        assertFalse(restored.seedDefinitions(Collections.singleton(
                definition("losttales:must-not-be-seeded"))));
        NBTTagCompound preserved = new NBTTagCompound();
        restored.writeToNBT(preserved);
        assertFalse(preserved.hasKey("SeededMarkers"));
        assertEquals(1, preserved.getTagList("Markers",
                Constants.NBT.TAG_COMPOUND).tagCount());
    }

    /**
     * The discovery scan finds a marker as far as its radius, up to 1,024
     * blocks across the ground, and no further, whatever the widest radius
     * in the world (WAY-15).
     */
    @Test
    public void discoveryFindsEachMarkerWithinItsOwnReach() {
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(java.util.Arrays.asList(
                definition("losttales:near", 64.0D, 8.0D, 0.0D),
                definition("losttales:town", 64.0D, 200.0D, 5000.0D),
                definition("losttales:wide", 64.0D, 1024.0D, -20000.0D),
                definition("losttales:huge", 64.0D, 50000.0D, 40000.0D)));

        assertEquals(ids("losttales:near"),
                ids(data.getDiscoveryCandidates(0, 32.0D, 7.0D, 1.0D)));
        assertEquals(ids(), ids(data.getDiscoveryCandidates(0, 32.0D, 9.0D, 1.0D)));
        assertEquals("Pathfinder widens every reach (PROG-06)", ids("losttales:near"),
                ids(data.getDiscoveryCandidates(0, 32.0D, 11.0D, 1.5D)));
        assertEquals(ids("losttales:town"),
                ids(data.getDiscoveryCandidates(0, 32.0D, 5000.0D + 299.0D, 1.5D)));
        assertEquals(ids("losttales:town"),
                ids(data.getDiscoveryCandidates(0, 32.0D, 5000.0D + 199.0D, 1.0D)));
        assertEquals(ids(),
                ids(data.getDiscoveryCandidates(0, 32.0D, 5000.0D + 201.0D, 1.0D)));
        assertEquals(ids("losttales:wide"),
                ids(data.getDiscoveryCandidates(0, 32.0D,
                        -20000.0D - 1023.0D, 1.0D)));
        assertEquals(ids(), ids(data.getDiscoveryCandidates(0, 32.0D,
                -20000.0D - 1025.0D, 1.0D)));
        assertEquals("a wider radius reaches only as far as the scan",
                ids("losttales:huge"), ids(data.getDiscoveryCandidates(0,
                        32.0D, 40000.0D + 1023.0D, 1.0D)));
        assertEquals(ids(), ids(data.getDiscoveryCandidates(0, 32.0D,
                40000.0D + 1100.0D, 1.0D)));
        assertEquals("another world holds none of them", ids(),
                ids(data.getDiscoveryCandidates(1, 32.0D, 7.0D, 1.0D)));
    }

    private static java.util.Set<String> ids(String... ids) {
        return new java.util.LinkedHashSet<String>(java.util.Arrays.asList(ids));
    }

    private static java.util.Set<String> ids(
            java.util.Collection<LostTalesMapMarkerRecord> records) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<String>();
        for (LostTalesMapMarkerRecord record : records) {
            ids.add(record.getId());
        }
        return ids;
    }

    private static LostTalesMapMarkerDefinition definition(String id) {
        return definition(id, 64.0D);
    }

    /** A discoverable bundled marker at x 32, {@code z}, found from {@code radius}. */
    private static LostTalesMapMarkerDefinition definition(
            String id, double y, double radius, double z) {
        return new LostTalesMapMarkerDefinition(
                id, id, "shack", "white",
                "Town", "", true, 0,
                32, y, z, 128, radius,
                false, true, false,
                LostTalesMapMarkerSource.CUSTOM_PRESET,
                false, "", 0);
    }

    private static LostTalesMapMarkerDefinition definition(
            String id, double y) {
        return new LostTalesMapMarkerDefinition(
                id, id, "fort", "white",
                "Town", "", true, 0,
                32, y, 32, 128, 8,
                true, true, false,
                LostTalesMapMarkerSource.CUSTOM_PRESET,
                true,
                "losttales:glowstone_house", 0);
    }
}
