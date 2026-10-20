package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A reseed takes the file and keeps what an operator saved on the marker's page (WAY-16). */
public final class LostTalesMapMarkerReseedServiceTest {
    /** A marker nobody edited on its page takes its file whole. */
    @Test
    public void aMarkerNobodyEditedTakesItsFileWhole() {
        LostTalesMapMarkerRecord existing =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("Old", 1.0D, 2.0D))
                        .toBuilder()
                        .name("Changed elsewhere")
                        .revision(7L)
                        .build();

        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        updated("From JSON", 9.0D, 10.0D),
                        existing);

        assertEquals("From JSON", reseeded.getName());
        assertEquals("camp", reseeded.getIconName());
        assertEquals("A new description.", reseeded.getDescription());
        assertEquals(9.0D, reseeded.getX(), 0.0D);
        assertEquals(10.0D, reseeded.getZ(), 0.0D);
        assertEquals(8L, reseeded.getRevision());
        assertTrue(reseeded.getSavedSettings().isEmpty());
    }

    /**
     * What an operator saved on the page stays; every setting nobody
     * saved, the place and the structure come from the file.
     */
    @Test
    public void whatThePageSavedStaysAndTheRestComesFromTheFile() {
        LostTalesMapMarkerRecord seeded =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("Old", 1.0D, 2.0D));
        LostTalesMapMarkerRecord edited = seeded.withEditableSettings(
                settings(seeded, "Bree Gate", "red", 32.0D,
                        LostTalesMapMarkerVisibility.PRIVATE));
        assertEquals(EnumSet.of(LostTalesMapMarkerSetting.NAME,
                        LostTalesMapMarkerSetting.COLOR,
                        LostTalesMapMarkerSetting.DISCOVERY_RADIUS,
                        LostTalesMapMarkerSetting.ACCESS),
                edited.getSavedSettings());

        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        updated("From JSON", 9.0D, 10.0D), edited);

        assertEquals("Bree Gate", reseeded.getName());
        assertEquals("red", reseeded.getColorName());
        assertEquals(32.0D, reseeded.getDiscoveryRadius(), 0.0D);
        assertEquals(LostTalesMapMarkerVisibility.PRIVATE,
                reseeded.getVisibility());
        assertEquals("the file's icon", "camp", reseeded.getIconName());
        assertEquals("A new description.", reseeded.getDescription());
        assertEquals(256.0D, reseeded.getCompassFadeInRadius(), 0.0D);
        assertEquals(9.0D, reseeded.getX(), 0.0D);
        assertEquals("losttales:glowstone_house",
                reseeded.getWaystoneStructureType());
        assertEquals(edited.getSavedSettings(), reseeded.getSavedSettings());
        assertEquals(edited.getRevision() + 1L, reseeded.getRevision());
    }

    /** A setting saved back as it was is no edit, and the file's next value reaches it. */
    @Test
    public void aSettingSavedAsItWasIsNoEdit() {
        LostTalesMapMarkerRecord seeded =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("Old", 1.0D, 2.0D));
        LostTalesMapMarkerRecord saved = seeded.withEditableSettings(
                LostTalesMapMarkerEditableSettings.fromRecord(seeded));

        assertTrue(saved.getSavedSettings().isEmpty());
        assertEquals("From JSON", LostTalesMapMarkerReseedService.createRecord(
                updated("From JSON", 9.0D, 10.0D), saved).getName());
    }

    /** Shares come from the page alone, so a reseed keeps them, and a private marker stays shared. */
    @Test
    public void sharesStay() {
        UUID character = UUID.fromString(
                "20000000-0000-0000-0000-000000000002");
        UUID fellowship = UUID.fromString(
                "25000000-0000-0000-0000-000000000025");
        LostTalesMapMarkerRecord seeded =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("Old", 1.0D, 2.0D));
        LostTalesMapMarkerRecord shared = seeded
                .withEditableSettings(settings(seeded, "Old", "white", 8.0D,
                        LostTalesMapMarkerVisibility.PRIVATE))
                .withSharedCharacters(new LinkedHashSet<UUID>(
                        Collections.singleton(character)))
                .withSharedFellowships(new LinkedHashSet<UUID>(
                        Collections.singleton(fellowship)));
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                shared.getVisibility());

        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        updated("From JSON", 9.0D, 10.0D), shared);

        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                reseeded.getVisibility());
        assertEquals(Collections.singleton(character),
                reseeded.getSharedCharacterIds());
        assertEquals(Collections.singleton(fellowship),
                reseeded.getSharedFellowshipIds());
        assertEquals("nobody saved the name", "From JSON",
                reseeded.getName());
    }

    /** A marker the store does not hold yet comes from its file. */
    @Test
    public void aNewMarkerComesFromItsFile() {
        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        updated("From JSON", 9.0D, 10.0D), null);

        assertEquals("From JSON", reseeded.getName());
        assertEquals(LostTalesMapMarkerVisibility.PUBLIC,
                reseeded.getVisibility());
        assertFalse(reseeded.isLinked());
        assertEquals(1L, reseeded.getRevision());
    }

    @Test
    public void linkedRecordKeepsItsPhysicalIdentity() {
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord linked =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("Old", 1.0D, 2.0D))
                        .withLink(100, 1, 64, 2, token);
        LostTalesMapMarkerRecord existing = linked.withEditableSettings(
                settings(linked, "Bree Gate", "white", 8.0D,
                        LostTalesMapMarkerVisibility.PUBLIC));

        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        updated("From JSON", 9.0D, 10.0D),
                        existing);

        assertEquals("Bree Gate", reseeded.getName());
        assertEquals("camp", reseeded.getIconName());
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

    @Test
    public void reseedPreservesPersistedNativeLotrIdCasing() {
        LostTalesMapMarkerRecord existing =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition("lotr:waypoint:WAYMEET", "Old",
                                1.0D, 2.0D));

        LostTalesMapMarkerRecord reseeded =
                LostTalesMapMarkerReseedService.createRecord(
                        definition("LOTR:WAYPOINT:waymeet", "Updated",
                                9.0D, 10.0D),
                        existing);

        assertEquals("lotr:waypoint:WAYMEET", reseeded.getId());
        assertEquals("Updated", reseeded.getName());
        assertEquals(existing.getRevision() + 1L,
                reseeded.getRevision());
    }

    /** The page's settings off {@code record}, with a name, colour, discovery radius and access of its own. */
    private static LostTalesMapMarkerEditableSettings settings(
            LostTalesMapMarkerRecord record, String name, String color,
            double discoveryRadius, LostTalesMapMarkerVisibility access) {
        return new LostTalesMapMarkerEditableSettings(
                name, record.getIconName(), color,
                record.getCategoryName(), record.getDescription(),
                record.hasFastTravel(), record.getCompassFadeInRadius(),
                discoveryRadius, record.isHiddenUntilDiscovered(),
                record.isDiscoverable(), record.requiresRegionUnlock(),
                record.getPriority(), access);
    }

    private static LostTalesMapMarkerDefinition definition(
            String name, double x, double z) {
        return definition("lotr:waypoint:waymeet", name, x, z);
    }

    private static LostTalesMapMarkerDefinition definition(
            String id, String name, double x, double z) {
        return new LostTalesMapMarkerDefinition(
                id, name,
                "fort", "white", "Town", "",
                true, 100,
                x, LostTalesMapMarkerDefinition.AUTOMATIC_Y, z,
                128.0D, 8.0D,
                false, false, true,
                LostTalesMapMarkerSource.LOTR_ADAPTER,
                true, "losttales:glowstone_house", 0);
    }

    /** The file as an update brings it: another icon, description and compass radius. */
    private static LostTalesMapMarkerDefinition updated(
            String name, double x, double z) {
        return new LostTalesMapMarkerDefinition(
                "lotr:waypoint:waymeet", name,
                "camp", "white", "Town", "A new description.",
                true, 100,
                x, LostTalesMapMarkerDefinition.AUTOMATIC_Y, z,
                256.0D, 8.0D,
                false, false, true,
                LostTalesMapMarkerSource.LOTR_ADAPTER,
                true, "losttales:glowstone_house", 0);
    }
}
