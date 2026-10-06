package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.network.packet.LostTalesServerStatusPacket;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesWords;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatServerStatusTest {
    private static final LostTalesWords ENGLISH = EnglishWords.INSTANCE;


    /**
     * The parts: the players against the cap, the address once written,
     * the speed, and the time up once the server has taken players.
     */
    @Test
    public void theStatusNamesPlayersAddressSpeedAndTimeUp() {
        assertEquals(Arrays.asList("3/20 players", "play.example.org", "20 TPS",
                        "up 2h 15m"),
                ChatServerStatus.partsOf(ENGLISH, 3, 20, "play.example.org", 20,
                        2L * 3600000L + 15L * 60000L));
        assertEquals(Arrays.asList("1 player", "20 TPS"),
                ChatServerStatus.partsOf(ENGLISH, 1, 0, "  ", 20, 0L));
        assertEquals(Arrays.asList("0 players", "0 TPS"),
                ChatServerStatus.partsOf(ENGLISH, -3, -1, null, -5, -1L));
        // An address is written by an operator but read by every player:
        // a status line's own cleaning holds it.
        assertEquals(Arrays.asList("3/20 players", "play.example.org", "20 TPS"),
                ChatServerStatus.partsOf(ENGLISH, 3, 20, "§cplay.example.org",
                        20, 0L));
    }

    /** The time up, to the minute, as the lang file words it. */
    @Test
    public void theTimeUpReadsToTheMinute() {
        assertEquals("2d 3h", ChatServerStatus.uptime(ENGLISH,
                2L * 86400000L + 3L * 3600000L));
        assertEquals("2h 15m", ChatServerStatus.uptime(ENGLISH,
                2L * 3600000L + 15L * 60000L));
        assertEquals("15m", ChatServerStatus.uptime(ENGLISH,
                15L * 60000L + 40000L));
        assertEquals("<1m", ChatServerStatus.uptime(ENGLISH, 40000L));
        assertEquals("<1m", ChatServerStatus.uptime(ENGLISH, -5L));
    }

    /** The ticks a second, from the time the last ticks took; never past twenty. */
    @Test
    public void ticksASecondComeFromTheTicksTimes() {
        assertEquals(20, ChatServerStatus.ticksPerSecond(new long[] {5000000L, 0L}));
        assertEquals(10, ChatServerStatus.ticksPerSecond(
                new long[] {100000000L, 100000000L}));
        assertEquals(20, ChatServerStatus.ticksPerSecond(new long[0]));
        assertEquals(20, ChatServerStatus.ticksPerSecond(null));
    }

    /** The status travels whole; a payload of any other shape is refused whole. */
    @Test
    public void theStatusPacketRoundTripsAndRefusesAnyOtherShape() {
        LostTalesServerStatusPacket sent = new LostTalesServerStatusPacket(3, 20,
                "play.example.org", 19, 90000L);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesServerStatusPacket read = new LostTalesServerStatusPacket();
        read.fromBytes(buffer.copy());
        assertFalse(read.isMalformed());
        assertEquals(3, read.getPlayers());
        assertEquals(20, read.getMaxPlayers());
        assertEquals("play.example.org", read.getAddress());
        assertEquals(19, read.getTicksPerSecond());
        assertEquals(90000L, read.getUpMillis());

        ByteBuf trailing = buffer.copy();
        trailing.writeByte(1);
        LostTalesServerStatusPacket longer = new LostTalesServerStatusPacket();
        longer.fromBytes(trailing);
        assertTrue(longer.isMalformed());
        assertEquals("", longer.getAddress());

        LostTalesServerStatusPacket shorter = new LostTalesServerStatusPacket();
        shorter.fromBytes(buffer.copy(0, buffer.readableBytes() - 1));
        assertTrue(shorter.isMalformed());

        ByteBuf fast = Unpooled.buffer();
        fast.writeInt(3);
        fast.writeInt(20);
        fast.writeByte(0);
        fast.writeByte(21);
        fast.writeLong(0L);
        LostTalesServerStatusPacket tooFast = new LostTalesServerStatusPacket();
        tooFast.fromBytes(fast);
        assertTrue("never more than twenty ticks a second", tooFast.isMalformed());
    }
}
