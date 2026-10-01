package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The server's side of the shared chat identity: a line's explicit
 * identity must be the one selected.
 */
public final class ChatIdentitySelectionTest {
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
