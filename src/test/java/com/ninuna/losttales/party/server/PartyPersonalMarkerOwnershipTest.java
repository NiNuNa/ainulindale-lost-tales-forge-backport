package com.ninuna.losttales.party.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.character.storage.CharacterWorldData;
import java.util.UUID;
import org.junit.Test;

/**
 * A personal map marker may be filed under a player instead of a character.
 *
 * <p>Roleplay characters are optional — an account can be played as itself —
 * so the referential-integrity pass has to recognise an account as an owner.
 * Treating one as an orphan quarantined and deleted the marker from under a
 * character-less player within seconds of them placing it.</p>
 */
public final class PartyPersonalMarkerOwnershipTest {
    @Test
    public void aPlayerWithNoCharacterStillOwnsTheirMarker() {
        UUID playerId = UUID.randomUUID();
        CharacterWorldData data = new CharacterWorldData(
                CharacterWorldData.DATA_NAME);
        data.getOrCreateRoster(playerId);

        PartyService.CharacterIndex index =
                PartyService.getInstance()
                        .buildCharacterIndex(data);

        assertTrue("an account is an owner in its own right",
                index.hasOwner(playerId));
    }

    @Test
    public void anIdBelongingToNeitherAPlayerNorACharacterHasNoOwner() {
        UUID playerId = UUID.randomUUID();
        CharacterWorldData data = new CharacterWorldData(
                CharacterWorldData.DATA_NAME);
        data.getOrCreateRoster(playerId);

        PartyService.CharacterIndex index =
                PartyService.getInstance()
                        .buildCharacterIndex(data);

        assertFalse("a marker left by a deleted character is still an orphan",
                index.hasOwner(UUID.randomUUID()));
        assertFalse(index.hasOwner(null));
    }
}
