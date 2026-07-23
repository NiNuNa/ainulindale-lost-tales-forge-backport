package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import net.minecraft.nbt.NBTTagCompound;

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
