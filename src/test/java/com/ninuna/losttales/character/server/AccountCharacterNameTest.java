package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The account character takes the account's name, unless a lore character
 * or one of the chat's voices goes by it: a lore character's full name is
 * its own, the account character's included.
 */
public final class AccountCharacterNameTest {

    @Before
    public void loadLore() {
        LoreCharacterRegistry.load(null);
    }

    @Test
    public void anOrdinaryAccountNameIsTakenAsItIs() {
        assertEquals("Steve", CharacterService.accountCharacterName("Steve"));
    }

    @Test
    public void aLoreNameIsNotTaken() {
        assertEquals("Gandalf the Wanderer",
                CharacterService.accountCharacterName("Gandalf"));
        assertEquals("gandalf_ the Wanderer",
                CharacterService.accountCharacterName("gandalf_"));
    }

    @Test
    public void aChatVoiceIsNotTaken() {
        assertEquals("Narrator the Wanderer",
                CharacterService.accountCharacterName("Narrator"));
    }
}
