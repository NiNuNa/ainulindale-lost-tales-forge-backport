package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The server's side of the shared chat identity: a line's explicit
 * identity must be the one selected, and what the client is told of the
 * selected identity's party.
 */
public final class ChatIdentitySelectionTest {
    /** A party is named after its leader's character; none has no name. */
    @Test
    public void thePartyIsNamedAfterItsLeader() {
        assertEquals("", ChatIdentitySelection.leaderName(null));
        UUID leader = new UUID(5L, 5L);
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        members.add(new PartyMember(leader, new UUID(6L, 6L), "Aldric", 1L,
                PartyColor.GREEN));
        Party party = new Party(new UUID(7L, 7L), leader, members, 1L, 0L,
                Party.CURRENT_DATA_VERSION);
        assertEquals("Aldric", ChatIdentitySelection.leaderName(party));
    }

    @Test
    public void staleExplicitIdentitiesCannotOverrideTheSharedSelection() {
        UUID selected = new UUID(1L, 1L);
        UUID other = new UUID(2L, 2L);
        assertTrue(ChatIdentitySelection.matches(selected,
                LostTalesChatSendPacket.IDENTITY_CHARACTER, selected));
        assertFalse(ChatIdentitySelection.matches(selected,
                LostTalesChatSendPacket.IDENTITY_CHARACTER, other));
        assertFalse(ChatIdentitySelection.matches(selected,
                LostTalesChatSendPacket.IDENTITY_ACCOUNT, null));
        assertFalse(ChatIdentitySelection.matches((UUID)null,
                LostTalesChatSendPacket.IDENTITY_CHARACTER, other));
        assertTrue(ChatIdentitySelection.matches((UUID)null,
                LostTalesChatSendPacket.IDENTITY_ACCOUNT, null));
        assertTrue(ChatIdentitySelection.matches(selected,
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null));
        assertFalse(ChatIdentitySelection.matches(selected, 999, selected));
    }
}
