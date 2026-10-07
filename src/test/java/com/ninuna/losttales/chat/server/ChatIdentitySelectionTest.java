package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The server's side of who a line speaks as: the copy it was typed in
 * names an identity, and only one the sender may wear is taken.
 */
public final class ChatIdentitySelectionTest {
    private static final UUID OWNER = new UUID(9L, 9L);

    @Test
    public void aLineSpeaksAsTheCharacterItNamesWhenTheSenderOwnsIt() {
        RoleplayCharacter played = character("Aldric");
        RoleplayCharacter other = character("Beren");
        ChatIdentitySelection.Worn worn = ChatIdentitySelection.decide(false,
                LostTalesChatSendPacket.IDENTITY_CHARACTER, played, other);
        assertFalse(worn.refused);
        assertSame(other, worn.character);
        assertTrue("a character the roster does not hold is refused",
                ChatIdentitySelection.decide(false,
                        LostTalesChatSendPacket.IDENTITY_CHARACTER, played, null).refused);
        assertTrue(ChatIdentitySelection.decide(false, 999, played, other).refused);
    }

    @Test
    public void theDefaultAndTheChannelsThatSpeakAsThePlayedCharacterWearIt() {
        RoleplayCharacter played = character("Aldric");
        RoleplayCharacter other = character("Beren");
        assertSame(played, ChatIdentitySelection.decide(false,
                LostTalesChatSendPacket.IDENTITY_DEFAULT, played, null).character);
        assertSame("Proximity and Fellowship wear the character played",
                played, ChatIdentitySelection.decide(true,
                        LostTalesChatSendPacket.IDENTITY_CHARACTER, played, other).character);
    }

    /** In character the account is never worn, and nobody without a character speaks. */
    @Test
    public void inCharacterNobodySpeaksWithoutACharacter() {
        assertTrue(ChatIdentitySelection.decide(false,
                LostTalesChatSendPacket.IDENTITY_ACCOUNT, null, null).refused);
        assertTrue(ChatIdentitySelection.decide(false,
                LostTalesChatSendPacket.IDENTITY_ACCOUNT, character("Aldric"), null).refused);
        assertTrue(ChatIdentitySelection.decide(true,
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null, null).refused);
    }

    private static RoleplayCharacter character(String name) {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(0)
                .name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .startingFaction("lotr:gondor")
                .build();
    }
}
