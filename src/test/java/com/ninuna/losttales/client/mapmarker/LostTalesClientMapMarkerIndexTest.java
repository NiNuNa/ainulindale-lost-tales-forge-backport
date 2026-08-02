package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import java.util.Arrays;
import java.util.Collections;
import lotr.common.LOTRDimension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class LostTalesClientMapMarkerIndexTest {
    @Test
    public void questMarkerReplacesWorldMarkerInEveryPersistentIndex() {
        LostTalesClientMapMarkerIndex index =
                new LostTalesClientMapMarkerIndex();
        LostTalesMapMarkerData world = marker(
                "lotr:waypoint:hobbiton", "World", 10.0D,
                LostTalesMapMarkerSource.LOTR_ADAPTER);
        LostTalesMapMarkerData quest = marker(
                "lotr:waypoint:hobbiton", "Quest", 20.0D,
                LostTalesMapMarkerSource.QUEST_DYNAMIC);

        index.replaceWorldMarkers(Collections.singleton(world));
        index.replaceQuestMarkers(Collections.singleton(quest));
        LostTalesClientMapMarkerIndex.Snapshot snapshot =
                index.getPersistentSnapshot();

        assertEquals(1, snapshot.getAllMarkers().size());
        assertSame(quest, snapshot.getAllMarkers().get(0));
        assertSame(quest, snapshot.findById(world.getId()));
        assertSame(quest, snapshot.findMappedWaypointMarker(
                "HOBBITON", "", 0, 0));
        assertEquals(Collections.singleton(quest.getId()),
                snapshot.getMarkerIds());
    }

    @Test
    public void nativeLotrIdentityIsCanonicalAcrossIdCase() {
        LostTalesClientMapMarkerIndex index =
                new LostTalesClientMapMarkerIndex();
        LostTalesMapMarkerData world = marker(
                "lotr:waypoint:HOBBITON", "World", 10.0D,
                LostTalesMapMarkerSource.LOTR_ADAPTER);
        LostTalesMapMarkerData quest = marker(
                "LOTR:WAYPOINT:hobbiton", "Quest", 20.0D,
                LostTalesMapMarkerSource.QUEST_DYNAMIC);

        index.replaceWorldMarkers(Collections.singleton(world));
        index.replaceQuestMarkers(Collections.singleton(quest));

        assertEquals(1,
                index.getPersistentSnapshot().getAllMarkers().size());
        assertSame(quest, index.getPersistentSnapshot()
                .findById("lotr:waypoint:hobbiton"));
    }

    @Test
    public void partyMarkersAreMapOnlyAndCannotReplacePersistentMarkers() {
        LostTalesClientMapMarkerIndex index =
                new LostTalesClientMapMarkerIndex();
        LostTalesMapMarkerData world = marker(
                "party_go_here:shared", "World", 10.0D,
                LostTalesMapMarkerSource.CUSTOM_PRESET);
        LostTalesMapMarkerData collidingParty = marker(
                "party_go_here:shared", "Party Collision", 20.0D,
                LostTalesMapMarkerSource.QUEST_DYNAMIC);
        LostTalesMapMarkerData party = marker(
                "party_go_here:member", "Party", 30.0D,
                LostTalesMapMarkerSource.QUEST_DYNAMIC);

        index.replaceWorldMarkers(Collections.singleton(world));
        LostTalesClientMapMarkerIndex.Snapshot map = index.getMapSnapshot(
                Arrays.asList(collidingParty, party));

        assertEquals(1,
                index.getPersistentSnapshot().getAllMarkers().size());
        assertSame(world,
                index.getPersistentSnapshot().findById(world.getId()));
        assertEquals(2, map.getAllMarkers().size());
        assertSame(world, map.findById(world.getId()));
        assertSame(party, map.findById(party.getId()));
    }

    @Test
    public void replacingPersistentDomainInvalidatesMapMerge() {
        LostTalesClientMapMarkerIndex index =
                new LostTalesClientMapMarkerIndex();
        LostTalesMapMarkerData first = marker(
                "losttales:first", "First", 10.0D,
                LostTalesMapMarkerSource.CUSTOM_PRESET);
        LostTalesMapMarkerData second = marker(
                "losttales:second", "Second", 20.0D,
                LostTalesMapMarkerSource.CUSTOM_PRESET);
        LostTalesMapMarkerData party = marker(
                "party_go_here:member", "Party", 30.0D,
                LostTalesMapMarkerSource.QUEST_DYNAMIC);

        index.replaceWorldMarkers(Collections.singleton(first));
        assertEquals(2, index.getMapSnapshot(
                Collections.singleton(party)).getAllMarkers().size());
        index.replaceWorldMarkers(Collections.singleton(second));
        LostTalesClientMapMarkerIndex.Snapshot rebuilt =
                index.getMapSnapshot(Collections.singleton(party));

        assertEquals(2, rebuilt.getAllMarkers().size());
        assertSame(second, rebuilt.findById(second.getId()));
        assertSame(party, rebuilt.findById(party.getId()));
    }

    private static LostTalesMapMarkerData marker(
            String id, String name, double x,
            LostTalesMapMarkerSource source) {
        return new LostTalesMapMarkerData(
                id, name, "fort", "white", "Map Marker", "",
                true, LOTRDimension.MIDDLE_EARTH.dimensionID,
                x, LostTalesMapMarkerDefinition.AUTOMATIC_Y, x,
                128.0D, 8.0D,
                false, false, false, false, 0, source);
    }
}
