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
    public void removedPresetTombstoneIsNotReseeded() {
        LostTalesMapMarkerDefinition definition =
                new LostTalesMapMarkerDefinition(
                        "losttales:preset", "Preset", "fort", "white",
                        "Town", "", true, "", 0,
                        32, 64, 32, 128, 8,
                        true, true, false,
                        LostTalesMapMarkerSource.CUSTOM_PRESET,
                        true,
                        LostTalesMapMarkerDefinition.DEFAULT_WAYSTONE_STRUCTURE);
        LostTalesMapMarkerWorldData data =
                new LostTalesMapMarkerWorldData("test");
        data.seedDefinitions(Collections.singleton(definition));
        LostTalesMapMarkerRecord seeded =
                data.getRecord(definition.getId());
        assertNotNull(seeded);

        data.saveRecord(seeded.withLink(
                0, 32, 70, 32, UUID.randomUUID()).withRemoved("broken"));
        data.seedDefinitions(Collections.singleton(definition));

        assertEquals(LostTalesWaystoneGenerationState.REMOVED,
                data.getRecord(definition.getId()).getGenerationState());
        assertFalse(data.getRecord(definition.getId()).isActive());
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
    public void updatedPresetCoordinatesAndFailedGenerationAreReconciled() {
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
        LostTalesMapMarkerRecord reconciled =
                restored.getRecord(updated.getId());
        assertEquals(LostTalesMapMarkerDefinition.AUTOMATIC_Y,
                reconciled.getY(), 0.0D);
        assertEquals(LostTalesWaystoneGenerationState.NOT_ATTEMPTED,
                reconciled.getGenerationState());
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

    private static LostTalesMapMarkerDefinition definition(String id) {
        return definition(id, 64.0D);
    }

    private static LostTalesMapMarkerDefinition definition(
            String id, double y) {
        return new LostTalesMapMarkerDefinition(
                id, id, "fort", "white",
                "Town", "", true, "", 0,
                32, y, 32, 128, 8,
                true, true, false,
                LostTalesMapMarkerSource.CUSTOM_PRESET,
                true,
                LostTalesMapMarkerDefinition.DEFAULT_WAYSTONE_STRUCTURE);
    }
}
