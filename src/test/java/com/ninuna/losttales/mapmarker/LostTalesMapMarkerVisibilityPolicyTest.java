package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerVisibilityPolicyTest {

    @Test
    public void ownerSharedPublicAndOperatorAccessAreDistinct() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID aldric = UUID.randomUUID();
        UUID beren = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        LostTalesMapMarkerRecord privateRecord = playerRecord(owner);

        assertTrue("the owner's account, whoever it plays",
                canView(privateRecord, owner, UUID.randomUUID(), false));
        assertFalse(canView(privateRecord, guest, aldric, false));
        assertTrue(canView(privateRecord, stranger, null, true));

        LostTalesMapMarkerRecord shared =
                privateRecord.withSharedCharacters(
                        Collections.singleton(aldric),
                        LostTalesMapMarkerVisibility.SHARED);
        assertTrue(canView(shared, guest, aldric, false));
        assertFalse("a share holds for the character, not its account's others",
                canView(shared, guest, beren, false));
        assertFalse(canView(shared, guest, null, false));
        assertTrue("whoever plays the character, a lore figure's next holder too",
                canView(shared, stranger, aldric, false));

        LostTalesMapMarkerRecord publicRecord =
                shared.toBuilder()
                        .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                        .build();
        assertTrue(canView(publicRecord, stranger, null, false));
    }

    @Test
    public void sharedFellowshipGrantsCurrentMembersAccess() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID fellowship = UUID.randomUUID();
        UUID otherFellowship = UUID.randomUUID();
        LostTalesMapMarkerRecord shared = playerRecord(owner)
                .withSharedFellowships(
                        Collections.singleton(fellowship),
                        LostTalesMapMarkerVisibility.SHARED);

        assertTrue(LostTalesMapMarkerVisibilityPolicy.canView(
                shared, member, null, false,
                Collections.singleton(fellowship)));
        assertFalse(LostTalesMapMarkerVisibilityPolicy.canView(
                shared, member, null, false,
                Collections.singleton(otherFellowship)));
        assertFalse(LostTalesMapMarkerVisibilityPolicy.canView(
                shared, member, null, false,
                Collections.<UUID>emptySet()));
    }

    private static boolean canView(LostTalesMapMarkerRecord record,
                                   UUID account, UUID character,
                                   boolean operator) {
        return LostTalesMapMarkerVisibilityPolicy.canView(record, account,
                character, operator, Collections.<UUID>emptySet());
    }

    private static LostTalesMapMarkerRecord playerRecord(UUID owner) {
        return LostTalesMapMarkerRecord.createPlayerMarker(
                "losttales:player/test", "Aldric",
                owner, 0, 1, 64, 2, UUID.randomUUID());
    }
}
