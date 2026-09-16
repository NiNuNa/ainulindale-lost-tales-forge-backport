package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * No chat identity is in no faction: the account and a character created
 * without one speak and read Faction chat in Unaligned, and a character
 * with one in its own.
 */
public final class ChatChannelFactionTest {

    @Test
    public void theAccountAndAFactionlessCharacterSpeakInUnaligned() {
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                ChatChannelPolicy.factionOf(null));
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                ChatChannelPolicy.factionOf(character("")));
        assertEquals("lotr:gondor", ChatChannelPolicy.factionOf(character(" LOTR:Gondor ")));
    }

    @Test
    public void theAdapterNormalizesOrFallsBackToUnaligned() {
        assertEquals("lotr:unaligned", LotrCharacterAdapter.UNALIGNED_FACTION_ID);
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                LotrCharacterAdapter.factionIdOrUnaligned(null));
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                LotrCharacterAdapter.factionIdOrUnaligned("gondor"));
        assertEquals("lotr:rohan", LotrCharacterAdapter.factionIdOrUnaligned("lotr:Rohan"));
    }

    private static RoleplayCharacter character(String faction) {
        return RoleplayCharacter.builder(new UUID(1L, 2L), new UUID(3L, 4L))
                .name("Aldric")
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .age(18)
                .startingFaction(faction)
                .build();
    }
}
