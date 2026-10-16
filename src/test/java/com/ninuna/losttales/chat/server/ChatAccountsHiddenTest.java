package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatBroadcastMarkers;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * While the server shows no accounts, an in-character mention reaches a
 * character by its name alone, a server line names characters without
 * their accounts, and the appearance a player is sent of a character
 * carries no account's name.
 */
public final class ChatAccountsHiddenTest {
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID BEREN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @After
    public void showAccountsAgain() {
        LostTalesConfig.showAccounts = true;
    }

    @Test
    public void anInCharacterMentionReachesTheCharacterByNameAlone() {
        List<ChatNamedPlayer> candidates = Arrays.asList(
                new ChatNamedPlayer(SAM, "Sam", BEREN, "Beren", "s"));
        assertSame(candidates, ChatMentionTargets.byCharacterOnly(ChatChannel.GLOBAL, candidates));
        LostTalesConfig.showAccounts = false;
        assertSame("out of character the account is who is named", candidates,
                ChatMentionTargets.byCharacterOnly(ChatChannel.OOC, candidates));
        List<ChatNamedPlayer> shown = ChatMentionTargets.byCharacterOnly(ChatChannel.GLOBAL,
                candidates);
        assertEquals("", shown.get(0).getAccount());
        assertEquals("Beren", shown.get(0).getIdentityName());
    }

    @Test
    public void aServerLineNamesACharacterWithoutItsAccount() {
        ChatNamedPlayer beren = new ChatNamedPlayer(SAM, "Sam", BEREN, "Beren", "s")
                .withoutAccount();
        ChatNamedPlayer back = ChatBroadcastMarkers.decodeNamed(
                ChatBroadcastMarkers.namedValue(beren));
        assertEquals("", back.getAccount());
        assertEquals(BEREN, back.getCharacterId());
        assertEquals("Beren", back.getIdentityName());
    }

    @Test
    public void aCharactersAppearanceCarriesNoAccountName() {
        CharacterAppearance beren = new CharacterAppearance(SAM, "Sam", "Beren", "human",
                "male", "skin", false, 0, "", 30, "", "");
        assertEquals("", beren.withoutAccountName().getAccountName());
        assertEquals("Beren", beren.withoutAccountName().getCharacterName());
        CharacterAppearance ghost = CharacterAppearance.forAccount(SAM, "Sam", "");
        assertSame(ghost, ghost.withoutAccountName());
    }
}
