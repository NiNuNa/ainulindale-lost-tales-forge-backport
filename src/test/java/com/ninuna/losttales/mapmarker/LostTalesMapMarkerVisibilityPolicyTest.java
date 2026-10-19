package com.ninuna.losttales.mapmarker;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Who sees a marker: a manager always, the owning character, anyone while
 * it is public, and while it is shared the characters it is shared with
 * and the played characters in its fellowships of ours.
 */
public final class LostTalesMapMarkerVisibilityPolicyTest {

    /** The placing character owns it; the account's other characters are anyone else (WAY-08). */
    @Test
    public void theOwningCharacterSeesItAndTheAccountsOthersDoNot() {
        UUID aragorn = UUID.randomUUID();
        UUID strider = UUID.randomUUID();
        LostTalesMapMarkerRecord privateRecord = playerRecord(aragorn);

        assertTrue(canView(privateRecord, aragorn, false));
        assertFalse("another character of the same account is anyone else",
                canView(privateRecord, strider, false));
        assertFalse("nobody played sees nothing private",
                canView(privateRecord, null, false));
        assertTrue("a manager sees every waystone",
                canView(privateRecord, null, true));
    }

    /**
     * A lore figure keeps its character id from one holder to the next,
     * so the next holder plays the owner and sees its waystones, and the
     * one before, playing it no longer, does not.
     */
    @Test
    public void aLoreFiguresNextHolderInheritsItsWaystones() {
        UUID gandalf = UUID.randomUUID();
        UUID firstHoldersOwnCharacter = UUID.randomUUID();
        LostTalesMapMarkerRecord placed = playerRecord(gandalf);
        assertTrue("whoever holds the figure plays the owner",
                canView(placed, gandalf, false));
        assertFalse("the first holder, back on a character of its own",
                canView(placed, firstHoldersOwnCharacter, false));
        assertEquals(gandalf, placed.getOwnerCharacterId());
    }

    @Test
    public void aCharacterShareHoldsForThatCharacterAlone() {
        UUID owner = UUID.randomUUID();
        UUID aldric = UUID.randomUUID();
        UUID beren = UUID.randomUUID();
        LostTalesMapMarkerRecord shared = playerRecord(owner)
                .withSharedCharacters(Collections.singleton(aldric));
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                shared.getVisibility());
        assertTrue(canView(shared, aldric, false));
        assertFalse("a share holds for the character, not its account's others",
                canView(shared, beren, false));
        assertFalse(canView(shared, null, false));

        LostTalesMapMarkerRecord publicRecord = shared.toBuilder()
                .visibility(LostTalesMapMarkerVisibility.PUBLIC).build();
        assertTrue(canView(publicRecord, beren, false));
        assertTrue(canView(publicRecord, null, false));
    }

    /** A fellowship share holds while the played character is in that fellowship of ours (WAY-09). */
    @Test
    public void aFellowshipShareHoldsForThePlayedCharactersFellowships() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID fellowship = UUID.randomUUID();
        UUID otherFellowship = UUID.randomUUID();
        LostTalesMapMarkerRecord shared = playerRecord(owner)
                .withSharedFellowships(Collections.singleton(fellowship));

        assertTrue(LostTalesMapMarkerVisibilityPolicy.canView(shared,
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(member, false,
                        Arrays.asList(otherFellowship, fellowship))));
        assertFalse(LostTalesMapMarkerVisibilityPolicy.canView(shared,
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(member, false,
                        Collections.singleton(otherFellowship))));
        assertFalse("a played character in no fellowship",
                LostTalesMapMarkerVisibilityPolicy.canView(shared,
                        LostTalesMapMarkerVisibilityPolicy.Viewer.of(
                                member, false, null)));
    }

    /** Shared is derived from the shares, so the last share gone leaves it private (WAY-14). */
    @Test
    public void sharedTurnsBackIntoPrivateWhenTheLastShareGoes() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        LostTalesMapMarkerRecord shared = playerRecord(owner)
                .withSharedCharacters(Collections.singleton(guest));
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                shared.getVisibility());
        LostTalesMapMarkerRecord unshared = shared.withSharedCharacters(
                Collections.<UUID>emptySet());
        assertEquals(LostTalesMapMarkerVisibility.PRIVATE,
                unshared.getVisibility());
        assertFalse(canView(unshared, guest, false));

        LostTalesMapMarkerRecord publicWithShares = shared.toBuilder()
                .visibility(LostTalesMapMarkerVisibility.PUBLIC).build();
        assertEquals("public stays public whatever the shares",
                LostTalesMapMarkerVisibility.PUBLIC,
                publicWithShares.getVisibility());
        assertEquals("taken private again, its shares make it shared",
                LostTalesMapMarkerVisibility.SHARED,
                publicWithShares.toBuilder()
                        .visibility(LostTalesMapMarkerVisibility.PRIVATE)
                        .build().getVisibility());
    }

    private static boolean canView(LostTalesMapMarkerRecord record,
                                   UUID character, boolean manager) {
        return LostTalesMapMarkerVisibilityPolicy.canView(record, character,
                manager, Collections.<UUID>emptySet());
    }

    private static LostTalesMapMarkerRecord playerRecord(UUID owner) {
        return LostTalesMapMarkerRecord.createPlayerMarker(
                "losttales:player/test", "Aldric",
                owner, 0, 1, 64, 2, UUID.randomUUID());
    }
}
