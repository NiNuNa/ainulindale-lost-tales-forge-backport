package com.ninuna.losttales.mapmarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * What a player's snapshot holds: every marker it may see, a marker
 * hidden until discovered only once the character played discovered it,
 * and for a manager everything it may see (WAY-11).
 */
public final class LostTalesMapMarkerSnapshotSelectionTest {
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    public void aHiddenMarkerIsSentOnlyOnceDiscovered() {
        LostTalesMapMarkerRecord hidden = publicMarker("losttales:hidden", true);
        LostTalesMapMarkerRecord open = publicMarker("losttales:open", false);
        List<LostTalesMapMarkerRecord> records = Arrays.asList(hidden, open);
        LostTalesMapMarkerVisibilityPolicy.Viewer stranger =
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(
                        UUID.randomUUID(), false, null);

        assertEquals(Collections.singletonList("losttales:open"),
                ids(LostTalesMapMarkerSyncManager.select(records, stranger,
                        discovered())));
        assertEquals(Arrays.asList("losttales:hidden", "losttales:open"),
                ids(LostTalesMapMarkerSyncManager.select(records, stranger,
                        discovered("losttales:hidden"))));
        assertEquals("no discoveries known keeps every hidden marker back",
                Collections.singletonList("losttales:open"),
                ids(LostTalesMapMarkerSyncManager.select(records, stranger,
                        null)));
    }

    @Test
    public void aManagerIsSentEveryMarkerDiscoveredOrNot() {
        List<LostTalesMapMarkerRecord> records = Arrays.asList(
                publicMarker("losttales:hidden", true),
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/private", "Aragorn", OWNER,
                        0, 1.0D, 64.0D, 1.0D, UUID.randomUUID()));
        assertEquals(2, LostTalesMapMarkerSyncManager.select(records,
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(null, true, null),
                discovered()).size());
    }

    /** A placed waystone is hidden until discovered too, its owner included until it counts as found. */
    @Test
    public void aPrivateWaystoneReachesItsOwnerOnceFound() {
        LostTalesMapMarkerRecord placed =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/bree", "Aragorn", OWNER,
                        0, 1.0D, 64.0D, 1.0D, UUID.randomUUID());
        List<LostTalesMapMarkerRecord> records =
                Collections.singletonList(placed);
        LostTalesMapMarkerVisibilityPolicy.Viewer owner =
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(OWNER, false, null);
        assertEquals(0, LostTalesMapMarkerSyncManager.select(records, owner,
                discovered()).size());
        assertEquals(1, LostTalesMapMarkerSyncManager.select(records, owner,
                discovered("losttales:player/bree")).size());
        assertEquals("a stranger never, discovered or not", 0,
                LostTalesMapMarkerSyncManager.select(records,
                        LostTalesMapMarkerVisibilityPolicy.Viewer.of(
                                UUID.randomUUID(), false, null),
                        discovered("losttales:player/bree")).size());
    }

    /**
     * A waystone handed over counts as found by its new owner: the next
     * snapshot the owner's character is sent, played then or later, finds
     * it first, so a hidden one reaches its map at once (WAY-08).
     */
    @Test
    public void aWaystoneHandedOverIsFoundByItsNewOwner() {
        UUID faramir = UUID.randomUUID();
        LostTalesMapMarkerRecord handed =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/bree", "Aragorn", OWNER,
                        0, 1.0D, 64.0D, 1.0D, UUID.randomUUID())
                        .withOwner(faramir);
        LostTalesMapMarkerRecord notOwned = publicMarker("losttales:open", true);
        List<LostTalesMapMarkerRecord> records = Arrays.asList(handed, notOwned);
        LostTalesMapMarkerVisibilityPolicy.Viewer newOwner =
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(faramir, false, null);

        assertEquals(Collections.singletonList("losttales:player/bree"),
                LostTalesMapMarkerSyncManager.ownedNotFound(records, newOwner,
                        discovered()));
        assertEquals("found once, it is not found again",
                Collections.<String>emptyList(),
                LostTalesMapMarkerSyncManager.ownedNotFound(records, newOwner,
                        discovered("losttales:player/bree")));
        assertEquals("the old owner finds nothing it no longer owns",
                Collections.<String>emptyList(),
                LostTalesMapMarkerSyncManager.ownedNotFound(records,
                        LostTalesMapMarkerVisibilityPolicy.Viewer.of(
                                OWNER, false, null), discovered()));
        assertEquals("nobody played finds nothing",
                Collections.<String>emptyList(),
                LostTalesMapMarkerSyncManager.ownedNotFound(records,
                        LostTalesMapMarkerVisibilityPolicy.Viewer.of(
                                null, true, null), discovered()));
        assertEquals("once found, the hidden waystone is sent", 1,
                LostTalesMapMarkerSyncManager.select(
                        Collections.singletonList(handed), newOwner,
                        discovered("losttales:player/bree")).size());
    }

    private static LostTalesMapMarkerRecord publicMarker(String id,
                                                         boolean hidden) {
        return LostTalesMapMarkerRecord.builder(id,
                LostTalesMapMarkerSource.CUSTOM_PRESET)
                .name(id)
                .position(0, 1.0D, 64.0D, 1.0D)
                .discovery(hidden, true, false)
                .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                .build();
    }

    private static LostTalesMapMarkerSyncManager.Discoveries discovered(
            String... ids) {
        final Set<String> found = new HashSet<String>(Arrays.asList(ids));
        return new LostTalesMapMarkerSyncManager.Discoveries() {
            @Override
            public boolean has(String markerId) {
                return found.contains(markerId);
            }
        };
    }

    private static List<String> ids(
            List<LostTalesMapMarkerDefinition> definitions) {
        List<String> ids = new ArrayList<String>();
        for (LostTalesMapMarkerDefinition definition : definitions) {
            ids.add(definition.getId());
        }
        return ids;
    }
}
