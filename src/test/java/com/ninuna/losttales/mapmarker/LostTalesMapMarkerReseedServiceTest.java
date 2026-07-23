package com.ninuna.losttales.mapmarker;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerReseedServiceTest {
    @Test
    public void unlinkedRecordIsResetToJsonValues() {
        LostTalesMapMarkerRecord existing =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("Old", 1.0D, 2.0D))
                        .toBuilder()
                        .name("Edited")
                        .revision(7L)
                        .build();

        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        definition("From JSON", 9.0D, 10.0D),
                        existing);

        assertEquals("From JSON", reseeded.getName());
        assertEquals(9.0D, reseeded.getX(), 0.0D);
        assertEquals(10.0D, reseeded.getZ(), 0.0D);
        assertEquals(8L, reseeded.getRevision());
    }

    @Test
    public void linkedRecordKeepsItsPhysicalIdentity() {
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord existing =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("Old", 1.0D, 2.0D))
                        .withLink(100, 1, 64, 2, token);

        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        definition("From JSON", 9.0D, 10.0D),
                        existing);

        assertEquals("From JSON", reseeded.getName());
        assertTrue(reseeded.isLinked());
        assertEquals(token, reseeded.getLinkToken());
        assertEquals(1.0D, reseeded.getX(), 0.0D);
        assertEquals(64.0D, reseeded.getY(), 0.0D);
        assertEquals(2.0D, reseeded.getZ(), 0.0D);
        assertEquals(LostTalesWaystoneGenerationState.PLACED,
                reseeded.getGenerationState());
        assertEquals(existing.getRevision() + 1L,
                reseeded.getRevision());
    }

    private static LostTalesMapMarkerDefinition definition(
            String name, double x, double z) {
        return new LostTalesMapMarkerDefinition(
                "lotr:waypoint:waymeet", name,
                "fort", "white", "Town", "",
                true, 100,
                x, LostTalesMapMarkerDefinition.AUTOMATIC_Y, z,
                128.0D, 8.0D,
                false, false, true,
                LostTalesMapMarkerSource.LOTR_ADAPTER,
                true, "losttales:glowstone_house");
    }
}
