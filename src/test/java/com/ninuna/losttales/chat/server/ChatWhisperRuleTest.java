package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Whispers reach anyone the world knows (CHAT-31): a person is found by
 * their account's name or by a character's, here or away, and addressed
 * as the identity the whisper names; a name that is not the account's
 * addresses the character that goes by it.
 */
public final class ChatWhisperRuleTest {
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALDRIC = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    public void anAccountsOwnNameAddressesTheAccount() {
        assertEquals("", LostTalesChatService.addressedAs("Steve", "steve", "", null));
        assertEquals("Aldric",
                LostTalesChatService.addressedAs("Steve", "Steve", "Aldric", null));
    }

    @Test
    public void aCharactersNameAddressesTheCharacter() {
        assertEquals("Aldric",
                LostTalesChatService.addressedAs("Steve", "Aldric", "", null));
        // A character named by id is addressed by it, whatever was typed.
        assertEquals("", LostTalesChatService.addressedAs("Steve", "Aldric", "",
                ALDRIC));
    }

    @Test
    public void aWhisperNamesThePersonAsTheIdentityAddressed() {
        LostTalesChatService.Whispered toAccount = new LostTalesChatService.Whispered(
                STEVE, "Steve", null,
                new LostTalesChatService.AddressedIdentity("Steve", null));
        ChatNamedPlayer named = toAccount.named();
        assertEquals(STEVE, named.getPlayerId());
        assertEquals("Steve", named.getAccount());
        assertNull(named.getCharacterId());
        // Someone away is sent nothing now: the history keeps the line.
        assertNull(toAccount.online);
    }
}
