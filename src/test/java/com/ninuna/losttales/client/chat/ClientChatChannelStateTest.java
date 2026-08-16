package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClientChatChannelStateTest {

    @After
    public void cleanUp() {
        ClientChatChannelState.clear();
        ClientCharacterRosterCache.clear();
    }

    @Test
    public void factionAvailabilityTracksTheActiveCharacter() {
        assertFalse(ClientChatChannelState.isAvailable(
                ChatChannel.FACTION));

        acceptRoster("lotr:gondor");
        assertTrue(ClientChatChannelState.isAvailable(
                ChatChannel.FACTION));
        ClientChatChannelState.select(ChatChannel.FACTION);

        acceptRoster("");
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.getSelected());
    }

    private static void acceptRoster(String factionId) {
        UUID ownerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        CharacterSummary character = new CharacterSummary(
                characterId, 0, "Arathorn", "human", "male",
                "human_male_0", 30, factionId, 1,
                0L, 1L, RoleplayCharacter.CURRENT_DATA_VERSION);
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                ownerId, 1, characterId, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                Collections.singletonList(character));
        ClientCharacterRosterCache.acceptRoster(0, snapshot);
    }
}
