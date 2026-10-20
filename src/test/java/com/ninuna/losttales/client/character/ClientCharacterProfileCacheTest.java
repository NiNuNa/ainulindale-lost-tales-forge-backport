package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.network.packet.character.CharacterProfilePacket;
import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A profile is asked for once while it is shown: a question waits for its
 * answer, a profile kept is not asked for again, and only a view opening
 * asks afresh (CHAR-18).
 */
public final class ClientCharacterProfileCacheTest {

    private static final UUID CHARACTER =
            UUID.fromString("c1000000-0000-0000-0000-00000000001c");

    @After
    public void clear() {
        ClientCharacterProfileCache.clear();
    }

    @Test
    public void aQuestionWaitsForItsAnswer() {
        assertTrue(ClientCharacterProfileCache.markAsked(CHARACTER, false));
        assertFalse(ClientCharacterProfileCache.markAsked(CHARACTER, false));
    }

    @Test
    public void aProfileKeptIsNotAskedForAgain() {
        assertTrue(ClientCharacterProfileCache.markAsked(CHARACTER, false));
        ClientCharacterProfileCache.accept(CharacterProfilePacket.of(
                CHARACTER, CharacterProfile.EMPTY));
        assertFalse(ClientCharacterProfileCache.markAsked(CHARACTER, false));
    }

    @Test
    public void aCharacterWithNoProfileIsNotAskedForAgain() {
        ClientCharacterProfileCache.accept(
                CharacterProfilePacket.unavailable(CHARACTER));
        assertFalse(ClientCharacterProfileCache.markAsked(CHARACTER, false));
    }

    @Test
    public void aViewOpeningAsksAfresh() {
        ClientCharacterProfileCache.accept(CharacterProfilePacket.of(
                CHARACTER, CharacterProfile.EMPTY));
        assertTrue(ClientCharacterProfileCache.markAsked(CHARACTER, true));
    }
}
