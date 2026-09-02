package com.ninuna.losttales.network.packet;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every Discord member gets a sender id of their own inside the bridge's
 * namespace, so they can be ignored and muted one by one while every
 * such id is still told from a player's.
 */
public final class LostTalesChatMessagePacketDiscordSenderTest {

    @Test
    public void membersGetStableDistinctIdsInTheBridgesNamespace() {
        UUID sam = LostTalesChatMessagePacket.discordSenderId(
                "123456789012345678");
        UUID frodo = LostTalesChatMessagePacket.discordSenderId(
                "223456789012345678");
        assertEquals(sam, LostTalesChatMessagePacket.discordSenderId(
                " 123456789012345678 "));
        assertFalse(sam.equals(frodo));
        assertFalse(sam.equals(LostTalesChatMessagePacket.DISCORD_SENDER_ID));
        assertTrue(LostTalesChatMessagePacket.isDiscordSender(sam));
        assertTrue(LostTalesChatMessagePacket.isDiscordSender(frodo));
        assertTrue(LostTalesChatMessagePacket.isDiscordSender(
                LostTalesChatMessagePacket.DISCORD_SENDER_ID));
        assertEquals("123456789012345678",
                LostTalesChatMessagePacket.discordUserIdOf(sam));
        // Snowflakes use the full unsigned range.
        UUID huge = LostTalesChatMessagePacket.discordSenderId(
                "18446744073709551615");
        assertTrue(LostTalesChatMessagePacket.isDiscordSender(huge));
        assertEquals("18446744073709551615",
                LostTalesChatMessagePacket.discordUserIdOf(huge));
    }

    @Test
    public void anUnreadableIdFallsBackToTheBridgesOwn() {
        assertEquals(LostTalesChatMessagePacket.DISCORD_SENDER_ID,
                LostTalesChatMessagePacket.discordSenderId(null));
        assertEquals(LostTalesChatMessagePacket.DISCORD_SENDER_ID,
                LostTalesChatMessagePacket.discordSenderId(""));
        assertEquals(LostTalesChatMessagePacket.DISCORD_SENDER_ID,
                LostTalesChatMessagePacket.discordSenderId("not-a-snowflake"));
        assertEquals("", LostTalesChatMessagePacket.discordUserIdOf(
                LostTalesChatMessagePacket.DISCORD_SENDER_ID));
    }

    @Test
    public void playersAreNeverTakenForMembers() {
        assertFalse(LostTalesChatMessagePacket.isDiscordSender(null));
        assertFalse(LostTalesChatMessagePacket.isDiscordSender(
                UUID.fromString("12345678-1234-1234-1234-123456789abc")));
        assertFalse(LostTalesChatMessagePacket.isDiscordSender(
                UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes())));
        assertEquals("", LostTalesChatMessagePacket.discordUserIdOf(
                UUID.randomUUID()));
    }
}
