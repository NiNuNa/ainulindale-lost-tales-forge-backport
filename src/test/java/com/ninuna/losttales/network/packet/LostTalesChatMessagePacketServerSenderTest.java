package com.ninuna.losttales.network.packet;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The server's own sender id is one fixed id, told apart from every
 * player and from the Discord bridge and its members.
 */
public final class LostTalesChatMessagePacketServerSenderTest {

    @Test
    public void theServerIsOneIdOfItsOwn() {
        UUID server = LostTalesChatMessagePacket.SERVER_SENDER_ID;
        assertTrue(LostTalesChatMessagePacket.isServerSender(server));
        assertFalse(LostTalesChatMessagePacket.isServerSender(null));
        assertFalse(LostTalesChatMessagePacket.isServerSender(
                UUID.fromString("a0000000-0000-0000-0000-00000000000a")));
        assertFalse(LostTalesChatMessagePacket.isServerSender(
                LostTalesChatMessagePacket.DISCORD_SENDER_ID));
        assertFalse(LostTalesChatMessagePacket.isServerSender(
                LostTalesChatMessagePacket.discordSenderId("123456789012345678")));
        assertFalse(LostTalesChatMessagePacket.isDiscordSender(server));
        // The client's own lines have an id of their own beside it.
        UUID client = LostTalesChatMessagePacket.CLIENT_SENDER_ID;
        assertFalse(client.equals(server));
        assertTrue(LostTalesChatMessagePacket.isClientSender(client));
        assertFalse(LostTalesChatMessagePacket.isClientSender(server));
        assertTrue(LostTalesChatMessagePacket.isSystemSender(client));
        assertTrue(LostTalesChatMessagePacket.isSystemSender(server));
        assertFalse(LostTalesChatMessagePacket.isSystemSender(
                LostTalesChatMessagePacket.DISCORD_SENDER_ID));
        // Stable across runs: the id is derived from a fixed name.
        assertTrue(server.equals(UUID.nameUUIDFromBytes(
                "losttales:server".getBytes(
                        java.nio.charset.Charset.forName("UTF-8")))));
    }
}
