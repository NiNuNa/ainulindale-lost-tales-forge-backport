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
                        "losttales:glowstone_house");
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(definition));
        LostTalesMapMarkerRecord seeded =
                data.getRecord(definition.getId());
        assertNotNull(seeded);
        assertTrue(data.getOrCreateLotrTravelId(
                definition.getId()) > 0);

        assertTrue(data.removeRecord(definition.getId()));
        assertEquals(0, data.getLotrTravelId(definition.getId()));
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

        assertEquals(first,
                restored.getLotrTravelId("losttales:first"));
        assertEquals(second,
                restored.getLotrTravelId("losttales:second"));
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
        assertEquals(travelId, data.getLotrTravelId(
                "LOTR:WAYPOINT:HOBBITON"));
        assertTrue(data.removeRecord(
                "LOTR:WAYPOINT:hobbiton"));
        assertEquals(null, data.getRecord(
                "lotr:waypoint:HOBBITON"));
        assertEquals(0, data.getLotrTravelId(
                "lotr:waypoint:hobbiton"));
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
        serialized.setBoolean("CatalogInitialized", true);

        LostTalesMapMarkerWorldData restored =
                new LostTalesMapMarkerWorldData("restored");
        restored.readFromNBT(serialized);

        assertEquals(1, restored.getRecords().size());
        assertEquals("New Hobbiton", restored.getRecord(
                "lotr:waypoint:HOBBITON").getName());
        assertEquals(1, restored.getQuarantinedEntryCount());
        NBTTagCompound repaired = new NBTTagCompound();
        restored.writeToNBT(repaired);
        NBTTagList quarantine = repaired.getTagList(
                "Quarantine", Constants.NBT.TAG_COMPOUND);
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
                        LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED,
                        "surface_not_level"));

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
        serialized.setBoolean("CatalogInitialized", true);
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

    private static LostTalesMapMarkerDefinition definition(String id) {
        return definition(id, 64.0D);
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
                "losttales:glowstone_house");
    }
}
