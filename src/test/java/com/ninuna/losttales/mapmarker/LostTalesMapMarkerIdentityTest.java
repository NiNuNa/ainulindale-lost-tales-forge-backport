package com.ninuna.losttales.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerIdentityTest {
    @Test
    public void nativeLotrWaypointCodesUseOneCanonicalIdentity() {
        LostTalesMapMarkerIdentity world =
                LostTalesMapMarkerIdentity.create(
                        "lotr:waypoint:HOBBITON",
                        LostTalesMapMarkerIdentity.Authority.WORLD_RECORD);
        LostTalesMapMarkerIdentity quest =
                LostTalesMapMarkerIdentity.create(
                        "LOTR:WAYPOINT:hobbiton",
                        LostTalesMapMarkerIdentity.Authority.QUEST_PLAYER);

        assertTrue(world.isSameLogicalMarker(quest));
        assertEquals(world, quest);
        assertEquals("lotr:waypoint:hobbiton",
                world.getCanonicalKey());
        assertEquals(
                LostTalesMapMarkerIdentity.Authority.WORLD_RECORD,
                world.getAuthority());
    }

    @Test
    public void nonLotrIdsRetainExistingCaseSensitiveIdentity() {
        LostTalesMapMarkerIdentity first =
                LostTalesMapMarkerIdentity.create(
                        "losttales:Town",
                        LostTalesMapMarkerIdentity.Authority.WORLD_RECORD);
        LostTalesMapMarkerIdentity second =
                LostTalesMapMarkerIdentity.create(
                        "losttales:town",
                        LostTalesMapMarkerIdentity.Authority.WORLD_RECORD);

        assertFalse(first.isSameLogicalMarker(second));
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankMarkerIdIsRejected() {
        LostTalesMapMarkerIdentity.create(
                "  ",
                LostTalesMapMarkerIdentity.Authority.WORLD_RECORD);
    }
}
