package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertTrue;

/** A success carries the roster the client is sent; none is made without it. */
public final class CharacterOperationResultTest {

    @Test
    public void aSuccessCarriesItsRoster() {
        CharacterRoster roster = new CharacterRoster(new UUID(1L, 1L));
        assertTrue(CharacterOperationResult.success(true, roster, null).isSuccessful());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aSuccessWithoutARosterIsNeverMade() {
        CharacterOperationResult.success(true, null, null);
    }
}
