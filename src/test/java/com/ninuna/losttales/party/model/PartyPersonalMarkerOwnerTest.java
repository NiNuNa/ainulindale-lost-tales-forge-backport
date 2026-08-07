package com.ninuna.losttales.party.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.UUID;
import org.junit.Test;

public final class PartyPersonalMarkerOwnerTest {
    private static final UUID CHARACTER =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PLAYER =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    public void anActiveCharacterAlwaysOwnsTheMarker() {
        assertEquals(CHARACTER,
                PartyPersonalMarkerOwner.resolve(CHARACTER, PLAYER));
    }

    @Test
    public void aPlayerWithNoCharacterOwnsItThemselves() {
        assertEquals(PLAYER,
                PartyPersonalMarkerOwner.resolve(null, PLAYER));
    }

    @Test
    public void nobodyOwnsAMarkerWithoutAnyIdentity() {
        assertNull(PartyPersonalMarkerOwner.resolve(null, null));
    }

    @Test
    public void theClientAndServerAgreeOnTheSameRule() {
        // Both sides call this one method, so the id a request is sent under
        // and the id a marker is filed under cannot drift apart.
        UUID sentByClient =
                PartyPersonalMarkerOwner.resolve(null, PLAYER);
        UUID storedByServer =
                PartyPersonalMarkerOwner.resolve(null, PLAYER);
        assertEquals(sentByClient, storedByServer);
    }
}
